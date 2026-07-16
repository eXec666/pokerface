package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class PlayerInterfaceTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    // -------------------------------------------------------------------------
    // ActionResult
    // -------------------------------------------------------------------------

    @Test
    void fold_hasFoldActionAndZeroAmount() {
        ActionResult r = ActionResult.fold();
        assertEquals(Action.FOLD, r.action());
        assertEquals(0, r.amount());
    }

    @Test
    void check_hasCheckActionAndZeroAmount() {
        ActionResult r = ActionResult.check();
        assertEquals(Action.CHECK, r.action());
        assertEquals(0, r.amount());
    }

    @Test
    void call_hasCallActionAndZeroAmount() {
        ActionResult r = ActionResult.call();
        assertEquals(Action.CALL, r.action());
        assertEquals(0, r.amount());
    }

    @Test
    void raiseTo_storesActionAndTargetAmount() {
        ActionResult r = ActionResult.raiseTo(75);
        assertEquals(Action.RAISE, r.action());
        assertEquals(75, r.amount());
    }

    @Test
    void constructor_negativeAmountThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ActionResult(Action.RAISE, -5));
    }

    @Test
    void constructor_foldWithNonzeroAmountThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ActionResult(Action.FOLD, 10));
    }

    @Test
    void constructor_checkWithNonzeroAmountThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ActionResult(Action.CHECK, 10));
    }

    @Test
    void constructor_callWithNonzeroAmountThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ActionResult(Action.CALL, 10));
    }

    @Test
    void constructor_raiseWithZeroAmountThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ActionResult(Action.RAISE, 0));
    }

    // -------------------------------------------------------------------------
    // OpponentInfo
    // -------------------------------------------------------------------------

    @Test
    void opponentInfo_storesAllFields() {
        OpponentInfo info = new OpponentInfo(2, "Alice", 500, 20, 60, true, false);
        assertEquals(2, info.seatIndex());
        assertEquals("Alice", info.name());
        assertEquals(500, info.stack());
        assertEquals(20, info.roundBet());
        assertEquals(60, info.totalCommitted());
        assertTrue(info.folded());
        assertFalse(info.allIn());
    }

    // -------------------------------------------------------------------------
    // PlayerView -- defensive copies
    // -------------------------------------------------------------------------

    @Test
    void constructor_defensivelyCopiesInputArrays() {
        int[] hole = {5, 17};
        int[] board = {1, 2, 3};
        List<OpponentInfo> infos = List.of(
                new OpponentInfo(0, "P0", 1000, 0, 0, false, false));
        PlayerView view = new PlayerView(0, hole, board, Round.FLOP, 0, 45, 20, 30, 40,
                List.of(20), infos);

        hole[0] = 99;   // mutate the array passed to the constructor
        board[0] = 51;

        assertEquals(5, view.myHoleCards()[0], "mutating the input array after construction must not affect the view");
        assertEquals(1, view.communityCards()[0]);
    }

    @Test
    void accessor_returnsDefensiveCopyNotLiveArray() {
        int[] hole = {5, 17};
        List<OpponentInfo> infos = List.of(
                new OpponentInfo(0, "P0", 1000, 0, 0, false, false));
        PlayerView view = new PlayerView(0, hole, new int[0], Round.PREFLOP, 0, 52, 0, 10, 0,
                List.of(), infos);

        int[] read = view.myHoleCards();
        read[0] = 99; // mutate the array returned by the accessor

        assertEquals(5, view.myHoleCards()[0],
                "mutating a previously-returned array must not corrupt the view's internal state");
    }

    @Test
    void playersList_isUnmodifiable() {
        List<OpponentInfo> infos = List.of(
                new OpponentInfo(0, "P0", 1000, 0, 0, false, false));
        PlayerView view = new PlayerView(0, new int[]{0, 1}, new int[0], Round.PREFLOP, 0, 52, 0, 10, 0,
                List.of(), infos);

        assertThrows(UnsupportedOperationException.class,
                () -> view.players().add(new OpponentInfo(1, "P1", 1000, 0, 0, false, false)));
    }

    @Test
    void potAmountsList_isUnmodifiable() {
        List<OpponentInfo> infos = List.of(
                new OpponentInfo(0, "P0", 1000, 0, 0, false, false));
        List<Integer> potAmounts = new ArrayList<>(List.of(50));
        PlayerView view = new PlayerView(0, new int[]{0, 1}, new int[0], Round.PREFLOP, 0, 52, 0, 10, 50,
                potAmounts, infos);

        assertThrows(UnsupportedOperationException.class, () -> view.potAmounts().add(10));
    }

    // -------------------------------------------------------------------------
    // PlayerView -- convenience methods
    // -------------------------------------------------------------------------

    @Test
    void me_returnsOwnSeatFromPlayersList() {
        List<OpponentInfo> infos = List.of(
                new OpponentInfo(0, "P0", 900, 100, 100, false, false),
                new OpponentInfo(1, "P1", 800, 200, 200, false, false));
        PlayerView view = new PlayerView(1, new int[]{0, 1}, new int[0], Round.PREFLOP, 0, 48, 200, 210, 300,
                List.of(300), infos);

        assertEquals("P1", view.me().name());
        assertEquals(800, view.me().stack());
    }

    @Test
    void amountToCall_reflectsGapBetweenCurrentBetAndOwnRoundBet() {
        List<OpponentInfo> infos = List.of(
                new OpponentInfo(0, "P0", 900, 30, 30, false, false),
                new OpponentInfo(1, "P1", 800, 100, 100, false, false));
        PlayerView view = new PlayerView(0, new int[]{0, 1}, new int[0], Round.PREFLOP, 0, 48, 100, 110, 130,
                List.of(130), infos);

        assertEquals(70, view.amountToCall());
    }

    @Test
    void amountToCall_isZeroWhenNothingOwed() {
        List<OpponentInfo> infos = List.of(
                new OpponentInfo(0, "P0", 900, 100, 100, false, false));
        PlayerView view = new PlayerView(0, new int[]{0, 1}, new int[0], Round.PREFLOP, 0, 48, 100, 110, 100,
                List.of(100), infos);

        assertEquals(0, view.amountToCall());
    }

    @Test
    void numActivePlayers_excludesFoldedAndAllIn() {
        List<OpponentInfo> infos = List.of(
                new OpponentInfo(0, "P0", 900, 100, 100, false, false),
                new OpponentInfo(1, "P1", 0, 100, 100, false, true),
                new OpponentInfo(2, "P2", 800, 0, 0, true, false));
        PlayerView view = new PlayerView(0, new int[]{0, 1}, new int[0], Round.PREFLOP, 0, 48, 100, 110, 200,
                List.of(200), infos);

        assertEquals(1, view.numActivePlayers());
    }

    // -------------------------------------------------------------------------
    // GameState.buildPlayerView()
    // -------------------------------------------------------------------------

    @Test
    void buildPlayerView_exposesOnlyOwnHoleCards() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);

        Player seat0 = state.getPlayers().get(0);
        PlayerView view = state.buildPlayerView(0);

        assertArrayEquals(seat0.getHoleCards(), view.myHoleCards());
    }

    @Test
    void buildPlayerView_deckRemainingMatchesEngine() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        state.dealHoleCards(1); // 6 cards dealt across 3 players

        PlayerView view = state.buildPlayerView(0);

        assertEquals(46, view.deckRemaining());
    }

    @Test
    void buildPlayerView_communityCardsMatchDealtPortionOnly() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);
        state.dealCommunityCard();
        state.dealCommunityCard();

        PlayerView view = state.buildPlayerView(0);

        assertEquals(2, view.communityCards().length);
        assertArrayEquals(state.getCommunityCards(), view.communityCards());
    }

    @Test
    void buildPlayerView_reflectsCurrentBetAndMinRaiseTarget() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        state.processAction(Action.RAISE, 30); // currentBet=30, minRaise becomes 20

        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());

        assertEquals(30, view.currentBet());
        assertEquals(50, view.minRaiseTarget()); // 30 + 20
    }

    @Test
    void buildPlayerView_potTotalAndPotAmountsMatchComputeSidePots() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        for (Player p : state.getPlayers()) {
            state.commitChips(p, 100);
        }

        PlayerView view = state.buildPlayerView(0);

        assertEquals(300, view.potTotal());
        assertEquals(List.of(300), view.potAmounts());
    }

    @Test
    void buildPlayerView_playersListIncludesFoldedAndAllInFlags() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.commitChips(players.get(0), 50);
        state.foldPlayer(players.get(1));
        players.get(2).commit(1000); // exact stack -> all-in

        PlayerView view = state.buildPlayerView(0);

        assertFalse(view.players().get(0).folded());
        assertTrue(view.players().get(1).folded());
        assertTrue(view.players().get(2).allIn());
    }

    // -------------------------------------------------------------------------
    // PokerAgent + GameState.takeTurn()
    // -------------------------------------------------------------------------

    private static class ScriptedAgent implements PokerAgent {
        private final ActionResult toReturn;
        private PlayerView lastView;

        ScriptedAgent(ActionResult toReturn) {
            this.toReturn = toReturn;
        }

        @Override
        public ActionResult performAction(PlayerView view) {
            this.lastView = view;
            return toReturn;
        }
    }

    @Test
    void takeTurn_appliesAgentsCheckDecision() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        ScriptedAgent agent = new ScriptedAgent(ActionResult.check());

        state.takeTurn(agent);

        assertEquals(1, state.getPlayerTurnIndex(), "turn should advance after the agent's action is applied");
    }

    @Test
    void takeTurn_appliesAgentsRaiseDecisionAndUpdatesCurrentBet() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        ScriptedAgent agent = new ScriptedAgent(ActionResult.raiseTo(30));

        state.takeTurn(agent);

        assertEquals(30, state.getCurrentBet());
    }

    @Test
    void takeTurn_passesViewMatchingThePlayerCurrentlyToAct() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        ScriptedAgent agent = new ScriptedAgent(ActionResult.check());
        int actingSeat = state.getPlayerTurnIndex();

        state.takeTurn(agent);

        assertEquals(actingSeat, agent.lastView.mySeatIndex());
    }

    @Test
    void takeTurn_rejectsIllegalRaiseTheSameWayProcessActionDoes() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        ScriptedAgent agent = new ScriptedAgent(ActionResult.raiseTo(15)); // below the minimum raise

        assertThrows(IllegalStateException.class, () -> state.takeTurn(agent));
    }
}