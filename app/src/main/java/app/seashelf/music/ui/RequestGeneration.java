package app.seashelf.music.ui;

import java.util.concurrent.atomic.AtomicLong;

/** Discards results from requests superseded by a new search or server. */
public final class RequestGeneration {
    private final AtomicLong value = new AtomicLong();

    public long next() { return value.incrementAndGet(); }
    public boolean isCurrent(long token) { return value.get() == token; }
}
