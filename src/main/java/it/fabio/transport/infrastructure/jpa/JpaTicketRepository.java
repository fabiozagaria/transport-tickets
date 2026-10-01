package it.fabio.transport.infrastructure.jpa;

import it.fabio.transport.application.*;
import it.fabio.transport.domain.Ticket;
import it.fabio.transport.infrastructure.*;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.*;
import java.nio.ByteBuffer;

/** Domain callbacks and history updates share a locked transaction; Redis runs after it. */
public final class JpaTicketRepository implements TicketRepository {
    private final TicketDataRepository tickets;
    private final DepartmentDataRepository departments;
    private final TransactionTemplate writes,reads;
    private final RedisTicketCache cache;
    public JpaTicketRepository(TicketDataRepository tickets,DepartmentDataRepository departments,PlatformTransactionManager manager,RedisTicketCache cache) {
        this.tickets=tickets;this.departments=departments;this.cache=cache;
        writes=new TransactionTemplate(manager);writes.setTimeout(10);
        reads=new TransactionTemplate(manager);reads.setReadOnly(true);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);reads.setTimeout(10);
    }
    private <T> T transaction(TransactionTemplate template,Supplier<T> work) {
        try { return template.execute(status->work.get()); }
        catch(org.springframework.dao.OptimisticLockingFailureException e) { throw new IllegalStateException("Ticket modificato da un’altra richiesta",e); }
        catch(DataAccessException | TransactionException e) { throw new RepositoryFailure(RepositoryFailure.Kind.UNAVAILABLE,"Archivio ticket non disponibile",e); }
    }
    @Override public TicketView add(Ticket t) {
        return transaction(writes,()-> {
            var row=TicketRowMapper.create(t,departments.getReferenceById(t.getOrigin().id()),departments.getReferenceById(t.getDestination().id()));
            tickets.saveAndFlush(row);return TicketView.from(t);
        });
    }
    @Override public <T> Optional<T> withTicket(UUID id,Function<Ticket,T> operation) {
        return transaction(writes,()->tickets.findForUpdate(id).map(row->{
            Ticket ticket=TicketRowMapper.read(row);T result=operation.apply(ticket);
            TicketRowMapper.update(row,ticket);tickets.flush();return result;
        }));
    }
    private record Snapshot(long revision,Ticket ticket) {}
    private static String key(UUID id,long version) { return "tickets:jpa:v1:"+id+":"+version; }
    @Override public <T> Optional<T> readTicket(UUID id,Function<Ticket,T> snapshot) {
        if(cache!=null) {
            Optional<Long> revision=transaction(reads,()->tickets.findRevision(id));
            if(revision.isEmpty()) return Optional.empty();
            byte[] cached=cache.get(key(id,revision.get()));
            Ticket cachedTicket=null;
            if(cached!=null && cached.length>8) {
                try {
                    var bytes=ByteBuffer.wrap(cached);long version=bytes.getLong();byte[] payload=new byte[bytes.remaining()];bytes.get(payload);
                    Ticket t=TicketCodec.decode(payload);
                    if(version==revision.get() && t.getId().equals(id)) cachedTicket=t;
                } catch(RepositoryFailure | IllegalArgumentException ignored) { /* recover from invalid cache */ }
            }
            if(cachedTicket!=null) return Optional.ofNullable(snapshot.apply(cachedTicket));
        }
        Optional<Snapshot> loaded=transaction(reads,()->tickets.findById(id).map(row->new Snapshot(row.revision,TicketRowMapper.read(row))));
        if(loaded.isEmpty()) return Optional.empty();
        var value=loaded.get();
        if(cache!=null) { byte[] payload=TicketCodec.encode(value.ticket());cache.put(key(id,value.revision()),ByteBuffer.allocate(8+payload.length).putLong(value.revision()).put(payload).array()); }
        return Optional.ofNullable(snapshot.apply(value.ticket()));
    }
    @Override public <T> List<T> readAll(Function<Ticket,T> snapshot) {
        return transaction(reads,()->tickets.findAll().stream().map(TicketRowMapper::read).map(snapshot).toList());
    }
}
