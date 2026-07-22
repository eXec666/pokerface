package com.andrei.pokerface;

import java.util.List;

/**
 * Forwards every event to multiple HandLoggers in order. Lets a session
 * write a machine-parseable file log (FileHandLogger) and a human-readable
 * console narration (ConsoleHandLogger) simultaneously without either
 * implementation knowing the other exists.
 */
public class CompositeHandLogger implements HandLogger {
    private final List<HandLogger> delegates;

    public CompositeHandLogger(HandLogger... delegates) {
        this.delegates = List.of(delegates);
    }

    @Override
    public void log(GameEvent event) {
        for (HandLogger delegate : delegates) {
            delegate.log(event);
        }
    }
}