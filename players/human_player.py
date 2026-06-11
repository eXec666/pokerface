"""
Module for the Human Player Class.

Defines an interface for a human to play the game with a CLI that runs on startup if a Human Player is initialized
in the game.
"""
from __future__ import annotations
from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from game.game_state import GameState
from players.player import Player, Action, ActionType

class HumanPlayer(Player):
    """
    A subclass of Player for a human to interface with the game.
    The subclass overrides __repr__ to display key information in console.
    Actions are submitted via CLI input

    Instance Attributes
        - hand: the players current hand, which is stored as a list of two card tuples.
        - balance: the amount of money that the player currently holds (in cents)

    Representation Invariants:
        - self.balance >= 0
        - len(self.hand) == 2 or self.hand is None
    """
    hand: Optional[list[tuple[int, int]]] = None
    balance: Optional[int]

    def __init__(self) -> None:
        """
        Initialize this Human Player
        """
        super().__init__()

    def get_action(self, state: GameState) -> Action:
        """
        Prompt the user for an action, and return the corresponding action based off the CLI response
        """
        pass
