"""Module responsible for evaluating hands and deciding winners (game-side).
Player subclasses are welcome to implement their own evaluators.
This module is used by the game engine to simply compute the best card on the table and assign winners at showdown.
"""
from enum import Enum
from collections import Counter
from typing import Optional
from game.deck import Deck, _poker_value
from game.game_state import GameState
from player import Player

class Combination(Enum):
    HIGH_CARD = 0
    PAIR = 1
    TWO_PAIR = 2
    TRIPLE = 3
    STRAIGHT = 4
    FLUSH = 5
    FULL_HOUSE = 6
    QUAD = 7
    STRAIGHT_FLUSH = 8
    ROYAL_FLUSH = 9

def _get_counter(player: Player, state: GameState, i: int = 0) -> Counter:
    """
    Return a Counter of card attribute frequencies across the player's hand and the table.
    i=0 counts denominations, i=1 counts suits.
    """
    all_cards = player.hand + state.table
    return Counter(card[i] for card in all_cards)

def _find_straight(cards: list[tuple[int, int]]) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card straight from a given list of cards, or None if no straight exists.
    Handles the ace-low (A, 2, 3, 4, 5) and ace-high (10, J, Q, K, A) cases.
    """
    unique_denoms = sorted(set(card[0] for card in cards))

    if 1 in unique_denoms:
        unique_denoms.append(14)

    best_window = None
    for i in range(len(unique_denoms) - 4):
        window = unique_denoms[i:i+5]
        if window[-1] - window[0] == 4:
            best_window = window

    if best_window is None:
        return None

    lookup_denoms = [1 if d == 14 else d for d in best_window]
    result = []
    for denom in lookup_denoms:
        result.append(next(card for card in cards if card[0] == denom))
    return result

def has_high_card(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.HIGH_CARD.
    Return None if the highest combination available is given exclusively by the table.
    """
    # compute the sorted list of the five highest kickers
    highest_combo = sorted(player.hand + state.table, key=lambda c: _poker_value(c[0]))[-5:]

    # Neither of the players cards are present in the best combination -> table wins.
    if not any(card in highest_combo for card in player.hand):
        return None

    # the highest combination must contain at least one card from the player's hand -> return the list
    return highest_combo

