package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Tag;

public class SessionRunnerTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    /** Deterministic, ever-incrementing seed source -- every hand gets a distinct deal. */
    private IntSupplier incrementingSeeds() {
        AtomicInteger counter = new AtomicInteger(0);
        return counter::getAndIncrement;
    }

    // -------------------------------------------------------------------------
    // BlindLevel
    // -------------------------------------------------------------------------

    @Test
    void blindLevel_rejectsNonPositiveSmallBlind() {
        assertThrows(IllegalArgumentException.class, () -> new BlindLevel(0, 10));
    }

    @Test
    void blindLevel_rejectsNonPositiveBigBlind() {
        assertThrows(IllegalArgumentException.class, () -> new BlindLevel(5, 0));
    }

    @Test
    void blindLevel_rejectsBigBlindSmallerThanSmallBlind() {
        assertThrows(IllegalArgumentException.class, () -> new BlindLevel(20, 10));
    }

    @Test
    void blindLevel_acceptsEqualSmallAndBigBlind() {
        assertDoesNotThrow(() -> new BlindLevel(10, 10));
    }

    // -------------------------------------------------------------------------
    // BlindSchedule.constant
    // -------------------------------------------------------------------------

    @Test
    void blindSchedule_constantReturnsSameLevelRegardlessOfHandsPlayed() {
        BlindSchedule schedule = BlindSchedule.constant(5, 10);
        assertEquals(new BlindLevel(5, 10), schedule.blindsFor(0));
        assertEquals(new BlindLevel(5, 10), schedule.blindsFor(500));
    }

    // -------------------------------------------------------------------------
    // BustHandler.ELIMINATE
    // -------------------------------------------------------------------------

    @Test
    void bustHandler_eliminateMarksPlayerEliminated() {
        Player p = new Player(0, "Alice", 0);
        assertFalse(p.isEliminated());
        BustHandler.ELIMINATE.onBust(p);
        assertTrue(p.isEliminated());
    }

    // -------------------------------------------------------------------------
    // SessionEndCondition
    // -------------------------------------------------------------------------

    @Test
    void lastPlayerStanding_falseWhileMultiplePlayersLive() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        assertFalse(SessionEndCondition.LAST_PLAYER_STANDING.isSessionOver(state, 5));
    }

    @Test
    void lastPlayerStanding_trueWhenOnlyOneLivePlayerRemains() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        state.getPlayers().get(0).eliminate();
        state.getPlayers().get(1).eliminate();
        assertTrue(SessionEndCondition.LAST_PLAYER_STANDING.isSessionOver(state, 5));
    }

    @Test
    void orAfter_stopsOnceHandCapReachedEvenIfMultipleLive() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        SessionEndCondition capped = SessionEndCondition.LAST_PLAYER_STANDING.orAfter(10);

        assertFalse(capped.isSessionOver(state, 9));
        assertTrue(capped.isSessionOver(state, 10));
    }

    @Test
    void orAfter_stillStopsEarlyOnGenuineElimination() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        state.getPlayers().get(0).eliminate();
        state.getPlayers().get(1).eliminate();
        SessionEndCondition capped = SessionEndCondition.LAST_PLAYER_STANDING.orAfter(1000);

        assertTrue(capped.isSessionOver(state, 1), "elimination should end the session long before the cap");
    }

    // -------------------------------------------------------------------------
    // SessionResult.winner()
    // -------------------------------------------------------------------------

    @Test
    void sessionResult_winnerPresentWhenExactlyOneLivePlayerRemains() {
        List<Player> players = makePlayers(3, 1000);
        players.get(1).eliminate();
        players.get(2).eliminate();

        SessionResult result = new SessionResult(20, players);

        assertTrue(result.winner().isPresent());
        assertSame(players.get(0), result.winner().get());
    }

    @Test
    void sessionResult_winnerEmptyWhenMultiplePlayersStillLive() {
        List<Player> players = makePlayers(3, 1000);
        players.get(1).eliminate();

        SessionResult result = new SessionResult(20, players);

        assertTrue(result.winner().isEmpty());
    }

    // -------------------------------------------------------------------------
    // SessionRunner.runSession -- argument validation
    // -------------------------------------------------------------------------

    @Test
    void runSession_mismatchedPlayerAndAgentCountsThrows() {
        List<Player> players = makePlayers(2, 1000);
        List<PokerAgent> agents = List.of(new AlwaysCallAgent()); // only 1 agent for 2 players

        assertThrows(IllegalArgumentException.class, () -> SessionRunner.runSession(
                players, agents,
                BlindSchedule.constant(5, 10),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING,
                incrementingSeeds(),
                HandLogger.NO_OP));
    }

    // -------------------------------------------------------------------------
    // SessionRunner -- integration: freezeout to a single winner
    // -------------------------------------------------------------------------

    @Test
    void runSession_terminatesWithExactlyOneWinnerAndConservesChips() {
        List<Player> players = makePlayers(2, 200);
        List<PokerAgent> agents = List.of(new AllInAgent(), new AllInAgent());
        int totalBefore = players.stream().mapToInt(Player::getStack).sum();

        // Safety cap: two AllInAgents decide the whole stack within the first hand
        // in the overwhelming majority of deals; the cap only guards the freak-tie case.
        SessionResult result = SessionRunner.runSession(
                players, agents,
                BlindSchedule.constant(5, 10),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING.orAfter(30),
                incrementingSeeds(),
                HandLogger.NO_OP);

        assertTrue(result.winner().isPresent(), "two all-in players should resolve to a single winner well within the cap");
        int totalAfter = players.stream().mapToInt(Player::getStack).sum();
        assertEquals(totalBefore, totalAfter, "no chips may be created or destroyed across the session");
        assertEquals(totalBefore, result.winner().get().getStack(), "winner should hold every chip in play");
    }

    @Test
    void runSession_bustPlayerIsMarkedEliminatedInFinalPlayers() {
        List<Player> players = makePlayers(2, 200);
        List<PokerAgent> agents = List.of(new AllInAgent(), new AllInAgent());

        SessionResult result = SessionRunner.runSession(
                players, agents,
                BlindSchedule.constant(5, 10),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING.orAfter(30),
                incrementingSeeds(),
                HandLogger.NO_OP);

        long eliminatedCount = result.finalPlayers().stream().filter(Player::isEliminated).count();
        assertEquals(1, eliminatedCount, "exactly the losing player should be eliminated");
    }

    // -------------------------------------------------------------------------
    // SessionRunner -- Multiway freezeout with RandomAgents on every seat
    // -------------------------------------------------------------------------

    @Test
    void runSession_multiwayFreezeoutWithRandomAgentsTerminatesAndConservesChips() {
        // 5 players, mixed stack depths, RandomAgents driving every seat -- exercises
        // FOLD/CHECK/CALL/RAISE, side pots, and elimination together in one session.
        List<Player> players = makePlayers(5, 500);
        List<PokerAgent> agents = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            agents.add(new RandomAgent(1000 + i));
        }
        int totalBefore = players.stream().mapToInt(Player::getStack).sum();

        SessionResult result = SessionRunner.runSession(
                players, agents,
                BlindSchedule.constant(5, 10),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING.orAfter(2000),
                incrementingSeeds(),
                HandLogger.NO_OP);

        assertTrue(result.winner().isPresent(), "5-way freezeout should resolve to a single winner within 2000 hands");
        int totalAfter = players.stream().mapToInt(Player::getStack).sum();
        assertEquals(totalBefore, totalAfter, "no chips may be created or destroyed across a multiway session");

        long eliminatedCount = result.finalPlayers().stream().filter(Player::isEliminated).count();
        assertEquals(4, eliminatedCount, "exactly the four losing players should be eliminated");
    }

    // -------------------------------------------------------------------------
    // SessionRunner -- orAfter cap stops the loop without a decisive winner
    // -------------------------------------------------------------------------

    @Test
    void runSession_stopsAtHandCapWithoutEliminationWhenNoOneBusts() {
        // Two FoldingAgents: whichever seat is BB each hand wins a small fold-out pot,
        // and the dealer/SB seat alternates every hand, so net drift stays near zero --
        // nobody busts, so only the hand cap should end the session.
        List<Player> players = makePlayers(2, 1000);
        List<PokerAgent> agents = List.of(new FoldingAgent(), new FoldingAgent());

        SessionResult result = SessionRunner.runSession(
                players, agents,
                BlindSchedule.constant(5, 10),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING.orAfter(10),
                incrementingSeeds(),
                HandLogger.NO_OP);

        assertEquals(10, result.handsPlayed(), "loop should run exactly to the hand cap");
        assertTrue(result.winner().isEmpty(), "neither player should have busted from blind attrition alone in 10 hands");
    }

    // -------------------------------------------------------------------------
    // SessionRunner.runFreezeout convenience overload
    // -------------------------------------------------------------------------

    @Test
    void runFreezeout_convenienceOverloadWiresTournamentDefaults() {
        List<Player> players = makePlayers(2, 150);
        List<PokerAgent> agents = List.of(new AllInAgent(), new AllInAgent());
        int totalBefore = players.stream().mapToInt(Player::getStack).sum();

        SessionResult result = SessionRunner.runFreezeout(players, agents, 5, 10, incrementingSeeds());

        assertTrue(result.winner().isPresent());
        assertEquals(totalBefore, players.stream().mapToInt(Player::getStack).sum());
    }

    // -------------------------------------------------------------------------
    // Fuzz harness -- manual-only, excluded from CI via @Tag("fuzz")
    // -------------------------------------------------------------------------

    @Tag("fuzz")
    @Test
    void fuzz_multiwaySessionsAcrossManySeedsSurfaceEdgeCases() {
        int trials = 2000;
        List<Integer> failures = new ArrayList<>();
        Exception lastException = null;

        for (int offset = 0; offset < trials; offset++) {
            List<Player> players = makePlayers(6, 200); // shallow stacks -> frequent all-ins
            List<PokerAgent> agents = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                agents.add(new RandomAgent(offset * 31L + i));
            }

            int totalBefore = players.stream().mapToInt(Player::getStack).sum();
            java.util.Random handSeedGen = new java.util.Random(offset);
            IntSupplier seedSource = handSeedGen::nextInt;

            try {
                SessionRunner.runSession(
                        players, agents,
                        BlindSchedule.constant(5, 10),
                        BustHandler.ELIMINATE,
                        SessionEndCondition.LAST_PLAYER_STANDING.orAfter(1000),
                        seedSource,
                        HandLogger.NO_OP);

                int totalAfter = players.stream().mapToInt(Player::getStack).sum();
                if (totalBefore != totalAfter) {
                    throw new AssertionError("chip conservation violated: before=" + totalBefore + " after=" + totalAfter);
                }
            } catch (Exception | AssertionError e) {
                failures.add(offset);
                lastException = (e instanceof Exception) ? (Exception) e : new RuntimeException(e);
                System.err.println("offset=" + offset + " failed: " + e);
            }
        }

        if (!failures.isEmpty()) {
            fail(failures.size() + "/" + trials + " seed offsets failed. First few: " + failures.subList(0, Math.min(10, failures.size())), lastException);
        }
    }
}