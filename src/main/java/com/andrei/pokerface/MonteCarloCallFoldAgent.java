package com.andrei.pokerface;
import java.util.Random;

public class MonteCarloCallFoldAgent implements PokerAgent {
    private final int trials;
    private final Random random;

    public MonteCarloCallFoldAgent(int trials, long seed) {
        this.trials = trials;
        this.random = new Random(seed);
    }

    @Override
    public ActionResult performAction(PlayerView view) {

        // check if we can check for free, in which case we always do
        if (view.amountToCall() == 0) {
            return ActionResult.check();
        }

        int[] holeCards = view.myHoleCards();
        int[] communityCards = view.communityCards();
        int numOpponents = 1; // Heads-up is assumed for now
        int C = view.amountToCall();
        int P = view.potTotal();
        double equity = MonteCarloEquityEstimator.estimateEquity(holeCards, communityCards, numOpponents, trials, random);
        // decision threshold:
        double threshold = C / (double)(C + P);
        if (C > 100) { // only print large bets -- filters out routine blind-level decisions
        System.out.println("C=" + C + " P=" + P + " threshold=" + threshold + " equity=" + equity
                + " -> " + (equity > threshold ? "CALL" : "FOLD"));
        }

        return (equity > threshold) ? ActionResult.call() : ActionResult.fold();
    }
}
