package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public class MonteCarloCallFoldAgentTest {

    /*
     * Card encoding reminder (consistent with HandEvalTest / MonteCarloEquityEstimatorTest):
     *   Ac=0, Kc=48, Qc=44, Jc=40, Tc=36, 2d=5, 3h=10, 4c=12, 2c=4
     */

    private PlayerView scriptedView(
            int[] myHoleCards, int[] communityCards,
            int myRoundBet, int myStack,
            int currentBet, int potTotal) {
        List<OpponentInfo> infos = List.of(
                new OpponentInfo(0, "Me", myStack, myRoundBet, myRoundBet, false, false),
                new OpponentInfo(1, "Opp", 1000, currentBet, currentBet, false, false));
        return new PlayerView(
                0, myHoleCards, communityCards, Round.RIVER, 0, 20,
                currentBet, currentBet + 10, potTotal, List.of(potTotal), infos);
    }

    // -------------------------------------------------------------------------
    // Nothing owed -> always checks, regardless of hand strength
    // -------------------------------------------------------------------------

    @Test
    void performAction_nothingOwed_checksWithoutRunningEquityEstimation() {
        // 2c, 4c -- a weak hand that would very likely fold if forced to call;
        // confirms the free-check short circuit fires before any equity math.
        PlayerView view = scriptedView(new int[]{4, 12}, new int[0], 0, 1000, 0, 20);
        MonteCarloCallFoldAgent agent = new MonteCarloCallFoldAgent(500, 1);

        ActionResult result = agent.performAction(view);

        assertEquals(Action.CHECK, result.action());
    }

    // -------------------------------------------------------------------------
    // Deterministic call: equity is exactly 1.0 (unbeatable hand, full board)
    // -------------------------------------------------------------------------

    @Test
    void performAction_unbeatableHandWithFullBoard_alwaysCallsRegardlessOfBetSize() {
        // Ac, Kc + Qc, Jc, Tc, 2d, 3h on the board = royal flush in clubs, using
        // every club needed for it. No possible opponent hand can beat or tie
        // this, so estimated equity is exactly 1.0 every time (per
        // MonteCarloEquityEstimatorTest's equivalent scenario) -- the call
        // decision is deterministic no matter the seed or trial count.
        int[] holeCards = {0, 48};
        int[] communityCards = {44, 40, 36, 5, 10};
        PlayerView view = scriptedView(holeCards, communityCards, 0, 1000, 500, 500);

        MonteCarloCallFoldAgent agent = new MonteCarloCallFoldAgent(50, 123);

        assertEquals(Action.CALL, agent.performAction(view).action());
    }

    @Test
    void performAction_unbeatableHand_staysCallAcrossManyDifferentSeeds() {
        int[] holeCards = {0, 48};
        int[] communityCards = {44, 40, 36, 5, 10};
        PlayerView view = scriptedView(holeCards, communityCards, 0, 1000, 900, 100);

        for (long seed = 0; seed < 20; seed++) {
            MonteCarloCallFoldAgent agent = new MonteCarloCallFoldAgent(100, seed);
            assertEquals(Action.CALL, agent.performAction(view).action(),
                    "seed " + seed + " should still call an unbeatable hand");
        }
    }

    // -------------------------------------------------------------------------
    // Deterministic fold: pot odds require far more equity than any hand has
    // -------------------------------------------------------------------------

    @Test
    void performAction_weakHandFacingHugeOverbet_folds() {
        // 2c, 4c preflop, facing a bet so large relative to the pot that the
        // required equity threshold is ~0.99. No starting hand -- not even
        // pocket aces at ~85% -- clears that bar, so this fold is stable
        // regardless of sampling noise.
        int[] holeCards = {4, 12}; // 2c, 4c
        int[] communityCards = new int[0];
        PlayerView view = scriptedView(holeCards, communityCards, 0, 1000, 990, 10);

        MonteCarloCallFoldAgent agent = new MonteCarloCallFoldAgent(2000, 55);

        assertEquals(Action.FOLD, agent.performAction(view).action());
    }

    @Test
    void performAction_weakHand_staysFoldAcrossManyDifferentSeeds() {
        int[] holeCards = {4, 12};
        int[] communityCards = new int[0];
        PlayerView view = scriptedView(holeCards, communityCards, 0, 1000, 990, 10);

        for (long seed = 0; seed < 20; seed++) {
            MonteCarloCallFoldAgent agent = new MonteCarloCallFoldAgent(200, seed);
            assertEquals(Action.FOLD, agent.performAction(view).action(),
                    "seed " + seed + " should still fold a weak hand facing a huge overbet");
        }
    }

    // -------------------------------------------------------------------------
    // Reproducibility: same seed and trial count -> same decision
    // -------------------------------------------------------------------------

    @Test
    void performAction_isReproducibleForAFixedSeed() {
        int[] holeCards = {0, 1}; // Ac, Ad -- borderline-ish preflop spot
        int[] communityCards = new int[0];
        PlayerView view = scriptedView(holeCards, communityCards, 0, 1000, 100, 100);

        MonteCarloCallFoldAgent first = new MonteCarloCallFoldAgent(3000, 42);
        MonteCarloCallFoldAgent second = new MonteCarloCallFoldAgent(3000, 42);

        assertEquals(first.performAction(view).action(), second.performAction(view).action(),
                "identical seed and trial count must produce the same decision on the same view");
    }

    // -------------------------------------------------------------------------
    // Integration: a full heads-up ring-game batch against RandomAgent
    // -------------------------------------------------------------------------

    private IntSupplier incrementingSeeds() {
        AtomicInteger counter = new AtomicInteger(0);
        return counter::getAndIncrement;
    }

    @Test
    void integratesWithRingGameRunner_playsAFullHeadsUpBatchWithoutErrors() {
        List<Player> players = List.of(new Player(0, "MonteCarlo", 1000), new Player(1, "Random", 1000));
        List<PokerAgent> agents = List.of(new MonteCarloCallFoldAgent(300, 7), new RandomAgent(99));

        assertDoesNotThrow(() -> RingGameRunner.runBatch(
                players, agents, 5, 10, 1000, 300, incrementingSeeds()));
    }

    @Test
    void integratesWithRingGameRunner_agentActuallyUsesBothCallAndFoldInRealPlay() {
        // Confirms the agent isn't degenerately always-folding or always-calling
        // once wired into real hands with varying hole cards and bet sizes --
        // a structural check, not a profitability check.
        List<Player> players = List.of(new Player(0, "MonteCarlo", 1000), new Player(1, "Random", 1000));
        List<PokerAgent> agents = List.of(new MonteCarloCallFoldAgent(300, 7), new RandomAgent(99));
        InMemoryHandLogger logger = new InMemoryHandLogger();

        RingGameRunner.runBatch(players, agents, 5, 10, 1000, 500, incrementingSeeds(), logger);

        long folds = logger.getEvents().stream()
                .filter(e -> e instanceof GameEvent.ActionTaken a && a.seatIndex() == 0 && a.action() == Action.FOLD)
                .count();
        long calls = logger.getEvents().stream()
                .filter(e -> e instanceof GameEvent.ActionTaken a && a.seatIndex() == 0 && a.action() == Action.CALL)
                .count();

        assertTrue(folds > 0, "agent should fold at least sometimes across 500 hands");
        assertTrue(calls > 0, "agent should call at least sometimes across 500 hands");
    }

    @Test
    void sensecheck_beatsRandomAgentHeadsUpByAWideMargin() {
        // Loose sanity check, not a rigorous benchmark (RunRingGameSim/BotRingStatsCollector
        // are the tools for that). RandomAgent bets and calls with no regard for hand
        // strength; a pot-odds-aware agent should not show a strongly negative result
        // against it. The bound here is deliberately generous to avoid flaking on
        // ordinary sampling variance.
        List<Player> players = List.of(new Player(0, "MonteCarlo", 1000), new Player(1, "Random", 1000));
        List<PokerAgent> agents = List.of(new MonteCarloCallFoldAgent(300, 7), new RandomAgent(99));

        RingGameResult result = RingGameRunner.runBatch(
                players, agents, 5, 10, 1000, 3000, incrementingSeeds());

        assertTrue(result.bbPer100(0) > -100,
                "expected the equity-aware agent not to show a strongly negative bb/100 vs RandomAgent, got "
                        + result.bbPer100(0));
    }
}