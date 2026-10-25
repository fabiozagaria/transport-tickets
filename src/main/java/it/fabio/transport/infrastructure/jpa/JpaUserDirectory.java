package it.fabio.transport.infrastructure.jpa;

import it.fabio.transport.application.*;
import it.fabio.transport.domain.*;
import it.fabio.transport.security.*;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import java.util.function.Supplier;

public final class JpaUserDirectory implements TransportDirectory,AccountRepository {
    private final UserDataRepository users;
    private final DepartmentDataRepository departments;
    private final TransactionTemplate transactions;
    private final EntityManager em;
    public JpaUserDirectory(UserDataRepository users,DepartmentDataRepository departments,PlatformTransactionManager manager,EntityManager em) {
        this.users=users;this.departments=departments;this.transactions=new TransactionTemplate(manager);this.em=em;
        transactions.setTimeout(10);
    }
    private <T> T run(Supplier<T> work) {
        try {return transactions.execute(status->work.get());}
        catch(DataAccessException | TransactionException e) {throw new RepositoryFailure(RepositoryFailure.Kind.UNAVAILABLE,"Archivio utenti non disponibile",e);}
    }
    public Optional<UserAccount> findByUsername(String name) {return run(()->users.findByUsername(name).map(UserRow::account));}
    public List<User> users() {return run(()->users.findByEnabledTrueOrderByUsernameAsc().stream().map(UserRow::domain).toList());}
    public List<Department> departments() {return run(()->departments.findAll().stream().map(DepartmentRow::domain).toList());}
    public Department department(UUID id) {return run(()->departments.findById(id).map(DepartmentRow::domain).orElseThrow(()->new IllegalArgumentException("Reparto inesistente")));}
    public User operator(UUID id) {return run(()->users.findById(id).filter(u->u.enabled && u.role.equals("OPERATOR")).map(UserRow::domain).orElseThrow(()->new IllegalArgumentException("Operatore inesistente")));}
    public void seedDemo(String password,PasswordEncoder encoder) {
        if(password==null || password.length()<12) throw new IllegalArgumentException("DEMO_PASSWORD richiede almeno 12 caratteri");
        // INSERT IGNORE makes bootstrap safe across instances without resetting existing accounts.
        var demo=new DemoDirectory();var accounts=new ArrayList<>(demo.users());
        accounts.add(new User(new UUID(0,5),"department2-demo",UserRole.DEPARTMENT,new UUID(0,1)));
        accounts.add(new User(new UUID(0,6),"radiology-demo",UserRole.DEPARTMENT,new UUID(0,2)));
        var hashes=new HashMap<UUID,String>();for(var user:accounts) hashes.put(user.id(),encoder.encode(password));
        run(()-> {
            for(var d:demo.departments()) em.createNativeQuery("INSERT IGNORE INTO departments(id,name) VALUES (?1,?2)").setParameter(1,d.id().toString()).setParameter(2,d.name()).executeUpdate();
            for(var u:accounts) em.createNativeQuery("INSERT IGNORE INTO app_users(id,username,password_hash,role,enabled,department_id) VALUES (?1,?2,?3,?4,true,?5)")
                .setParameter(1,u.id().toString()).setParameter(2,u.username()).setParameter(3,hashes.get(u.id())).setParameter(4,u.role().name()).setParameter(5,u.departmentId()==null?null:u.departmentId().toString()).executeUpdate();
            return null;
        });
    }
}
