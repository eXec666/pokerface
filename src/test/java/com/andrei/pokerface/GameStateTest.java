package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameStateTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Test
    void constructor_rejectsFewerThanTwoPlayers() {
        List<Player> onePlayer = makePlayers(1, 1000);
        assertThrows(IllegalArgumentException.class, () -> new GameState(onePlayer, 5, 10));
    }

    @Test
    void constructor_rejectsNullPlayerList() {
        assertThrows(IllegalArgumentException.class, () -> new GameState(null, 5, 10));
    }

    @Test
    void constructor_setsExpectedInitialState() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        assertEquals(Round.PREFLOP, state.getRound());
        assertEquals(0, state.getDealerIndex());
        assertEquals(0, state.getPlayerTurnIndex());
        assertEquals(0, state.getCurrentBet());
        assertFalse(state.isHandComplete());
        assertEquals(0, state.getCommunityCards().length);
    }

    @Test
    void constructor_fourArgOverloadSetsInitialDealerIndex() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10, 2);
        assertEquals(2, state.getDealerIndex());
        assertEquals(2, state.getPlayerTurnIndex());
    }

    @Test
    void constructor_fourArgOverload_startNewHandAdvancesFromInitialDealerIndex() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10, 2);
        state.startNewHand(1);
        assertEquals(0, state.getDealerIndex(), "dealer should advance from the supplied initial index, wrapping 2 -> 0");
    }

    @Test
    void constructor_threeArgOverloadStillDefaultsDealerIndexToZero() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        assertEquals(0, state.getDealerIndex());
    }

    // -------------------------------------------------------------------------
    // Dealing
    // -------------------------------------------------------------------------

    @Test
    void dealHoleCards_dealsDistinctCardsToEveryPlayer() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.dealHoleCards(42);

        Set<Integer> seen = new HashSet<>();
        for (Player p : state.getPlayers()) {
            for (int card : p.getHoleCards()) {
                assertNotEquals(-1, card, "every hole card slot should be filled");
                assertTrue(seen.add(card), "no card should be dealt twice");
            }
        }
        assertEquals(8, seen.size()); // 4 players * 2 cards
    }

    @Test
    void dealCommunityCard_stopsAtFiveCards() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        for (int i = 0; i < 5; i++) {
            state.dealCommunityCard();
        }
        assertEquals(5, state.getCommunityCards().length);
        assertThrows(IllegalStateException.class, state::dealCommunityCard);
    }

    @Test
    void getCommunityCards_returnsOnlyDealtPortion() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealCommunityCard();
        state.dealCommunityCard();
        state.dealCommunityCard();
        assertEquals(3, state.getCommunityCards().length);
    }

    @Test
    void getEvaluableCards_combinesHoleAndCommunityCards() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(7);
        state.dealCommunityCard();
        state.dealCommunityCard();
        state.dealCommunityCard();

        Player p = state.getPlayers().get(0);
        int[] combined = state.getEvaluableCards(p);
        assertEquals(5, combined.length); // 2 hole + 3 community
    }

    // -------------------------------------------------------------------------
    // Betting bookkeeping: commitChips / foldPlayer / advanceRound / advanceTurn
    // -------------------------------------------------------------------------

    @Test
    void commitChips_raisesCurrentBetToHighestRoundBet() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        List<Player> players = state.getPlayers();

        state.commitChips(players.get(0), 50);
        assertEquals(50, state.getCurrentBet());

        state.commitChips(players.get(1), 120);
        assertEquals(120, state.getCurrentBet());
    }

    @Test
    void foldPlayer_marksPlayerFolded() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        Player p = state.getPlayers().get(0);
        state.foldPlayer(p);
        assertTrue(p.isFolded());
    }

    @Test
    void advanceRound_cyclesThroughAllRoundsInOrder() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        assertEquals(Round.PREFLOP, state.getRound());
        state.advanceRound();
        assertEquals(Round.FLOP, state.getRound());
        state.advanceRound();
        assertEquals(Round.TURN, state.getRound());
        state.advanceRound();
        assertEquals(Round.RIVER, state.getRound());
        state.advanceRound();
        assertEquals(Round.SHOWDOWN, state.getRound());
    }

    @Test
    void advanceRound_throwsPastShowdown() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.advanceRound(); // FLOP
        state.advanceRound(); // TURN
        state.advanceRound(); // RIVER
        state.advanceRound(); // SHOWDOWN
        assertThrows(IllegalStateException.class, state::advanceRound);
    }

    @Test
    void advanceRound_resetsCurrentBetAndPlayerRoundBets() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        Player p = state.getPlayers().get(0);
        state.commitChips(p, 100);

        state.advanceRound();

        assertEquals(0, state.getCurrentBet());
        assertEquals(0, p.getRoundBet());
        assertEquals(100, p.getTotalCommitted(), "totalCommitted must survive into the next round");
    }

    @Test
    void advanceTurn_skipsFoldedAndAllInPlayers() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        players.get(1).fold();
        players.get(2).commit(1000); // exact stack -> all-in

        // playerTurnIndex starts at 0; next active player should be seat 3
        state.advanceTurn();
        assertEquals(3, state.getPlayerTurnIndex());
    }

    @Test
    void advanceTurn_wrapsAroundTheTable() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        assertEquals(0, state.getPlayerTurnIndex());
        state.advanceTurn();
        assertEquals(1, state.getPlayerTurnIndex());
        state.advanceTurn();
        assertEquals(0, state.getPlayerTurnIndex());
    }

    // -------------------------------------------------------------------------
    // isBettingOver()
    // -------------------------------------------------------------------------

    @Test
    void isBettingOver_falseWhenMultipleActivePlayersRemain() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        assertFalse(state.isBettingOver());
    }

    @Test
    void isBettingOver_trueWhenOnlyOnePlayerRemainsUnfolded() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        players.get(0).fold();
        players.get(1).fold();
        assertTrue(state.isBettingOver());
    }

    @Test
    void isBettingOver_trueWhenAllRemainingPlayersAreAllIn() {
        GameState state = new GameState(makePlayers(2, 500), 5, 10);
        List<Player> players = state.getPlayers();
        players.get(0).commit(500);
        players.get(1).commit(500);
        assertTrue(state.isBettingOver());
    }

    // -------------------------------------------------------------------------
    // postBlinds()
    // -------------------------------------------------------------------------

    @Test
    void postBlinds_headsUp_dealerPostsSmallBlind() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        List<Player> players = state.getPlayers();

        state.postBlinds();

        assertEquals(5, players.get(0).getRoundBet(), "dealer posts small blind heads-up");
        assertEquals(10, players.get(1).getRoundBet(), "non-dealer posts big blind heads-up");
        assertEquals(10, state.getCurrentBet());
    }

    @Test
    void postBlinds_multiway_postsFromSeatsLeftOfDealer() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();

        state.postBlinds(); // dealerIndex == 0

        assertEquals(0, players.get(0).getRoundBet(), "dealer posts no blind with 3+ players");
        assertEquals(5, players.get(1).getRoundBet());
        assertEquals(10, players.get(2).getRoundBet());
        assertEquals(10, state.getCurrentBet());
    }

    @Test
    void postBlinds_shortStackCapsAtRemainingStack() {
        List<Player> players = new ArrayList<>();
        players.add(new Player(0, "P0", 1000));
        players.add(new Player(1, "P1", 3)); // can't cover a 10-chip big blind
        GameState state = new GameState(players, 5, 10);

        state.postBlinds();

        Player bb = players.get(1);
        assertEquals(3, bb.getRoundBet(), "short stack posts an all-in blind for less");
        assertEquals(0, bb.getStack());
        assertTrue(bb.isAllIn());
    }

    // -------------------------------------------------------------------------
    // setFirstActor()
    // -------------------------------------------------------------------------

    @Test
    void setFirstActor_preflopHeadsUp_dealerActsFirst() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        state.setFirstActor();
        assertEquals(state.getDealerIndex(), state.getPlayerTurnIndex());
    }

    @Test
    void setFirstActor_preflopThreeHanded_dealerActsFirst() {
        // Standard 3-handed rule: action returns to the button preflop.
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        state.postBlinds();
        state.setFirstActor();
        assertEquals(state.getDealerIndex(), state.getPlayerTurnIndex());
    }

    @Test
    void setFirstActor_preflopFourHanded_utgActsFirst() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.postBlinds();
        state.setFirstActor();
        assertEquals((state.getDealerIndex() + 3) % 4, state.getPlayerTurnIndex());
    }

    @Test
    void setFirstActor_postflop_seatLeftOfDealerActsFirst() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        state.advanceRound(); // FLOP
        state.setFirstActor();
        assertEquals((state.getDealerIndex() + 1) % 4, state.getPlayerTurnIndex());
    }

    @Test
    void setFirstActor_skipsFoldedSeat() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        players.get(1).fold(); // dealerIndex + 1 is folded

        state.advanceRound(); // FLOP
        state.setFirstActor();

        assertEquals(2, state.getPlayerTurnIndex(), "should skip folded seat 1 and land on seat 2");
    }

    @Test
    void setFirstActor_skipsAllInSeat() {
        GameState state = new GameState(makePlayers(4, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        players.get(1).commit(1000); // dealerIndex + 1 is all-in

        state.advanceRound(); // FLOP
        state.setFirstActor();

        assertEquals(2, state.getPlayerTurnIndex(), "should skip all-in seat 1 and land on seat 2");
    }

    // -------------------------------------------------------------------------
    // resolveShowdown()
    // -------------------------------------------------------------------------

    @Test
    void resolveShowdown_singleWinnerGetsEntirePot() {
        // At seed 0, seat 0 holds the best hand of the three (verified via HandEvaluator).
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.dealHoleCards(0);
        for (int i = 0; i < 5; i++) state.dealCommunityCard();

        for (Player p : players) state.commitChips(p, 100);
        List<Player> winners = state.resolveShowdown();

        assertEquals(1, winners.size());
        assertSame(players.get(0), winners.get(0));
        assertEquals(1200, players.get(0).getStack());
        assertEquals(900, players.get(1).getStack());
        assertEquals(900, players.get(2).getStack());
        assertTrue(state.isHandComplete());
    }

    @Test
    void resolveShowdown_tiedHandsSplitPotEvenly() {
        // At seed 13, seat 0 and seat 2 hold the identical straight (verified tie).
        // (Was seed 10 prior to burn cards being introduced -- burning shifts which
        // cards land as community cards for a given seed, so the seed that produces
        // this tie changed too; the expected payouts below are unchanged.)
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.dealHoleCards(13);
        for (int i = 0; i < 5; i++) state.dealCommunityCard();

        for (Player p : players) state.commitChips(p, 90);
        List<Player> winners = state.resolveShowdown();

        assertEquals(2, winners.size());
        assertTrue(winners.contains(players.get(0)));
        assertTrue(winners.contains(players.get(2)));
        assertEquals(1045, players.get(0).getStack());
        assertEquals(1045, players.get(2).getStack());
        assertEquals(910, players.get(1).getStack(), "losing player keeps nothing back");
    }

    @Test
    void resolveShowdown_foldedPlayerExcludedEvenWithBestHand() {
        // Seat 0 objectively has the best hand at seed 0, but folds -- seat 2 (next best) should win.
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.dealHoleCards(0);
        for (int i = 0; i < 5; i++) state.dealCommunityCard();

        for (Player p : players) state.commitChips(p, 100);
        state.foldPlayer(players.get(0));
        List<Player> winners = state.resolveShowdown();

        assertEquals(1, winners.size());
        assertSame(players.get(2), winners.get(0));
        assertEquals(900, players.get(0).getStack(), "folded player never gets a hand-strength check");
        assertEquals(1200, players.get(2).getStack());
        assertEquals(900, players.get(1).getStack());
    }

    @Test
    void resolveShowdown_respectsSidePotEligibilitySeparatelyPerPot() {
        // Seat 0 has the best hand overall but is only in for 30 (main pot).
        // Between seat 1 and seat 2 (the side-pot contestants), seat 2 is stronger.
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.dealHoleCards(0);
        for (int i = 0; i < 5; i++) state.dealCommunityCard();

        state.commitChips(players.get(0), 30);
        state.commitChips(players.get(1), 130);
        state.commitChips(players.get(2), 130);

        List<Player> winners = state.resolveShowdown();

        assertEquals(1060, players.get(0).getStack(), "seat0 wins the main pot only (-30 +90)");
        assertEquals(870, players.get(1).getStack(), "seat1 wins nothing");
        assertEquals(1070, players.get(2).getStack(), "seat2 wins the side pot (-130 +200)");
        assertTrue(winners.contains(players.get(0)));
        assertTrue(winners.contains(players.get(2)));
        assertEquals(3000, players.get(0).getStack() + players.get(1).getStack() + players.get(2).getStack(),
                "no chips created or destroyed");
    }

    // -------------------------------------------------------------------------
    // checkFoldWin()
    // -------------------------------------------------------------------------

    @Test
    void checkFoldWin_emptyWhenMultiplePlayersStillIn() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        state.foldPlayer(state.getPlayers().get(0));

        assertTrue(state.checkFoldWin().isEmpty());
        assertFalse(state.isHandComplete());
    }

    @Test
    void checkFoldWin_awardsEntirePotToLastPlayerRemaining() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.commitChips(players.get(0), 50);
        state.commitChips(players.get(1), 50);
        state.foldPlayer(players.get(1));

        var result = state.checkFoldWin();

        assertTrue(result.isPresent());
        assertSame(players.get(0), result.get());
        assertEquals(1050, players.get(0).getStack());
        assertTrue(state.isHandComplete());
        assertTrue(state.getPots().isEmpty());
    }

    @Test
    void checkFoldWin_lastPlayerTakesEverythingAcrossSidePotLayers() {
        // Seat0 all-in for 30 (would normally only be eligible for the main pot), but
        // seats 1 and 2 -- who built the 200-level side pot -- both fold. Seat0 must
        // still take the whole 430, not just the 90 they'd be "eligible" for at showdown.
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.commitChips(players.get(0), 30);
        state.commitChips(players.get(1), 200);
        state.commitChips(players.get(2), 200);
        state.foldPlayer(players.get(1));
        state.foldPlayer(players.get(2));

        var result = state.checkFoldWin();

        assertTrue(result.isPresent());
        assertSame(players.get(0), result.get());
        assertEquals(1400, players.get(0).getStack(), "winner takes the full pot, not just their own layer");
        assertEquals(800, players.get(1).getStack());
        assertEquals(800, players.get(2).getStack());
    }

    // -------------------------------------------------------------------------
    // computeSidePots() / totalPot() -- basic wiring sanity check
    // (Exhaustive all-in permutations are covered in PotTest.)
    // -------------------------------------------------------------------------

    @Test
    void computeSidePots_singlePotWhenContributionsAreEqual() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        for (Player p : state.getPlayers()) {
            state.commitChips(p, 100);
        }
        List<Pot> pots = state.computeSidePots();
        assertEquals(1, pots.size());
        assertEquals(300, pots.get(0).getAmount());
        assertEquals(300, state.totalPot());
    }

    // -------------------------------------------------------------------------
    // startNewHand()
    // -------------------------------------------------------------------------

    @Test
    void startNewHand_resetsHandStateAndRotatesDealer() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();

        state.commitChips(players.get(0), 100);
        state.foldPlayer(players.get(1));
        state.dealCommunityCard();
        state.dealCommunityCard();
        state.computeSidePots();

        int dealerBefore = state.getDealerIndex();
        state.startNewHand(99);

        assertEquals((dealerBefore + 1) % 3, state.getDealerIndex());
        assertEquals(Round.PREFLOP, state.getRound());
        assertEquals(0, state.getCurrentBet());
        assertFalse(state.isHandComplete());
        assertEquals(0, state.getCommunityCards().length);
        assertTrue(state.getPots().isEmpty());

        for (Player p : players) {
            assertEquals(0, p.getRoundBet());
            assertEquals(0, p.getTotalCommitted());
            assertFalse(p.isFolded());
            assertNotEquals(-1, p.getHoleCards()[0], "hole cards should be freshly dealt");
        }
        assertEquals(900, players.get(0).getStack(), "stack must persist across hands, only per-hand state resets");
    }

    // -------------------------------------------------------------------------
    // processAction()
    // -------------------------------------------------------------------------

    @Test
    void processAction_checkSucceedsWhenNothingOwed() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.processAction(Action.CHECK);
        assertEquals(1, state.getPlayerTurnIndex(), "turn should advance after a legal action");
    }

    @Test
    void processAction_checkThrowsWhenBetIsOutstanding() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        Player seat1 = state.getPlayers().get(1);
        state.commitChips(seat1, 50); // simulate seat1 having an outstanding bet

        assertThrows(IllegalStateException.class, () -> state.processAction(Action.CHECK));
    }

    @Test
    void processAction_foldDoesNotMoveChips() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        Player seat0 = state.getPlayerToAct();
        state.processAction(Action.FOLD);

        assertTrue(seat0.isFolded());
        assertEquals(1000, seat0.getStack());
        assertEquals(0, seat0.getRoundBet());
    }

    @Test
    void processAction_callCapsAtStackForShortStack() {
        // seat1 starts with a genuinely short stack, not an artificially reduced one
        List<Player> players = new ArrayList<>();
        players.add(new Player(0, "P0", 1000));
        players.add(new Player(1, "P1", 30));
        GameState state = new GameState(players, 5, 10);

        state.processAction(Action.RAISE, 100); // seat0 opens to 100
        assertEquals(100, state.getCurrentBet());

        // now seat1 is to act with only 30 chips, facing a 100 bet
        state.processAction(Action.CALL);
        Player seat1 = players.get(1);
        assertEquals(0, seat1.getStack());
        assertTrue(seat1.isAllIn());
        assertEquals(30, seat1.getRoundBet());
    }
    

    @Test
    void processAction_raiseThrowsWhenNotExceedingCurrentBet() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        assertThrows(IllegalStateException.class, () -> state.processAction(Action.RAISE, 0));
    }

    @Test
    void processAction_raiseCommitsCorrectDeltaAndUpdatesCurrentBet() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        Player seat0 = state.getPlayerToAct();

        state.processAction(Action.RAISE, 150);

        assertEquals(150, seat0.getRoundBet());
        assertEquals(850, seat0.getStack());
        assertEquals(150, state.getCurrentBet());
    }

    @Test
    void processAction_singleArgOverloadDelegatesCorrectly() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        // CALL with nothing owed behaves like a no-op call (amountToCall = 0)
        assertDoesNotThrow(() -> state.processAction(Action.CALL));
        assertEquals(1, state.getPlayerTurnIndex());
    }
}