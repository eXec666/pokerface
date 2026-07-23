package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public class RingGameRunnerTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    private IntSupplier incrementingSeeds() {
        AtomicInteger counter = new AtomicInteger(0);
        return counter::getAndIncrement;
    }

    // -------------------------------------------------------------------------
    // Argument validation
    // -------------------------------------------------------------------------

    @Test
    void runBatch_mismatchedPlayerAndAgentCountsThrows() {
        List<Player> players = makePlayers(2, 1000);
        List<PokerAgent> agents = List.of(new AlwaysCallAgent()); // only 1 agent

        assertThrows(IllegalArgumentException.class, () -> RingGameRunner.runBatch(
                players, agents, 5, 10, 1000, 10, incrementingSeeds()));
    }

    @Test
    void runBatch_nonPositiveBuyInThrows() {
        List<Player> players = makePlayers(2, 1000);
        List<PokerAgent> agents = List.of(new AlwaysCallAgent(), new AlwaysCallAgent());

        assertThrows(IllegalArgumentException.class, () -> RingGameRunner.runBatch(
                players, agents, 5, 10, 0, 10, incrementingSeeds()));
    }

    @Test
    void runBatch_nonPositiveHandsToPlayThrows() {
        List<Player> players = makePlayers(2, 1000);
        List<PokerAgent> agents = List.of(new AlwaysCallAgent(), new AlwaysCallAgent());

        assertThrows(IllegalArgumentException.class, () -> RingGameRunner.runBatch(
                players, agents, 5, 10, 1000, 0, incrementingSeeds()));
    }

    // -------------------------------------------------------------------------
    // Core behavior
    // -------------------------------------------------------------------------

    @Test
    void runBatch_playsExactlyTheRequestedHandCount() {
        List<Player> players = makePlayers(2, 1000);
        List<PokerAgent> agents = List.of(new AlwaysCallAgent(), new AlwaysCallAgent());

        RingGameResult result = RingGameRunner.runBatch(
                players, agents, 5, 10, 1000, 200, incrementingSeeds());

        assertEquals(200, result.handsPlayed());
        assertEquals(200, result.netChipsPerHand().size());
    }

    @Test
    void runBatch_netChipsSumToZeroForEveryHand() {
        // Chip conservation within any single hand must hold regardless of the
        // stack-reset happening between hands -- each hand is still a closed system.
        List<Player> players = makePlayers(4, 500);
        List<PokerAgent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new RandomAgent(i * 13L + 1));
        }

        RingGameResult result = RingGameRunner.runBatch(
                players, agents, 5, 10, 500, 300, incrementingSeeds());

        for (int[] handNet : result.netChipsPerHand()) {
            int sum = 0;
            for (int v : handNet) sum += v;
            assertEquals(0, sum, "net chip changes across all seats must sum to zero for every hand");
        }
    }

    @Test
    void runBatch_neverEliminatesPlayersRegardlessOfBustingWithinAHand() {
        // AllInAgent vs AllInAgent guarantees somebody hits 0 stack most hands --
        // ring play must not treat that as elimination.
        List<Player> players = makePlayers(2, 200);
        List<PokerAgent> agents = List.of(new AllInAgent(), new AllInAgent());

        RingGameResult result = RingGameRunner.runBatch(
                players, agents, 5, 10, 200, 50, incrementingSeeds());

        assertEquals(50, result.handsPlayed(), "busting within a hand must not halt the batch");
        for (Player p : players) {
            assertFalse(p.isEliminated(), "ring play must never eliminate a player");
        }
    }

    @Test
    void runBatch_stacksAreIndependentlyResetEachHand() {
        // Even though AllInAgent can drive a stack to 0, every subsequent hand
        // must still be played with a full buy-in on both sides -- proven by
        // every recorded hand's net-chip magnitude being bounded by the buy-in.
        List<Player> players = makePlayers(2, 300);
        List<PokerAgent> agents = List.of(new AllInAgent(), new AllInAgent());

        RingGameResult result = RingGameRunner.runBatch(
                players, agents, 5, 10, 300, 40, incrementingSeeds());

        for (int[] handNet : result.netChipsPerHand()) {
            for (int v : handNet) {
                assertTrue(Math.abs(v) <= 300,
                        "no hand should show a net swing larger than the fixed buy-in");
            }
        }
    }

    @Test
    void runBatch_dealerRotatesAcrossHands() {
        List<Player> players = makePlayers(2, 1000);
        List<PokerAgent> agents = List.of(new AlwaysCallAgent(), new AlwaysCallAgent());
        InMemoryHandLogger logger = new InMemoryHandLogger();

        RingGameRunner.runBatch(players, agents, 5, 10, 1000, 6, incrementingSeeds(), logger);

        List<Integer> dealerSeats = logger.getEvents().stream()
                .filter(e -> e instanceof GameEvent.HandStarted)
                .map(e -> ((GameEvent.HandStarted) e).dealerSeat())
                .toList();

        assertEquals(6, dealerSeats.size());
        for (int i = 1; i < dealerSeats.size(); i++) {
            assertNotEquals(dealerSeats.get(i - 1), dealerSeats.get(i),
                    "heads-up dealer button must keep alternating across hands");
        }
    }

    // -------------------------------------------------------------------------
    // RingGameResult -- bb/100 math
    // -------------------------------------------------------------------------

    @Test
    void meanBbPerHand_computesCorrectAverage() {
        List<int[]> hands = List.of(new int[]{20, -20}, new int[]{-10, 10});
        RingGameResult result = new RingGameResult(2, hands, 10);

        assertEquals(0.5, result.meanBbPerHand(0), 1e-9);
        assertEquals(-0.5, result.meanBbPerHand(1), 1e-9);
    }

    @Test
    void bbPer100_scalesMeanByOneHundred() {
        List<int[]> hands = List.of(new int[]{20, -20}, new int[]{-10, 10});
        RingGameResult result = new RingGameResult(2, hands, 10);

        assertEquals(50.0, result.bbPer100(0), 1e-9);
        assertEquals(-50.0, result.bbPer100(1), 1e-9);
    }

    @Test
    void bbPer100StdError_computesSampleStandardError() {
        // seat0 per-hand bb values: 2, -1 -> mean 0.5, sample variance 4.5,
        // stdErrorPerHand = sqrt(4.5/2) = 1.5 -> bbPer100StdError = 150
        List<int[]> hands = List.of(new int[]{20, -20}, new int[]{-10, 10});
        RingGameResult result = new RingGameResult(2, hands, 10);

        assertEquals(150.0, result.bbPer100StdError(0), 1e-9);
    }

    @Test
    void bbPer100StdError_isZeroForASingleHand() {
        List<int[]> hands = List.of(new int[]{20, -20});
        RingGameResult result = new RingGameResult(1, hands, 10);

        assertEquals(0.0, result.bbPer100StdError(0), 1e-9);
    }

    @Test
    void seatCount_reflectsWidthOfRecordedHands() {
        List<int[]> hands = List.of(new int[]{20, -20, 0});
        RingGameResult result = new RingGameResult(1, hands, 10);

        assertEquals(3, result.seatCount());
    }

    @Test
    void seatCount_isZeroWhenNoHandsRecorded() {
        RingGameResult result = new RingGameResult(0, List.of(), 10);
        assertEquals(0, result.seatCount());
    }

    @Test
    void netChipsPerHand_isDefensivelyCopied() {
        List<int[]> mutableHands = new ArrayList<>();
        mutableHands.add(new int[]{10, -10});
        RingGameResult result = new RingGameResult(1, mutableHands, 10);

        mutableHands.add(new int[]{5, -5});

        assertEquals(1, result.netChipsPerHand().size(),
                "mutating the input list after construction must not affect the result");
    }

    // -------------------------------------------------------------------------
    // Sensecheck: identical random agents should show ~0 bb/100 with real overlap around zero
    // -------------------------------------------------------------------------

    @Test
    void sensecheck_identicalRandomAgentsShowNoSignificantEdgeForAnySeat() {
        List<Player> players = makePlayers(4, 1000);
        List<PokerAgent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new RandomAgent(500 + i)); // same weights, different seeds only
        }

        RingGameResult result = RingGameRunner.runBatch(
                players, agents, 5, 10, 1000, 2000, incrementingSeeds());

        for (int seat = 0; seat < 4; seat++) {
            double bb100 = result.bbPer100(seat);
            double stdErr = result.bbPer100StdError(seat);
            // A real, reproducible edge should be many standard errors from zero;
            // this just checks the observed edge isn't wildly outside its own
            // uncertainty band, which would indicate an engine bug (e.g. a
            // positional/blind asymmetry) rather than sampling noise.
            assertTrue(Math.abs(bb100) < 10 * stdErr + 50,
                    "seat " + seat + " showed bb100=" + bb100 + " stdErr=" + stdErr
                            + " -- disproportionate to sampling noise, suggests an engine asymmetry");
        }
    }
}