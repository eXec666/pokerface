package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public class BotRingStatsCollectorTest {

    private IntSupplier incrementingSeeds() {
        AtomicInteger counter = new AtomicInteger(0);
        return counter::getAndIncrement;
    }

    // -------------------------------------------------------------------------
    // shuffledSeatAssignment() -- the core fix
    // -------------------------------------------------------------------------

    @Test
    void shuffledSeatAssignment_isAlwaysAValidPermutation() {
        Random r = new Random(1);
        for (int trial = 0; trial < 50; trial++) {
            List<Integer> assignment = BotRingStatsCollector.shuffledSeatAssignment(4, r);
            Set<Integer> seen = new TreeSet<>(assignment);
            assertEquals(Set.of(0, 1, 2, 3), seen, "must be a permutation of 0..n-1");
        }
    }

    @Test
    void shuffledSeatAssignment_variesAcrossCalls() {
        // Regression guard against reintroducing a fixed/cyclic pattern: with a
        // real shuffle, consecutive draws from the same Random should not all
        // land on the same permutation.
        Random r = new Random(1);
        List<Integer> first = BotRingStatsCollector.shuffledSeatAssignment(4, r);
        boolean sawDifferent = false;
        for (int i = 0; i < 20; i++) {
            if (!BotRingStatsCollector.shuffledSeatAssignment(4, r).equals(first)) {
                sawDifferent = true;
                break;
            }
        }
        assertTrue(sawDifferent, "shuffle must not collapse to a single repeated permutation");
    }

    @Test
    void shuffledSeatAssignment_sameSeedIsReproducible() {
        List<Integer> a = BotRingStatsCollector.shuffledSeatAssignment(4, new Random(99));
        List<Integer> b = BotRingStatsCollector.shuffledSeatAssignment(4, new Random(99));
        assertEquals(a, b);
    }

    @Test
    void shuffledSeatAssignment_exploresMoreThanJustCyclicShiftsOverManyDraws() {
        // With n=3 there are 6 possible permutations, only 3 of which are cyclic
        // shifts. If shuffledSeatAssignment only ever produced cyclic shifts,
        // this would be indistinguishable from the old (n + a cyclic offset)
        // scheme. Drawing many times with a real Random should surface at
        // least one non-cyclic-shift permutation.
        Random random = new Random(1);
        boolean sawNonCyclicPermutation = false;

        for (int i = 0; i < 50 && !sawNonCyclicPermutation; i++) {
            List<Integer> assignment = BotRingStatsCollector.shuffledSeatAssignment(3, random);
            boolean isCyclicShift = false;
            for (int shift = 0; shift < 3; shift++) {
                boolean matches = true;
                for (int seat = 0; seat < 3; seat++) {
                    if (assignment.get(seat) != (seat + shift) % 3) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    isCyclicShift = true;
                    break;
                }
            }
            if (!isCyclicShift) {
                sawNonCyclicPermutation = true;
            }
        }

        assertTrue(sawNonCyclicPermutation,
                "expected at least one non-cyclic-shift permutation across 50 draws");
    }

    // -------------------------------------------------------------------------
    // BotRingStatsCollector.collect -- argument validation
    // -------------------------------------------------------------------------

    @Test
    void collect_nonPositiveSeatShuffleBlockSizeThrows() {
        List<NamedAgent> bots = List.of(
                new NamedAgent("A", new AlwaysCallAgent()), new NamedAgent("B", new AlwaysCallAgent()));
        assertThrows(IllegalArgumentException.class, () -> BotRingStatsCollector.collect(
                bots, 5, 10, 1000, 100, 0, incrementingSeeds(), 1L));
    }

    // -------------------------------------------------------------------------
    // BotRingStatsCollector.collect -- full hand coverage regardless of block size
    // -------------------------------------------------------------------------

    @Test
    void collect_totalHandsFullyDistributedDespiteUnevenBlockSize() {
        // 1009 hands, blocks of 137, 3 bots -- block size doesn't divide evenly
        // into totalHands, exercising the final partial block.
        List<NamedAgent> bots = List.of(
                new NamedAgent("A", new AlwaysCallAgent()),
                new NamedAgent("B", new AlwaysCallAgent()),
                new NamedAgent("C", new AlwaysCallAgent()));

        BotPerformanceReport report = BotRingStatsCollector.collect(
                bots, 5, 10, 1000, 1009, 137, incrementingSeeds(), 7L);

        assertEquals(1009, report.forBot("A").handsPlayed());
        assertEquals(1009, report.forBot("B").handsPlayed());
        assertEquals(1009, report.forBot("C").handsPlayed());
    }

    @Test
    void collect_conservesTotalHandsPerBotAcrossShuffledSeating() {
        List<NamedAgent> bots = List.of(
                new NamedAgent("A", new AlwaysCallAgent()),
                new NamedAgent("B", new FoldingAgent()),
                new NamedAgent("C", new AllInAgent())
        );
        IntSupplier seedSource = new Random(5)::nextInt;

        BotPerformanceReport report = BotRingStatsCollector.collect(
                bots, 5, 10, 500, 3000, 100, seedSource, 11L);

        for (NamedAgent b : bots) {
            assertEquals(3000, report.forBot(b.name()).handsPlayed(),
                    "every bot must accumulate exactly totalHands samples regardless of seating");
        }
    }

    // -------------------------------------------------------------------------
    // Order-effect regression: a bot seated adjacent to a calling station in
    // EVERY block should no longer be structurally disadvantaged once the
    // seat permutation is redrawn frequently relative to totalHands.
    // -------------------------------------------------------------------------

    @Test
    void collect_frequentSeatShuffleNarrowsGapBetweenIdenticalRandomAgentsNextToACaller() {
        // Same setup that originally exposed the bug: two identical-policy
        // RandomAgents alongside an AlwaysCallAgent. With a small
        // seatShuffleBlockSize (frequent reshuffling relative to totalHands),
        // neither Random bot should end up consistently and structurally
        // disadvantaged by always trailing the caller in table order.
        List<NamedAgent> bots = List.of(
                new NamedAgent("RandomA", new RandomAgent(11)),
                new NamedAgent("RandomB", new RandomAgent(22)),
                new NamedAgent("Caller", new AlwaysCallAgent()));

        BotPerformanceReport report = BotRingStatsCollector.collect(
                bots, 5, 10, 1000, 30_000, 50, incrementingSeeds(), 99L);

        BotStatLine a = report.forBot("RandomA");
        BotStatLine b = report.forBot("RandomB");
        double gap = Math.abs(a.bbPer100() - b.bbPer100());
        double combinedStdErr = Math.sqrt(
                a.bbPer100StdError() * a.bbPer100StdError() + b.bbPer100StdError() * b.bbPer100StdError());

        assertTrue(gap < 10 * combinedStdErr,
                "identical-policy bots showed gap=" + gap + " vs combined stderr=" + combinedStdErr
                        + " -- frequent seat shuffling should have washed out order-dependent bias");
    }

    // -------------------------------------------------------------------------
    // BotRingStatsCollector.collectWithSeedRotation -- argument validation
    // -------------------------------------------------------------------------

    @Test
    void collectWithSeedRotation_nonPositiveSeatShuffleBlockSizeThrows() {
        List<NamedAgentFactory> bots = List.of(
                new NamedAgentFactory("A", seed -> new AlwaysCallAgent()),
                new NamedAgentFactory("B", seed -> new AlwaysCallAgent()));
        assertThrows(IllegalArgumentException.class, () -> BotRingStatsCollector.collectWithSeedRotation(
                bots, 5, 10, 1000, 100, 100, 0, incrementingSeeds(), incrementingSeeds(), 1L));
    }

    // -------------------------------------------------------------------------
    // BotRingStatsCollector.collectWithSeedRotation -- decoupled block sizes
    // -------------------------------------------------------------------------

    @Test
    void collectWithSeedRotation_totalHandsFullyDistributedWithIndependentBlockSizes() {
        // handsPerSeedBlock and seatShuffleBlockSize deliberately don't divide
        // each other evenly, confirming the nested loops handle partial blocks
        // correctly on both levels.
        List<NamedAgentFactory> bots = List.of(
                new NamedAgentFactory("A", seed -> new AlwaysCallAgent()),
                new NamedAgentFactory("B", seed -> new AlwaysCallAgent()));

        BotPerformanceReport report = BotRingStatsCollector.collectWithSeedRotation(
                bots, 5, 10, 1000, 733, 211, 60,
                incrementingSeeds(), incrementingSeeds(), 3L);

        assertEquals(733, report.forBot("A").handsPlayed());
        assertEquals(733, report.forBot("B").handsPlayed());
    }

    // -------------------------------------------------------------------------
    // Regression test for the reported bug: two exchangeable RandomAgents
    // must not diverge far beyond what their combined standard error allows.
    // -------------------------------------------------------------------------

    @Test
    void collectWithSeedRotation_symmetricRandomBotsHaveComparablePerformance() {
        List<NamedAgentFactory> bots = List.of(
                new NamedAgentFactory("Random-A", RandomAgent::new),
                new NamedAgentFactory("Random-B", RandomAgent::new),
                new NamedAgentFactory("Caller", seed -> new AlwaysCallAgent()),
                new NamedAgentFactory("Folder", seed -> new FoldingAgent())
        );

        IntSupplier dealSeedSource = new Random(42)::nextInt;
        IntSupplier agentSeedSource = new Random(1337)::nextInt;
        long seatShuffleSeed = 7L;

        BotPerformanceReport report = BotRingStatsCollector.collectWithSeedRotation(
                bots, 10, 20, 2000, 40_000, 100, 50, dealSeedSource, agentSeedSource, seatShuffleSeed);

        BotStatLine a = report.forBot("Random-A");
        BotStatLine b = report.forBot("Random-B");

        double diff = Math.abs(a.bbPer100() - b.bbPer100());
        double combinedStdErr = Math.sqrt(
                a.bbPer100StdError() * a.bbPer100StdError() + b.bbPer100StdError() * b.bbPer100StdError());

        // Before the fix this ratio was ~10x; a real fix should keep two
        // policy-identical bots within a handful of combined standard errors.
        assertTrue(diff < 6 * combinedStdErr,
                "two exchangeable Random bots diverged far beyond sampling noise: diff=" + diff
                        + " combinedStdErr=" + combinedStdErr);
    }
}