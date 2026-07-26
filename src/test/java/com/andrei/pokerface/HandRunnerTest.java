package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class HandRunnerTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    /**
     * Records how many times performAction is invoked, and calls any bet up
     * to 10 chips but folds anything larger. Used to directly observe whether
     * a seat is actually given the chance to respond to a shove, rather than
     * inferring it indirectly from aggregate stats.
     */
    private static class CallSmallElseFoldAgent implements PokerAgent {
        int actionCount = 0;

        @Override
        public ActionResult performAction(PlayerView view) {
            actionCount++;
            if (view.amountToCall() == 0) {
                return ActionResult.check();
            }
            if (view.amountToCall() <= 10) {
                return ActionResult.call();
            }
            return ActionResult.fold();
        }
    }

    // -------------------------------------------------------------------------
    // Regression test for the missed-turn-after-an-all-in-raise bug
    // -------------------------------------------------------------------------

    @Test
    void playHand_nonRaiserStillGetsATurnWhenOpponentShovesAllIn() {
        // Heads-up: GameState.startNewHand() advances the dealer button once
        // before dealing the very first hand (see GameStateTest), so seat1
        // ends up as dealer/SB here and acts first preflop -- it's seat1
        // (AllInAgent) that shoves immediately as its very first action.
        // seat0's ONLY action in the entire hand is responding to that shove.
        // Before the fix, this response would have been skipped entirely,
        // and the hand would have incorrectly proceeded to later streets
        // with seat0 only ever checking for free -- never actually asked to
        // respond to the shove at all.
        List<Player> players = makePlayers(2, 1000);
        CallSmallElseFoldAgent seat0 = new CallSmallElseFoldAgent();
        List<PokerAgent> agents = List.of(seat0, new AllInAgent());

        GameState state = new GameState(players, 5, 10);
        HandResult result = HandRunner.playHand(state, agents, 1);

        // seat0 must be asked to act exactly once: responding to seat1's shove.
        assertEquals(1, seat0.actionCount,
                "seat0 should be asked to respond to the all-in shove");

        // Given CallSmallElseFoldAgent's rule, it must have folded to the shove,
        // so the hand should end as a fold-win for seat1, not run out to showdown.
        assertTrue(result.wonByFold(), "seat0 should have folded to the shove, ending the hand immediately");
        assertEquals(players.get(1), result.winners().get(0));

        // seat0 posted the big blind (10, since seat1 is dealer/SB here) and folded --
        // no further chips committed. seat1 shoved its entire remaining stack and
        // wins everything committed by both players.
        assertEquals(990, players.get(0).getStack(), "seat0 should have lost only the big blind, not the full shove");
        assertEquals(1010, players.get(1).getStack(), "seat1 should win the full pot: its own 1000 plus seat0's 10");
        assertEquals(2000, players.get(0).getStack() + players.get(1).getStack(),
                "no chips may be created or destroyed across the hand");
    }

    @Test
    void playHand_nonRaiserStillGetsATurnWhenRaiserRemainsActiveAfterRaising() {
        // Companion case: a raise that does NOT put the raiser all-in must still
        // correctly require a response from the other player -- guards against
        // a fix that accidentally always skips the -1 adjustment instead of
        // making it conditional.
        List<Player> players = makePlayers(2, 1000);
        CallSmallElseFoldAgent seat0 = new CallSmallElseFoldAgent();

        // seat1 raises to 20 (well short of all-in) whenever it's legal, otherwise
        // calls/checks -- deliberately NOT an AllInAgent, so the raiser stays active.
        PokerAgent smallRaiser = new PokerAgent() {
            @Override
            public ActionResult performAction(PlayerView view) {
                int maxTarget = view.me().roundBet() + view.me().stack();
                if (maxTarget > view.currentBet() && view.currentBet() < 20) {
                    return ActionResult.raiseTo(20);
                }
                return view.amountToCall() == 0 ? ActionResult.check() : ActionResult.call();
            }
        };
        List<PokerAgent> agents = List.of(seat0, smallRaiser);

        GameState state = new GameState(players, 5, 10);
        HandRunner.playHand(state, agents, 1);

        // seat0 must have been asked to act at least twice: once for the initial
        // call, and again in response to seat1's non-all-in raise to 20.
        assertTrue(seat0.actionCount >= 2,
                "seat0 should still be asked to respond to a raise that leaves the raiser active");
    }
}