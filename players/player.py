"""Module that defines the Player Base Class."""
from __future__ import annotations
from typing import TYPE_CHECKING, Optional
from abc import ABC, abstractmethod
from dataclasses import dataclass
from enum import Enum
from game.game_state import Street

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
    amount: int = 0

    def __repr__(self) -> str:
        return f"{self.action_type}, amount: {self.amount}"


class Player(ABC):
    """
    Abstract Base class for a poker player.

    Instance Attributes:
        - hand: the players current hand, which is stored as a list of two card tuples.
        - balance: the amount of money that the player currently holds (in cents)

    Representation Invariants:
        - self.balance >= 0
        - len(self.hand) == 2 or self.hand is None
    """
    hand: Optional[list[tuple[int, int]]] = None
    balance: Optional[int]

    def __init__(self, balance: Optional[float] = None) -> None:
        """Initialize this player."""
        self.hand = None
        self.balance = balance
        return

    @abstractmethod
    def get_action(self, state: GameState) -> Action:
        """Given the current game state, return the player's action"""
        ...


class CheckPlayer(Player):
    """Player Subclass used for testing. Always checks"""
    def __init__(self) -> None:
        super().__init__()

    def get_action(self, state: GameState) -> Action:
        """Only plays the blind. If any player raises - fold."""
        # find this player's index to check balance
        actor_idx = next(i for i, p in enumerate(state.players) if p is self)
        still_needed = state.current_bet - state.player_bets[actor_idx]
        affordable = min(still_needed, self.balance)

        # Call BB on preflop
        if state.street == Street.PREFLOP and state.current_bet == state.big_blind:
            return Action(ActionType.CALL, amount=affordable)
        # Fold if anyone raises preflop
        elif state.street == Street.PREFLOP:
            return Action(ActionType.FOLD)
        # Check on any future streets
        elif state.current_bet == 0:
            return Action(ActionType.CHECK)
        # Fold otherwise
        else:
            return Action(ActionType.FOLD)


