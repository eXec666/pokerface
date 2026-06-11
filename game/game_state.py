"""The game state module. Defines what happens at each turn."""
from __future__ import annotations
from typing import TYPE_CHECKING
from enum import Enum
from dataclasses import dataclass, field
if TYPE_CHECKING:
    from players.player import Player
from game.deck import Deck

@dataclass
class Pot:
    """
    Represents a single pot in a poker hand
    
    Instance Attributes:
        - amount: the total monetary value of this pot
        - eligible: the player indices (into players[]) who can win this pot
    
    Representation Invariants:
        - self.amount >= 0.0
        - len(self.eligible) >= 1
    """
    amount: int
    eligible: list[int] = field(default_factory=list)

    def __repr__(self):
        return f"Pot(amount={self.amount / 100:.2f}, eligible={self.eligible})"
        
class Street(Enum):
    PREFLOP = 0
    FLOP = 1
    TURN = 2
    RIVER = 3

class GameState:
    """
    A class to encapsulate all the information available about a game of poker at a point in time.

    The state of a poker game is  as all the player, bet and card data
    on a particular player's turn during a single hand in that game.
    Instance Attributes:
        - players: ALL players in the session (including folded ones this hand).
        - active_players: indices into players[] who haven't folded.
        - curr_dealer: index into players[] for the dealer seat.
        - curr_actor: index into active_players[] for whose turn it is.
        - street: the current betting round.
        - pots: A list of all active pots.
        - current_bet: the highest bet placed this street (the amount to call).
        - last_raise_amount: the size of the latest raise increment
        - player_bets: maps player index -> amount they've bet this street.
        - deck: the deck for this hand.
        - table: community cards dealt so far (0–5).
        - small_blind: small blind amount. (in cents)
        - big_blind: big blind amount. (in cents)

    Representation Invariants:
        - len(self.players) >= 2
        - len(self.active_players) >= 1
        - all(p.amount >= 0 for p in self.pots)
        - 0 <= len(self.table) <= 5
        - self.current_bet >= 0.0
        - 0 <= self.curr_actor < len(self.active_players)
        - 0 <= self.curr_dealer < len(self.players)
    """
    players: list[Player]
    active_players: list[int]
    curr_dealer: int
    curr_actor: int
    street: Street
    pots: list[Pot]
    current_bet: int
    last_raise_amount: int
    player_bets: dict[int, int]
    deck: Deck
    table: list[tuple[int, int]]
    small_blind: int
    big_blind: int

    def __init__(self, players: list[Player], curr_dealer: int, small_blind: int, big_blind: int) -> None:
        """
        Initialize this game state.
        For a fresh hand:
            - All players are active
            - self.street is always PREFLOP
            - pot is 0.0
            - the deck is a randomly shuffled full deck
        """
        self.players = players
        self.active_players = list(range(len(players)))
        self.curr_dealer = curr_dealer
        self.street = Street.PREFLOP
        self.pots = [Pot(amount=0, eligible=list(range(len(players))))]
        self.current_bet = 0
        self.last_raise_amount = big_blind
        self.player_bets = {i: 0 for i in range(len(players))}
        self.deck = Deck()
        self.table = []
        self.small_blind = small_blind
        self.big_blind = big_blind

        self.curr_actor = 0  # will be set by Game after active_players is filtered

    @property
    def pot(self) -> int:
        """Return the total amount across all pots."""
        return sum(p.amount for p in self.pots)



    def get_current_actor(self) -> Player:
        """Return the Player object whose turn it is to act."""
        return self.players[self.active_players[self.curr_actor]]