package it.fabio.transport.api;

import it.fabio.transport.security.AccountPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @GetMapping("/csrf") public Object csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/me") public Object me(@AuthenticationPrincipal AccountPrincipal principal) {
        var user=principal.account().user(); var result=new java.util.LinkedHashMap<String,Object>();
        result.put("id",user.id());result.put("username",user.username());result.put("role",user.role());result.put("departmentId",user.departmentId());return result;
    }
}
