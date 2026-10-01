package it.fabio.transport.security;

import it.fabio.transport.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Import(SecurityFlowTest.TestConfig.class)
class RepositoryErrorTest {
    @Autowired MockMvc mvc;
    @Autowired SecurityFlowTest.TestAccounts accounts;
    @MockitoBean TicketRepository repository;
    @Test void persistenceErrorsHaveDifferentStatuses() throws Exception {
        for(var kind:RepositoryFailure.Kind.values()) {
            when(repository.readTicket(any(),any())).thenThrow(new RepositoryFailure(kind,"sensitive details",null));
            mvc.perform(get("/api/tickets/"+UUID.randomUUID()).with(user(new AccountPrincipal(accounts.findByUsername("cut-demo").orElseThrow()))))
                    .andExpect(status().is(kind==RepositoryFailure.Kind.UNAVAILABLE ? 503 : 500))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
            reset(repository);
        }
    }
}
