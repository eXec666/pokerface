package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records every event it receives, in order, with no formatting or I/O.
 * Built for tests and for programmatic invariant-checking -- e.g. summing
 * ActionTaken/PotAwarded amounts across a simulated session to assert chip
 * conservation, or counting HandEnded events to confirm every hand actually
 * terminated. Use FileHandLogger (or a custom HandLogger) for human-readable
 * output; this class is deliberately just a buffer.
 */
public class InMemoryHandLogger implements HandLogger {
    private final List<GameEvent> events = new ArrayList<>();

    @Override
    public void log(GameEvent event) {
        events.add(event);
    }

    /** Defensive view; callers cannot mutate the recorder's internal history through it. */
    public List<GameEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void clear() {
        events.clear();
    }
}