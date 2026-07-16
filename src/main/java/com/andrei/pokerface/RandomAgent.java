package com.andrei.pokerface;

import java.util.Random;

/**
 * Reference bot: picks uniformly at random among the actions that are legal
 * in the current spot, weighted by configurable fold/call/raise propensities.
 * Exists to exercise every branch of GameState.processAction across a full
 * multiway session, unlike AlwaysCallAgent/FoldingAgent/AllInAgent, which
 * each only ever take one kind of voluntary action.
 */
public class RandomAgent implements PokerAgent {
    private final Random random;
    private final double foldWeight;
    private final double raiseWeight;
    // remaining probability mass goes to check/call

    public RandomAgent(long seed) {
        this(seed, 0.15, 0.25);
    }

    /**
     * @param foldWeight  probability of folding when facing a bet (ignored when checking is free)
     * @param raiseWeight probability of raising, whether facing a bet or not
     */
    public RandomAgent(long seed, double foldWeight, double raiseWeight) {
        if (foldWeight < 0 || raiseWeight < 0 || foldWeight + raiseWeight > 1) {
            throw new IllegalArgumentException("Weights must be non-negative and sum to at most 1");
        }
        this.random = new Random(seed);
        this.foldWeight = foldWeight;
        this.raiseWeight = raiseWeight;
    }

    @Override
    public ActionResult performAction(PlayerView view) {
        int maxTarget = view.me().roundBet() + view.me().stack();
        boolean canRaise = maxTarget > view.currentBet();
        boolean owesChips = view.amountToCall() > 0;
        double roll = random.nextDouble();

        if (!owesChips) {
            if (canRaise && roll < raiseWeight) {
                return chooseRaise(view, maxTarget);
            }
            return ActionResult.check();
        }

        if (roll < foldWeight) {
            return ActionResult.fold();
        }
        if (canRaise && roll < foldWeight + raiseWeight) {
            return chooseRaise(view, maxTarget);
        }
        return ActionResult.call();
    }

    /**
     * Picks a legal raise target between the minimum legal raise and a full
     * shove. If no legal min-raise exists (stack too short to meet minRaise)
     * but shoving is still a legal short all-in raise, shoves instead --
     * mirrors AllInAgent's handling of that edge case.
     */
    private ActionResult chooseRaise(PlayerView view, int maxTarget) {
        int minTarget = view.minRaiseTarget();
        if (minTarget > maxTarget) {
            return ActionResult.raiseTo(maxTarget); // short all-in
        }
        int target = minTarget + random.nextInt(maxTarget - minTarget + 1);
        return ActionResult.raiseTo(target);
    }

    @Override
    public String getName() {
        return "Random";
    }
}