package io.github.shumtugle.clepsydra;

import java.util.Random;

/**
 * Which flask comes next. Not a ladder: every five flasks hold each tier
 * once, in an order shaken anew, so a knotty flask is followed by a kind
 * one as often as by another knot. Two hard flasks never stand side by
 * side, the same tier never comes twice running, and the styles take turns
 * the same way. The rules that hold the hand back (how far the flask may
 * turn, or for how long) come from a bag of their own, half of it free.
 * Bubbles, seeds and floats come now and then, never on the gentlest flask; rarer
 * still a flask turns by itself, flips like a coin, or catches snow, lava or
 * slime with its rain. No flask carries more than two troubles at once.
 * A plain flask, with none of them, sometimes breathes instead: once its
 * rain is over, its maze changes now and then.
 * The very first flask is always the gentlest and has none of them.
 * Every flask is played once out of sight before it is handed over.
 */
final class Course {
    private final Random rnd;
    private final int[] tiers = new int[Maze.TIERS];
    private final int[] styles = new int[Maze.STYLES];
    private final int[] rules = {Maze.FREE, Maze.FREE, Maze.TURNS, Maze.TIME};
    private int tierAt = Maze.TIERS, styleAt = Maze.STYLES, ruleAt = 4;
    private int lastTier, lastStyle = -1, lastRule = Maze.FREE;
    private boolean first = true;

    Course(Random rnd) {
        this.rnd = rnd;
    }

    Maze next() {
        if (first) {
            first = false;
            lastTier = 1;
            lastStyle = Maze.SCATTER;
            // the gentlest flask needs no trial, and the game should not wait for one to open
            return Maze.make(1, Maze.SCATTER, 0, 0, Maze.FREE, rnd);
        }
        if (tierAt >= tiers.length) shakeTiers();
        int tier = tiers[tierAt++];
        if (styleAt >= styles.length) shakeStyles();
        int style = styles[styleAt++];
        if (ruleAt >= rules.length) shakeRules();
        int rule = rules[ruleAt++];

        int bubbles = 0, seeds = 0, floats = 0;
        if (tier >= 2 && rnd.nextFloat() < 0.35f) bubbles = tier >= 3 && rnd.nextFloat() < 0.4f ? 2 : 1;
        if (tier >= 2 && rnd.nextFloat() < 0.35f) seeds = 1 + (rnd.nextFloat() < 0.35f ? 1 : 0);
        if (tier >= 2 && rnd.nextFloat() < 0.3f) floats = tier >= 4 && rnd.nextFloat() < 0.4f ? 2 : 1;
        int troubles = (rule != Maze.FREE ? 1 : 0) + (bubbles > 0 ? 1 : 0) + (seeds > 0 ? 1 : 0) + (floats > 0 ? 1 : 0);
        if (tier >= 4 && troubles > 2) {
            if (rnd.nextBoolean()) bubbles = 0; else seeds = 0;
        }
        // the rarer troubles: a flask that turns itself (never with a count of turns,
        // or it would spend the count on its own), a flask that flips like a coin,
        // and weather: snow, lava, or, where time is short, slime
        boolean wild = rule != Maze.TURNS && rnd.nextFloat() < 0.12f;
        boolean coin = rnd.nextFloat() < 0.15f;
        int weather = Maze.RAIN;
        float roll = rnd.nextFloat();
        // snow and lava are dodged by turning the neck away, which a count of turns
        // cannot afford: they never come with one
        if (rule == Maze.TIME && roll < 0.4f) weather = Maze.SLIME;
        else if (rule != Maze.TURNS && roll < 0.15f) weather = Maze.SNOW;
        else if (rule != Maze.TURNS && roll < 0.27f) weather = Maze.LAVA;
        // no flask carries more than two troubles; when there are more, the common ones give way
        int cap = 2;
        troubles = (rule != Maze.FREE ? 1 : 0) + (bubbles > 0 ? 1 : 0) + (seeds > 0 ? 1 : 0) + (floats > 0 ? 1 : 0)
                + (wild ? 1 : 0) + (coin ? 1 : 0) + (weather != Maze.RAIN ? 1 : 0);
        while (troubles > cap) {
            if (bubbles > 0) bubbles = 0;
            else if (floats > 0) floats = 0;
            else if (seeds > 0) seeds = 0;
            else if (coin) coin = false;
            else if (weather != Maze.RAIN && weather != Maze.SLIME) weather = Maze.RAIN;
            else if (wild) wild = false;
            else if (rule == Maze.TURNS) rule = Maze.FREE;
            else if (weather == Maze.SLIME) weather = Maze.RAIN;
            else break;
            troubles--;
        }
        // a plain flask, with nothing else to it, may breathe once its rain is over
        boolean plain = rule == Maze.FREE && bubbles == 0 && seeds == 0 && floats == 0
                && weather == Maze.RAIN && !wild && !coin;
        boolean breathing = plain && rnd.nextFloat() < 0.35f;
        lastTier = tier;
        lastStyle = style;
        lastRule = rule;
        return tried(tier, style, bubbles, seeds, floats, rule, wild, coin, weather, breathing);
    }

