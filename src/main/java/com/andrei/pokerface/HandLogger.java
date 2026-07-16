package com.andrei.pokerface;

/**
 * Subscriber interface for GameState's event stream. Implementations decide
 * what to do with each event -- print it, write it to disk, accumulate it
 * in memory for test assertions. GameState only ever calls log(); it never
 * reads anything back from a logger and never has its behavior altered by
 * one.
 *
 * Register via GameState.setLogger(). The default (HandLogger.NO_OP) is
 * wired in automatically, so any GameState that never calls setLogger()
 * sees zero behavior change.
 */
@FunctionalInterface
public interface HandLogger {

    void log(GameEvent event);

    /** Logger that discards every event -- the default for any GameState that never opts in. */
    HandLogger NO_OP = event -> {};
}