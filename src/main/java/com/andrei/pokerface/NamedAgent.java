package com.andrei.pokerface;

/**
 * Pairs a PokerAgent with a stable display name for aggregation purposes.
 * PokerAgent.getName() is not sufficient on its own -- two RandomAgent
 * instances with different seeds both return "Random" -- so stat collectors
 * need an explicit, caller-supplied label to tell bots apart in a report.
 */
public record NamedAgent(String name, PokerAgent agent) {
    public NamedAgent {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bot name cannot be blank");
        }
        if (agent == null) {
            throw new IllegalArgumentException("Agent cannot be null");
        }
    }
}