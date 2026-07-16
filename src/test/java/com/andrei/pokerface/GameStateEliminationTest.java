package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Covers the mark-and-skip elimination model: eliminated players never
 * receive the button, a blind, first-actor status, or hole cards, and
 * countLivePlayers() (not players.size()) drives every heads-up trigger.
 */
public class GameStateEliminationTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    // -------------------------------------------------------------------------
    // Player-level: eliminated + resetForNewHand interaction
    // -------------------------------------------------------------------------

    @Test
    void eliminate_setsEliminatedFlag() {
        Player p = new Player(0, "Alice", 1000);
        assertFalse(p.isEliminated());
        p.eliminate();
        assertTrue(p.isEliminated());
    }

    @Test
    void resetForNewHand_eliminatedPlayerStaysFoldedAfterReset() {
        Player p = new Player(0, "Alice", 0);
        p.eliminate();
        p.resetForNewHand();
        assertTrue(p.isFolded(), "eliminated player must reset into a pre-folded state");
        assertFalse(p.isActive());
    }

    @Test
    void resetForNewHand_nonEliminatedPlayerStillResetsToUnfolded() {
        Player p = new Player(0, "Alice", 1000);
        p.fold(); // folded THIS hand, but not eliminated
        p.resetForNewHand();
        assertFalse(p.isFolded(), "a merely-folded (not eliminated) player must be unfolded next hand");
    }

    @Test
    void resetForNewHand_eliminationSurvivesMultipleResets() {
        Player p = new Player(0, "Alice", 0);
        p.eliminate();
        p.resetForNewHand();
        p.resetForNewHand();
        assertTrue(p.isEliminated());
        assertTrue(p.isFolded());
    }

    // -------------------------------------------------------------------------
    // GameState: dealer rotation skips eliminated seats
    // -------------------------------------------------------------------------

    @Test
    void startNewHand_dealerRotationSkipsEliminatedSeat() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();

        state.startNewHand(1);
        assertEquals(2, state.getDealerIndex(), "dealer should skip eliminated seat 1 and land on 2");

        state.startNewHand(2);
        assertEquals(3, state.getDealerIndex());

        state.startNewHand(3);
        assertEquals(0, state.getDealerIndex(), "rotation wraps around, still skipping seat 1");

        state.startNewHand(4);
        assertEquals(2, state.getDealerIndex());
    }

    @Test
    void startNewHand_dealerNeverLandsOnEliminatedSeatAcrossManyRotations() {
        GameState state = new GameState(makePlayers(6, 1000), 5, 10);
        state.getPlayers().get(2).eliminate();
        state.getPlayers().get(4).eliminate();

        for (int i = 0; i < 20; i++) {
            state.startNewHand(i);
            assertFalse(state.getPlayers().get(state.getDealerIndex()).isEliminated(),
                    "dealer button must never sit on an eliminated seat");
        }
    }

    // -------------------------------------------------------------------------
    // GameState: dealHoleCards skips eliminated players
    // -------------------------------------------------------------------------

    @Test
    void dealHoleCards_skipsEliminatedPlayers() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();

        state.dealHoleCards(1);

        assertArrayEquals(new int[]{-1, -1}, state.getPlayers().get(1).getHoleCards(),
                "eliminated seat must not receive hole cards");
        assertNotEquals(-1, state.getPlayers().get(0).getHoleCards()[0]);
        assertNotEquals(-1, state.getPlayers().get(2).getHoleCards()[0]);
    }

    @Test
    void dealHoleCards_deckConsumptionReflectsOnlyLivePlayers() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();

        state.dealHoleCards(1); // 2 live players * 2 cards = 4 dealt

        assertEquals(48, state.buildPlayerView(0).deckRemaining());
    }

    // -------------------------------------------------------------------------
    // GameState: postBlinds skips eliminated seats and re-derives heads-up
    // -------------------------------------------------------------------------

    @Test
    void postBlinds_skipsEliminatedSeatForSbAndBb() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();
        // dealerIndex = 0; live seats in order from dealer: 2 (SB), 3 (BB)

        state.postBlinds();

        assertEquals(5, state.getPlayers().get(2).getRoundBet(), "SB should land on seat 2, skipping eliminated seat 1");
        assertEquals(10, state.getPlayers().get(3).getRoundBet(), "BB should land on seat 3");
        assertEquals(0, state.getPlayers().get(1).getRoundBet(), "eliminated seat must never post a blind");
    }

    @Test
    void postBlinds_headsUpTriggeredByLiveCountNotTotalSeats() {
        // 4 seats total, but only seats 0 and 3 are still live -> heads-up rules apply.
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();
        state.getPlayers().get(2).eliminate();

        state.postBlinds();

        assertEquals(5, state.getPlayers().get(0).getRoundBet(), "dealer posts SB heads-up, even with 4 nominal seats");
        assertEquals(10, state.getPlayers().get(3).getRoundBet(), "the other live seat posts BB");
        assertEquals(0, state.getPlayers().get(1).getRoundBet());
        assertEquals(0, state.getPlayers().get(2).getRoundBet());
    }

    @Test
    void postBlinds_tooFewLivePlayersThrows() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();
        state.getPlayers().get(2).eliminate();
        state.getPlayers().get(3).eliminate();
        // Only seat 0 remains live -- can't post two blinds.

        assertThrows(IllegalStateException.class, state::postBlinds);
    }

    // -------------------------------------------------------------------------
    // GameState: setFirstActor skips eliminated seats, re-derives N-handed rules
    // -------------------------------------------------------------------------

    @Test
    void setFirstActor_preflopThreeLiveActsLikeThreeHanded() {
        // 4 seats, 1 eliminated -> 3 live players -> button acts first preflop,
        // exactly like the genuine 3-handed case in GameStateTest.
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();
        state.postBlinds();

        state.setFirstActor();

        assertEquals(state.getDealerIndex(), state.getPlayerTurnIndex());
    }

    @Test
    void setFirstActor_preflopHeadsUpByLiveCount_dealerActsFirst() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();
        state.getPlayers().get(2).eliminate();
        state.postBlinds();

        state.setFirstActor();

        assertEquals(state.getDealerIndex(), state.getPlayerTurnIndex());
    }

    @Test
    void setFirstActor_postflopSkipsEliminatedSeat() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();
        state.advanceRound(); // FLOP

        state.setFirstActor();

        assertEquals(2, state.getPlayerTurnIndex(), "should skip eliminated seat 1 and land on seat 2");
    }

    @Test
    void setFirstActor_postflopAlsoSkipsFoldedLivePlayerAfterEliminatedSeat() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.getPlayers().get(1).eliminate();
        state.getPlayers().get(2).fold(); // folded this hand, but not eliminated
        state.advanceRound(); // FLOP

        state.setFirstActor();

        assertEquals(3, state.getPlayerTurnIndex(), "should skip both the eliminated seat and the folded seat");
    }

    // -------------------------------------------------------------------------
    // getLivePlayerCount()
    // -------------------------------------------------------------------------

    @Test
    void getLivePlayerCount_reflectsEliminations() {
        GameState state = new GameState(makePlayers(5, 1000), 5, 10);
        assertEquals(5, state.getLivePlayerCount());

        state.getPlayers().get(0).eliminate();
        state.getPlayers().get(3).eliminate();

        assertEquals(3, state.getLivePlayerCount());
    }

    // -------------------------------------------------------------------------
    // Full-hand integration: an eliminated player never acts across a whole hand
    // -------------------------------------------------------------------------

    @Test
    void eliminatedPlayer_neverGetsATurnAcrossAFullHandViaHandRunner() {
        List<Player> players = makePlayers(3, 1000);
        players.get(1).eliminate();
        GameState state = new GameState(players, 5, 10);

        List<PokerAgent> agents = List.of(new AlwaysCallAgent(), new AlwaysCallAgent(), new AlwaysCallAgent());
        HandRunner.playHand(state, agents, 7);

        // The eliminated seat should have been pre-folded for the whole hand and
        // therefore never contributed chips or received cards.
        assertArrayEquals(new int[]{-1, -1}, players.get(1).getHoleCards());
        assertEquals(0, players.get(1).getTotalCommitted());
        assertEquals(1000, players.get(1).getStack(), "eliminated player's stack must never move");
    }
}