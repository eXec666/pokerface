package com.andrei.pokerface;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Drives a full multi-hand session on a fixed roster of players: repeatedly
 * calls HandRunner.playHand(), applies bust handling after each hand, and
 * stops once the configured SessionEndCondition fires. This is the layer
 * HandRunner's own javadoc explicitly leaves unbuilt.
 *
 * Deliberately takes BlindSchedule / BustHandler / SessionEndCondition as
 * collaborators rather than hardcoding a single tournament shape: a
 * cash-game or hot-seat GUI mode wants a different bust rule (rebuy instead
 * of eliminate) and a different end condition (run until the human quits),
 * and adding those later should mean supplying a different collaborator, not
 * rewriting this loop.
 */
public final class SessionRunner {

    private SessionRunner() {}

    public static SessionResult runSession(
            List<Player> players,
            List<PokerAgent> agents,
            BlindSchedule blindSchedule,
            BustHandler bustHandler,
            SessionEndCondition endCondition,
            IntSupplier seedSource,
            HandLogger logger) {

        if (players == null || agents == null || players.size() != agents.size()) {
            throw new IllegalArgumentException(
                    "Need exactly one agent per player (" + (players == null ? 0 : players.size()) + " players)");
        }

        BlindLevel level = blindSchedule.blindsFor(0);
        GameState state = new GameState(players, level.smallBlind(), level.bigBlind(), 0);
        state.setLogger(logger);

        int handsPlayed = 0;
        while (!endCondition.isSessionOver(state, handsPlayed)) {
            HandRunner.playHand(state, agents, seedSource.getAsInt());
            handsPlayed++;

            for (Player p : players) {
                if (p.getStack() == 0 && !p.isEliminated()) {
                    bustHandler.onBust(p);
                }
            }

            BlindLevel nextLevel = blindSchedule.blindsFor(handsPlayed);
            if (nextLevel.smallBlind() != state.getSmallBlind() || nextLevel.bigBlind() != state.getBigBlind()) {
                state = new GameState(players, nextLevel.smallBlind(), nextLevel.bigBlind(), state.getDealerIndex());
                state.setLogger(logger);
            }
        }

        return new SessionResult(handsPlayed, players);
    }

    /** Convenience overload: constant blinds, tournament bust rule, stop at one survivor, no logging. */
    public static SessionResult runFreezeout(
            List<Player> players, List<PokerAgent> agents,
            int smallBlind, int bigBlind, IntSupplier seedSource) {
        return runSession(players, agents,
                BlindSchedule.constant(smallBlind, bigBlind),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING,
                seedSource,
                HandLogger.NO_OP);
    }
}