def has_pair(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.PAIR
    Return None if no pair exists.
    """
    # collect all card denominations which appear in the combination twice
    counts = _get_counter(player, state)
    all_cards = player.hand + state.table
    pair_denoms = sorted([d for d, v in counts.items() if v >= 2], key=_poker_value)

    # No pairs exist - return None
    if not pair_denoms:
        return None
    else:
        # compute the highest pair, take the top 3 kickers, and concatenate
        best_pair_denom = pair_denoms.pop()
        head = [card for card in all_cards if card[0] == best_pair_denom][-2:]
        remaining_cards = sorted(set(all_cards) - set(head), key=lambda card: _poker_value(card[0]))
        return head + remaining_cards[-3:]


def has_two_pair(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.TWO_PAIR.
    Return None if no such combination exists.
    """
    counts = _get_counter(player, state)
    all_cards = player.hand + state.table

    # get the two highest-valued pair denominations
    pair_denoms = sorted([d for d, v in counts.items() if v >= 2], key=_poker_value)[-2:]

    # at most one pair exists - fall through to has_pair or has_high_card
    if len(pair_denoms) < 2:
        return None

    # take exactly 2 cards from each pair denom
    head = []
    for denom in pair_denoms:
        head += [card for card in all_cards if card[0] == denom][-2:]

    # best remaining card is the kicker
    kicker = max(set(all_cards) - set(head), key=lambda c: _poker_value(c[0]))
    return head + [kicker]


def has_triple(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.TRIPLE.
    Return None if no such combination exists.
    """
    # find all card denominations that appear thrice
    counts = _get_counter(player, state)
    all_cards = player.hand + state.table
    triple_denoms = sorted([d for d, v in counts.items() if v >= 3], key=_poker_value)

    # no triple exists: fall through
    if not triple_denoms:
        return None
    else:
        # calculate the best possible triple
        best_triple_denom = triple_denoms.pop()
        head = [card for card in all_cards if card[0] == best_triple_denom][-3:]
        remaining_cards = sorted(set(all_cards) - set(head), key=lambda card: _poker_value(card[0]))
        return head + remaining_cards[-2:]


def has_straight(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.STRAIGHT.
    Return None if no such combination exists.
    """
    return _find_straight(player.hand + state.table)


def has_flush(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.FLUSH.
    Return None if no such combination exists.
    """
    counts = _get_counter(player, state, 1)
    all_cards = player.hand + state.table
    flush_suites = [s for s, v in counts.items() if v >= 5]
    if not flush_suites:
        return None
    else:
        eligible_cards = sorted(
            (card for card in all_cards if card[1] in flush_suites),
             key=lambda c: _poker_value(c[0])
        )
        return eligible_cards[-5:]


def has_full_house(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.FULL_HOUSE.
    Return None if no such combination exists.
    """
    counts = _get_counter(player, state)
    all_cards = player.hand + state.table
    pair_denoms = sorted([d for d, v in counts.items() if v >= 2], key=_poker_value)
    triple_denoms = sorted([d for d, v in counts.items() if v >= 3], key=_poker_value)
    available_pair_denoms = sorted(set(pair_denoms) - set(triple_denoms), key=_poker_value)

    if not triple_denoms or not available_pair_denoms:
        return None

    best_triple_denom = triple_denoms.pop()
    best_pair_denom = available_pair_denoms.pop()
    head = [card for card in all_cards if card[0] == best_triple_denom][-3:]
    tail = [card for card in all_cards if card[0] == best_pair_denom][-2:]
    return head + tail


def has_quad(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.QUAD.
    Return None if no such combination exists.
    """
    counts = _get_counter(player, state)
    all_cards = player.hand + state.table
    quad_denoms = sorted([d for d, v in counts.items() if v == 4], key=_poker_value)

    if not quad_denoms:
        return None
    else:
        best_quad_denom = quad_denoms.pop()
        head = [card for card in all_cards if card[0] == best_quad_denom]

        kicker = max(set(all_cards) - set(head), key=lambda c: _poker_value(c[0]))
        return head + [kicker]



def has_straight_flush(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.STRAIGHT_FLUSH.
    Return None if no such combination exists.
    """
    all_cards = player.hand + state.table
    best = None

    for suit in range(1, 5):
        suited_cards = [card for card in all_cards if card[1] == suit]
        if len(suited_cards) < 5:
            continue
        result = _find_straight(suited_cards)
        if result is not None:
            # keep the highest straight flush found across all suits
            if best is None or _poker_value(result[-1][0]) > _poker_value(best[-1][0]):
                best = result

    return best


def has_royal_flush(player: Player, state: GameState) -> Optional[list[tuple[int, int]]]:
    """
    Return the best 5-card combination for player containing Combination.ROYAL_FLUSH.
    Return None if no such combination exists.
    """
    straight_flush = has_straight_flush(player, state)
    if straight_flush is None:
        return None
    # a royal flush is a straight flush topped by an ace (stored as 1)
    denoms = {card[0] for card in straight_flush}
    if 1 in denoms and 13 in denoms:  # ace and king both present means A-high straight flush
        return straight_flush
    return None


def best_hand(player: Player, state: GameState) -> tuple[Combination, list[tuple[int, int]]]:
    """
    Return the best combination and corresponding 5-card hand for the player.
    """
    checks = [
        (Combination.ROYAL_FLUSH, has_royal_flush),
        (Combination.STRAIGHT_FLUSH, has_straight_flush),
        (Combination.QUAD, has_quad),
        (Combination.FULL_HOUSE, has_full_house),
        (Combination.FLUSH, has_flush),
        (Combination.STRAIGHT, has_straight),
        (Combination.TRIPLE, has_triple),
        (Combination.TWO_PAIR, has_two_pair),
        (Combination.PAIR, has_pair),
        (Combination.HIGH_CARD, has_high_card),
    ]
    for combination, fn in checks:
        result = fn(player, state)
        if result is not None:
            return (combination, result)

    # fallback: table dominates, return the 5 best cards anyway
    fallback = sorted(player.hand + state.table, key=lambda c: _poker_value(c[0]))[-5:]
    return (Combination.HIGH_CARD, fallback)


def compare_hands(
    h1: tuple[Combination, list[tuple[int, int]]],
    h2: tuple[Combination, list[tuple[int, int]]]
) -> int:
    """
    Compare two hands. Returns 1 if h1 wins, -1 if h2 wins, 0 if tie.
    """
    c1, cards1 = h1
    c2, cards2 = h2

    # higher combination wins outright
    if c1.value != c2.value:
        return 1 if c1.value > c2.value else -1

    # same combination - compare card values from highest to lowest
    vals1 = sorted([_poker_value(c[0]) for c in cards1], reverse=True)
    vals2 = sorted([_poker_value(c[0]) for c in cards2], reverse=True)

    for v1, v2 in zip(vals1, vals2):
        if v1 != v2:
            return 1 if v1 > v2 else -1

    return 0  # tie