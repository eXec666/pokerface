"""Module that defines the Player Base Class."""
from __future__ import annotations
from typing import TYPE_CHECKING
from abc import ABC, abstractmethod
from typing import Optional
from dataclasses import dataclass
from enum import Enum

if TYPE_CHECKING:
    from game.game_state import GameState


class ActionType(Enum):
    FOLD = "fold"
    CHECK = "check"
    CALL = "call"
    RAISE = "raise"

@dataclass
class Action:
    """
    A player action in a poker game.
    Instance Attributes:
        - action_type: one of FOLD, CHECK, CALL, or RAISE.
        - amount: the associated betting amount (only relevant for CALL & RAISE actions)
    """
    action_type: ActionType
    amount: float = 0.0

class Player(ABC):
    """
    Abstract Base class for a poker player.

    Instance Attributes:
        - hand: the players current hand, which is stored as a list of two card tuples.
        - balance: the amount of money that the player currently holds

    Representation Invariants:
        - self.balance >= 0
        - len(self.hand) == 2 or self.hand is None
    """
    hand: Optional[list[tuple[int, int]]] = None
    balance: float

    def __init__(self, balance: float) -> None:
        """Initialize this player."""
        self.hand = None
        self.balance = balance
        return

    @abstractmethod
    def get_action(self, state: GameState) -> Action:
        """Given the current game state, return the player's action"""
        ...


class TestPlayer(Player):
    """Temporary subclass for testing. Always folds"""
    def __init__(self, balance: float) -> None:
        super().__init__(balance)

    def get_action(self, state: GameState) -> Action:
        return Action(ActionType.FOLD)



