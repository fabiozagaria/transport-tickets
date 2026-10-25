package it.fabio.transport;

import it.fabio.transport.application.*;
import it.fabio.transport.infrastructure.*;
import it.fabio.transport.infrastructure.jpa.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Clock;

@Configuration
public class BackendConfiguration {
    @Bean Clock clock() {return Clock.systemUTC();}
    @Bean TicketService ticketService(TicketRepository repository,TransportDirectory directory,Clock clock) {return new TicketService(repository,directory,clock);}
    @Configuration @Profile("!test")
    static class PersistentConfiguration {
        @Bean JpaUserDirectory userDirectory(UserDataRepository users,DepartmentDataRepository departments,PlatformTransactionManager manager,EntityManager em,Environment env,PasswordEncoder encoder) {
            var directory=new JpaUserDirectory(users,departments,manager,em);
            if(env.getProperty("DEMO_USERS_ENABLED",Boolean.class,false)) directory.seedDemo(env.getRequiredProperty("DEMO_PASSWORD"),encoder);
            return directory;
        }
        @Bean TicketRepository ticketRepository(TicketDataRepository tickets,DepartmentDataRepository departments,PlatformTransactionManager manager,Environment env) {
            String mode=env.getProperty("TICKET_STORAGE","mysql");
            if(mode.equals("memory")) return new InMemoryTicketRepository();
            if(!mode.equals("mysql")) throw new IllegalArgumentException("TICKET_STORAGE non valido");
            String host=env.getProperty("REDIS_HOST");
            return new JpaTicketRepository(tickets,departments,manager,host==null?null:new RedisTicketCache(host,env.getProperty("REDIS_PORT",Integer.class,6379)));
        }
    }
}
