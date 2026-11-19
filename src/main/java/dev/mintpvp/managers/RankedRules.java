package dev.mintpvp.managers;

/** ranked progression rules, kept free of server code so they can be tested alone */
final class RankedRules {

    /** progress is counted in half wins; a full win is UNITS */
    static final int UNITS = 2;
    private static final long DAY = 86_400_000L;

    record Cfg(int promoteWins, int demoteLosses, int streakFrom, int eliteFrom, boolean scaling) {}

    record Gain(int units, String note) {}

    record Decay(int tier, long last, int dropped) {}

    static boolean elite(Cfg c, int tier) {
        return tier >= c.eliteFrom();
    }

    /** units of progress needed to leave a tier; every elite tier costs one more win than the last */
    static int promoteNeed(Cfg c, int tier) {
        int wins = c.promoteWins();
        if (c.scaling() && elite(c, tier)) wins += tier - c.eliteFrom() + 1;
        return wins * UNITS;
    }

    /** units of losses that drop you from a tier; elite tiers are less forgiving */
    static int demoteNeed(Cfg c, int tier) {
        int losses = c.demoteLosses();
        if (c.scaling() && elite(c, tier)) losses = Math.max(1, losses - 1);
        return losses * UNITS;
    }

    /** progress for a win: tw = your tier, tl = the loser's tier (-1 = unranked) */
    static Gain winGain(Cfg c, int tw, int tl, int streak, boolean flawless) {
        int base;
        String note = "";
        if (tl >= tw + 2) {
            base = 2 * UNITS;
            note = "big upset";
        } else if (tl >= tw) {
            base = UNITS;
        } else if (tl == tw - 1) {
            base = 1;
        } else if (elite(c, tw)) {
            base = 0;
            note = "opponent too far below you";
        } else {
            base = 1;
        }
        if (base > 0) {
            if (c.streakFrom() > 0 && streak >= c.streakFrom()) {
                base += UNITS;
                note = join(note, "win streak");
            }
            if (flawless) {
                base += 1;
                note = join(note, "flawless");
            }
        }
        return new Gain(base, note);
    }

    /** progress lost for a loss: tw = the winner's tier, tl = your tier */
    static Gain lossPenalty(Cfg c, int tw, int tl, boolean close) {
        if (tw > tl) return new Gain(0, "");
        if (elite(c, tl) && tl - tw >= 2) return new Gain(2 * UNITS, "upset loss");
        if (close) return new Gain(1, "close loss");
        return new Gain(UNITS, "");
    }

    /**
     * Inactivity decay for an elite tier: after graceDays without a ranked match you lose a tier, then another
     * every stepDays, never below floorTier. Returns dropped = 0 when nothing decays.
     */
    static Decay decay(int tier, long last, long now, int graceDays, int stepDays, int floorTier) {
        if (tier <= floorTier) return new Decay(tier, last, 0);
        double idle = (now - last) / (double) DAY;
        if (idle <= graceDays) return new Decay(tier, last, 0);
        int drops = 1 + (int) ((idle - graceDays) / Math.max(1, stepDays));
        int to = Math.max(floorTier, tier - drops);
        long newLast = to == floorTier ? now : now - (long) (graceDays - Math.max(1, stepDays)) * DAY;
        return new Decay(to, newLast, tier - to);
    }

    private static String join(String a, String b) {
        return a.isEmpty() ? b : a + ", " + b;
    }
}
