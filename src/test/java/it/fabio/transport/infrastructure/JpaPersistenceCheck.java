package it.fabio.transport.infrastructure;

import it.fabio.transport.application.*;
import it.fabio.transport.domain.*;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.*;

/** Integration checks requiring real MySQL and Redis; each run creates its own ticket. */
public final class JpaPersistenceCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String url = System.getenv("MYSQL_URL"), user = System.getenv("MYSQL_USER"), password = System.getenv("MYSQL_PASSWORD");
        var cache = new RedisTicketCache(System.getenv("REDIS_HOST"), 6379);
        TicketView imported=migrationCheck(url,user,password);
        try(var context=org.springframework.boot.SpringApplication.run(it.fabio.transport.TransportApplication.class,"--server.port=0","--DEMO_USERS_ENABLED=true","--DEMO_PASSWORD=local-demo-change-me")) {
        var tickets=context.getBean(it.fabio.transport.infrastructure.jpa.TicketDataRepository.class);
        var departments=context.getBean(it.fabio.transport.infrastructure.jpa.DepartmentDataRepository.class);
        var manager=context.getBean(org.springframework.transaction.PlatformTransactionManager.class);
        var first = new it.fabio.transport.infrastructure.jpa.JpaTicketRepository(tickets,departments,manager,cache);
        // Separate application instance and persistence context against the same database.
        try(var secondContext=org.springframework.boot.SpringApplication.run(it.fabio.transport.TransportApplication.class,"--server.port=0","--DEMO_USERS_ENABLED=true","--DEMO_PASSWORD=local-demo-change-me")) {
        var second=(TicketRepository)secondContext.getBean(TicketRepository.class);
        var directory = new DemoDirectory();
        var migrated=first.readTicket(new UUID(0,100),TicketView::from).orElseThrow();
        check(migrated.equals(imported),"JPA migration changed the complete snapshot");
        var scheduled=first.readTicket(new UUID(0,101),TicketView::from).orElseThrow();
        check(scheduled.scheduledAt().equals(imported.createdAt().plusSeconds(60)),"Scheduled time lost precision");
        var account=context.getBean(it.fabio.transport.security.AccountRepository.class).findByUsername("cut-demo").orElseThrow();
        check(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("local-demo-change-me",account.passwordHash()),"JPA account seed failed");
        var directoryJpa=context.getBean(it.fabio.transport.infrastructure.jpa.JpaUserDirectory.class);
        try(var c=java.sql.DriverManager.getConnection(url,user,password);var q=c.createStatement()) {q.executeUpdate("UPDATE app_users SET enabled=false,revision=revision+1 WHERE username='operator2-demo'");}
        check(!directoryJpa.findByUsername("operator2-demo").orElseThrow().enabled(),"Disabled account remained active");
        try {directoryJpa.operator(new UUID(0,4));throw new AssertionError("Disabled operator accepted");} catch(IllegalArgumentException expected) {}
        try(var c=java.sql.DriverManager.getConnection(url,user,password);var q=c.createStatement()) {q.executeUpdate("UPDATE app_users SET enabled=true,revision=revision+1 WHERE username='operator2-demo'");}
        check(directoryJpa.findByUsername("cut-demo").orElseThrow().equals(secondContext.getBean(it.fabio.transport.security.AccountRepository.class).findByUsername("cut-demo").orElseThrow()),"Bootstrap changed existing account");
        var a = new TicketService(first, directory, Clock.systemUTC());
        var b = new TicketService(second, directory, Clock.systemUTC());
        var department = directory.authenticate("department-demo");
        var cut = directory.authenticate("cut-demo");
        var operator = directory.authenticate("operator-demo");
        var other = directory.authenticate("operator2-demo");
        var created = a.create(department, new CreateTicket("FAKE-DB-😀", new UUID(0, 1), new UUID(0, 2), TicketPriority.URGENT, null));
        UUID id = created.id();
        check(created.equals(b.get(cut, id)), "Fresh repository cannot reload ticket");
        String key = "tickets:jpa:v1:" + id + ":0";
        check(cache.get(key) != null, "Redis was not populated");
        cache.put(key, new byte[] {1, 2, 3});
        check(created.equals(b.get(cut, id)), "Corrupt cache did not fall back to MySQL");
        try {
            first.withTicket(id, t -> {
                t.assign(cut, operator, java.time.Instant.now());
                throw new IllegalArgumentException("rollback test");
            });
            throw new AssertionError("Expected callback failure");
        } catch (IllegalArgumentException expected) { }
        check(created.equals(b.get(cut, id)), "Rollback did not preserve original ticket");
        var assignedSnapshot = a.act(cut, id, TicketAction.ASSIGN, operator.id(), null);
        check(assignedSnapshot.equals(b.get(cut, id)), "Assigned operator did not round-trip");
        check(b.get(operator, id).status() == TicketStatus.ASSIGNED, "Old cached version returned");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Boolean> one = () -> { start.await(); try { a.act(operator, id, TicketAction.ACCEPT, null, null); return true; } catch (IllegalStateException e) { return false; } };
            Callable<Boolean> two = () -> { start.await(); try { b.act(operator, id, TicketAction.ACCEPT, null, null); return true; } catch (IllegalStateException e) { return false; } };
            var f1 = pool.submit(one); var f2 = pool.submit(two); start.countDown();
            check(f1.get(10, TimeUnit.SECONDS) != f2.get(10, TimeUnit.SECONDS), "Both accepts succeeded or failed");
        } finally { pool.shutdownNow(); }
        check(b.get(cut, id).history().size() == 3, "Concurrent accepts added wrong history");
        a.act(cut, id, TicketAction.ASSIGN, other.id(), null);
        try {
            b.get(operator, id);
            throw new AssertionError("Stale cache grants former operator access");
        } catch (ApplicationFailure e) { check(e.kind() == ApplicationFailure.Kind.NOT_FOUND, "Wrong access error"); }
        a.act(other, id, TicketAction.ACCEPT, null, null);
        a.act(other, id, TicketAction.START, null, null);
        var before = b.get(cut, id);
        try { a.act(other, id, TicketAction.IDENTIFY, null, "WRONG"); throw new AssertionError("Wrong code accepted"); }
        catch (IllegalArgumentException expected) { }
        check(before.equals(b.get(cut, id)), "Failed identification changed stored ticket");
        a.act(other, id, TicketAction.IDENTIFY, null, "FAKE-DB-😀");
        for (var action : new TicketAction[] {TicketAction.DEPART, TicketAction.ARRIVE, TicketAction.COMPLETE}) a.act(other, id, action, null, null);
        check(b.get(cut, id).status() == TicketStatus.COMPLETED, "Stored lifecycle incomplete");
        var unavailableCache = new RedisTicketCache("127.0.0.1", 1);
        var fallback = new TicketService(new it.fabio.transport.infrastructure.jpa.JpaTicketRepository(tickets,departments,manager,unavailableCache), directory, Clock.systemUTC());
        check(fallback.get(cut, id).equals(b.get(cut, id)), "Redis outage blocked database read");
        race(first, second, directory, true);
        race(first, second, directory, false);
        System.out.println("MySQL/Redis checks passed: reload, cache, corrupt cache, rollback, cross-instance concurrency, stale access, full lifecycle, cache outage.");
    }
    }
    }
    private static TicketView migrationCheck(String url,String user,String password) throws Exception {
        var directory=new DemoDirectory();
        var ticket=new Ticket(new UUID(0,100),"FAKE-MIGRATION",directory.department(new UUID(0,1)),directory.department(new UUID(0,2)),directory.authenticate("department-demo"),TicketPriority.URGENT,java.time.Instant.parse("2026-01-01T00:00:00.123456789Z"),null);
        var cut=directory.authenticate("cut-demo");var operator=directory.authenticate("operator-demo");var at=ticket.getCreatedAt().plusSeconds(1);
        ticket.assign(cut,operator,at);ticket.accept(operator,at);ticket.start(operator,at);ticket.identifyPatient(operator,"FAKE-MIGRATION",at);ticket.depart(operator,at);ticket.arrive(operator,at);ticket.complete(operator,at);
        var legacy=new MySqlTicketRepository(url,user,password,null);legacy.add(ticket);
        legacy.add(new Ticket(new UUID(0,101),"FAKE-SCHEDULED",ticket.getOrigin(),ticket.getDestination(),ticket.getCreatedBy(),TicketPriority.NORMAL,ticket.getCreatedAt(),ticket.getCreatedAt().plusSeconds(60)));
        UUID invalid=new UUID(0,102);
        try(var c=java.sql.DriverManager.getConnection(url,user,password);var q=c.prepareStatement("INSERT INTO tickets(id,version,payload) VALUES (?,1,?)")) {q.setString(1,invalid.toString());q.setBytes(2,new byte[]{1,2,3});q.executeUpdate();}
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,user,password).baselineOnMigrate(true).baselineVersion("0").load();
        try {flyway.migrate();throw new AssertionError("Corrupt archive was accepted");} catch(org.flywaydb.core.api.FlywayException expected) {}
        try(var c=java.sql.DriverManager.getConnection(url,user,password);var q=c.createStatement();var rows=q.executeQuery("SELECT COUNT(*) FROM ticket_records")) {rows.next();check(rows.getInt(1)==0,"Failed import partially committed");}
        // Repair only this disposable test database after removing the deliberate corrupt fixture.
        try(var c=java.sql.DriverManager.getConnection(url,user,password);var q=c.prepareStatement("DELETE FROM tickets WHERE id=?")) {q.setString(1,invalid.toString());q.executeUpdate();}
        flyway.repair();flyway.migrate();flyway.migrate();
        try(var c=java.sql.DriverManager.getConnection(url,user,password);var q=c.createStatement();var rows=q.executeQuery("SELECT legacy_version,completed_nano,event_count FROM ticket_records WHERE id='00000000-0000-0000-0000-000000000064'")) {check(rows.next() && rows.getLong(1)==1 && rows.getInt(2)==123456789 && rows.getInt(3)==8,"Migration lost history/precision/version");}
        try(var c=java.sql.DriverManager.getConnection(url,user,password);var q=c.prepareStatement("UPDATE tickets SET version=version+1 WHERE id=?")) {q.setString(1,ticket.getId().toString());try {q.executeUpdate();throw new AssertionError("Old table still accepts writes");} catch(java.sql.SQLException expected) {}}
        try(var c=java.sql.DriverManager.getConnection(url,user,password);var q=c.prepareStatement("SELECT payload FROM legacy_tickets_archive WHERE id=?")) {q.setString(1,ticket.getId().toString());try(var rows=q.executeQuery()) {check(rows.next() && java.util.Arrays.equals(rows.getBytes(1),TicketCodec.encode(ticket)),"Archive payload changed");}}
        return TicketView.from(ticket);
    }
    private static void race(TicketRepository first, TicketRepository second,
                             DemoDirectory directory, boolean startFirst) throws Exception {
        var cut = directory.authenticate("cut-demo");
        var operator = directory.authenticate("operator-demo");
        var other = directory.authenticate("operator2-demo");
        var base = new TicketService(first, directory, Clock.systemUTC());
        var created = base.create(directory.authenticate("department-demo"),
                new CreateTicket("FAKE-RACE", new UUID(0, 1), new UUID(0, 2), TicketPriority.URGENT, null));
        base.act(cut, created.id(), TicketAction.ASSIGN, operator.id(), null);
        var accepted = base.act(operator, created.id(), TicketAction.ACCEPT, null, null);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempted = new CountDownLatch(1);
        TicketRepository gated = new TicketRepository() {
            public TicketView add(Ticket ticket) { return first.add(ticket); }
            public <T> java.util.Optional<T> withTicket(UUID id, java.util.function.Function<Ticket, T> callback) {
                return first.withTicket(id, ticket -> {
                    locked.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Race release timed out");
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                    return callback.apply(ticket);
                });
            }
            public <T> java.util.List<T> readAll(java.util.function.Function<Ticket, T> callback) { return first.readAll(callback); }
        };
        var winnerService = new TicketService(gated, directory, Clock.systemUTC());
        var loserService = new TicketService(second, directory, Clock.systemUTC());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var winner = pool.submit(() -> winnerService.act(startFirst ? operator : cut, created.id(),
                    startFirst ? TicketAction.START : TicketAction.ASSIGN, startFirst ? null : other.id(), null));
            check(locked.await(10, TimeUnit.SECONDS), "First request did not acquire row lock");
            var loser = pool.submit(() -> {
                attempted.countDown();
                try {
                    loserService.act(startFirst ? cut : operator, created.id(),
                            startFirst ? TicketAction.ASSIGN : TicketAction.START, startFirst ? other.id() : null, null);
                    return "success";
                } catch (ApplicationFailure e) { return e.kind().name(); }
                catch (IllegalStateException e) { return "conflict"; }
            });
            check(attempted.await(10, TimeUnit.SECONDS), "Second request not attempted");
            try { loser.get(200, TimeUnit.MILLISECONDS); throw new AssertionError("Second request bypassed row lock"); }
            catch (TimeoutException expected) { }
            release.countDown();
            winner.get(10, TimeUnit.SECONDS);
            check(loser.get(10, TimeUnit.SECONDS).equals(startFirst ? "conflict" : "NOT_FOUND"), "Wrong losing outcome");
            var result = loserService.get(cut, created.id());
            check(result.status() == (startFirst ? TicketStatus.STARTED : TicketStatus.ASSIGNED), "Wrong race state");
            check(result.assignedOperator().id().equals(startFirst ? operator.id() : other.id()), "Wrong race operator");
            check(result.history().size() == 4, "Rejected race operation added an event");
            check(result.firstAssignedAt().equals(accepted.firstAssignedAt()), "Race reset first assignment");
            try (var c = java.sql.DriverManager.getConnection(System.getenv("MYSQL_URL"), System.getenv("MYSQL_USER"), System.getenv("MYSQL_PASSWORD"));
                 var q = c.prepareStatement("SELECT revision FROM ticket_records WHERE id = ?")) {
                q.setString(1, created.id().toString());
                try (var rows = q.executeQuery()) { check(rows.next() && rows.getLong(1) == 3, "Rejected request changed version"); }
            }
        } finally { release.countDown(); pool.shutdownNow(); }
    }

}
