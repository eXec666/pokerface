package com.andrei.pokerface;

/**
 * Decides what happens to a player whose stack hits exactly 0 at the end of a
 * hand. Kept pluggable so a tournament (eliminate) and a cash-game / hot-seat
 * mode (rebuy, prompt the GUI, etc.) can share the same SessionRunner loop.
 */
@FunctionalInterface
public interface BustHandler {
    /** Called once per player found at 0 chips after a hand completes. */
    void onBust(Player player);

    /** Tournament rule: a busted player is permanently out of the session. */
    BustHandler ELIMINATE = Player::eliminate;
}