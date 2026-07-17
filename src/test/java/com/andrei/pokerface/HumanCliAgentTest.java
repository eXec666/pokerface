package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HumanCliAgentTest {

    private ByteArrayOutputStream outputBuffer;
    private PrintStream out;

    private HumanCliAgent agentWithInput(String input) {
        outputBuffer = new ByteArrayOutputStream();
        out = new PrintStream(outputBuffer, true, StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        return new HumanCliAgent(in, out);
    }

    private String output() {
        return outputBuffer.toString(StandardCharsets.UTF_8);
    }

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    // -------------------------------------------------------------------------
    // getName()
    // -------------------------------------------------------------------------

    @Test
    void getName_defaultsToHuman() {
        HumanCliAgent agent = agentWithInput("");
        assertEquals("Human", agent.getName());
    }

    @Test
    void getName_usesSuppliedName() {
        ByteArrayInputStream in = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        HumanCliAgent agent = new HumanCliAgent(in, new PrintStream(new ByteArrayOutputStream()), "Andrei");
        assertEquals("Andrei", agent.getName());
    }

    // -------------------------------------------------------------------------
    // Happy paths
    // -------------------------------------------------------------------------

    @Test
    void fold_isAlwaysLegal() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("fold\n");

        assertEquals(Action.FOLD, agent.performAction(view).action());
    }

    @Test
    void foldAbbreviation_f_works() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("f\n");

        assertEquals(Action.FOLD, agent.performAction(view).action());
    }

    @Test
    void check_legalWhenNothingOwed() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("check\n");

        assertEquals(Action.CHECK, agent.performAction(view).action());
    }

    @Test
    void checkAbbreviation_k_works() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("k\n");

        assertEquals(Action.CHECK, agent.performAction(view).action());
    }

    @Test
    void call_legalWhenAmountOwed() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("call\n");

        assertEquals(Action.CALL, agent.performAction(view).action());
    }

    @Test
    void callAbbreviation_c_works() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("c\n");

        assertEquals(Action.CALL, agent.performAction(view).action());
    }

    @Test
    void raise_legalAtExactMinimumIncrement() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds(); // currentBet=10, minRaiseTarget=20
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("raise 20\n");

        ActionResult result = agent.performAction(view);

        assertEquals(Action.RAISE, result.action());
        assertEquals(20, result.amount());
    }

    @Test
    void raiseAbbreviation_r_works() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("r 20\n");

        assertEquals(20, agent.performAction(view).amount());
    }

    @Test
    void allin_targetsFullStack() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        int expectedTarget = view.me().roundBet() + view.me().stack();
        HumanCliAgent agent = agentWithInput("allin\n");

        ActionResult result = agent.performAction(view);

        assertEquals(Action.RAISE, result.action());
        assertEquals(expectedTarget, result.amount());
    }

    @Test
    void commandsAreCaseInsensitiveAndTrimmed() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("  CHECK  \n");

        assertEquals(Action.CHECK, agent.performAction(view).action());
    }

    // -------------------------------------------------------------------------
    // Re-prompting on illegal / unrecognized input
    // -------------------------------------------------------------------------

    @Test
    void unrecognizedCommand_reprompts() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("gibberish\ncheck\n");

        ActionResult result = agent.performAction(view);

        assertEquals(Action.CHECK, result.action());
        assertTrue(output().contains("Unrecognized command"));
    }

    @Test
    void check_whenAmountOwed_repromptsThenAcceptsCall() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("check\ncall\n");

        ActionResult result = agent.performAction(view);

        assertEquals(Action.CALL, result.action());
        assertTrue(output().contains("Cannot check"));
    }

    @Test
    void call_whenNothingOwed_repromptsThenAcceptsCheck() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("call\ncheck\n");

        ActionResult result = agent.performAction(view);

        assertEquals(Action.CHECK, result.action());
        assertTrue(output().contains("Nothing to call"));
    }

    @Test
    void raise_equalToCurrentBet_isRejected() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("raise 10\nraise 20\n");

        ActionResult result = agent.performAction(view);

        assertEquals(20, result.amount());
        assertTrue(output().contains("must exceed the current bet"));
    }

    @Test
    void raise_belowCurrentBet_repromptsThenAcceptsValidRaise() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("raise 5\nraise 20\n");

        ActionResult result = agent.performAction(view);

        assertEquals(20, result.amount());
        assertTrue(output().contains("must exceed the current bet"));
    }

    @Test
    void raise_belowMinimumIncrement_repromptsThenAcceptsValidRaise() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds(); // currentBet=10, minRaise=10 -> min legal raise target=20
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("raise 15\nraise 20\n");

        ActionResult result = agent.performAction(view);

        assertEquals(20, result.amount());
        assertTrue(output().contains("must be at least"));
    }

    @Test
    void raise_shortAllInBelowMinimumIsAccepted() {
        List<Player> players = new ArrayList<>();
        players.add(new Player(0, "P0", 105));
        players.add(new Player(1, "P1", 1000));
        GameState state = new GameState(players, 5, 10);
        state.commitChips(players.get(1), 100); // currentBet=100
        PlayerView view = state.buildPlayerView(0);
        HumanCliAgent agent = agentWithInput("raise 105\n");

        ActionResult result = agent.performAction(view);

        assertEquals(105, result.amount());
    }

    @Test
    void raise_aboveStack_repromptsThenAcceptsValidRaise() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("raise 999999\nraise 20\n");

        ActionResult result = agent.performAction(view);

        assertEquals(20, result.amount());
        assertTrue(output().contains("max raise target is"));
    }

    @Test
    void raiseWithMalformedAmount_isUnrecognized() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("raise abc\nraise 20\n");

        ActionResult result = agent.performAction(view);

        assertEquals(20, result.amount());
        assertTrue(output().contains("Unrecognized command"));
    }

    @Test
    void allin_illegalWhenStackTooShortToExceedCurrentBet_repromptsThenAcceptsCall() {
        List<Player> players = new ArrayList<>();
        players.add(new Player(0, "P0", 20));
        players.add(new Player(1, "P1", 1000));
        GameState state = new GameState(players, 5, 10);
        state.commitChips(players.get(1), 100); // currentBet=100, seat0's max target (20) can't exceed it
        PlayerView view = state.buildPlayerView(0);
        HumanCliAgent agent = agentWithInput("allin\ncall\n");

        ActionResult result = agent.performAction(view);

        assertEquals(Action.CALL, result.action());
        assertTrue(output().contains("Cannot go all-in"));
    }

    // -------------------------------------------------------------------------
    // Input stream closed
    // -------------------------------------------------------------------------

    @Test
    void inputStreamClosed_throwsIllegalStateException() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput(""); // immediate EOF

        assertThrows(IllegalStateException.class, () -> agent.performAction(view));
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Test
    void render_includesRoundHoleCardsPotAndSeats() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);
        state.postBlinds();
        PlayerView view = state.buildPlayerView(state.getPlayerTurnIndex());
        HumanCliAgent agent = agentWithInput("fold\n");

        agent.performAction(view);
        String rendered = output();

        assertTrue(rendered.contains("PREFLOP"));
        assertTrue(rendered.contains("Your hand:"));
        assertTrue(rendered.contains("Pot:"));
        assertTrue(rendered.contains("Seats:"));
    }

    // -------------------------------------------------------------------------
    // Integration: full hand through HandRunner
    // -------------------------------------------------------------------------

    @Test
    void integratesWithHandRunnerForAFullHeadsUpHand() {
        // Heads-up: dealer(seat0, Human) posts SB and acts first preflop;
        // postflop, seat1 (BB, AlwaysCall) acts first each street.
        List<Player> players = makePlayers(2, 1000);
        GameState state = new GameState(players, 5, 10);
        HumanCliAgent human = agentWithInput("check\ncheck\ncheck\ncheck\n");
        List<PokerAgent> agents = List.of(human, new AlwaysCallAgent());

        HandResult result = HandRunner.playHand(state, agents, 7);

        assertNotNull(result);
        assertTrue(state.isHandComplete());
        assertEquals(2000, players.get(0).getStack() + players.get(1).getStack(),
                "no chips created or destroyed across the hand");
    }
}