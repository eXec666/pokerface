package com.andrei.pokerface;

/**
 * Reference bot: shoves its entire remaining stack any time doing so would
 * be a legal raise, otherwise calls/checks. Exercises HandRunner's side-pot
 * and "betting ends early because everyone's all-in, but streets must still
 * be dealt out" paths.
 */
public class AllInAgent implements PokerAgent {
    @Override
    public ActionResult performAction(PlayerView view) {
        int target = view.me().roundBet() + view.me().stack();
        if (target <= view.currentBet()) {
            // Already covered by an outstanding bet larger than our whole stack --
            // shoving wouldn't even reach currentBet, so it's not a legal raise.
            return view.amountToCall() == 0 ? ActionResult.check() : ActionResult.call();
        }
        return ActionResult.raiseTo(target);
    }

    @Override
    public String getName() {
        return "AllIn";
    }
}