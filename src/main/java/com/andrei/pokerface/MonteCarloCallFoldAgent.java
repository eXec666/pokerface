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
        int numOpponents = countLiveOpponents(view);
        int C = view.amountToCall();
        int P = view.potTotal();
        double equity = MonteCarloEquityEstimator.estimateEquity(holeCards, communityCards, numOpponents, trials, random);
        // decision threshold:
        double threshold = C / (double)(C + P);
        return (equity > threshold) ? ActionResult.call() : ActionResult.fold();
    }

    private int countLiveOpponents(PlayerView view) {
        int count = 0;
        for (OpponentInfo p : view.players()) {
            if (p.seatIndex() != view.mySeatIndex() && !p.folded()) {
                count++;
            }
        }
        return count;
    }
}
