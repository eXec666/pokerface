package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntSupplier;

/**
 * Runs RingGameRunner across seat-randomized sub-batches and aggregates
 * bb/100 per bot NAME rather than per seat index.
 *
 * Randomization must be redrawn far more often than once per "rotation" of
 * n offsets: with only n permutation draws (n = bot count), relative ORDER
 * between bots (e.g. "always acts immediately after the calling station")
 * is nowhere near averaged out by the time totalHands is exhausted, even
 * though seat-INDEX time can be balanced exactly by a deterministic cyclic
 * shift. A cyclic shift, in fact, provably CANNOT fix the order problem --
 * it preserves every bot's relative position to every other bot on every
 * offset, by construction. Hence seatShuffleBlockSize: the seat permutation
 * is redrawn independently every seatShuffleBlockSize hands, decoupled
 * entirely from bot count, giving as many independent order-permutation
 * draws as the hand budget allows.
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

    /**
     * @param seatShuffleBlockSize hands played before the seat permutation is
     *                             redrawn; smaller values give more independent
     *                             permutation draws for a fixed totalHands, at
     *                             the cost of more RingGameRunner.runBatch calls
     */
    public static BotPerformanceReport collect(
            List<NamedAgent> bots,
            int smallBlind, int bigBlind, int buyIn,
            int totalHands, int seatShuffleBlockSize,
            IntSupplier seedSource, long seatShuffleSeed, HandLogger logger) {

        if (bots == null || bots.size() < 2) {
            throw new IllegalArgumentException("Need at least two bots to compare");
        }
        if (totalHands <= 0) {
            throw new IllegalArgumentException("totalHands must be positive");
        }
        if (seatShuffleBlockSize <= 0) {
            throw new IllegalArgumentException("seatShuffleBlockSize must be positive");
        }

        int n = bots.size();
        Random seatRandom = new Random(seatShuffleSeed);

        Map<String, List<Double>> bbSamplesByName = new LinkedHashMap<>();
        for (NamedAgent b : bots) {
            bbSamplesByName.put(b.name(), new ArrayList<>());
        }

        int handsRemaining = totalHands;
        while (handsRemaining > 0) {
            int handsThisBlock = Math.min(seatShuffleBlockSize, handsRemaining);
            handsRemaining -= handsThisBlock;

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
                    players, agents, smallBlind, bigBlind, buyIn, handsThisBlock, seedSource, logger);

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
    public static BotPerformanceReport collect(
            List<NamedAgent> bots, int smallBlind, int bigBlind, int buyIn,
            int totalHands, int seatShuffleBlockSize, IntSupplier seedSource, long seatShuffleSeed) {
        return collect(bots, smallBlind, bigBlind, buyIn, totalHands, seatShuffleBlockSize,
                seedSource, seatShuffleSeed, HandLogger.NO_OP);
    }

    /**
     * Same aggregation as collect(), but for bots whose policy is itself
     * seed-driven: totalHands is split into blocks of handsPerSeedBlock hands,
     * and at the start of every such block each bot's agent is rebuilt from its
     * factory with a fresh seed drawn from agentSeedSource. Independently,
     * WITHIN each seed-block, the seat permutation is redrawn every
     * seatShuffleBlockSize hands -- reseeding cadence and seat-shuffle cadence
     * are deliberately decoupled, since they address different concerns
     * (policy randomness vs. positional/order balance) and there's no reason
     * to force them to move together. dealSeedSource and agentSeedSource must
     * be independent streams -- conflating them would correlate agent
     * reseeding with specific deals.
     */
    public static BotPerformanceReport collectWithSeedRotation(
            List<NamedAgentFactory> bots,
            int smallBlind, int bigBlind, int buyIn,
            int totalHands, int handsPerSeedBlock, int seatShuffleBlockSize,
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
        if (seatShuffleBlockSize <= 0) {
            throw new IllegalArgumentException("seatShuffleBlockSize must be positive");
        }

        int n = bots.size();
        Random seatRandom = new Random(seatShuffleSeed);
        Map<String, List<Double>> bbSamplesByName = new LinkedHashMap<>();
        for (NamedAgentFactory b : bots) {
            bbSamplesByName.put(b.name(), new ArrayList<>());
        }

        int handsRemaining = totalHands;

        while (handsRemaining > 0) {
            int handsThisSeedBlock = Math.min(handsPerSeedBlock, handsRemaining);
            handsRemaining -= handsThisSeedBlock;

            List<PokerAgent> freshAgents = new ArrayList<>(n);
            for (NamedAgentFactory b : bots) {
                freshAgents.add(b.factory().apply(agentSeedSource.getAsInt()));
            }

            int seedBlockHandsRemaining = handsThisSeedBlock;
            while (seedBlockHandsRemaining > 0) {
                int handsThisShuffleBlock = Math.min(seatShuffleBlockSize, seedBlockHandsRemaining);
                seedBlockHandsRemaining -= handsThisShuffleBlock;

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
                        players, agents, smallBlind, bigBlind, buyIn, handsThisShuffleBlock, dealSeedSource, logger);

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
            int totalHands, int handsPerSeedBlock, int seatShuffleBlockSize,
            IntSupplier dealSeedSource, IntSupplier agentSeedSource, long seatShuffleSeed) {
        return collectWithSeedRotation(bots, smallBlind, bigBlind, buyIn, totalHands,
                handsPerSeedBlock, seatShuffleBlockSize, dealSeedSource, agentSeedSource,
                seatShuffleSeed, HandLogger.NO_OP);
    }
}