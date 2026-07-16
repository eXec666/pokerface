package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class RandomAgentTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    @Test
    void constructor_rejectsNegativeFoldWeight() {
        assertThrows(IllegalArgumentException.class, () -> new RandomAgent(1, -0.1, 0.2));
    }

    @Test
    void constructor_rejectsWeightsSummingAboveOne() {
        assertThrows(IllegalArgumentException.class, () -> new RandomAgent(1, 0.7, 0.5));
    }

    @Test
    void constructor_acceptsWeightsSummingToExactlyOne() {
        assertDoesNotThrow(() -> new RandomAgent(1, 0.5, 0.5));
    }

    @Test
    void performAction_neverProducesIllegalActionOverManyDecisions() {
        // Drive real GameStates through many random spots (facing no bet, facing a bet,
        // short-stacked) and confirm GameState.processAction never throws.
        for (long seed = 0; seed < 500; seed++) {
            GameState state = new GameState(makePlayers(4, 200), 5, 10);
            RandomAgent agent = new RandomAgent(seed);
            state.startNewHand((int) seed);
            state.postBlinds();
            state.setFirstActor();

            int guard = 0;
            while (!state.isBettingOver() && guard < 100) {
                PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
                ActionResult result = agent.performAction(view);
                assertDoesNotThrow(() -> state.processAction(result.action(), result.amount()),
                        "seed " + seed + " produced an illegal action: " + result);
                guard++;
            }
        }
    }

    @Test
    void performAction_neverRaisesWhenNoLegalRaiseExists() {
        // Player with a stack exactly equal to the amount already matched cannot
        // raise at all -- agent must fall back to check/call/fold only.
        List<Player> players = new ArrayList<>();
        players.add(new Player(0, "P0", 100));
        players.add(new Player(1, "P1", 1000));
        GameState state = new GameState(players, 5, 10);
        state.commitChips(players.get(0), 100); // seat0 already all-in for exactly currentBet-to-be
        state.commitChips(players.get(1), 100); // matches -- seat0 has 0 stack left, can't raise further

        RandomAgent agent = new RandomAgent(1, 0.0, 1.0); // raiseWeight=1 to force the raise branch if legal
        PlayerView view = state.buildPlayerView(0);

        // seat0 has 0 stack -> maxTarget == roundBet == currentBet -> canRaise is false
        ActionResult result = agent.performAction(view);
        assertNotEquals(Action.RAISE, result.action());
    }

    @Test
    void performAction_allFoldWeightAlwaysFoldsWhenFacingABet() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds(); // seat0 (SB) owes chips to match BB
        RandomAgent agent = new RandomAgent(1, 1.0, 0.0);

        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        ActionResult result = agent.performAction(view);

        assertEquals(Action.FOLD, result.action());
    }

    @Test
    void performAction_allFoldWeightStillChecksWhenNothingOwed() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        RandomAgent agent = new RandomAgent(1, 1.0, 0.0); // foldWeight irrelevant when amountToCall==0

        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        ActionResult result = agent.performAction(view);

        assertEquals(Action.CHECK, result.action());
    }
}