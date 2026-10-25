package it.fabio.transport.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Import(SecurityFlowTest.TestConfig.class)
class RealServletFormTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Test void loginAndBoundedFormWorkWithRealTomcat() throws Exception {
        var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        var client=HttpClient.newBuilder().cookieHandler(cookies).build();
        String base="http://127.0.0.1:"+port;
        var csrf=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        String token=mapper.readTree(csrf.body()).get("token").asText();
        var login=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login"))
                .header("X-CSRF-TOKEN",token).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=department-demo&password=test-password-123")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,login.statusCode());
        csrf=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        token=mapper.readTree(csrf.body()).get("token").asText();
        String body="patientCode=FAKE%2B001&originId=00000000-0000-0000-0000-000000000001&destinationId=00000000-0000-0000-0000-000000000002&priority=URGENT";
        var create=post(client,base,token,body);
        assertEquals(201,create.statusCode(),create.body());
        assertEquals("FAKE+001",mapper.readTree(create.body()).get("patientCode").asText());
        assertEquals(400,post(client,base,token,body+"&priority=NORMAL").statusCode());
        assertEquals(400,post(client,base,token,"patientCode=%ZZ").statusCode());
        assertEquals(413,post(client,base,token,"x".repeat(8193)).statusCode());
    }
    private static HttpResponse<String> post(HttpClient client,String base,String token,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+"/api/tickets")).header("X-CSRF-TOKEN",token)
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
