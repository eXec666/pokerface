package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class GameStateMinRaiseTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    // -------------------------------------------------------------------------
    // Defaults / resets
    // -------------------------------------------------------------------------

    @Test
    void constructor_minRaiseDefaultsToBigBlind() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 20);
        assertEquals(20, state.getMinRaise());
    }

    @Test
    void postBlinds_doesNotChangeMinRaise() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        assertEquals(10, state.getMinRaise());
    }

    @Test
    void advanceRound_resetsMinRaiseToBigBlind() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        state.processAction(Action.RAISE, 30); // increment 20 over currentBet 10 -> minRaise becomes 20
        assertEquals(20, state.getMinRaise());

        state.advanceRound();

        assertEquals(state.getBigBlind(), state.getMinRaise());
    }

    @Test
    void startNewHand_resetsMinRaiseToBigBlind() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        state.processAction(Action.RAISE, 30); // minRaise becomes 20
        assertEquals(20, state.getMinRaise());

        state.startNewHand(1);

        assertEquals(state.getBigBlind(), state.getMinRaise());
    }

    // -------------------------------------------------------------------------
    // Enforcement
    // -------------------------------------------------------------------------

    @Test
    void raise_belowMinimumIncrementThrows() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds(); // currentBet=10, minRaise=10, seat0 (posted SB=5) to act

        // Raising to 15 is only a 5-chip increment over the 10 currentBet, below the 10 minimum.
        assertThrows(IllegalStateException.class, () -> state.processAction(Action.RAISE, 15));
    }

    @Test
    void raise_exactMinimumIncrementSucceeds() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();

        assertDoesNotThrow(() -> state.processAction(Action.RAISE, 20)); // exactly +10
        assertEquals(20, state.getCurrentBet());
    }

    @Test
    void raise_aboveMinimumUpdatesMinRaiseForNextRaise() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();

        state.processAction(Action.RAISE, 30); // +20 over currentBet 10

        assertEquals(30, state.getCurrentBet());
        assertEquals(20, state.getMinRaise(), "a raise larger than the old minimum becomes the new minimum");
    }

    @Test
    void raise_meetingButNotExceedingMinimumLeavesMinRaiseUnchanged() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();

        state.processAction(Action.RAISE, 20); // exactly the minimum, +10

        assertEquals(10, state.getMinRaise(), "meeting (not exceeding) the minimum should not change it");
    }

    @Test
    void reRaise_belowNewMinimumThrows() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        state.processAction(Action.RAISE, 30); // minRaise now 20, currentBet 30, turn moves to seat1

        // Legal re-raise must reach at least 30 + 20 = 50; 40 is only a +10 increment.
        assertThrows(IllegalStateException.class, () -> state.processAction(Action.RAISE, 40));
    }

    @Test
    void reRaise_meetingNewMinimumSucceeds() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        state.processAction(Action.RAISE, 30); // minRaise now 20

        assertDoesNotThrow(() -> state.processAction(Action.RAISE, 50)); // exactly +20
        assertEquals(50, state.getCurrentBet());
    }

    @Test
    void raise_stillRequiresExceedingCurrentBetRegardlessOfMinimum() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        assertThrows(IllegalStateException.class, () -> state.processAction(Action.RAISE, 10));
    }

    // -------------------------------------------------------------------------
    // Short all-in exception
    // -------------------------------------------------------------------------

    @Test
    void raise_shortAllInBelowMinimumIsAllowed() {
        List<Player> players = new ArrayList<>();
        players.add(new Player(0, "P0", 105)); // can only ever reach a 105 roundBet
        players.add(new Player(1, "P1", 1000));
        GameState state = new GameState(players, 5, 10);

        state.commitChips(players.get(1), 100); // currentBet=100, minRaise stays at default 10

        // seat0 (default turn) shoves for everything: 105 total, only a +5 increment.
        assertDoesNotThrow(() -> state.processAction(Action.RAISE, 105));

        assertEquals(0, players.get(0).getStack());
        assertTrue(players.get(0).isAllIn());
        assertEquals(105, state.getCurrentBet());
    }

    @Test
    void raise_shortAllInDoesNotRaiseMinRaiseBar() {
        List<Player> players = new ArrayList<>();
        players.add(new Player(0, "P0", 105));
        players.add(new Player(1, "P1", 1000));
        GameState state = new GameState(players, 5, 10);

        state.commitChips(players.get(1), 100);
        state.processAction(Action.RAISE, 105); // short all-in, +5 increment

        assertEquals(10, state.getMinRaise(), "a short all-in raise must not become the new minimum");
    }

    @Test
    void raise_toExactStackCapWithSufficientIncrementUpdatesMinRaiseNormally() {
        List<Player> players = new ArrayList<>();
        players.add(new Player(0, "P0", 130)); // can reach a 130 roundBet, a +30 increment is possible
        players.add(new Player(1, "P1", 1000));
        GameState state = new GameState(players, 5, 10);

        state.commitChips(players.get(1), 100); // currentBet=100, minRaise=10

        state.processAction(Action.RAISE, 130); // all-in, but a +30 increment (exceeds the 10 minimum)

        assertEquals(30, state.getMinRaise(), "an all-in raise that meets the minimum still updates it normally");
    }
}