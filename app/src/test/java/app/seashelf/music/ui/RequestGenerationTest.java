package app.seashelf.music.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class RequestGenerationTest {
    @Test public void ignoresResultsFromPreviousSearchOrServer() {
        RequestGeneration generation = new RequestGeneration();
        long first = generation.next();
        long second = generation.next();
        assertFalse(generation.isCurrent(first));
        assertTrue(generation.isCurrent(second));
        generation.next();
        assertFalse(generation.isCurrent(second));
    }
}
