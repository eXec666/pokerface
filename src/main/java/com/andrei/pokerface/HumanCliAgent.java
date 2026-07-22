package com.andrei.pokerface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * PokerAgent backed by a human at a terminal. Renders the PlayerView as
 * readable text, reads a command line, validates it against the same
 * legality rules GameState.processAction enforces, and re-prompts on any
 * violation instead of ever handing GameState something that would throw.
 *
 * Input/output are injected (not System.in/System.out directly) so tests
 * can drive this with canned input and capture output without touching the
 * real console.
 */
public class HumanCliAgent implements PokerAgent {

    private final BufferedReader in;
    private final PrintStream out;
    private final String name;

    public HumanCliAgent(InputStream in, PrintStream out) {
        this(in, out, "Human");
    }

    public HumanCliAgent(InputStream in, PrintStream out, String name) {
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
        this.name = name;
    }

    @Override
    public ActionResult performAction(PlayerView view) {
        render(view);
        while (true) {
            out.print("> ");
            out.flush();
            String line = readLine();
            ParsedCommand parsed = parse(line);
            if (parsed == null) {
                out.println("Unrecognized command. Options: fold, check, call, raise <amount>, allin");
                continue;
            }
            String error = validate(parsed, view);
            if (error != null) {
                out.println(error);
                continue;
            }
            return toActionResult(parsed, view);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    /* -------------------------------------------------------------- */
    /* Rendering                                                        */
    /* -------------------------------------------------------------- */

    private void render(PlayerView view) {
        OpponentInfo me = view.me();
        int maxTarget = me.roundBet() + me.stack();
        boolean canRaise = maxTarget > view.currentBet();

        out.println();
        out.println("---- " + view.round() + " ----");
        out.println("Board: " + (view.communityCards().length == 0
                ? "(none)" : CardUtils.handToString(view.communityCards())));
        out.println("Your hand: " + renderHoleCards(view.myHoleCards()));
        out.println("Pot: " + view.potTotal() + "  Current bet: " + view.currentBet()
                + "  To call: " + view.amountToCall());
        out.println("Your stack: " + me.stack() + "  Your round bet: " + me.roundBet());
        if (canRaise) {
            out.println("Min raise target: " + view.minRaiseTarget() + "  Max (all-in) target: " + maxTarget);
        }
        out.println("Seats:");
        for (OpponentInfo o : view.players()) {
            String status = o.folded() ? "folded" : (o.allIn() ? "all-in" : "active");
            String marker = (o.seatIndex() == view.mySeatIndex()) ? " (you)" : "";
            out.println("  " + o.seatIndex() + " " + o.name() + marker
                    + " stack=" + o.stack() + " bet=" + o.roundBet() + " [" + status + "]");
        }
        out.println("Commands: fold | check | call | raise <amount> | allin");
    }

    private String renderHoleCards(int[] holeCards) {
    for (int c : holeCards) {
        if (c < 0) {
            return "(not dealt)";
        }
    }
    return CardUtils.handToString(holeCards);
}

    /* -------------------------------------------------------------- */
    /* Input reading                                                     */
    /* -------------------------------------------------------------- */

    private String readLine() {
        try {
            String line = in.readLine();
            if (line == null) {
                throw new IllegalStateException(
                        "Input stream closed before a command was entered. "
                        + "If you're launching via 'mvn exec:java', stdin may not be attached correctly -- "
                        + "run 'java -cp target/classes com.andrei.pokerface.PlayHuman' instead.");
            }
            return line;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading human input", e);
        }
    }

    /* -------------------------------------------------------------- */
    /* Parsing                                                           */
    /* -------------------------------------------------------------- */

    private enum Kind { FOLD, CHECK, CALL, RAISE, ALLIN }

    private record ParsedCommand(Kind kind, Integer amount) {}

    private ParsedCommand parse(String line) {
        String trimmed = line.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        String head = parts[0];

        return switch (head) {
            case "fold", "f" -> new ParsedCommand(Kind.FOLD, null);
            case "check", "k" -> new ParsedCommand(Kind.CHECK, null);
            case "call", "c" -> new ParsedCommand(Kind.CALL, null);
            case "allin", "shove", "all-in" -> new ParsedCommand(Kind.ALLIN, null);
            case "raise", "bet", "r" -> parseRaise(parts);
            default -> null;
        };
    }

    private ParsedCommand parseRaise(String[] parts) {
        if (parts.length != 2) {
            return null;
        }
        try {
            return new ParsedCommand(Kind.RAISE, Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /* -------------------------------------------------------------- */
    /* Validation -- mirrors GameState.processAction's legality rules     */
    /* -------------------------------------------------------------- */

    private String validate(ParsedCommand cmd, PlayerView view) {
        OpponentInfo me = view.me();
        int maxTarget = me.roundBet() + me.stack();

        return switch (cmd.kind()) {
            case FOLD -> null;
            case CHECK -> (view.amountToCall() > 0)
                    ? "Cannot check: " + view.amountToCall() + " to call. Use call, raise, or fold."
                    : null;
            case CALL -> (view.amountToCall() == 0)
                    ? "Nothing to call. Use check instead."
                    : null;
            case ALLIN -> (maxTarget <= view.currentBet())
                    ? "Cannot go all-in as a raise here. Use call or fold."
                    : null;
            case RAISE -> validateRaise(cmd.amount(), view, me, maxTarget);
        };
    }

    private String validateRaise(int amount, PlayerView view, OpponentInfo me, int maxTarget) {
        if (amount <= view.currentBet()) {
            return "Raise must exceed the current bet of " + view.currentBet() + ".";
        }
        if (amount > maxTarget) {
            return "You only have " + me.stack() + " chips; max raise target is " + maxTarget + ".";
        }
        boolean isAllIn = (amount == maxTarget);
        int minIncrement = view.minRaiseTarget() - view.currentBet();
        int increment = amount - view.currentBet();
        if (increment < minIncrement && !isAllIn) {
            return "Raise must be at least " + view.minRaiseTarget()
                    + " (or go all-in for less with 'allin').";
        }
        return null;
    }

    /* -------------------------------------------------------------- */
    /* Conversion                                                        */
    /* -------------------------------------------------------------- */

    private ActionResult toActionResult(ParsedCommand cmd, PlayerView view) {
        OpponentInfo me = view.me();
        int maxTarget = me.roundBet() + me.stack();
        return switch (cmd.kind()) {
            case FOLD -> ActionResult.fold();
            case CHECK -> ActionResult.check();
            case CALL -> ActionResult.call();
            case ALLIN -> ActionResult.raiseTo(maxTarget);
            case RAISE -> ActionResult.raiseTo(cmd.amount());
        };
    }
}