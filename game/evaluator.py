"""Module responsible for evaluating hands and deciding winners (game-side).
Player subclasses are welcome to implement their own evaluators.
This module is used by the game engine to simply compute the best card on the table and assign winners at showdown.
"""

from deck import Deck
from game_state import GameState
from player import Player
from enum import Enum
from collections import Counter

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

def _get_denoms(player: Player, state: GameState) -> Counter:
    """Return a counter of denomination frequencies across the player's hand and the table."""
    all_cards = player.hand + state.table
    return Counter(card[0] for card in all_cards)

def has_pair(player: Player, state: GameState) -> bool:
    """
    Return whether player achieves Combination.PAIR given the current GameState.
    Return True even if the combination is achieved by the table without using the player's hand.
    """
    counts = _get_denoms(player, state)
    return any(v >= 2 for v in counts.values())

def has_two_pair(player: Player, state: GameState) -> bool:
    """
    Return whether player achieves Combination.TWO_PAIR given the current GameState.
    Return True even if the combination is achieved by the table without using the player's hand.
    """
    counts = _get_denoms(player, state)
    return sum(1 for v in counts.values() if v >= 2) >= 2

def has_triple(player: Player, state: GameState) -> bool:
    """
    Return whether player achieves Combination.TRIPLE given the current GameState.
    Return True even if the combination is achieved by the table without using the player's hand.
    """
    counts = _get_denoms(player, state)
    return any(v >= 3 for v in counts.values())

def has_straight(player: Player, state: GameState) -> bool:
    """
    Return whether player achieves Combination.STRAIGHT given the current GameState.
    Return True even if the combination is achieved by the table without using the player's hand.
    """
    pass

def has_flush(player: Player, state: GameState) -> bool:
    """
    Return whether player achieves Combination.FLUSH given the current GameState.
    Return True even if the combination is achieved by the table without using the player's hand.
    """
    pass

def has_full_house(player: Player, state: GameState) -> bool:
    """
    Return whether player achieves Combination.FULL_HOUSE given the current GameState.
    Return True even if the combination is achieved by the table without using the player's hand.
    """
    counts = _get_denoms(player, state)
    pair_denoms = [d for d, v in counts.items() if v >= 2]
    triple_denoms = [d for d, v in counts.items() if v >= 3]
    return has_triple(player, state) and any(d not in triple_denoms for d in pair_denoms)

def has_quad(player: Player, state: GameState) -> bool:
    """
    Return whether player achieves Combination.QUAD given the current GameState.
    Return True even if the combination is achieved by the table without using the player's hand.
    """
    counts = _get_denoms(player, state)
    return any(v >= 4 for v in counts.values())

def has_straight_flush(player: Player, state: GameState) -> bool:
    """
    Return whether player achieves Combination.STRAIGHT_FLUSH given the current GameState.
    Return True even if the combination is achieved by the table without using the player's hand.
    """
    pass

def has_royal_flush(player: Player, state: GameState) -> bool:
    """
    Return whether player achieves Combination.ROYAL_FLUSH given the current GameState.
    Return True even if the combination is achieved by the table without using the player's hand.
    """
    pass





