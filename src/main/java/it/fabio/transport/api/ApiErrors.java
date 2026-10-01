package it.fabio.transport.api;

import it.fabio.transport.application.*;
import java.time.DateTimeException;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
    private ResponseEntity<?> error(int status,String message) { return ResponseEntity.status(status).body(Map.of("error",message)); }
    @ExceptionHandler(RepositoryFailure.class) ResponseEntity<?> repository(RepositoryFailure e) {
        return error(e.kind()==RepositoryFailure.Kind.UNAVAILABLE ? 503 : 500,e.kind()==RepositoryFailure.Kind.UNAVAILABLE ? "Archivio temporaneamente non disponibile" : "Errore interno dell'archivio");
    }
    @ExceptionHandler(ApplicationFailure.class) ResponseEntity<?> application(ApplicationFailure e) { return error(e.kind()==ApplicationFailure.Kind.FORBIDDEN ? 403 : 404,e.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class,DateTimeException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(Exception e) { return error(400,"Richiesta non valida"); }
    @ExceptionHandler(IllegalStateException.class) ResponseEntity<?> conflict(IllegalStateException e) { return error(409,e.getMessage()); }
    @ExceptionHandler(TicketController.HttpFailure.class) ResponseEntity<?> http(TicketController.HttpFailure e) { return error(e.status,e.getMessage()); }
}
