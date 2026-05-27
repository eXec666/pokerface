"""
Module that defines the deck, and methods for manipulating a deck.
"""
import random

# suits global var
SUITS = {1: "club", 2: "diamond", 3: "heart", 4: "spade"}

class Deck:
    """
    A deck of cards in poker.
    A deck is a stack of cards - which are tuples of (value, suit).
    value is the integer denomination of the card: Ace = 1, then all numbers, then J = 11, Q = 12, K = 13.
    suit is one of {1, 2, 3, 4}, which maps to the corresponding suit in the SUITS global var above.

    Instance Attributes:
        - _cards: A list of the cards currently in the deck, with the last index corresponding to the topmost card.

    Representation Invariants:
        - len(self._cards) <= 52
        - all(1 <= card[0] <= 13 for card in self._cards)
        - all(card[1] in {1, 2, 3, 4} for card in self._cards)
    """
    _cards: list[tuple[int, int]]

    def __init__(self, ordered: bool = False) -> None:
        """
        Initialize this deck of cards.
        The ordered parameter determines whether the deck is generated in sorted order.
        sorted order works as follows:
            1. Clubs in ascending order (A -> K)
            2. Diamonds in ascending order (A -> K)
            3. Hearts in ascending order (A -> K)
            4. Spades in ascending order (A -> K)
            Jokers are excluded.
        A random permutation of the deck is created if ordered = False.
        """
        self._cards = []
        self._cards.extend((i, j) for j in range(1, 5) for i in range(1, 14))
        if ordered:
            return
        else:
            random.shuffle(self._cards)
            return

    def is_empty(self) -> bool:
        """Return whether this deck is empty"""
        return len(self._cards) == 0

    def draw(self) -> tuple[int, int]:
        """
        Draw a card from the top of the deck.
        Raise ValueError if the deck is empty
        """
        if self.is_empty():
            raise ValueError("This deck is empty: No cards to draw")
        else:
            return self._cards.pop()

    def reshuffle(self) -> None:
        """
        Reshuffle the deck in place.
        """
        random.shuffle(self._cards)

