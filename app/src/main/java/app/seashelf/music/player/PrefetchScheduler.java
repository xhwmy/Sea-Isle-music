package app.seashelf.music.player;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** One best-effort background download. Caller owns the executor and supplies monotonic time. */
public final class PrefetchScheduler implements AutoCloseable {
    public interface Task {
        void run() throws IOException;
        void cancel();
    }
    private enum State { IDLE, RUNNING, SUCCEEDED, FAILED }
    private static final long RETRY_MS = 60000;
    private final Executor executor;
    private final LongSupplier clock;
    private String key;
    private Task task;
    private State state = State.IDLE;
    private long retryAtMs;
    private long generation;
    private boolean closed;

    public PrefetchScheduler(Executor executor, LongSupplier clock) {
        this.executor = executor;
        this.clock = clock;
    }

    public synchronized boolean request(String wantedKey, Supplier<Task> create) {
        if (closed) return false;
        if (wantedKey == null || wantedKey.isEmpty()) throw new IllegalArgumentException("Cache key required");
        long nowMs = clock.getAsLong();
        if (wantedKey.equals(key) && (state == State.RUNNING || state == State.SUCCEEDED
                || state == State.FAILED && nowMs < retryAtMs)) return false;
        cancelCurrent();
        Task selected = create.get();
        key = wantedKey; task = selected; state = State.RUNNING;
        long ticket = generation;
        executor.execute(() -> run(ticket, selected));
        return true;
    }

    private void run(long ticket, Task selected) {
        synchronized (this) { if (ticket != generation || closed) return; }
        boolean success = false;
        try { selected.run(); success = true; }
        catch (IOException | RuntimeException ignored) { /* Best effort; playing audio remains independent. */ }
        synchronized (this) {
            if (ticket != generation || closed) return;
            state = success ? State.SUCCEEDED : State.FAILED;
            retryAtMs = clock.getAsLong() + RETRY_MS;
        }
    }

    public synchronized void cancel() { cancelCurrent(); }
    private void cancelCurrent() {
        generation++;
        if (state == State.RUNNING && task != null) task.cancel();
        key = null; task = null; state = State.IDLE; retryAtMs = 0;
    }
    @Override public synchronized void close() { closed = true; cancelCurrent(); }
}
