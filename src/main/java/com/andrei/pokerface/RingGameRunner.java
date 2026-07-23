package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Drives a fixed-stack "ring game" style batch of hands: unlike SessionRunner,
 * every player's stack is reset to a fixed buy-in before each hand, and
 * nobody is ever eliminated. This isolates the chip result of each hand's
 * decisions from tournament survival/ICM effects, which is what makes bb/100
 * a usable, low-variance metric for comparing bots -- tournament win-rate
 * needs thousands of tournaments to separate skill from variance; bb/100 over
 * a batch of independent, fixed-stack hands converges far faster, because
 * each hand is an independent sample of decision quality rather than a
 * multi-hour survival outcome.
 *
 * Dealer button rotation carries across hands exactly as it would in a real
 * session (GameState.startNewHand() advances it internally) -- only the
 * stacks are force-reset. Blinds are constant for the whole batch: a bb/100
 * comparison requires a fixed blind size to normalize against, so (unlike
 * SessionRunner) there is no blind schedule parameter here.
 *
 * Calls HandRunner.playHand() directly, one hand at a time, rather than going
 * through SessionRunner -- elimination, bust handling, and blind escalation
 * are all tournament concepts that don't apply to ring play.
 */
public final class RingGameRunner {

    private RingGameRunner() {}

    /**
     * Plays handsToPlay hands on a single GameState, resetting every player's
     * stack to buyIn before each hand.
     *
     * @param players     seated players; stack values are overwritten before every
     *                    hand, so their starting stacks don't matter, but list
     *                    order (seat index) does -- agents.get(i) must control
     *                    players.get(i)
     * @param agents      one agent per seat, same contract as HandRunner.playHand
     * @param smallBlind  constant small blind for every hand in the batch
     * @param bigBlind    constant big blind for every hand in the batch
     * @param buyIn       stack every player is reset to before each hand
     * @param handsToPlay number of hands to play
     * @param seedSource  supplies a fresh RNG seed per hand
     * @param logger      event subscriber for the whole batch (HandLogger.NO_OP if unused)
     */
    public static RingGameResult runBatch(
            List<Player> players,
            List<PokerAgent> agents,
            int smallBlind,
            int bigBlind,
            int buyIn,
            int handsToPlay,
            IntSupplier seedSource,
            HandLogger logger) {

        if (players == null || agents == null || players.size() != agents.size()) {
            throw new IllegalArgumentException(
                    "Need exactly one agent per player (" + (players == null ? 0 : players.size()) + " players)");
        }
        if (buyIn <= 0) {
            throw new IllegalArgumentException("Buy-in must be positive");
        }
        if (handsToPlay <= 0) {
            throw new IllegalArgumentException("handsToPlay must be positive");
        }

        GameState state = new GameState(players, smallBlind, bigBlind, 0);
        state.setLogger(logger);

        List<int[]> netChipsPerHand = new ArrayList<>(handsToPlay);

        for (int h = 0; h < handsToPlay; h++) {
            for (Player p : players) {
                p.setStack(buyIn);
            }

            HandRunner.playHand(state, agents, seedSource.getAsInt());

            int[] net = new int[players.size()];
            for (int i = 0; i < players.size(); i++) {
                net[i] = players.get(i).getStack() - buyIn;
            }
            netChipsPerHand.add(net);
        }

        return new RingGameResult(handsToPlay, netChipsPerHand, bigBlind);
    }

    /** Convenience overload: no logging. */
    public static RingGameResult runBatch(
            List<Player> players, List<PokerAgent> agents,
            int smallBlind, int bigBlind, int buyIn,
            int handsToPlay, IntSupplier seedSource) {
        return runBatch(players, agents, smallBlind, bigBlind, buyIn, handsToPlay, seedSource, HandLogger.NO_OP);
    }
}