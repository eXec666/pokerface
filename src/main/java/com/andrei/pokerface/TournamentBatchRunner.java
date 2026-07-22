package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Runs SessionRunner.runSession() repeatedly, once per tournament, and
 * collects the outcome of each run. Exists alongside RingGameRunner rather
 * than instead of it: bb/100 is the right tool for comparing decision quality
 * (fast-converging, free of ICM/survival effects), but it says nothing about
 * how bots perform under real tournament conditions (elimination, escalating
 * blinds, short-stack push/fold spots). Running many independent tournaments
 * and looking at the win-rate distribution answers that different question,
 * and doubles as a coarse end-to-end sanity check -- e.g. confirming every
 * seat wins roughly 1/N of the time when every seat runs the same bot.
 *
 * A fresh set of Player objects is required for every tournament: a player
 * eliminated in tournament 1 must not start tournament 2 pre-eliminated, and
 * stacks must not carry over. Hence playerFactory rather than a single players
 * list. Agents ARE reused across tournaments -- the reference agents
 * (RandomAgent, etc.) are stateless apart from their internal RNG, and letting
 * that RNG continue to advance across tournaments is still a valid,
 * deterministic source of randomness that doesn't bias the win-rate
 * distribution.
 */
public final class TournamentBatchRunner {

    private TournamentBatchRunner() {}

    /**
     * @param playerFactory   builds a fresh, non-eliminated player roster for each
     *                        tournament; must return players in the same seat order
     *                        every call, matching agents' indexing
     * @param agents          one agent per seat, reused across every tournament
     * @param tournamentCount number of independent tournaments to run
     */
    public static TournamentBatchResult runBatch(
            Supplier<List<Player>> playerFactory,
            List<PokerAgent> agents,
            BlindSchedule blindSchedule,
            BustHandler bustHandler,
            SessionEndCondition endCondition,
            IntSupplier seedSource,
            HandLogger logger,
            int tournamentCount) {

        if (agents == null || agents.isEmpty()) {
            throw new IllegalArgumentException("Need at least one agent");
        }
        if (tournamentCount <= 0) {
            throw new IllegalArgumentException("tournamentCount must be positive");
        }

        List<SessionResult> results = new ArrayList<>(tournamentCount);

        for (int t = 0; t < tournamentCount; t++) {
            List<Player> players = playerFactory.get();
            if (players.size() != agents.size()) {
                throw new IllegalArgumentException(
                        "playerFactory produced " + players.size() + " players, expected " + agents.size());
            }
            SessionResult result = SessionRunner.runSession(
                    players, agents, blindSchedule, bustHandler, endCondition, seedSource, logger);
            results.add(result);
        }

        return new TournamentBatchResult(results);
    }

    /** Convenience overload: constant blinds, tournament elimination rule, no logging. */
    public static TournamentBatchResult runFreezeoutBatch(
            Supplier<List<Player>> playerFactory,
            List<PokerAgent> agents,
            int smallBlind, int bigBlind,
            IntSupplier seedSource,
            int tournamentCount) {
        return runBatch(playerFactory, agents,
                BlindSchedule.constant(smallBlind, bigBlind),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING,
                seedSource,
                HandLogger.NO_OP,
                tournamentCount);
    }
}