"""Module that defines the Player Base Class."""
from abc import ABC, abstractmethod
from typing import Optional
import random

class Player(ABC):
    """
    Abstract Base class for a poker player.

    Instance Attributes:
        - hand: the players current hand, which is stored as a tuple of two card tuples. Immutable.
        - balance: the amount of money that the player currently holds

    Representation Invariants:
        - self.balance >= 0
    """
    hand: Optional[tuple[tuple[int, int], tuple[int, int]]] = None
    balance: float

    def __init__(self, balance: float) -> None:
        """Initialize this player."""
        self.hand = None
        self.balance = balance
        return

    @abstractmethod
    def bet(self) -> float:
        """Bid some amount of money in this current round."""
        ...

    def fold(self) -> None:
        """Fold in this round"""
        return

    def check(self) -> None:
        """Check in this round"""
        return

class TestPlayer(Player):
    """Temporary subclass for testing. Bets a random amount between 0 and self."""
    def __init__(self, balance: float) -> None:
        super().__init__(balance)

    def bet(self) -> float:
        """Return a random number between 0 and self.balance inclusive"""
        return random.uniform(0, self.balance)


