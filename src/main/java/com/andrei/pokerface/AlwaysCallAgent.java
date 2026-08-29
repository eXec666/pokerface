package com.andrei.pokerface;

/**
 * Reference bot: checks when it owes nothing, otherwise calls. Never folds,
 * never raises.
 */
public class AlwaysCallAgent implements PokerAgent {
    @Override
    public ActionResult performAction(PlayerView view) {
        if (view.amountToCall() == 0) {
            return ActionResult.check();
        }
        return ActionResult.call();
    }

    @Override
    public String getName() {
        return "AlwaysCall";
    }
}