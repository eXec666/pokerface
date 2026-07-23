package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Runs TournamentBatchRunner across seat-rotated tournaments and aggregates
 * win rate per bot NAME. Rotation offset advances by one seat every
 * tournament (t % n) rather than being pre-split into blocks like the ring
 * collector -- tournament counts are typically much smaller than hand
 * counts, so per-tournament rotation gives a more even seat distribution
 * for a given budget than block rotation would.
 */
public final class BotTournamentStatsCollector {

    private BotTournamentStatsCollector() {}

    public static BotTournamentReport collect(
            List<NamedAgent> bots,
            int startingStack,
            BlindSchedule blindSchedule,
            BustHandler bustHandler,
            SessionEndCondition endCondition,
            IntSupplier seedSource,
            HandLogger logger,
            int tournamentCount) {

        if (bots == null || bots.size() < 2) {
            throw new IllegalArgumentException("Need at least two bots to compare");
        }
        if (tournamentCount <= 0) {
            throw new IllegalArgumentException("tournamentCount must be positive");
        }

        int n = bots.size();
        Map<String, Integer> winsByName = new LinkedHashMap<>();
        Map<String, Integer> enteredByName = new LinkedHashMap<>();
        for (NamedAgent b : bots) {
            winsByName.put(b.name(), 0);
            enteredByName.put(b.name(), 0);
        }

        long inconclusive = 0;

        for (int t = 0; t < tournamentCount; t++) {
            int offset = t % n;

            List<Player> players = new ArrayList<>(n);
            List<PokerAgent> agents = new ArrayList<>(n);
            List<String> seatOwner = new ArrayList<>(n);
            for (int seat = 0; seat < n; seat++) {
                NamedAgent owner = bots.get((seat + offset) % n);
                players.add(new Player(seat, owner.name(), startingStack));
                agents.add(owner.agent());
                seatOwner.add(owner.name());
                enteredByName.merge(owner.name(), 1, Integer::sum);
            }

            SessionResult result = SessionRunner.runSession(
                    players, agents, blindSchedule, bustHandler, endCondition, seedSource, logger);

            if (result.winner().isPresent()) {
                String winnerName = seatOwner.get(result.winner().get().getSeatIndex());
                winsByName.merge(winnerName, 1, Integer::sum);
            } else {
                inconclusive++;
            }
        }

        List<BotTournamentStatLine> lines = new ArrayList<>();
        for (NamedAgent b : bots) {
            lines.add(BotTournamentStatLine.of(b.name(), winsByName.get(b.name()), enteredByName.get(b.name())));
        }
        return new BotTournamentReport(lines, inconclusive);
    }

    /** Convenience overload: constant blinds, tournament elimination rule, no logging. */
    public static BotTournamentReport collectFreezeout(
            List<NamedAgent> bots, int startingStack,
            int smallBlind, int bigBlind,
            IntSupplier seedSource, int tournamentCount) {
        return collect(bots, startingStack,
                BlindSchedule.constant(smallBlind, bigBlind),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING,
                seedSource, HandLogger.NO_OP, tournamentCount);
    }
}