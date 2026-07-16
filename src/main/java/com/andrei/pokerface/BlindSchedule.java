package com.andrei.pokerface;

/**
 * Determines the blinds in effect after a given number of hands have been
 * played this session. GameState's smallBlind/bigBlind fields are final, so a
 * change in blind level means SessionRunner constructs a fresh GameState
 * rather than mutating the existing one -- see SessionRunner.runSession().
 */
@FunctionalInterface
public interface BlindSchedule {
    BlindLevel blindsFor(int handsPlayed);

    /** A schedule that never changes -- correct for a cash game or a single-level freezeout. */
    static BlindSchedule constant(int smallBlind, int bigBlind) {
        BlindLevel level = new BlindLevel(smallBlind, bigBlind);
        return handsPlayed -> level;
    }
}