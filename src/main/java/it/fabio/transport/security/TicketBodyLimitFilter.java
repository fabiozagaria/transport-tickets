package it.fabio.transport.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound ticket bodies before Spring buffers them, including chunked requests. */
final class TicketBodyLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    TicketBodyLimitFilter(ObjectMapper mapper) { this.mapper=mapper; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(!request.getMethod().equals("POST") || !request.getServletPath().startsWith("/api/tickets")) {
            chain.doFilter(request,response); return;
        }
        if(request.getContentLengthLong()>8192) { SecurityConfiguration.json(response,413,"Richiesta troppo grande",mapper); return; }
        byte[] body=request.getInputStream().readNBytes(8193);
        if(body.length>8192) { SecurityConfiguration.json(response,413,"Richiesta troppo grande",mapper); return; }
        Map<String,String[]> parameters;
        try {
            var values=new LinkedHashMap<String,List<String>>();
            String contentType=request.getContentType();
            if(contentType!=null && contentType.split(";",2)[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded")) {
                String encoded=new String(body,StandardCharsets.UTF_8);
                if(request.getQueryString()!=null && !request.getQueryString().isEmpty())
                    encoded=encoded.isEmpty() ? request.getQueryString() : encoded+"&"+request.getQueryString();
                if(!encoded.isEmpty()) for(String pair:encoded.split("&",-1)) {
                    var parts=pair.split("=",2);
                    String key=URLDecoder.decode(parts[0],StandardCharsets.UTF_8);
                    String value=parts.length==2 ? URLDecoder.decode(parts[1],StandardCharsets.UTF_8) : "";
                    values.computeIfAbsent(key,ignored->new ArrayList<>()).add(value);
                }
            }
            parameters=new LinkedHashMap<>();
            values.forEach((key,value)->parameters.put(key,value.toArray(String[]::new)));
        } catch(IllegalArgumentException e) { SecurityConfiguration.json(response,400,"Form non valido",mapper); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public Map<String,String[]> getParameterMap() {
                var copy=new LinkedHashMap<String,String[]>(); parameters.forEach((key,value)->copy.put(key,value.clone()));
                return Collections.unmodifiableMap(copy);
            }
            @Override public Enumeration<String> getParameterNames() { return Collections.enumeration(parameters.keySet()); }
            @Override public String[] getParameterValues(String name) { return parameters.containsKey(name) ? parameters.get(name).clone() : null; }
            @Override public String getParameter(String name) { var values=parameters.get(name); return values==null ? null : values[0]; }
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public boolean isFinished() { return input.available()==0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous ticket requests only"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
        },response);
    }
}
