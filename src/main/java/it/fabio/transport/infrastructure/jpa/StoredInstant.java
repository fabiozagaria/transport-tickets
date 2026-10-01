package it.fabio.transport.infrastructure.jpa;

import jakarta.persistence.*;
import java.time.Instant;

/** Seconds plus nanoseconds preserve timestamps exactly, without MySQL precision loss. */
@Embeddable
public class StoredInstant {
    Long epochSecond;
    Integer nano;
    protected StoredInstant() {}
    private StoredInstant(Instant value) { epochSecond=value.getEpochSecond(); nano=value.getNano(); }
    static StoredInstant from(Instant value) { return value==null ? null : new StoredInstant(value); }
    Instant value() { return Instant.ofEpochSecond(epochSecond,nano); }
    static Instant value(StoredInstant stored) { return stored==null ? null : stored.value(); }
}
