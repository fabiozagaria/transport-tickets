package it.fabio.transport.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.fabio.transport.application.RepositoryFailure;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(AccountRepository accounts) {
        return username->new AccountPrincipal(accounts.findByUsername(username).orElseThrow(()->new UsernameNotFoundException("Credenziali non valide")));
    }
    static void json(HttpServletResponse response,int status,String message,ObjectMapper mapper) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(),Map.of("message",message));
    }
    @Bean SecurityFilterChain security(HttpSecurity http, AccountRepository accounts, ObjectMapper mapper) throws Exception {
        var csrfHandler=new CsrfTokenRequestAttributeHandler() {
            @Override public String resolveCsrfTokenValue(HttpServletRequest request,CsrfToken token) {
                return request.getHeader(token.getHeaderName());
            }
        };
        http.csrf(csrf->csrf.csrfTokenRequestHandler(csrfHandler))
            .authorizeHttpRequests(auth->auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET,"/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/tickets").hasRole("DEPARTMENT")
                .requestMatchers("/api/operators").hasRole("CUT")
                .requestMatchers(HttpMethod.POST,"/api/tickets/*/assign").hasRole("CUT")
                .requestMatchers(HttpMethod.POST,"/api/tickets/*/accept","/api/tickets/*/start","/api/tickets/*/identify","/api/tickets/*/depart","/api/tickets/*/arrive","/api/tickets/*/complete").hasRole("OPERATOR")
                .anyRequest().authenticated())
            .exceptionHandling(errors->errors
                .authenticationEntryPoint((req,res,e)->json(res,401,"Autenticazione richiesta",mapper))
                .accessDeniedHandler((req,res,e)->json(res,403,"Accesso negato o token CSRF non valido",mapper)))
            .formLogin(login->login.loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth)->json(res,200,"Login eseguito",mapper))
                .failureHandler((req,res,e)->json(res,e instanceof org.springframework.security.authentication.InternalAuthenticationServiceException ? 503 : 401,
                        e instanceof org.springframework.security.authentication.InternalAuthenticationServiceException ? "Archivio utenti non disponibile" : "Credenziali non valide",mapper)))
            .logout(logout->logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth)->json(res,200,"Logout eseguito",mapper)))
            .requestCache(cache->cache.disable())
            .addFilterBefore(new TicketBodyLimitFilter(mapper),CsrfFilter.class)
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                    var authentication=SecurityContextHolder.getContext().getAuthentication();
                    if(authentication!=null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
                        try {
                            var current=accounts.findByUsername(principal.getUsername());
                            if(current.isEmpty() || !current.get().enabled() || !current.get().equals(principal.account())) {
                                SecurityContextHolder.clearContext();
                                var session=req.getSession(false); if(session!=null) session.invalidate();
                                json(res,401,"Sessione non più valida",mapper); return;
                            }
                        } catch(RepositoryFailure e) { json(res,503,"Archivio utenti non disponibile",mapper); return; }
                    }
                    chain.doFilter(req,res);
                }
            },AuthorizationFilter.class);
        return http.build();
    }
}
