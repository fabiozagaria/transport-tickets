package it.fabio.transport.api;

import it.fabio.transport.application.*;
import it.fabio.transport.domain.*;
import it.fabio.transport.security.AccountPrincipal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TicketController {
    private final TicketService service;
    private final TransportDirectory directory;
    public TicketController(TicketService service,TransportDirectory directory) { this.service=service; this.directory=directory; }
    @GetMapping("/departments") public Object departments() { return directory.departments(); }
    @GetMapping("/operators") public Object operators() { return directory.users().stream().filter(u->u.role()==UserRole.OPERATOR).map(u->Map.of("id",u.id(),"username",u.username())).toList(); }
    @GetMapping("/tickets") public Object list(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.list(principal.account().user()).stream().map(TicketPresenter::snapshot).toList();
    }
    @GetMapping("/tickets/{id}") public Object get(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable UUID id) {
        return TicketPresenter.snapshot(service.get(principal.account().user(),id));
    }
    @PostMapping(value="/tickets",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> create(@AuthenticationPrincipal AccountPrincipal principal,@RequestBody byte[] body) {
        var form=form(body,Set.of("patientCode","originId","destinationId","priority","scheduledAt"));
        var result=service.create(principal.account().user(),new CreateTicket(required(form,"patientCode"),UUID.fromString(required(form,"originId")),
                UUID.fromString(required(form,"destinationId")),TicketPriority.valueOf(required(form,"priority")),
                form.containsKey("scheduledAt") ? Instant.parse(required(form,"scheduledAt")) : null));
        return ResponseEntity.created(URI.create("/api/tickets/"+result.id())).body(TicketPresenter.snapshot(result));
    }
    @PostMapping("/tickets/{id}/{action}")
    public Object act(@AuthenticationPrincipal AccountPrincipal principal,@PathVariable UUID id,@PathVariable String action,
                      @RequestBody(required=false) byte[] body,@RequestHeader(value="Content-Type",required=false) String type) {
        TicketAction operation;
        try { operation=TicketAction.valueOf(action.toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException e) { throw new HttpFailure(404,"Azione inesistente"); }
        if(!action.equals(operation.name().toLowerCase(Locale.ROOT))) throw new HttpFailure(404,"Azione inesistente");
        if(body!=null && body.length>0 && (type==null || !type.split(";",2)[0].trim().equalsIgnoreCase(MediaType.APPLICATION_FORM_URLENCODED_VALUE)))
            throw new HttpFailure(415,"Usare application/x-www-form-urlencoded");
        var form=form(body,operation==TicketAction.ASSIGN ? Set.of("operatorId") : operation==TicketAction.IDENTIFY ? Set.of("patientCode") : Set.of());
        return TicketPresenter.snapshot(service.act(principal.account().user(),id,operation,
                operation==TicketAction.ASSIGN ? UUID.fromString(required(form,"operatorId")) : null,
                operation==TicketAction.IDENTIFY ? required(form,"patientCode") : null));
    }
    private static Map<String,String> form(byte[] body,Set<String> allowed) {
        if(body==null || body.length==0) return Map.of();
        if(body.length>8192) throw new HttpFailure(413,"Richiesta troppo grande");
        var result=new HashMap<String,String>();
        for(String pair:new String(body,StandardCharsets.UTF_8).split("&",-1)) {
            var pieces=pair.split("=",2); String key=URLDecoder.decode(pieces[0],StandardCharsets.UTF_8);
            if(!allowed.contains(key)) throw new IllegalArgumentException("Campo non consentito: "+key);
            if(result.putIfAbsent(key,pieces.length==2 ? URLDecoder.decode(pieces[1],StandardCharsets.UTF_8) : "")!=null)
                throw new IllegalArgumentException("Campo duplicato: "+key);
        }
        return result;
    }
    private static String required(Map<String,String> form,String key) {
        var value=form.get(key); if(value==null || value.isBlank()) throw new IllegalArgumentException("Campo obbligatorio: "+key); return value;
    }
    static final class HttpFailure extends RuntimeException {
        final int status; HttpFailure(int status,String message) { super(message); this.status=status; }
    }
}
