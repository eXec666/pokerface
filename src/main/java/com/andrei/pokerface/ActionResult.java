package com.andrei.pokerface;

/**
 * Immutable result of a PokerAgent's decision: which action to take, and
 * the chip amount tied to it. Amount semantics mirror
 * GameState.processAction(Action, int):
 *   - FOLD / CHECK : amount must be 0
 *   - CALL         : amount must be 0 (GameState computes the call size)
 *   - RAISE        : amount is the target roundBet total ("raise TO X"),
 *                     not a delta
 */
public record ActionResult(Action action, int amount) {

    public ActionResult {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if ((action == Action.FOLD || action == Action.CHECK) && amount != 0) {
            throw new IllegalArgumentException(action + " must carry amount 0");
        }
        if (action == Action.CALL && amount != 0) {
            throw new IllegalArgumentException(
                    "CALL amount is derived by GameState, not the agent; pass 0");
        }
        if (action == Action.RAISE && amount <= 0) {
            throw new IllegalArgumentException("RAISE requires a positive target amount");
        }
    }

    public static ActionResult fold() {
        return new ActionResult(Action.FOLD, 0);
    }

    public static ActionResult check() {
        return new ActionResult(Action.CHECK, 0);
    }

    public static ActionResult call() {
        return new ActionResult(Action.CALL, 0);
    }

    public static ActionResult raiseTo(int targetAmount) {
        return new ActionResult(Action.RAISE, targetAmount);
    }
}