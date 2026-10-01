package it.fabio.transport.application;

/** Application errors have no dependency on HTTP status codes. */
public final class ApplicationFailure extends RuntimeException {
    public enum Kind { FORBIDDEN, NOT_FOUND }
    private final Kind kind;

    public ApplicationFailure(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
