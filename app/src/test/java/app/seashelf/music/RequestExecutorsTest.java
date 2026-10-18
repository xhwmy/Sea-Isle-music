package app.seashelf.music;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequestExecutorsTest {
    @Test public void albumRequestDoesNotWaitForBlockedCoverDownloads() throws Exception {
        RequestExecutors pools = new RequestExecutors();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch imageStarted = new CountDownLatch(2);
        CountDownLatch albumLoaded = new CountDownLatch(1);
        try {
            for (int i = 0; i < 60; i++) pools.images().execute(() -> {
                imageStarted.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertTrue(imageStarted.await(2, TimeUnit.SECONDS));
            pools.api().execute(albumLoaded::countDown);
            assertTrue("Album API must not queue behind 60 covers", albumLoaded.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pools.close();
        }
    }
}
