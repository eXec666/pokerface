"""
Module which defines cards.
"""

from dataclasses import dataclass
from python_ta.contracts import check_contracts

@dataclass
class Card:
    """
    A card in a game of poker.
    A card is defined by its value (an integer between 1-13 inclusive) and its suit.
    Ace is defined to have self.value == 1 and King has self.value == 13.

    Representation Invariants:
        - 1 <= self.value <= 13
        - self.suit in {"heart", "club", "diamond", "spade"}
    """
    value: int
    suit: str

    @property
    def name(self) -> str:
        """
        Return the name of this card.
        The name of the card is given by the concatenation of its value and its suit.
        For example, a card with self.value == 13 and self.suit == 'heart' becomes 'King heart'

        >>> card = Card(13, "heart")
        >>> card.name
        'King heart'
        """
        # Case 1: value is between 2 and 13 inclusive - the name is simply card denomination
        if 2 <= self.value <= 10:
            return str(self.value) + " " + self.suit
        # Jack
        elif self.value == 11:
            return "Jack " + self.suit
        # Queen
        elif self.value == 12:
            return "Queen " + self.suit
        # King
        elif self.value == 13:
            return "King " + self.suit
        # Ace
        else:
            return "Ace " + self.suit

