package com.andrei.pokerface;

import java.util.List;

/**
 * Structured description of something that happened during a hand, emitted by
 * GameState to whatever HandLogger is currently registered. This is a
 * passive, one-way event stream -- GameState fires events as a side effect
 * of its normal state-mutating calls (startNewHand, postBlinds,
 * processAction, dealCommunityCard, awardPot, checkFoldWin,
 * resolveShowdown). It never asks a logger for permission or a return
 * value, and nothing in GameState ever reads events back. Consumers
 * (console printer, file writer, in-memory test recorder) subscribe without
 * GameState knowing anything about how the event is used.
 *
 * Being a sealed interface of records means any consumer switching over
 * GameEvent gets a compiler error the moment a new event type is added --
 * there is no way to silently miss a case.
 */
public sealed interface GameEvent {

    /** Fired once per startNewHand() call, after the dealer button has moved and cards are dealt. */
    record HandStarted(int dealerSeat, int handSeed) implements GameEvent {}

    /** A small or big blind (or a short all-in blind) posted before betting starts. */
    record BlindPosted(int seatIndex, String blindType, int amount, int resultingStack) implements GameEvent {}

    /** Any voluntary action taken during a betting round (CHECK/FOLD/CALL/RAISE). */
    record ActionTaken(int seatIndex, Round round, Action action, int amount, int resultingStack) implements GameEvent {}

    /** One community card revealed (flop/turn/river). Burns are intentionally NOT logged as events. */
    record CommunityCardDealt(Round round, int card, int cardsDealtSoFar) implements GameEvent {}

    /**
     * A single pot awarded to one or more (possibly tied) winners.
     * potIndex is the pot's position in GameState.getPots() for a normal showdown award;
     * it is -1 for the aggregate award checkFoldWin() makes (no Pot objects exist on that path).
     */
    record PotAwarded(int potIndex, int amount, List<Integer> winnerSeats) implements GameEvent {}

    /** Terminal event for the hand: either a fold-out or a full showdown. */
    record HandEnded(boolean wonByFold, List<Integer> winnerSeats) implements GameEvent {}
}