package it.fabio.transport.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.fabio.transport.application.*;
import it.fabio.transport.domain.*;
import it.fabio.transport.infrastructure.InMemoryTicketRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityFlowTest.TestConfig.class)
public class SecurityFlowTest {
    @TestConfiguration static class TestConfig {
        @Bean TestAccounts accounts(PasswordEncoder encoder) { return new TestAccounts(encoder); }
        @Bean TicketRepository repository() { return new InMemoryTicketRepository(); }
    }
    public static class TestAccounts implements AccountRepository,TransportDirectory {
        final Map<String,UserAccount> accounts=new ConcurrentHashMap<>();
        final DemoDirectory demo=new DemoDirectory();
        TestAccounts(PasswordEncoder encoder) {
            String hash=encoder.encode("test-password-123");
            demo.users().forEach(user->accounts.put(user.username(),new UserAccount(user,hash,true)));
            accounts.put("department2-demo",new UserAccount(new User(new UUID(0,5),"department2-demo",UserRole.DEPARTMENT,new UUID(0,1)),hash,true));
            accounts.put("radiology-demo",new UserAccount(new User(new UUID(0,6),"radiology-demo",UserRole.DEPARTMENT,new UUID(0,2)),hash,true));
        }
        public Optional<UserAccount> findByUsername(String name) { return Optional.ofNullable(accounts.get(name)); }
        public List<User> users() { return accounts.values().stream().filter(UserAccount::enabled).map(UserAccount::user).toList(); }
        public List<Department> departments() { return demo.departments(); }
        public Department department(UUID id) { return demo.department(id); }
        public User operator(UUID id) { return users().stream().filter(u->u.id().equals(id) && u.role()==UserRole.OPERATOR).findFirst().orElseThrow(()->new IllegalArgumentException("Operatore inesistente")); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TestAccounts accounts;
    @Autowired PasswordEncoder encoder;
    private record Session(MockHttpSession http,String token) {}
    private Session login(String username) throws Exception {
        var csrf=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)csrf.getRequest().getSession();
        String token=mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        String oldId=session.getId();
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN",token)
                .param("username",username).param("password","test-password-123")).andExpect(status().isOk());
        Assertions.assertNotEquals(oldId,session.getId(),"Session ID must rotate on login");
        var fresh=mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return new Session(session,mapper.readTree(fresh.getResponse().getContentAsString()).get("token").asText());
    }
    private ResultActions action(Session session,String path,String body) throws Exception {
        return mvc.perform(post(path).session(session.http()).header("X-CSRF-TOKEN",session.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).content(body));
    }
    private String create(Session session) throws Exception {
        var result=action(session,"/api/tickets","patientCode=FAKE-001&originId=00000000-0000-0000-0000-000000000001&destinationId=00000000-0000-0000-0000-000000000002&priority=URGENT")
                .andExpect(status().isCreated()).andReturn();
        return "/api/tickets/"+mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
    @Test void authenticationCsrfAndLogout() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tickets").header("Authorization","Bearer cut-demo")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").param("username","cut-demo").param("password","test-password-123")).andExpect(status().isForbidden());
        var token=mvc.perform(get("/api/auth/csrf")).andReturn();
        var anonymous=(MockHttpSession)token.getRequest().getSession();
        String value=mapper.readTree(token.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/login").session(anonymous).header("X-CSRF-TOKEN",value).param("username","cut-demo").param("password","wrong")).andExpect(status().isUnauthorized());
        var cut=login("cut-demo");
        mvc.perform(get("/api/auth/me").session(cut.http())).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUT")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(cut.http())).andExpect(status().isForbidden());
        action(cut,"/api/auth/logout","").andExpect(status().isOk());
        Assertions.assertTrue(cut.http().isInvalid());
        mvc.perform(get("/api/tickets").cookie(new jakarta.servlet.http.Cookie("JSESSIONID",cut.http().getId()))).andExpect(status().isUnauthorized());
    }
    @Test void lifecycleAndDepartmentAccess() throws Exception {
        var department=login("department-demo"); var colleague=login("department2-demo");
        var radiology=login("radiology-demo"); var cut=login("cut-demo");
        var operator=login("operator-demo"); var other=login("operator2-demo");
        String path=create(department);
        mvc.perform(get(path).session(colleague.http())).andExpect(status().isOk());
        mvc.perform(get(path).session(radiology.http())).andExpect(status().isNotFound());
        action(radiology,"/api/tickets","patientCode=FAKE&originId=00000000-0000-0000-0000-000000000001&destinationId=00000000-0000-0000-0000-000000000002&priority=NORMAL").andExpect(status().isForbidden());
        action(cut,"/api/tickets","x=y").andExpect(status().isForbidden());
        mvc.perform(post(path+"/assign").session(cut.http()).contentType(MediaType.APPLICATION_FORM_URLENCODED).content("operatorId=00000000-0000-0000-0000-000000000003")).andExpect(status().isForbidden());
        action(department,path+"/assign","operatorId=00000000-0000-0000-0000-000000000003").andExpect(status().isForbidden());
        action(cut,path+"/assign","operatorId=00000000-0000-0000-0000-000000000003").andExpect(status().isOk());
        action(other,path+"/accept","").andExpect(status().isNotFound());
        action(operator,path+"/start","").andExpect(status().isConflict());
        action(operator,path+"/accept","").andExpect(status().isOk());
        action(operator,path+"/start","").andExpect(status().isOk());
        action(cut,path+"/assign","operatorId=00000000-0000-0000-0000-000000000004").andExpect(status().isConflict());
        action(operator,path+"/identify","patientCode=WRONG").andExpect(status().isBadRequest());
        mvc.perform(get(path).session(operator.http())).andExpect(jsonPath("$.status").value("STARTED")).andExpect(jsonPath("$.history.length()").value(4));
        action(operator,path+"/identify","patientCode=FAKE-001").andExpect(status().isOk());
        for(String step:List.of("depart","arrive","complete")) action(operator,path+"/"+step,"").andExpect(status().isOk());
        mvc.perform(get(path).session(cut.http())).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.history.length()").value(8));
    }
    @Test void sessionRevokedWhenAccountDisabledOrChanged() throws Exception {
        var session=login("operator2-demo"); var original=accounts.accounts.get("operator2-demo");
        try {
            accounts.accounts.put("operator2-demo",new UserAccount(original.user(),original.passwordHash(),false));
            mvc.perform(get("/api/auth/me").session(session.http())).andExpect(status().isUnauthorized());
            Assertions.assertTrue(session.http().isInvalid());
            accounts.accounts.put("operator2-demo",original);
            session=login("operator2-demo");
            accounts.accounts.put("operator2-demo",new UserAccount(new User(original.user().id(),original.user().username(),UserRole.CUT),original.passwordHash(),true));
            mvc.perform(get("/api/auth/me").session(session.http())).andExpect(status().isUnauthorized());
        } finally { accounts.accounts.put("operator2-demo",original); }
    }
    @Test void requestValidationAndReassignment() throws Exception {
        var department=login("department-demo");var cut=login("cut-demo");var operator=login("operator-demo");var other=login("operator2-demo");
        action(department,"/api/tickets","x".repeat(8193)).andExpect(status().isPayloadTooLarge());
        action(department,"/api/tickets","priority=NORMAL&priority=URGENT").andExpect(status().isBadRequest());
        action(department,"/api/tickets","createdAt=2026-01-01T00%3A00%3A00Z").andExpect(status().isBadRequest());
        mvc.perform(post("/api/tickets").session(department.http()).header("X-CSRF-TOKEN",department.token()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnsupportedMediaType());
        String path=create(department);
        action(cut,path+"/assign","operatorId=00000000-0000-0000-0000-000000000003").andExpect(status().isOk());
        action(operator,path+"/accept","").andExpect(status().isOk());
        var before=mvc.perform(get(path).session(cut.http())).andReturn();
        action(cut,path+"/assign","operatorId=00000000-0000-0000-0000-000000000004").andExpect(status().isOk());
        action(operator,path+"/start","").andExpect(status().isNotFound());
        action(other,path+"/start","").andExpect(status().isConflict());
        var after=mvc.perform(get(path).session(cut.http())).andReturn();
        Assertions.assertEquals(mapper.readTree(before.getResponse().getContentAsString()).get("firstAssignedAt"),mapper.readTree(after.getResponse().getContentAsString()).get("firstAssignedAt"));
    }
    @Test void disabledLoginAndPasswordRevocation() throws Exception {
        var original=accounts.accounts.get("operator2-demo");
        try {
            accounts.accounts.put("operator2-demo",new UserAccount(original.user(),original.passwordHash(),false));
            mvc.perform(post("/api/auth/login").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                    .param("username","operator2-demo").param("password","test-password-123")).andExpect(status().isUnauthorized());
            accounts.accounts.put("operator2-demo",original);
            var session=login("operator2-demo");
            accounts.accounts.put("operator2-demo",new UserAccount(original.user(),encoder.encode("new-password-123"),true));
            mvc.perform(get("/api/auth/me").session(session.http())).andExpect(status().isUnauthorized());
        } finally { accounts.accounts.put("operator2-demo",original); }
    }
    @Test void invalidSessionCookieIsRejected() throws Exception {
        mvc.perform(get("/api/tickets").cookie(new jakarta.servlet.http.Cookie("JSESSIONID","expired-session"))).andExpect(status().isUnauthorized());
    }
}
