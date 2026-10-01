package it.fabio.transport.infrastructure;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Minimal RESP client for GET and SET EX. Cache outages never block database operations. */
public final class RedisTicketCache {
    private final String host;
    private final int port;
    public RedisTicketCache(String host, int port) { this.host = host; this.port = port; }

    public byte[] get(String key) {
        try {
            String value = command("GET", key);
            return value == null ? null : Base64.getDecoder().decode(value);
        } catch (IOException | RuntimeException e) { return null; }
    }

    public void put(String key, byte[] value) {
        try { command("SET", key, Base64.getEncoder().encodeToString(value), "EX", "300"); }
        catch (IOException | RuntimeException ignored) { /* optional cache */ }
    }

    private String command(String... args) throws IOException {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 300);
            socket.setSoTimeout(300);
            var out = socket.getOutputStream();
            out.write(("*" + args.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (String arg : args) {
                byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
                out.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(bytes); out.write(new byte[] {13, 10});
            }
            out.flush();
            var in = new BufferedInputStream(socket.getInputStream());
            String line = line(in);
            if (line.startsWith("+")) return line.substring(1);
            if (!line.startsWith("$")) throw new IOException("Risposta Redis inattesa");
            int length = Integer.parseInt(line.substring(1));
            if (length == -1) return null;
            if (length < 0 || length > 16_000_000) throw new IOException("Cache troppo grande");
            byte[] bytes = in.readNBytes(length);
            if (bytes.length != length || in.read() != 13 || in.read() != 10) throw new IOException("Risposta Redis incompleta");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String line(InputStream in) throws IOException {
        var result = new ByteArrayOutputStream();
        for (int i = 0; i < 1024; i++) {
            int c = in.read();
            if (c == -1) throw new EOFException();
            if (c == 13) {
                if (in.read() != 10) throw new IOException("RESP non valido");
                return result.toString(StandardCharsets.UTF_8);
            }
            result.write(c);
        }
        throw new IOException("RESP troppo lungo");
    }
}
