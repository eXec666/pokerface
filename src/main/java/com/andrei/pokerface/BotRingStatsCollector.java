package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntSupplier;

/**
 * Runs RingGameRunner across seat-rotated sub-batches and aggregates bb/100
 * per bot NAME rather than per seat index. Rotation is necessary because
 * RingGameRunner alone always seats agent i at seat i for the whole batch,
 * conflating seat-position edge with bot skill. Here, bot i occupies seat
 * (i + offset) mod n for each of n rotation offsets, with hands split as
 * evenly as possible across offsets -- over the full collection every bot
 * spends an equal share of hands in every seat, cancelling the positional
 * confound out of the aggregate exactly.
 */
public final class BotRingStatsCollector {

    private BotRingStatsCollector() {}

    static List<Integer> shuffledSeatAssignment(int n, Random random) {
        List<Integer> assignment = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            assignment.add(i);
        }
        Collections.shuffle(assignment, random);
        return assignment;
    }

    public static BotPerformanceReport collect(
            List<NamedAgent> bots,
            int smallBlind, int bigBlind, int buyIn,
            int totalHands, IntSupplier seedSource, long seatShuffleSeed, HandLogger logger) {

        if (bots == null || bots.size() < 2) {
            throw new IllegalArgumentException("Need at least two bots to compare");
        }
        if (totalHands <= 0) {
            throw new IllegalArgumentException("totalHands must be positive");
        }

        int n = bots.size();
        int baseHands = totalHands / n;
        int remainder = totalHands % n;
        Random seatRandom = new Random(seatShuffleSeed);

        Map<String, List<Double>> bbSamplesByName = new LinkedHashMap<>();
        for (NamedAgent b : bots) {
            bbSamplesByName.put(b.name(), new ArrayList<>());
        }

        for (int offset = 0; offset < n; offset++) {
            int handsThisRotation = baseHands + (offset < remainder ? 1 : 0);
            if (handsThisRotation == 0) {
                continue;
            }

            List<Integer> seatAssignment = shuffledSeatAssignment(n, seatRandom);

            List<Player> players = new ArrayList<>(n);
            List<PokerAgent> agents = new ArrayList<>(n);
            List<String> seatOwner = new ArrayList<>(n);
            for (int seat = 0; seat < n; seat++) {
                NamedAgent owner = bots.get(seatAssignment.get(seat));
                players.add(new Player(seat, owner.name(), buyIn));
                agents.add(owner.agent());
                seatOwner.add(owner.name());
            }

            RingGameResult result = RingGameRunner.runBatch(
                    players, agents, smallBlind, bigBlind, buyIn, handsThisRotation, seedSource, logger);

            for (int[] handNet : result.netChipsPerHand()) {
                for (int seat = 0; seat < n; seat++) {
                    double bb = handNet[seat] / (double) bigBlind;
                    bbSamplesByName.get(seatOwner.get(seat)).add(bb);
                }
            }
        }

        List<BotStatLine> lines = new ArrayList<>();
        for (NamedAgent b : bots) {
            lines.add(BotStatLine.fromSamples(b.name(), bbSamplesByName.get(b.name())));
        }
        return new BotPerformanceReport(lines);
    }

    /** Convenience overload: no logging. */
    public static BotPerformanceReport collect(List<NamedAgent> bots, int smallBlind, int bigBlind, int buyIn, int totalHands, IntSupplier seedSource, long seatShuffleSeed) {
        return collect(bots, smallBlind, bigBlind, buyIn, totalHands, seedSource, seatShuffleSeed, HandLogger.NO_OP);
    }

    /**
     * Same aggregation as collect(), but for bots whose policy is itself
     * seed-driven: totalHands is split into blocks of handsPerSeedBlock hands,
     * and at the start of every block each bot's agent is rebuilt from its
     * factory with a fresh seed drawn from agentSeedSource. Seat rotation
     * still happens within every block exactly as in collect(). dealSeedSource
     * and agentSeedSource must be independent streams -- conflating them would
     * correlate agent reseeding with specific deals.
     */
    public static BotPerformanceReport collectWithSeedRotation(
            List<NamedAgentFactory> bots,
            int smallBlind, int bigBlind, int buyIn,
            int totalHands, int handsPerSeedBlock,
            IntSupplier dealSeedSource,
            IntSupplier agentSeedSource,
            long seatShuffleSeed,
            HandLogger logger) {

        if (bots == null || bots.size() < 2) {
            throw new IllegalArgumentException("Need at least two bots to compare");
        }
        if (totalHands <= 0) {
            throw new IllegalArgumentException("totalHands must be positive");
        }
        if (handsPerSeedBlock <= 0) {
            throw new IllegalArgumentException("handsPerSeedBlock must be positive");
        }

        int n = bots.size();
        Random seatRandom = new Random(seatShuffleSeed);
        Map<String, List<Double>> bbSamplesByName = new LinkedHashMap<>();
        for (NamedAgentFactory b : bots) {
            bbSamplesByName.put(b.name(), new ArrayList<>());
        }

        int handsRemaining = totalHands;

        while (handsRemaining > 0) {
            int handsThisBlock = Math.min(handsPerSeedBlock, handsRemaining);
            handsRemaining -= handsThisBlock;

            List<PokerAgent> freshAgents = new ArrayList<>(n);
            for (NamedAgentFactory b : bots) {
                freshAgents.add(b.factory().apply(agentSeedSource.getAsInt()));
            }

            int baseHandsPerOffset = handsThisBlock / n;
            int remainder = handsThisBlock % n;

            for (int offset = 0; offset < n; offset++) {
                int handsThisRotation = baseHandsPerOffset + (offset < remainder ? 1 : 0);
                if (handsThisRotation == 0) {
                    continue;
                }

                List<Integer> seatAssignment = shuffledSeatAssignment(n, seatRandom);

                List<Player> players = new ArrayList<>(n);
                List<PokerAgent> agents = new ArrayList<>(n);
                List<String> seatOwner = new ArrayList<>(n);
                for (int seat = 0; seat < n; seat++) {
                    int botIndex = seatAssignment.get(seat);
                    NamedAgentFactory owner = bots.get(botIndex);
                    players.add(new Player(seat, owner.name(), buyIn));
                    agents.add(freshAgents.get(botIndex));
                    seatOwner.add(owner.name());
                }

                RingGameResult result = RingGameRunner.runBatch(
                        players, agents, smallBlind, bigBlind, buyIn, handsThisRotation, dealSeedSource, logger);

                for (int[] handNet : result.netChipsPerHand()) {
                    for (int seat = 0; seat < n; seat++) {
                        double bb = handNet[seat] / (double) bigBlind;
                        bbSamplesByName.get(seatOwner.get(seat)).add(bb);
                    }
                }
            }
        }

        List<BotStatLine> lines = new ArrayList<>();
        for (NamedAgentFactory b : bots) {
            lines.add(BotStatLine.fromSamples(b.name(), bbSamplesByName.get(b.name())));
        }
        return new BotPerformanceReport(lines);
    }

    /** Convenience overload: no logging. */
    public static BotPerformanceReport collectWithSeedRotation(
            List<NamedAgentFactory> bots,
            int smallBlind, int bigBlind, int buyIn,
            int totalHands, int handsPerSeedBlock,
            IntSupplier dealSeedSource, IntSupplier agentSeedSource, long seatShuffleSeed) {
        return collectWithSeedRotation(bots, smallBlind, bigBlind, buyIn, totalHands, handsPerSeedBlock,
                dealSeedSource, agentSeedSource, seatShuffleSeed, HandLogger.NO_OP);
    }
}