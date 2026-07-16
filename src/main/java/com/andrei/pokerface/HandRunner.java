package com.andrei.pokerface;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Drives a single poker hand end-to-end: blinds -> preflop betting -> flop ->
 * turn -> river -> showdown (or an earlier fold-out), against a set of
 * PokerAgent implementations. This is the missing "engine driver" layer --
 * GameState exposes every primitive (postBlinds, setFirstActor, takeTurn,
 * advanceRound, dealCommunityCard, checkFoldWin, resolveShowdown) but nothing
 * previously called them in the right order for a full hand.
 *
 * One HandRunner.playHand() call plays exactly one hand. Chip stacks persist
 * on the Player objects inside the GameState across calls, so a caller wanting
 * a full session calls playHand() repeatedly with a fresh seed each time --
 * that's the next layer up (a SessionRunner), not built here. Note that this
 * runner does NOT handle player elimination: a 0-stack player will still be
 * dealt in and will check/call for 0 next hand (Player.resetForNewHand()
 * clears allIn/folded every hand), but can never win a pot since
 * GameState.computeSidePots() only counts players with totalCommitted() > 0.
 * A SessionRunner needs to filter busted players out before calling this
 * again if elimination is desired.
 */
public final class HandRunner {

    private HandRunner() {}

    /** How many community cards get dealt when entering each post-flop street. */
    private static final Map<Round, Integer> CARDS_FOR_ROUND = Map.of(
            Round.FLOP, 3,
            Round.TURN, 1,
            Round.RIVER, 1
    );

    /**
     * Plays one complete hand on the given GameState.
     *
     * @param state  the table to play on; startNewHand(seed) is called internally,
     *               so the caller should NOT call it beforehand.
     * @param agents agents indexed by seat -- agents.get(i) must be the agent
     *               controlling state.getPlayers().get(i). Size must match the
     *               player count.
     * @param seed   RNG seed forwarded to GameState.startNewHand / shuffle.
     * @return a HandResult describing how the hand ended and who won.
     */
    public static HandResult playHand(GameState state, List<PokerAgent> agents, int seed) {
        if (agents == null || agents.size() != state.getPlayers().size()) {
            throw new IllegalArgumentException(
                    "Need exactly one agent per seat (" + state.getPlayers().size() + " players)");
        }

        state.startNewHand(seed);
        state.postBlinds();
        state.setFirstActor();

        runBettingRound(state, agents);
        Optional<Player> foldWinner = state.checkFoldWin();
        if (foldWinner.isPresent()) {
            return HandResult.foldWin(foldWinner.get());
        }

        // Flop, turn, river: advance the street, deal its cards, run betting,
        // then re-check for a fold-out after every street. If everyone left is
        // already all-in, runBettingRound() below is a no-op (isBettingOver()
        // short-circuits it immediately) but we still walk through every street
        // so the board is fully dealt out before showdown -- no separate code
        // path is needed for the "everyone shoved early" case.
        while (state.getRound() != Round.RIVER) {
            state.advanceRound();
            dealStreetCards(state);

            state.setFirstActor();
            runBettingRound(state, agents);

            foldWinner = state.checkFoldWin();
            if (foldWinner.isPresent()) {
                return HandResult.foldWin(foldWinner.get());
            }
        }

        state.advanceRound(); // RIVER -> SHOWDOWN
        List<Player> winners = state.resolveShowdown();
        return HandResult.showdown(winners);
    }

    /** Deals the correct number of community cards for whatever round GameState is now in. */
    private static void dealStreetCards(GameState state) {
        int count = CARDS_FOR_ROUND.getOrDefault(state.getRound(), 0);
        for (int i = 0; i < count; i++) {
            state.dealCommunityCard();
        }
    }

    /**
     * Runs betting for the current street until every player who can still act
     * has either matched the current bet or folded, or only one bettor remains.
     * <p>
     * GameState doesn't track "has everyone acted since the last raise" -- that
     * state lives here instead. Algorithm: start needing one action from each
     * player who can currently act (toAct = count of isActive() players). Every
     * action consumes one. A RAISE reopens the betting for everyone else, so it
     * resets toAct to (active count - 1) -- the raiser doesn't need to act again
     * immediately, everyone else does. The round ends when toAct hits 0
     * (everyone's had the last word with no further raise) or GameState reports
     * isBettingOver() (a fold-out or a wave of all-ins ended it early).
     */
    private static void runBettingRound(GameState state, List<PokerAgent> agents) {
        if (state.isBettingOver()) {
            return; // e.g. everyone left is already all-in from a previous street
        }

        int toAct = countActive(state);
        while (toAct > 0 && !state.isBettingOver()) {
            int actingSeat = state.getPlayerTurnIndex();
            int betBefore = state.getCurrentBet();

            state.takeTurn(agents.get(actingSeat));

            toAct--;
            if (state.getCurrentBet() > betBefore) {
                // That action was a raise -- CALL/CHECK/FOLD never increase currentBet
                // (CALL is capped at matching it, see GameState.processAction).
                toAct = countActive(state) - 1;
            }
        }
    }

    private static int countActive(GameState state) {
        return (int) state.getPlayers().stream().filter(Player::isActive).count();
    }
}