package it.fabio.transport.application;

import org.junit.jupiter.api.Test;
import it.fabio.transport.infrastructure.TicketCodecCheck;

class DomainRegressionTest {
    @Test void serviceChecks() { TicketServiceCheck.main(new String[0]); }
    @Test void codecChecks() throws Exception { TicketCodecCheck.main(new String[0]); }
}
