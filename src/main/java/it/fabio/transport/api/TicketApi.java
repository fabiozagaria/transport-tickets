package it.fabio.transport.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import it.fabio.transport.domain.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import it.fabio.transport.application.*;
import it.fabio.transport.infrastructure.InMemoryTicketRepository;
import java.util.*;
import java.util.concurrent.Executors;

/** Local educational API. State is lost on restart. */
public final class TicketApi {
    private final TicketService service;
    private final DemoDirectory directory;

    public TicketApi(Clock clock) {
        this(clock, new DemoDirectory());
    }

    private TicketApi(Clock clock, DemoDirectory directory) {
        this(new TicketService(new InMemoryTicketRepository(), directory, clock), directory);
    }

    public TicketApi(TicketService service, DemoDirectory directory) {
        this.service = Objects.requireNonNull(service);
        this.directory = Objects.requireNonNull(directory);
    }

    public HttpServer start(int port) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        return server;
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        var server = new TicketApi(Clock.systemUTC()).start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        System.out.println("API demo: http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            var actor = directory.authenticate(Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization"))
                    .filter(h -> h.startsWith("Bearer ")).map(h -> h.substring(7)).orElse(""));
            if (actor == null) throw new Failure(401, "Credenziale demo richiesta");
            // Body reads and response writes occur outside repository locks.
            route(exchange, actor);
        } catch (ApplicationFailure e) {
            int status = e.kind() == ApplicationFailure.Kind.FORBIDDEN ? 403 : 404;
            respond(exchange, status, Map.of("error", e.getMessage()));
        } catch (Failure e) {
            if (e.status == 401) exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            respond(exchange, e.status, Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            respond(exchange, 400, Map.of("error", Optional.ofNullable(e.getMessage()).orElse("Richiesta non valida")));
        } catch (IllegalStateException e) {
            respond(exchange, 409, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            respond(exchange, 500, Map.of("error", "Errore interno"));
        } finally { exchange.close(); }
    }

    private void route(HttpExchange x, User actor) throws IOException {
        String path = x.getRequestURI().getPath();
        String method = x.getRequestMethod();
        if (path.equals("/api/departments")) {
            requireMethod(x, "GET");
            respond(x, 200, directory.departments().stream().map(d -> Map.of("id", d.id(), "name", d.name())).toList());
        } else if (path.equals("/api/operators")) {
            requireMethod(x, "GET");
            requireRole(actor, UserRole.CUT);
            respond(x, 200, directory.users().stream().filter(u -> u.role() == UserRole.OPERATOR)
                    .sorted(Comparator.comparing(User::username)).map(u -> Map.of("id", u.id(), "username", u.username())).toList());
        } else if (path.equals("/api/tickets")) {
            if (method.equals("GET")) {
                respond(x, 200, service.list(actor).stream().map(TicketPresenter::snapshot).toList());
            } else {
                requireMethod(x, "GET", "POST");
                requireRole(actor, UserRole.DEPARTMENT);
                var f = form(x, Set.of("patientCode", "originId", "destinationId", "priority", "scheduledAt"));
                var ticket = service.create(actor, new CreateTicket(
                        required(f, "patientCode"), UUID.fromString(required(f, "originId")),
                        UUID.fromString(required(f, "destinationId")),
                        TicketPriority.valueOf(required(f, "priority")),
                        f.containsKey("scheduledAt") ? Instant.parse(required(f, "scheduledAt")) : null));
                x.getResponseHeaders().set("Location", "/api/tickets/" + ticket.id());
                respond(x, 201, TicketPresenter.snapshot(ticket));
            }
        } else if (path.startsWith("/api/tickets/")) {
            String[] parts = path.substring("/api/tickets/".length()).split("/", -1);
            if (parts.length > 2 || parts[0].isBlank()) throw new Failure(404, "Endpoint inesistente");
            UUID id = UUID.fromString(parts[0]);
            var ticket = service.get(actor, id);
            if (parts.length == 1) {
                requireMethod(x, "GET");
                respond(x, 200, TicketPresenter.snapshot(ticket));
                return;
            }
            String action = parts[1];
            if (!Set.of("assign", "accept", "start", "identify", "depart", "arrive", "complete").contains(action))
                throw new Failure(404, "Azione inesistente");
            requireMethod(x, "POST");
            requireRole(actor, action.equals("assign") ? UserRole.CUT : UserRole.OPERATOR);
            var f = form(x, action.equals("assign") ? Set.of("operatorId") : action.equals("identify") ? Set.of("patientCode") : Set.of());
            ticket = service.act(actor, id, TicketAction.valueOf(action.toUpperCase(Locale.ROOT)),
                    action.equals("assign") ? UUID.fromString(required(f, "operatorId")) : null,
                    action.equals("identify") ? required(f, "patientCode") : null);
            respond(x, 200, TicketPresenter.snapshot(ticket));
        } else throw new Failure(404, "Endpoint inesistente");
    }

    private static void requireRole(User actor, UserRole role) {
        if (actor.role() != role) throw new Failure(403, "Ruolo non autorizzato");
    }

    private static void requireMethod(HttpExchange x, String... allowed) {
        if (!Arrays.asList(allowed).contains(x.getRequestMethod())) {
            x.getResponseHeaders().set("Allow", String.join(", ", allowed));
            throw new Failure(405, "Metodo non consentito");
        }
    }

    private static Map<String, String> form(HttpExchange x, Set<String> allowed) throws IOException {
        byte[] body = x.getRequestBody().readNBytes(8193);
        if (body.length > 8192) throw new Failure(413, "Richiesta troppo grande");
        if (body.length > 0) {
            String type = x.getRequestHeaders().getFirst("Content-Type");
            if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded"))
                throw new Failure(415, "Usare application/x-www-form-urlencoded");
        }
        var result = new HashMap<String, String>();
        if (body.length == 0) return result;
        for (String pair : new String(body, StandardCharsets.UTF_8).split("&", -1)) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            if (!allowed.contains(key)) throw new IllegalArgumentException("Campo non consentito: " + key);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Campo duplicato: " + key);
        }
        return result;
    }

    private static String required(Map<String, String> form, String key) {
        String value = form.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Campo obbligatorio: " + key);
        return value;
    }

    private static void respond(HttpExchange x, int status, Object body) throws IOException {
        byte[] bytes = Json.encode(body).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
    }

    private static final class Failure extends RuntimeException {
        final int status;
        Failure(int status, String message) { super(message); this.status = status; }
    }
}
