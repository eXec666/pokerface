package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntSupplier;

public class BotRingStatsCollectorTest {

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
                bots, 10, 20, 2000, 40_000, 100, dealSeedSource, agentSeedSource, seatShuffleSeed);

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

    @Test
    void collect_conservesTotalHandsPerBotAcrossShuffledSeating() {
        List<NamedAgent> bots = List.of(
                new NamedAgent("A", new AlwaysCallAgent()),
                new NamedAgent("B", new FoldingAgent()),
                new NamedAgent("C", new AllInAgent())
        );
        IntSupplier seedSource = new Random(5)::nextInt;

        BotPerformanceReport report = BotRingStatsCollector.collect(
                bots, 5, 10, 500, 3000, seedSource, 11L);

        for (NamedAgent b : bots) {
            assertEquals(3000, report.forBot(b.name()).handsPlayed(),
                    "every bot must accumulate exactly totalHands samples regardless of seating");
        }
    }
}