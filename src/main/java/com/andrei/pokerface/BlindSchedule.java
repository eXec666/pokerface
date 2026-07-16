package com.andrei.pokerface;

/**
 * Determines the blinds in effect after a given amount of session time has
 * elapsed. GameState's smallBlind/bigBlind fields are final, so a change in
 * blind level means SessionRunner constructs a fresh GameState rather than
 * mutating the existing one -- see SessionRunner.runSession().
 *
 * Time-based (not hand-count-based) so a future per-decision timer feature
 * for bots and humans can share the same injectable clock abstraction --
 * a schedule keyed on hands played would need a second, incompatible
 * notion of "how much time has passed."
 */
@FunctionalInterface
public interface BlindSchedule {
    BlindLevel blindsFor(long elapsedMillis);

    /** A schedule that never changes -- correct for a single-level freezeout. */
    static BlindSchedule constant(int smallBlind, int bigBlind) {
        BlindLevel level = new BlindLevel(smallBlind, bigBlind);
        return elapsedMillis -> level;
    }

    /**
     * Standard tournament-style geometric blind escalation: every intervalMillis,
     * both blinds grow by increasePct (e.g. 0.33 for a common +33% step),
     * compounding from baseLevel. Raw geometric values are then snapped to the
     * nearest multiple of chipDenomination -- mirroring how live tournaments
     * round blind levels to whatever chip denominations are actually on the
     * table rather than using exact fractional values (e.g. a chipDenomination
     * of 100 produces levels like 300/600, 400/800, 500/1100 instead of
     * 300/600, 399/798, 530.67/1061.34).
     *
     * @param baseLevel        the starting (level 0) blinds
     * @param increasePct      fractional growth per level, e.g. 0.33 for +33%
     * @param intervalMillis   how long each level lasts before the next increase
     * @param chipDenomination blinds are rounded to the nearest multiple of this value
     */
    static BlindSchedule increasing(BlindLevel baseLevel, double increasePct, long intervalMillis, int chipDenomination) {
        if (baseLevel == null) {
            throw new IllegalArgumentException("Base level cannot be null");
        }
        if (increasePct <= 0) {
            throw new IllegalArgumentException("Increase percentage must be positive");
        }
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("Interval must be positive");
        }
        if (chipDenomination <= 0) {
            throw new IllegalArgumentException("Chip denomination must be positive");
        }

        return elapsedMillis -> {
            int levelIndex = (int) (elapsedMillis / intervalMillis);
            double factor = Math.pow(1.0 + increasePct, levelIndex);

            int roundedSmall = roundToNearest(baseLevel.smallBlind() * factor, chipDenomination);
            int roundedBig = roundToNearest(baseLevel.bigBlind() * factor, chipDenomination);

            // Rounding to nearest is monotonic and the pre-rounding ratio (big >= small)
            // is always preserved by the shared factor, so this can only ever produce
            // equality, never a true inversion -- kept as defensive insurance against
            // floating-point edge cases exactly on a rounding boundary.
            if (roundedBig < roundedSmall) {
                roundedBig = roundedSmall;
            }

            return new BlindLevel(roundedSmall, roundedBig);
        };
    }

    private static int roundToNearest(double value, int unit) {
        long rounded = Math.round(value / unit) * (long) unit;
        long clamped = Math.max(rounded, unit);
        return (int) clamped;
    }
}