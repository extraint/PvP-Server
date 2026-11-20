package dev.mintpvp.managers;

/** instant tier test rules, kept free of server code so they can be tested alone */
final class TierTestRules {

    /** who holds the HT1 crown in the kit, seen from the winner */
    enum Crown { NONE, WINNER, LOSER, OTHER }

    record Outcome(boolean passed, int winnerTier, int loserTier, String winnerNote, String loserNote) {}

    private TierTestRules() {}

    /** a test is passed by finishing the first-to with at most firstTo - 2 rounds lost (4-2 or better) */
    static boolean passed(int winnerRounds, int loserRounds, int firstTo) {
        return winnerRounds >= firstTo && loserRounds <= firstTo - 2;
    }

    /**
     * Winner who passes goes up one tier (an unranked player is placed at LT5). A loser never goes down, except an
     * HT1 holder, who falls to LT1. There is only one HT1: it can only be won from the holder, or taken while free.
     * @param tw winner's tier (-1 unranked), tl loser's tier, top = index of HT1
     */
    static Outcome resolve(int tw, int tl, int wr, int lr, int firstTo, int top, Crown crown) {
        boolean pass = passed(wr, lr, firstTo);
        int newW = tw;
        String noteW = "";
        if (!pass) {
            noteW = "win by 2+ rounds (" + firstTo + "-" + (firstTo - 2) + " or better) to move up";
        } else if (tw >= top) {
            noteW = "already at the top";
        } else {
            int target = tw + 1;
            if (target == top && (crown == Crown.OTHER)) {
                noteW = "HT1 is taken, beat its holder to take it";
            } else {
                newW = target;
                noteW = tw < 0 ? "placed" : "tier test passed";
            }
        }
        int newL = tl;
        String noteL = "";
        if (tl >= top) {
            newL = top - 1;
            noteL = "HT1 drops to LT1 on a loss";
        } else {
            noteL = "no tier lost";
        }
        return new Outcome(pass, newW, newL, noteW, noteL);
    }
}
