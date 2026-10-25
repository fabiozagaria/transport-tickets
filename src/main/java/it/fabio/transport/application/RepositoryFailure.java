package it.fabio.transport.application;

/** Persistence failures are distinct from rejected domain transitions. */
public final class RepositoryFailure extends RuntimeException {
    public enum Kind { CORRUPT_DATA, UNAVAILABLE, SERIALIZATION }
    private final Kind kind;
    public RepositoryFailure(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }
    public Kind kind() { return kind; }
}
