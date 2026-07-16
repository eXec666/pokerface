package com.andrei.pokerface;

/**
 * Implemented by anything that plays poker: a scripted bot, an RL policy,
 * a CLI adapter for a human, etc. GameState calls performAction() once per
 * turn and applies the result via processAction(). The interface's job is
 * to make hidden information (other hole cards, deck order) unreachable
 * -- it does not, and cannot, prevent an implementation from behaving
 * badly with what it's given (e.g. betting more than its stack); GameState
 * still validates and throws on illegal actions.
 */
public interface PokerAgent {
    ActionResult performAction(PlayerView view);

    /** Display name; override for anything other than a generic label. */
    default String getName() {
        return "Agent";
    }
}