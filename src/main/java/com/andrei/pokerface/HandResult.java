package com.andrei.pokerface;

import java.util.List;

/**
 * Outcome of a single HandRunner.playHand() call.
 *
 * A hand ends one of two ways: everyone but one player folds (wonByFold=true,
 * winners is that single player), or the hand reaches showdown and
 * GameState.resolveShowdown() determines the winner(s) pot-by-pot (wonByFold=false,
 * winners may contain more than one player if pots were split by ties, and/or if
 * different players won different side pots).
 */
public record HandResult(boolean wonByFold, List<Player> winners) {

    public static HandResult foldWin(Player winner) {
        return new HandResult(true, List.of(winner));
    }

    public static HandResult showdown(List<Player> winners) {
        return new HandResult(false, List.copyOf(winners));
    }
}