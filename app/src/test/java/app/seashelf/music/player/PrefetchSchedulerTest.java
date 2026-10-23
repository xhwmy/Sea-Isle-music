package app.seashelf.music.player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PrefetchSchedulerTest {
    private static final class Job implements PrefetchScheduler.Task {
        int runs, cancellations;
        boolean fail;
        @Override public void run() throws IOException { runs++; if (fail) throw new IOException("offline"); }
        @Override public void cancel() { cancellations++; }
    }

    @Test public void completedSongIsNotDownloadedAgainButNewSongIs() {
        long[] now = {1000};
        PrefetchScheduler scheduler = new PrefetchScheduler(Runnable::run, () -> now[0]);
        Job first = new Job(), second = new Job();
        assertTrue(scheduler.request("first", () -> first));
        assertEquals(1, first.runs);
        assertFalse(scheduler.request("first", Job::new));
        assertTrue(scheduler.request("second", () -> second));
        assertEquals(1, second.runs);
        scheduler.close();
    }

    @Test public void failureWaitsBeforeRetryAndDoesNotInterruptPlayback() {
        long[] now = {1000};
        PrefetchScheduler scheduler = new PrefetchScheduler(Runnable::run, () -> now[0]);
        Job failed = new Job(); failed.fail = true;
        assertTrue(scheduler.request("song", () -> failed));
        assertEquals(1, failed.runs);
        now[0] = 59000;
        assertFalse(scheduler.request("song", Job::new));
        now[0] = 61000;
        Job retried = new Job();
        assertTrue(scheduler.request("song", () -> retried));
        assertEquals(1, retried.runs);
        scheduler.close();
    }

    @Test public void slowFailureStartsCooldownWhenItFailsRatherThanWhenItStarts() {
        long[] now = {1000};
        PrefetchScheduler scheduler = new PrefetchScheduler(Runnable::run, () -> now[0]);
        assertTrue(scheduler.request("song", () -> new PrefetchScheduler.Task() {
            @Override public void run() throws IOException { now[0] = 100000; throw new IOException("late failure"); }
            @Override public void cancel() { }
        }));
        assertFalse(scheduler.request("song", Job::new));
        now[0] = 159999;
        assertFalse(scheduler.request("song", Job::new));
        now[0] = 160000;
        assertTrue(scheduler.request("song", Job::new));
        scheduler.close();
    }

    @Test public void switchingSongsCancelsQueuedWorkAndIgnoresStaleCompletion() {
        List<Runnable> queued = new ArrayList<>();
        PrefetchScheduler scheduler = new PrefetchScheduler(queued::add, () -> 1000);
        Job first = new Job(), second = new Job();
        assertTrue(scheduler.request("first", () -> first));
        assertFalse(scheduler.request("first", Job::new));
        assertTrue(scheduler.request("second", () -> second));
        assertEquals(1, first.cancellations);
        queued.get(0).run();
        assertEquals(0, first.runs);
        queued.get(1).run();
        assertEquals(1, second.runs);
        scheduler.close();
        assertFalse(scheduler.request("third", Job::new));
    }
}
