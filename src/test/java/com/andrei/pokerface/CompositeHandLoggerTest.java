package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class CompositeHandLoggerTest {

    @Test
    void forwardsEventToEveryDelegate() {
        InMemoryHandLogger a = new InMemoryHandLogger();
        InMemoryHandLogger b = new InMemoryHandLogger();
        CompositeHandLogger composite = new CompositeHandLogger(a, b);

        GameEvent event = new GameEvent.HandStarted(0, 1);
        composite.log(event);

        assertEquals(1, a.getEvents().size());
        assertEquals(1, b.getEvents().size());
        assertSame(event, a.getEvents().get(0));
        assertSame(event, b.getEvents().get(0));
    }

    @Test
    void forwardsMultipleEventsInOrderToEachDelegate() {
        InMemoryHandLogger a = new InMemoryHandLogger();
        InMemoryHandLogger b = new InMemoryHandLogger();
        CompositeHandLogger composite = new CompositeHandLogger(a, b);

        composite.log(new GameEvent.HandStarted(0, 1));
        composite.log(new GameEvent.HandEnded(true, List.of(0)));

        assertEquals(2, a.getEvents().size());
        assertEquals(2, b.getEvents().size());
        assertInstanceOf(GameEvent.HandStarted.class, a.getEvents().get(0));
        assertInstanceOf(GameEvent.HandEnded.class, a.getEvents().get(1));
    }

    @Test
    void noDelegates_doesNotThrow() {
        CompositeHandLogger composite = new CompositeHandLogger();
        assertDoesNotThrow(() -> composite.log(new GameEvent.HandStarted(0, 1)));
    }

    @Test
    void singleDelegate_behavesLikeThatDelegateAlone() {
        InMemoryHandLogger only = new InMemoryHandLogger();
        CompositeHandLogger composite = new CompositeHandLogger(only);

        composite.log(new GameEvent.HandStarted(2, 5));

        assertEquals(1, only.getEvents().size());
    }
}