package com.andrei.pokerface;

/**
 * Reference bot: checks when it owes nothing, otherwise folds. Exercises
 * HandRunner's early-fold-win path (GameState.checkFoldWin()) and, in
 * multiway pots, the "one player folds but betting continues" path.
 */
public class FoldingAgent implements PokerAgent {
    @Override
    public ActionResult performAction(PlayerView view) {
        if (view.amountToCall() == 0) {
            return ActionResult.check();
        }
        return ActionResult.fold();
    }

    @Override
    public String getName() {
        return "Folding";
    }
}