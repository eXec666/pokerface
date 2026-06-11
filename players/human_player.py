"""Module for the Human Player Class."""
from __future__ import annotations
from typing import TYPE_CHECKING, Optional

if TYPE_CHECKING:
    from game.game_state import GameState
from players.player import Player, Action, ActionType

class HumanPlayer(Player):
    """
    A subclass of Player for a human to interface with the game via CLI.
    """

    hand: Optional[list[tuple[int, int]]] = None
    balance: Optional[int]

    def __init__(self) -> None:
        super().__init__()

    def _card_str(self, card: tuple[int, int]) -> str:
        """Convert a card tuple to a readable string."""
        suit_map = {1: "♣", 2: "♦", 3: "♥", 4: "♠"}
        rank_map = {1: "A", 11: "J", 12: "Q", 13: "K"}
        rank = rank_map.get(card[0], str(card[0]))
        suit = suit_map[card[1]]
        return f"{rank}{suit}"

    def _display_state(self, state: GameState, actor_idx: int) -> None:
        """Print current game information for the human player."""
        print("\n" + "=" * 50)
        print(f"Your balance: {self.balance / 100:.2f}")
        if self.hand:
            print(f"Your hand: {self._card_str(self.hand[0])} {self._card_str(self.hand[1])}")
        print(f"Community cards: {[self._card_str(c) for c in state.table]}")
        print(f"Current street: {state.street.name}")
        print(f"Current bet to call: {state.current_bet / 100:.2f}")
        print(f"You have already bet this street: {state.player_bets[actor_idx] / 100:.2f}")
        print(f"Total pot: {state.pot / 100:.2f}")
        print("=" * 50)

    def get_action(self, state: GameState) -> Action:
        actor_idx = next(i for i, p in enumerate(state.players) if p is self)
        still_needed = state.current_bet - state.player_bets[actor_idx]
        call_amount = min(still_needed, self.balance) if still_needed > 0 else 0

        # Display state before prompting
        self._display_state(state, actor_idx)

        # Determine available actions based on game rules
        available = []
        # Fold is always available
        available.append(("fold", "f"))
        # Check only if no bet to call
        if still_needed == 0:
            available.append(("check", "k"))  # use 'k' to avoid conflict with 'call'
        # Call only if there is a bet to call AND player can afford it
        if 0 < still_needed <= self.balance:
            available.append(("call", "c"))
            # We'll use 'ca' for call, 'ch' for check to avoid conflict.
        # Raise is always allowed (engine will validate min raise)
        if self.balance > 0:
            available.append(("raise", "r"))

        # To avoid key conflict, we use distinct single letters:
        # f = fold, k = check, c = call, r = raise
        action_map = {
            "fold": "f",
            "check": "k",
            "call": "c",
            "raise": "r"
        }
        # Update available list with letters
        valid_actions = []
        prompt_parts = []
        if "fold" in [a[0] for a in available]:
            valid_actions.append("f")
            prompt_parts.append("fold (f)")
        if "check" in [a[0] for a in available]:
            valid_actions.append("k")
            prompt_parts.append("check (k)")
        if "call" in [a[0] for a in available]:
            valid_actions.append("c")
            prompt_parts.append("call (c)")
        if "raise" in [a[0] for a in available]:
            valid_actions.append("r")
            prompt_parts.append("raise (r)")

        while True:
            print(f"\nAvailable actions: {', '.join(prompt_parts)}")
            choice = input("Your choice: ").strip().lower()

            # Fold
            if choice in ("fold", "f") and "f" in valid_actions:
                confirm = input("Fold? (y/n): ").lower()
                if confirm == "y":
                    return Action(ActionType.FOLD)

            # Check
            elif choice in ("check", "k") and "k" in valid_actions:
                confirm = input("Check? (y/n): ").lower()
                if confirm == "y":
                    return Action(ActionType.CHECK)

            # Call
            elif choice in ("call", "c") and "c" in valid_actions:
                print(f"Call amount = {call_amount / 100:.2f}")
                confirm = input(f"Call {call_amount / 100:.2f}? (y/n): ").lower()
                if confirm == "y":
                    return Action(ActionType.CALL, amount=call_amount)

            # Raise
            elif choice in ("raise", "r") and "r" in valid_actions:
                # Ask for raise increment (extra amount on top of current bet)
                try:
                    inc_input = input("Raise by (in cents, e.g., 100 for $1.00): ")
                    increment = int(inc_input)
                    if increment <= 0:
                        print("Raise amount must be positive.")
                        continue
                    # Total amount to put in = current bet + increment
                    total_amount = state.current_bet + increment
                    # Ensure the player has enough balance
                    if total_amount > self.balance:
                        print(f"Insufficient balance. You have {self.balance / 100:.2f}, need {total_amount / 100:.2f}.")
                        continue
                    # Minimum raise validation can be left to engine, but we can give a warning
                    min_raise = state.last_raise_amount
                    if increment < min_raise and total_amount < self.balance:
                        print(f"Minimum raise increment is {min_raise / 100:.2f}. Your increment {increment / 100:.2f} is too low.")
                        # Allow all-in short raise though
                        if total_amount < self.balance:
                            continue
                    confirm = input(f"Raise to {total_amount / 100:.2f}? (y/n): ").lower()
                    if confirm == "y":
                        # The engine expects action.amount = total amount contributed this street
                        # current player already has player_bets[actor_idx] in the pot this street.
                        # The raise action.amount should be the new total bet = total_amount.
                        # But note: player_bets[actor_idx] already includes previous bets this street.
                        # The engine will compute: new_total_bet = player_bets[actor_idx] + action.amount ???
                        # In game.py RAISE branch: total_bet = state.player_bets[actor_idx]; then increment = (total_bet + action.amount) - state.current_bet.
                        # So action.amount should be the additional amount on top of current total_bet? Wait, let's re-read.
                        # From game.py, line ~125: increment = (total_bet + action.amount) - self.state.current_bet
                        # Here total_bet = player_bets[actor_idx] (what they've already put in this street).
                        # current_bet is the highest bet on the table.
                        # So if I want to raise to a total of T (including my previous bet), then I need to put in additional = T - player_bets[actor_idx].
                        # The engine expects action.amount to be that additional amount. Because then total_bet + action.amount = T.
                        # So we should compute: additional = total_amount - state.player_bets[actor_idx]
                        additional = total_amount - state.player_bets[actor_idx]
                        return Action(ActionType.RAISE, amount=additional)
                except ValueError:
                    print("Invalid number. Please enter an integer amount in cents.")
                    continue

            else:
                print("Invalid choice. Please enter one of the listed actions.")