    /**
     * Makes a flask and plays it out of sight before handing it over: only a
     * flask the hand pours water out of by its own rules is kept. Mazes that
     * fail are drawn again; after a few, the troubles are eased, then the
     * flask is made gentler, so something always comes.
     */
    private Maze tried(int tier, int style, int bubbles, int seeds, int floats, int rule,
                       boolean wild, boolean coin, int weather, boolean breathing) {
        Maze last = null;
        for (int attempt = 0; attempt < 18; attempt++) {
            if (attempt == 6) { bubbles = 0; seeds = 0; floats = 0; weather = Maze.RAIN; }
            if (attempt == 12) { tier = Math.max(1, tier - 1); rule = Maze.FREE; }
            Maze m = Maze.make(tier, style, bubbles, seeds, floats, rule, rnd);
            m.wild = wild;
            m.coin = coin;
            m.weather = weather;
            m.breathing = breathing;
            m.falls = falls(weather);
            last = m;
            if (Hand.tries(m, rnd)) return m;
        }
        return last;
    }

    /** What falls: rain, with a few volumes of the weather among it, never the first. */
    private byte[] falls(int weather) {
        byte[] falls = new byte[Water.VOLUMES];
        if (weather == Maze.RAIN) return falls;
        byte what = weather == Maze.SNOW ? Water.SNOW : weather == Maze.LAVA ? Water.LAVA : Water.SLIME;
        int many = weather == Maze.LAVA ? 2 + rnd.nextInt(2) : 3;
        for (int i = 0; i < many; ) {
            int at = 1 + rnd.nextInt(Water.VOLUMES - 1);
            if (falls[at] == Water.WATER) { falls[at] = what; i++; }
        }
        return falls;
    }

    private void shakeTiers() {
        for (int tries = 0; tries < 200; tries++) {
            for (int i = 0; i < tiers.length; i++) tiers[i] = i + 1;
            shuffle(tiers);
            if (tiers[0] == lastTier) continue;
            if (hard(lastTier) && hard(tiers[0])) continue;
            boolean ok = true;
            for (int i = 1; i < tiers.length && ok; i++) ok = !(hard(tiers[i - 1]) && hard(tiers[i]));
            if (ok) break;
        }
        tierAt = 0;
    }

    private void shakeStyles() {
        do {
            for (int i = 0; i < styles.length; i++) styles[i] = i;
            shuffle(styles);
        } while (styles[0] == lastStyle);
        styleAt = 0;
    }

    private void shakeRules() {
        // a held hand twice running is enough
        do {
            shuffle(rules);
        } while (lastRule != Maze.FREE && rules[0] == lastRule);
        ruleAt = 0;
    }

    private static boolean hard(int tier) {
        return tier >= 4;
    }

    private void shuffle(int[] a) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }
}
