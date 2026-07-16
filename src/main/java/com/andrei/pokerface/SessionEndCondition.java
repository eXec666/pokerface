package com.andrei.pokerface;

/** Decides whether the session loop should stop after the hand just played. */
@FunctionalInterface
public interface SessionEndCondition {
    boolean isSessionOver(GameState state, int handsPlayed);

    /** Standard tournament/freezeout rule: stop once at most one player still has chips. */
    SessionEndCondition LAST_PLAYER_STANDING = (state, handsPlayed) -> state.getLivePlayerCount() <= 1;

    /**
     * Combinator: also stop after a hard cap on hands played, regardless of chip
     * counts. Useful as a safety bound in tests, or for a training loop that wants
     * a fixed-length episode rather than playing to a single survivor.
     */
    default SessionEndCondition orAfter(int maxHands) {
        return (state, handsPlayed) -> this.isSessionOver(state, handsPlayed) || handsPlayed >= maxHands;
    }
}