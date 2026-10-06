package io.github.shumtugle.clepsydra;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

/**
 * The glass inside the flask: two to four rings with gaps in them and short
 * walls between the rings. Everything is measured in flask radii; angles are
 * in radians, zero to the right and growing clockwise, as on a screen. The
 * neck looks up and the bottom opening looks down while the flask stands.
 *
 * A maze is made for a tier, from one (gentle) to five (knotty), and in one
 * of three styles: scattered gaps, a spiral of gaps, or chambers walled off
 * beside the gaps. The rings cut the flask into bands, the walls cut each
 * band into sectors, and a gap joins two sectors. The tier asks for a number
 * of gaps on the shortest way from the neck to the bottom; a maze that does
 * not give it is thrown away and drawn again.
 */
final class Maze {
    static final float TAU = (float) (2 * Math.PI);
    static final float PI = (float) Math.PI;
    static final float NECK = -PI / 2;
    static final float BOTTOM = PI / 2;
    static final float NECK_H = 0.17f, BOTTOM_H = 0.16f;
    static final float NECK_LEN = 1.24f, LIP_LEN = 1.08f;

    static final int SCATTER = 0, SPIRAL = 1, CHAMBERS = 2;
    static final int STYLES = 3, TIERS = 5;
    /** What holds the hand back: nothing, how far the flask may turn, or how long. */
    static final int FREE = 0, TURNS = 1, TIME = 2;
    static final int BUBBLE = 1, SEED = 2, FLOAT = 3;
    /** What falls besides rain. */
    static final int RAIN = 0, SNOW = 1, LAVA = 2, SLIME = 3;
    /** When the first volume falls, and how far apart they come. */
    static final float FIRST_SPAWN = 1f, SPAWN_EVERY = 2.8f;

    /** Ring radii for two, three and four rings. */
    private static final float[][] RADII = {
            {0.68f, 0.38f},
            {0.74f, 0.50f, 0.27f},
            {0.78f, 0.61f, 0.44f, 0.27f},
    };
    /** Per tier: how many rings, how wide a gap is (flask radii), the shortest way wanted. */
    private static final int[] RINGS = {2, 3, 3, 4, 4};
    private static final float[] WIDTH = {0.30f, 0.28f, 0.25f, 0.23f, 0.21f};
    private static final int[] DIST_MIN = {2, 2, 3, 4, 5};
    private static final int[] DIST_MAX = {2, 3, 4, 5, 7};
    /** Per tier: walls in each inner band, from and to. */
    private static final int[] SPOKES_LO = {0, 1, 1, 1, 1};
    private static final int[] SPOKES_HI = {1, 2, 2, 2, 3};

    /** One circle of glass: where it opens, the edges of each opening, and the solid arcs. */
    static final class Ring {
        final float r;
        final float[] gapC, gapH;
        final float[] capX, capY;
        final float[] arcFrom, arcTo;

        Ring(float r, float[] gapC, float[] gapH) {
            this.r = r;
            this.gapC = gapC;
            this.gapH = gapH;
            int n = gapC.length;
            capX = new float[2 * n];
            capY = new float[2 * n];
            for (int i = 0; i < n; i++) {
                float a = gapC[i] - gapH[i], b = gapC[i] + gapH[i];
                capX[2 * i] = (float) Math.cos(a) * r;
                capY[2 * i] = (float) Math.sin(a) * r;
                capX[2 * i + 1] = (float) Math.cos(b) * r;
                capY[2 * i + 1] = (float) Math.sin(b) * r;
            }
            float[] c = new float[n], h = new float[n];
            for (int i = 0; i < n; i++) { c[i] = norm(gapC[i]); h[i] = gapH[i]; }
            for (int i = 1; i < n; i++) {
                for (int j = i; j > 0 && c[j - 1] > c[j]; j--) {
                    float t = c[j]; c[j] = c[j - 1]; c[j - 1] = t;
                    t = h[j]; h[j] = h[j - 1]; h[j - 1] = t;
                }
            }
            arcFrom = new float[n];
            arcTo = new float[n];
            for (int i = 0; i < n; i++) {
                int k = (i + 1) % n;
                arcFrom[i] = c[i] + h[i];
                float to = c[k] - h[k];
                if (i == n - 1) to += TAU;
                arcTo[i] = to;
            }
        }

        boolean open(float th) {
            for (int i = 0; i < gapC.length; i++) if (adist(th, gapC[i]) < gapH[i]) return true;
            return false;
        }
    }

    /**
     * Something lying across a corridor, or in a gap. A bubble of air blocks
     * the corridor it fills, or corks the gap it sits in, until it is popped.
     * A seed, a pearl stuck to the glass, lets water by, but once wet enough
     * it sprouts, and the flower opens from the wall it sits on across to the
     * other wall and shuts the way. The radii run from the base to the far
     * side, in flask radii; a cork sits on its ring, both radii the same.
     */
    static final class Obstacle {
        final int kind, band;
        /** The ring a cork sits in, or -1 for anything across a corridor. */
        final int ring;
        /** What a bubble is there for: to be popped, to be kept, or for nothing. */
        final int role;
        final float a, cs, sn, r0, r1;
        /** Which flower a seed holds: a red pearl a rose, a green one clover, a golden one a daisy. */
        final int variant;
        boolean active = true;
        float wet, grow, gone = -1f;
        boolean sprouting;
        /** A float: its radius, where it starts on its rail, where it is, and whether water holds it up. */
        float size, s0, s;
        boolean up;

        /**
         * A copy: seen in a mirror and with all it has lived through, or as it
         * was when the flask was made.
         */
        Obstacle(Obstacle o, boolean mirror) {
            kind = o.kind;
            band = o.band;
            ring = o.ring;
            role = o.role;
            a = mirror ? norm(PI - o.a) : o.a;
            cs = (float) Math.cos(a);
            sn = (float) Math.sin(a);
            r0 = o.r0;
            r1 = o.r1;
            variant = o.variant;
            size = o.size;
            s0 = o.s0;
            s = o.s0;
            if (!mirror) return;
            active = o.active;
            wet = o.wet;
            grow = o.grow;
            gone = o.gone;
            sprouting = o.sprouting;
            s = o.s;
            up = o.up;
        }

        Obstacle(int kind, int band, float a, float r0, float r1, int role, Random rnd) {
            this.kind = kind;
            this.band = band;
            this.ring = -1;
            this.role = role;
            this.a = norm(a);
            this.cs = (float) Math.cos(a);
            this.sn = (float) Math.sin(a);
            this.r0 = r0;
            this.r1 = r1;
            variant = kind == SEED ? rnd.nextInt(3) : 0;
            // a bubble across a corridor is one round bubble, wall to wall
            if (kind == BUBBLE) size = Math.abs(r1 - r0) / 2;
        }

        /** A cork: one bubble sitting in the gap at angle a of ring k, a little wider than the gap. */
        Obstacle(int k, float a, float radius, float halfGap, int role) {
            kind = BUBBLE;
            band = k;
            ring = k;
            this.role = role;
            this.a = norm(a);
            cs = (float) Math.cos(a);
            sn = (float) Math.sin(a);
            r0 = r1 = radius;
            variant = 0;
            size = halfGap * radius + 0.02f;
        }

        /** Where the middle of a bubble is, in flask radii from the centre. */
        float mid() {
            return (r0 + r1) / 2;
        }
    }

    /** What a bubble is there for. */
    static final int LOCK = 0, GUARD = 1, DECOY = 2;
    /** The ring a cork in the flask's own glass sits in; how wide a vent there is, half of it. */
    static final int WALL = 64;
    static final float VENT_H = 0.09f;

    /** Holes cut in the flask's own glass, each corked by a guard. */
    float[] vents = new float[0];

    /** Cuts the vents into the flask's own glass, beside the neck and the bottom. */
    void vent(float[] v) {
        vents = v;
        float[] c = new float[2 + v.length], h = new float[2 + v.length];
        c[0] = NECK; h[0] = NECK_H;
        c[1] = BOTTOM; h[1] = BOTTOM_H;
        for (int i = 0; i < v.length; i++) { c[2 + i] = v[i]; h[2 + i] = VENT_H; }
        rings[0] = new Ring(1f, c, h);
    }

    final int tier, style, rule;
    /** The flask wall first, then the inner rings from outside in. */
    final Ring[] rings;
    /** Straight walls, four numbers each: from x, from y, to x, to y. */
    final float[] segs;
    final Obstacle[] obstacles;
    /** The least turning, in radians, that brings water from the neck to the bottom. */
    final float need;
    /** How to do it: the angles the flask stands at, one after another. */
    final float[] plan;
    /** The bubbles to pop for that; none may have to go. */
    final boolean[] pops;
    /** The flask turns by itself; the flask flips over like a coin now and then; what falls. */
    boolean wild, coin;
    /** Once the rain is over, the flask breathes now and then and its maze changes. */
    boolean breathing;
    /** What each volume is: rain, or the flask's weather. */
    byte[] falls = new byte[Water.VOLUMES];
    int weather = RAIN;

    final float[] radii, half;
    final float[][] gaps, spokes;

    private Maze(int tier, int style, float[] radii, float[] half, float[][] gaps, float[][] spokes,
                 Obstacle[] obstacles, int rule, float[] solved, boolean[] pops) {
        this.tier = tier;
        this.style = style;
        this.rule = rule;
        this.obstacles = obstacles;
        this.need = solved[0];
        this.plan = Arrays.copyOfRange(solved, 1, solved.length);
        this.pops = pops;
        this.radii = radii;
        this.half = half;
        this.gaps = gaps;
        this.spokes = spokes;
        int n = radii.length;
        rings = new Ring[n + 1];
        rings[0] = new Ring(1f, new float[] {NECK, BOTTOM}, new float[] {NECK_H, BOTTOM_H});
        for (int k = 0; k < n; k++) {
            float[] h = new float[gaps[k].length];
            Arrays.fill(h, half[k]);
            rings[k + 1] = new Ring(radii[k], gaps[k], h);
        }
        int count = 4;
        for (float[] s : spokes) count += s.length;
        segs = new float[count * 4];
        int at = 0;
        for (int b = 0; b < n; b++) {
            float ro = b == 0 ? 1f : radii[b - 1], ri = radii[b];
            for (float a : spokes[b]) {
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                segs[at++] = cs * ri; segs[at++] = sn * ri;
                segs[at++] = cs * ro; segs[at++] = sn * ro;
            }
        }
        // the neck: a short tube standing on the opening
        float nx = (float) Math.sin(NECK_H), ny = (float) -Math.cos(NECK_H);
        segs[at++] = nx; segs[at++] = ny; segs[at++] = nx; segs[at++] = -NECK_LEN;
        segs[at++] = -nx; segs[at++] = ny; segs[at++] = -nx; segs[at++] = -NECK_LEN;
        // the bottom: two lips, so the two openings are told apart at a glance
        float bx = (float) Math.sin(BOTTOM_H), by = (float) Math.cos(BOTTOM_H);
        segs[at++] = bx; segs[at++] = by; segs[at++] = bx; segs[at++] = LIP_LEN;
        segs[at++] = -bx; segs[at++] = by; segs[at++] = -bx; segs[at] = LIP_LEN;
    }

    /**
     * The maze turned over like a coin about its upright axis: left becomes
     * right, the neck and the bottom stay where they are.
     */
    Maze mirrored() {
        float[][] g = new float[gaps.length][], s = new float[spokes.length][];
        for (int k = 0; k < g.length; k++) {
            g[k] = new float[gaps[k].length];
            for (int i = 0; i < g[k].length; i++) g[k][i] = norm(PI - gaps[k][i]);
        }
        for (int b = 0; b < s.length; b++) {
            s[b] = new float[spokes[b].length];
            for (int i = 0; i < s[b].length; i++) s[b][i] = norm(PI - spokes[b][i]);
        }
        Obstacle[] o = new Obstacle[obstacles.length];
        for (int i = 0; i < o.length; i++) o[i] = new Obstacle(obstacles[i], true);
        float[] solved = new float[plan.length + 1];
        solved[0] = need;
        for (int i = 0; i < plan.length; i++) solved[i + 1] = -plan[i];
        Maze m = new Maze(tier, style, radii, half, g, s, o, rule, solved, pops.clone());
        float[] v = new float[vents.length];
        for (int i = 0; i < v.length; i++) v[i] = norm(PI - vents[i]);
        if (v.length > 0) m.vent(v);
        m.wild = wild;
        m.breathing = breathing;
        m.falls = falls.clone();
        m.coin = coin;
        m.weather = weather;
        return m;
    }

    /**
     * The turning a flask with a count of turns allows: the least the maze
     * needs, a third more, and a little over a quarter turn to spare.
     */
    float turnAllowance() {
        return need * 1.35f + 1.6f;
    }

    /**
     * The time a flask on the clock allows, from the first volume: as long as
     * the rain, and as much again for each stop on the way and for the turning.
     */
    float timeAllowance() {
        return Math.max(40f, Math.min(90f, 32 + 4 * need + 3 * plan.length));
    }

    /** The same flask as it was made: bubbles whole, seeds dry, not flipped. */
    Maze fresh() {
        Obstacle[] o = new Obstacle[obstacles.length];
        for (int i = 0; i < o.length; i++) o[i] = new Obstacle(obstacles[i], false);
        float[] solved = new float[plan.length + 1];
        solved[0] = need;
        System.arraycopy(plan, 0, solved, 1, plan.length);
        Maze m = new Maze(tier, style, radii, half, gaps, spokes, o, rule, solved, pops.clone());
        if (vents.length > 0) m.vent(vents.clone());
        m.wild = wild;
        m.breathing = breathing;
        m.falls = falls.clone();
        m.coin = coin;
        m.weather = weather;
        return m;
    }

    static float norm(float a) {
        a %= TAU;
        return a < 0 ? a + TAU : a;
    }

    static float adist(float a, float b) {
        float d = Math.abs(norm(a) - norm(b));
        return d > PI ? TAU - d : d;
    }

    private static float sym(Random rnd, float spread) {
        return (rnd.nextFloat() * 2 - 1) * spread;
    }

    private static float range(Random rnd, float lo, float hi) {
        return lo + rnd.nextFloat() * (hi - lo);
    }

    /**
     * A new maze of the given tier (1 to 5) and style, with so many bubbles
     * and so many seeds, held back by the given rule. Always passable: with
     * bubbles, after the right ones are popped and the rest kept.
     */
    static Maze make(int tier, int style, int bubbles, int seeds, int rule, Random rnd) {
        return make(tier, style, bubbles, seeds, 0, rule, rnd);
    }

    /**
     * The same with floats: a float rides a rail across a band, between a gap
     * in the ring above it and a gap in the ring below, at the same angle.
     * Water lifts it into the upper gap and shuts it; when the water drains
     * away it sinks into the lower gap and shuts that. Floats need a band with
     * a ring on each side, so a flask of two rings has room for one.
     */
    static Maze make(int tier, int style, int bubbles, int seeds, int floats, int rule, Random rnd) {
        tier = Math.max(1, Math.min(TIERS, tier));
        int t = tier - 1;
        int n = RINGS[t];
        float[] radii = RADII[n - 2];
        float[] half = new float[n];
        for (int k = 0; k < n; k++) half[k] = WIDTH[t] / (2 * radii[k]);

        Maze best = null;
        int bestMiss = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 2500; attempt++) {
            ArrayList<ArrayList<Float>> gaps = new ArrayList<ArrayList<Float>>();
            for (int k = 0; k < n; k++) gaps.add(new ArrayList<Float>());

            // the first ring: one way in near the top half, one way out near the bottom half
            // the first ring opens no nearer the neck than this, or water poured into
            // that gap with the neck tilted would run straight back out of the neck
            float lean = rnd.nextBoolean() ? 1f : -1f;
            if (!gap(gaps, half, 0, NECK + lean * range(rnd, NECK_H + half[0] + 0.45f, 1.4f))) continue;
            if (!gap(gaps, half, 0, BOTTOM + sym(rnd, 1.3f))) continue;
            if (tier == 1 && rnd.nextFloat() < 0.3f) gap(gaps, half, 0, rnd.nextFloat() * TAU);

            boolean ok = true;
            float dir = rnd.nextBoolean() ? 1f : -1f;
            float last = gaps.get(0).get(rnd.nextInt(2));
            for (int k = 1; k < n && ok; k++) {
                if (style == SPIRAL) {
                    // each ring opens a step further round than the ring outside it
                    last = last + dir * range(rnd, 1.0f, 1.8f);
                    ok = gap(gaps, half, k, last);
                    if (ok && rnd.nextFloat() < (tier <= 2 ? 0.5f : 0.2f)) {
                        place(gaps, half, k, rnd, 40);
                    }
                } else {
                    int want = tier >= 5 ? 1 + rnd.nextInt(2) : (k == n - 1 ? 1 + rnd.nextInt(2) : 2);
                    for (int i = 0; i < want && ok; i++) ok = place(gaps, half, k, rnd, 40);
                }
            }
            if (!ok) continue;

            ArrayList<ArrayList<Float>> spokes = new ArrayList<ArrayList<Float>>();
            for (int b = 0; b < n; b++) spokes.add(new ArrayList<Float>());
            // the outer band is always parted left and right, so the neck cannot simply
            // pour round the rim into the bottom
            if (!wall(spokes, gaps, half, n, 0, rnd, 0f, 0.9f)) continue;
            if (!wall(spokes, gaps, half, n, 0, rnd, PI, 0.9f)) continue;
            if (tier >= 4 && rnd.nextBoolean()) wall(spokes, gaps, half, n, 0, rnd, -1f, 0f);
            for (int b = 1; b < n; b++) {
                int want = SPOKES_LO[t] + rnd.nextInt(SPOKES_HI[t] - SPOKES_LO[t] + 1);
                if (style == CHAMBERS) want++;
                for (int i = 0; i < want; i++) {
                    if (style == CHAMBERS && rnd.nextFloat() < 0.75f) {
                        // a wall just beside a gap turns the sector behind it into a cup
                        int ring = rnd.nextBoolean() ? b - 1 : b;
                        if (gaps.get(ring).isEmpty()) ring = b - 1;
                        ArrayList<Float> near = gaps.get(ring);
                        float c = near.get(rnd.nextInt(near.size()));
                        float side = rnd.nextBoolean() ? 1f : -1f;
                        wall(spokes, gaps, half, n, b, rnd, c + side * (half[ring] + 0.4f), 0.15f);
                    } else {
                        wall(spokes, gaps, half, n, b, rnd, -1f, 0f);
                    }
                }
            }

            ArrayList<float[]> sites = new ArrayList<float[]>();
            for (int f = 0; f < floats && ok; f++) ok = floatSite(gaps, spokes, half, n, rnd, sites);
            if (!ok) continue;

            float[][] g = arrays(gaps);
            float[][] s = arrays(spokes);
            int dist = shortest(g, s, n);
            if (dist < 0) continue;
            int miss = dist < DIST_MIN[t] ? DIST_MIN[t] - dist : dist > DIST_MAX[t] ? dist - DIST_MAX[t] : 0;
            if (miss == 0) {
                Maze dressed = dress(tier, style, radii, half, g, s, bubbles, seeds, rule, rnd, sites, WIDTH[t]);
                if (dressed != null) return dressed;
                continue;
            }
            if (miss < bestMiss) {
                float[] plan = solve(g, s, radii, half);
                if (plan != null) {
                    bestMiss = miss;
                    best = new Maze(tier, style, radii, half, g, s, new Obstacle[0], rule, plan, new boolean[0]);
                }
            }
        }
        if (best != null) return best;
        float[][] g = {{NECK + 0.8f, BOTTOM - 0.5f}, {0.3f, 3.4f}};
        float[][] s = {{0.2f, PI + 0.2f}, {}};
        float[] h = {0.30f / 1.36f, 0.30f / 0.76f};
        float[] plan = solve(g, s, RADII[0], h);
        if (plan == null) plan = new float[] {0f, 0f};
        return new Maze(1, SCATTER, RADII[0], h, g, s, new Obstacle[0], FREE, plan, new boolean[0]);
    }

    /**
     * Lays bubbles and seeds on a finished maze; null if they will not fit or
     * would shut it. Bubbles come in three kinds, alike to the eye. A lock
     * shuts the way down, in a corridor on it or as a cork in one of its
     * gaps, and has to be popped. A decoy stands somewhere harmless. A guard
     * is laid later, once the water has been watched (see guarded). The
     * flask's one decision is which of them to pop.
     */
    private static Maze dress(int tier, int style, float[] radii, float[] half, float[][] g, float[][] s,
                              int bubbles, int seeds, int rule, Random rnd, ArrayList<float[]> sites, float width) {
        int n = radii.length;
        ArrayList<Obstacle> obs = new ArrayList<Obstacle>();
        for (float[] site : sites) {
            int b = (int) site[0];
            Obstacle o = new Obstacle(FLOAT, b, site[1], radii[b], radii[b - 1], DECOY, rnd);
            // a little wider than the gaps it shuts
            o.size = width / 2 + 0.025f;
            // it starts sunk: at whichever end of its rail lies lower with the flask upright
            o.s0 = o.s = (float) Math.sin(site[1]) > 0 ? radii[b - 1] : radii[b];
            obs.add(o);
        }
        if (bubbles == 0 && seeds == 0 && obs.isEmpty()) {
            float[] plan = solve(g, s, radii, half);
            if (plan == null) return null;
            return new Maze(tier, style, radii, half, g, s, new Obstacle[0], rule, plan, new boolean[0]);
        }
        boolean[] none = new boolean[obs.size()];
        Bands bands = new Bands(s, n);
        ArrayList<float[]> legs = route(open(g, obs, none, n), bands, n);
        if (legs == null) return null;
        int locks = bubbles > 0 ? 1 : 0;
        for (int i = 0; i < locks; i++) {
            // a lock is a cork in a gap on the way about as often as a bubble across it
            Obstacle o = rnd.nextBoolean() ? cork(legs, half, radii, obs, n, rnd) : null;
            if (o == null) o = onLeg(BUBBLE, LOCK, legs, bands, g, half, radii, obs, rnd);
            if (o == null) return null;
            obs.add(o);
        }
        for (int i = locks; i < bubbles; i++) {
            Obstacle o = anywhere(BUBBLE, DECOY, bands, g, half, radii, obs, rnd);
            if (o == null) return null;
            obs.add(o);
        }
        for (int i = 0; i < seeds; i++) {
            Obstacle o = onLeg(SEED, DECOY, legs, bands, g, half, radii, obs, rnd);
            if (o == null) o = anywhere(SEED, DECOY, bands, g, half, radii, obs, rnd);
            if (o == null) return null;
            obs.add(o);
        }
        // the hand pops no bubble or one, never a guard; the way must stay open with
        // every flower grown, so a seed shuts one corridor, not the flask
        float[] best = null;
        boolean[] pick = null;
        // most of the time a lock is not there for nothing: the way is shut until it is popped
        boolean mustPop = locks > 0 && rnd.nextFloat() < 0.7f;
        for (int skip = -1; skip < obs.size(); skip++) {
            if (skip >= 0 && (obs.get(skip).kind != BUBBLE || obs.get(skip).role == GUARD)) continue;
            boolean[] popped = new boolean[obs.size()];
            if (skip >= 0) popped[skip] = true;
            float[] plan = solve(open(g, obs, popped, n), withWalls(s, obs, popped, n), radii, half);
            if (skip < 0 && mustPop && plan != null) return null;
            if (plan != null && (best == null || plan[0] < best[0])) { best = plan; pick = popped; }
        }
        if (best == null) return null;
        return new Maze(tier, style, radii, half, g, s, obs.toArray(new Obstacle[0]), rule, best, pick);
    }

    /**
     * The same flask with one more bubble, a guard: a vent cut in the flask's
     * own glass, corked by a bubble. The flask is played through out of sight
     * and the places where water rests against the glass are counted; the
     * vent goes where it rests most, away from the neck, the bottom and the
     * walls. Kept, the bubble holds the water in; popped, the water runs out
     * of the vent and is lost. The guard stays only if losing it costs the
     * pour a good part of its water. Null when no vent does that.
     */
    static Maze guarded(Maze m, Random rnd) {
        int bins = 72;
        float[] heat = heat(m, bins, rnd);
        int n = m.radii.length;
        for (int q = 0; q < 5; q++) {
            int bj = -1;
            float hot = 0;
            for (int j = 0; j < bins; j++) if (heat[j] > hot) { hot = heat[j]; bj = j; }
            if (bj < 0 || hot < 30) return null;
            // the spot and its neighbours are spent whether the vent fits or not
            for (int j = bj - 2; j <= bj + 2; j++) heat[(j + bins) % bins] = 0;
            float a = (bj + 0.5f) * TAU / bins;
            if (adist(a, NECK) < NECK_H + VENT_H + 0.35f || adist(a, BOTTOM) < BOTTOM_H + VENT_H + 0.35f) continue;
            boolean clear = true;
            for (float sp : m.spokes[0]) if (adist(a, sp) < VENT_H + 0.12f) clear = false;
            for (float v : m.vents) if (adist(a, v) < 2 * VENT_H + 0.3f) clear = false;
            for (Obstacle o : m.obstacles) {
                boolean outer = o.ring == WALL || (o.ring < 0 && o.band == 0) || o.ring == 0;
                if (outer && adist(a, o.a) < VENT_H + 0.35f) clear = false;
            }
            if (!clear) continue;
            Obstacle[] obs = new Obstacle[m.obstacles.length + 1];
            for (int i = 0; i < m.obstacles.length; i++) obs[i] = new Obstacle(m.obstacles[i], false);
            obs[obs.length - 1] = new Obstacle(WALL, a, 1f, VENT_H, GUARD);
            float[] solved = new float[m.plan.length + 1];
            solved[0] = m.need;
            System.arraycopy(m.plan, 0, solved, 1, m.plan.length);
            Maze out = new Maze(m.tier, m.style, m.radii, m.half, m.gaps, m.spokes, obs, m.rule, solved,
                    Arrays.copyOf(m.pops, obs.length));
            out.wild = m.wild;
            out.coin = m.coin;
            out.breathing = m.breathing;
            out.falls = m.falls.clone();
            out.weather = m.weather;
            float[] v = Arrays.copyOf(m.vents, m.vents.length + 1);
            v[v.length - 1] = a;
            out.vent(v);
            int[] kept = Hand.play(out, out.pops, new Random(rnd.nextLong()));
            boolean[] all = out.pops.clone();
            all[all.length - 1] = true;
            int[] lost = Hand.play(out, all, new Random(rnd.nextLong()));
            if (kept[1] >= 10 && (lost[0] <= kept[0] * 0.75f || lost[1] <= kept[1] * 0.7f)) return out;
        }
        return null;
    }

    /**
     * Where the water rests against the flask's own glass while the hand
     * plays the flask: in bins of angle, counted only where the glass is
     * underneath the water, so that a hole there would let it out.
     */
    private static float[] heat(Maze made, int bins, Random rnd) {
        float[] heat = new float[bins];
        Maze m = made.fresh();
        Water w = new Water();
        w.size(1080, 2400, 540, 1272, 453);
        w.reset(m);
        Hand hand = new Hand(m);
        float dt = 1f / 240, lastSpawn = -99f;
        int spawned = 0;
        for (int step = 0; step < 240 * 90; step++) {
            w.t += dt;
            if (spawned < Water.VOLUMES && w.t >= FIRST_SPAWN + spawned * SPAWN_EVERY) {
                w.spawn(w.cx + (rnd.nextFloat() * 0.2f - 0.1f) * w.r, m.falls[spawned]);
                spawned++;
                lastSpawn = w.t;
            }
            boolean rained = spawned == Water.VOLUMES && w.t > lastSpawn + 3f;
            if (hand.pop(rained)) Hand.popAll(w.maze, m.pops);
            float rot = hand.goal(w, rained, dt) - w.ang;
            rot = rot < -Hand.TURN ? -Hand.TURN : rot > Hand.TURN ? Hand.TURN : rot;
            w.step(dt, rot);
            if (hand.walked() && w.t - hand.end() > 6f) break;
            if (step % 8 != 0) continue;
            float ca = (float) Math.cos(w.ang), sa = (float) Math.sin(w.ang);
            for (int i = 0; i < w.n; i++) {
                if (w.state[i] != Water.INSIDE || w.kind[i] != Water.WATER) continue;
                float dx = w.x[i] - w.cx, dy = w.y[i] - w.cy;
                float lx = ca * dx + sa * dy, ly = -sa * dx + ca * dy;
                float rho = (float) Math.sqrt(lx * lx + ly * ly) / w.r;
                if (rho < 0.9f || rho > 1f) continue;
                float th = norm((float) Math.atan2(ly, lx));
                // the glass is underneath where the spot is low on the screen
                if (Math.sin(th + w.ang) < 0.3f) continue;
                heat[(int) (th / TAU * bins) % bins] += 1;
            }
        }
        return heat;
    }

    /** A cork for one of the gaps the way down passes through, or null. */
    private static Obstacle cork(ArrayList<float[]> legs, float[] half, float[] radii, ArrayList<Obstacle> obs,
                                 int n, Random rnd) {
        if (legs.size() < 2) return null;
        for (int q = 0; q < 8; q++) {
            int i = rnd.nextInt(legs.size() - 1);
            int b0 = (int) legs.get(i)[0], b1 = (int) legs.get(i + 1)[0];
            int k = Math.min(b0, b1);
            if (k < 0 || k >= n || Math.abs(b0 - b1) != 1) continue;
            float a = legs.get(i)[2];
            boolean taken = false;
            for (Obstacle o : obs) if (o.ring == k && adist(o.a, a) < 0.05f) taken = true;
            if (taken) continue;
            return new Obstacle(k, a, radii[k], half[k], LOCK);
        }
        return null;
    }

    /** The gaps as water finds them: a gap with a cork still in it is shut. */
    private static float[][] open(float[][] g, ArrayList<Obstacle> obs, boolean[] popped, int n) {
        float[][] out = new float[n][];
        for (int k = 0; k < n; k++) {
            float[] keep = new float[g[k].length];
            int m = 0;
            for (float c : g[k]) {
                boolean shut = false;
                for (int i = 0; i < obs.size(); i++) {
                    Obstacle o = obs.get(i);
                    if (o.ring == k && !popped[i] && adist(o.a, c) < 0.05f) shut = true;
                }
                if (!shut) keep[m++] = c;
            }
            out[k] = Arrays.copyOf(keep, m);
        }
        return out;
    }

    /** The walls with every obstacle across a corridor standing as one more wall, but for the popped. */
    private static float[][] withWalls(float[][] s, ArrayList<Obstacle> obs, boolean[] popped, int n) {
        float[][] out = new float[n][];
        for (int b = 0; b < n; b++) {
            int extra = 0;
            for (int i = 0; i < obs.size(); i++) if (wall(obs.get(i), popped[i], b)) extra++;
            out[b] = Arrays.copyOf(s[b], s[b].length + extra);
            int at = s[b].length;
            for (int i = 0; i < obs.size(); i++) if (wall(obs.get(i), popped[i], b)) out[b][at++] = obs.get(i).a;
        }
        return out;
    }

    /** Bubbles and seeds across a corridor stand like walls; a float moves and does not, a cork shuts a gap. */
    private static boolean wall(Obstacle o, boolean popped, int b) {
        return !popped && o.band == b && o.kind != FLOAT && o.ring < 0;
    }

    /** A rail for a float in some band with a ring on each side, and the two gaps it serves. */
    private static boolean floatSite(ArrayList<ArrayList<Float>> gaps, ArrayList<ArrayList<Float>> spokes,
                                     float[] half, int n, Random rnd, ArrayList<float[]> sites) {
        if (n < 2) return false;
        for (int q = 0; q < 60; q++) {
            int b = 1 + rnd.nextInt(n - 1);
            float a = rnd.nextFloat() * TAU;
            boolean ok = true;
            for (float o : gaps.get(b - 1)) if (adist(a, o) <= 2 * half[b - 1] + 0.6f) ok = false;
            for (float o : gaps.get(b)) if (adist(a, o) <= 2 * half[b] + 0.6f) ok = false;
            // room around the rail for the float and the water beside it
            for (float o : spokes.get(b)) if (adist(a, o) < Math.max(0.5f, half[b] + 0.2f)) ok = false;
            for (float o : spokes.get(b - 1)) if (adist(a, o) < half[b - 1] + 0.15f) ok = false;
            if (b + 1 < n) for (float o : spokes.get(b + 1)) if (adist(a, o) < half[b] + 0.15f) ok = false;
            for (float[] st : sites) if (adist(a, st[1]) < 0.9f) ok = false;
            if (!ok) continue;
            gaps.get(b - 1).add(norm(a));
            gaps.get(b).add(norm(a));
            sites.add(new float[] {b, norm(a)});
            return true;
        }
        return false;
    }

    private static Obstacle lay(int kind, int role, int b, float a, float[] radii, Random rnd) {
        float out = b == 0 ? 1f : radii[b - 1], in = radii[b];
        if (kind == SEED && rnd.nextBoolean()) return new Obstacle(kind, b, a, out, in, role, rnd);
        return new Obstacle(kind, b, a, in, out, role, rnd);
    }

    private static Obstacle onLeg(int kind, int role, ArrayList<float[]> legs, Bands bands, float[][] g, float[] half,
                                  float[] radii, ArrayList<Obstacle> obs, Random rnd) {
        int n = radii.length;
        for (int q = 0; q < legs.size() * 3; q++) {
            float[] leg = legs.get(rnd.nextInt(legs.size()));
            int b = (int) leg[0];
            if (b >= n) continue;
            float arc = bands.arc(b, leg[1], leg[2]);
            if (arc < 0.6f) continue;
            float a = bands.mid(b, leg[1], leg[2]) + sym(rnd, arc * 0.15f);
            if (roomFor(b, a, bands, g, half, obs)) return lay(kind, role, b, a, radii, rnd);
        }
        return null;
    }

    private static Obstacle anywhere(int kind, int role, Bands bands, float[][] g, float[] half, float[] radii,
                                     ArrayList<Obstacle> obs, Random rnd) {
        int n = radii.length;
        for (int q = 0; q < 80; q++) {
            int b = rnd.nextInt(n);
            float a = rnd.nextFloat() * TAU;
            if (roomFor(b, a, bands, g, half, obs)) return lay(kind, role, b, a, radii, rnd);
        }
        return null;
    }

    private static boolean roomFor(int b, float a, Bands bands, float[][] g, float[] half, ArrayList<Obstacle> obs) {
        if (b == 0) {
            if (adist(a, NECK) < NECK_H + 0.35f || adist(a, BOTTOM) < BOTTOM_H + 0.35f) return false;
        } else {
            for (float c : g[b - 1]) if (adist(a, c) < half[b - 1] + 0.2f) return false;
        }
        for (float c : g[b]) if (adist(a, c) < half[b] + 0.2f) return false;
        for (float sp : bands.sorted[b]) if (adist(a, sp) < 0.35f) return false;
        for (Obstacle o : obs) {
            boolean near = o.ring < 0 ? o.band == b : o.ring == b || o.ring == b - 1;
            if (near && adist(a, o.a) < 0.5f) return false;
        }
        return true;
    }

    /**
     * Rings cut the flask into bands and walls cut the bands into sectors.
     * Water in a sector lies where the sector is lowest; to reach a gap the
     * flask turns until the gap is underneath, and it may not carry the low
     * point across a wall on the way.
     */
    static final class Bands {
        final float[][] sorted;
        final int n;

        Bands(float[][] spokes, int n) {
            this.n = n;
            sorted = new float[n][];
            for (int b = 0; b < n; b++) {
                sorted[b] = spokes[b].clone();
                for (int i = 0; i < sorted[b].length; i++) sorted[b][i] = norm(sorted[b][i]);
                Arrays.sort(sorted[b]);
            }
        }

        int id(int b, float th) {
            return b * 32 + (b >= n ? 0 : sectorOf(sorted[b], th));
        }

        /** How far the low point travels from p to c inside one sector. */
        float arc(int b, float p, float c) {
            if (b >= n || sorted[b].length == 0) return adist(p, c);
            float[] s = sorted[b];
            int m = s.length;
            float lo = s[(sectorOf(s, p) - 1 + m) % m];
            return Math.abs(norm(p - lo) - norm(c - lo));
        }

        /** The same travel with its direction: positive when the angle grows. */
        float signed(int b, float p, float c) {
            if (b >= n || sorted[b].length == 0) {
                float d = norm(c - p);
                return d > PI ? d - TAU : d;
            }
            float[] s = sorted[b];
            int m = s.length;
            float lo = s[(sectorOf(s, p) - 1 + m) % m];
            return norm(c - lo) - norm(p - lo);
        }

        /** Halfway from p to c inside one sector. */
        float mid(int b, float p, float c) {
            if (b >= n || sorted[b].length == 0) {
                float d = norm(c - p);
                if (d > PI) d -= TAU;
                return norm(p + d / 2);
            }
            float[] s = sorted[b];
            int m = s.length;
            float lo = s[(sectorOf(s, p) - 1 + m) % m];
            return norm(lo + (norm(p - lo) + norm(c - lo)) / 2);
        }
    }

    /** The sectors on the shortest way down: band, where water comes in, where it leaves. */
    private static ArrayList<float[]> route(float[][] gaps, Bands bands, int n) {
        HashMap<Integer, ArrayList<float[]>> adj = new HashMap<Integer, ArrayList<float[]>>();
        for (int k = 0; k < n; k++) {
            for (float c : gaps[k]) {
                int u = bands.id(k, c), v = bands.id(k + 1, c);
                edge(adj, u, v, c);
                edge(adj, v, u, c);
            }
        }
        int start = bands.id(0, NECK), goal = bands.id(0, BOTTOM);
        HashMap<Integer, float[]> parent = new HashMap<Integer, float[]>();
        parent.put(start, new float[] {-1, 0});
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        queue.add(start);
        while (!queue.isEmpty() && !parent.containsKey(goal)) {
            int at = queue.poll();
            ArrayList<float[]> next = adj.get(at);
            if (next == null) continue;
            for (float[] e : next) {
                int to = (int) e[0];
                if (!parent.containsKey(to)) { parent.put(to, new float[] {at, e[1]}); queue.add(to); }
            }
        }
        if (!parent.containsKey(goal)) return null;
        ArrayList<Integer> nodes = new ArrayList<Integer>();
        ArrayList<Float> through = new ArrayList<Float>();
        for (int at = goal; at != -1; ) {
            float[] p = parent.get(at);
            nodes.add(0, at);
            if (p[0] != -1) through.add(0, p[1]);
            at = (int) p[0];
        }
        ArrayList<float[]> legs = new ArrayList<float[]>();
        for (int i = 0; i < nodes.size(); i++) {
            float from = i == 0 ? NECK : through.get(i - 1);
            float to = i == nodes.size() - 1 ? BOTTOM : through.get(i);
            legs.add(new float[] {nodes.get(i) / 32, from, to});
        }
        return legs;
    }

    private static void edge(HashMap<Integer, ArrayList<float[]>> adj, int u, int v, float c) {
        if (!adj.containsKey(u)) adj.put(u, new ArrayList<float[]>());
        adj.get(u).add(new float[] {v, c});
    }

    /**
     * The least turning that leads water from the neck to the bottom, in
     * radians, or -1 if no turning does.
     */
    static float rotationNeed(float[][] gaps, float[][] spokes, float[] radii, float[] half) {
        float[] plan = solve(gaps, spokes, radii, half);
        return plan == null ? -1 : plan[0];
    }

    /**
     * The cheapest chain of gaps, as a plan: the total turning first, then
     * the angles the flask stands at, one after another, ending where water
     * leaves the bottom. Water in a sector lies where the sector is lowest;
     * to pass a gap, the flask turns until the gap is underneath it, without
     * carrying the low point across a wall on the way. The flask starts
     * upright with the water caught in the neck, and the first turn goes the
     * way that keeps the neck from facing down, or the water would pour back
     * out of it. Checked against the physics with a full flask of rain.
     */
    static float[] solve(float[][] gaps, float[][] spokes, float[] radii, float[] half) {
        int n = radii.length;
        Bands bands = new Bands(spokes, n);
        int count = 0;
        for (int k = 0; k < n; k++) count += gaps[k].length;
        int size = count * 2;
        int[] band = new int[size], id = new int[size];
        float[] at = new float[size];
        int i = 0;
        for (int k = 0; k < n; k++) {
            for (float c : gaps[k]) {
                band[i] = k; id[i] = bands.id(k, c); at[i] = c; i++;
                band[i] = k + 1; id[i] = bands.id(k + 1, c); at[i] = c; i++;
            }
        }
        float[] dist = new float[size];
        int[] prev = new int[size];
        boolean[] done = new boolean[size];
        Arrays.fill(dist, Float.MAX_VALUE);
        Arrays.fill(prev, -1);
        int start = bands.id(0, NECK), goal = bands.id(0, BOTTOM);
        for (int j = 0; j < size; j++) {
            if (id[j] != start) continue;
            float d = Math.abs(clearOfNeck(BOTTOM, at[j]));
            if (d < dist[j ^ 1]) dist[j ^ 1] = d;
        }
        float best = Float.MAX_VALUE;
        int last = -1;
        while (true) {
            int j = -1;
            for (int q = 0; q < size; q++) if (!done[q] && dist[q] < Float.MAX_VALUE && (j < 0 || dist[q] < dist[j])) j = q;
            if (j < 0) break;
            done[j] = true;
            if (id[j] == goal) {
                float total = dist[j] + bands.arc(0, at[j], BOTTOM);
                if (total < best) { best = total; last = j; }
            }
            for (int q = 0; q < size; q++) {
                if (q == j || id[q] != id[j]) continue;
                float nd = dist[j] + bands.arc(band[j], at[j], at[q]);
                if (nd < dist[q ^ 1]) { dist[q ^ 1] = nd; prev[q ^ 1] = j; }
            }
        }
        if (last < 0) return null;
        ArrayList<Integer> chain = new ArrayList<Integer>();
        for (int j = last; j != -1; j = prev[j]) chain.add(0, j);
        // the low point of the glass: where it starts, each gap in turn, the bottom
        float[] plan = new float[chain.size() + 3];
        plan[0] = best;
        float ang = 0, low = BOTTOM;
        plan[1] = 0;
        for (int c = 0; c < chain.size(); c++) {
            int j = chain.get(c);
            float step = c == 0 ? clearOfNeck(low, at[j]) : bands.signed(band[chain.get(c - 1)], low, at[j]);
            low = at[j];
            ang -= step;
            plan[c + 2] = ang;
        }
        ang -= bands.signed(0, low, BOTTOM);
        plan[plan.length - 1] = ang;
        return plan;
    }

    /** From p to c the way round that does not pass the neck; signed, positive when the angle grows. */
    private static float clearOfNeck(float p, float c) {
        float up = norm(c - p);
        return norm(NECK - p) < up ? -norm(p - c) : up;
    }

    /** A gap on ring k at the given angle, if it keeps clear of the gaps already there. */
    private static boolean gap(ArrayList<ArrayList<Float>> gaps, float[] half, int k, float c) {
        ArrayList<Float> list = gaps.get(k);
        for (float o : list) if (adist(c, o) <= 2 * half[k] + 0.6f) return false;
        list.add(norm(c));
        return true;
    }

    private static boolean place(ArrayList<ArrayList<Float>> gaps, float[] half, int k, Random rnd, int tries) {
        for (int q = 0; q < tries; q++) if (gap(gaps, half, k, rnd.nextFloat() * TAU)) return true;
        return false;
    }

    /** One wall in band b: near the given angle if spread is positive, anywhere otherwise. */
    private static boolean wall(ArrayList<ArrayList<Float>> spokes, ArrayList<ArrayList<Float>> gaps,
                                float[] half, int n, int b, Random rnd, float around, float spread) {
        for (int q = 0; q < 60; q++) {
            float a = spread > 0 ? around + sym(rnd, spread) : rnd.nextFloat() * TAU;
            if (clear(spokes, gaps, half, n, b, a)) { spokes.get(b).add(norm(a)); return true; }
        }
        return false;
    }

    private static boolean clear(ArrayList<ArrayList<Float>> spokes, ArrayList<ArrayList<Float>> gaps,
                                 float[] half, int n, int b, float a) {
        for (float o : spokes.get(b)) if (adist(a, o) < 0.55f) return false;
        if (b == 0) {
            if (adist(a, NECK) < NECK_H + 0.3f || adist(a, BOTTOM) < BOTTOM_H + 0.3f) return false;
        } else {
            for (float c : gaps.get(b - 1)) if (adist(a, c) < half[b - 1] + 0.12f) return false;
        }
        if (b < n) for (float c : gaps.get(b)) if (adist(a, c) < half[b] + 0.12f) return false;
        return true;
    }

    /** Gaps on the shortest way from the neck to the bottom, or -1 if there is none. */
    private static int shortest(float[][] gaps, float[][] spokes, int n) {
        float[][] sorted = new float[n][];
        for (int b = 0; b < n; b++) {
            sorted[b] = spokes[b].clone();
            Arrays.sort(sorted[b]);
        }
        HashMap<String, ArrayList<String>> adj = new HashMap<String, ArrayList<String>>();
        for (int k = 0; k < n; k++) {
            for (float c : gaps[k]) link(adj, key(sorted, n, k, c), key(sorted, n, k + 1, c));
        }
        String start = key(sorted, n, 0, NECK), goal = key(sorted, n, 0, BOTTOM);
        if (start.equals(goal)) return -1;
        HashMap<String, Integer> dist = new HashMap<String, Integer>();
        dist.put(start, 0);
        ArrayDeque<String> queue = new ArrayDeque<String>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String at = queue.poll();
            ArrayList<String> next = adj.get(at);
            if (next == null) continue;
            for (String m : next) {
                if (!dist.containsKey(m)) { dist.put(m, dist.get(at) + 1); queue.add(m); }
            }
        }
        Integer d = dist.get(goal);
        return d == null ? -1 : d;
    }

    private static float[][] arrays(ArrayList<ArrayList<Float>> lists) {
        float[][] out = new float[lists.size()][];
        for (int i = 0; i < out.length; i++) {
            ArrayList<Float> l = lists.get(i);
            out[i] = new float[l.size()];
            for (int j = 0; j < out[i].length; j++) out[i][j] = l.get(j);
        }
        return out;
    }

    private static int sectorOf(float[] sorted, float th) {
        if (sorted.length == 0) return 0;
        float x = norm(th);
        int c = 0;
        for (float s : sorted) if (s <= x) c++;
        return c % sorted.length;
    }

    private static String key(float[][] sorted, int n, int band, float th) {
        return band == n ? n + ":0" : band + ":" + sectorOf(sorted[band], th);
    }

    private static void link(HashMap<String, ArrayList<String>> adj, String a, String b) {
        if (!adj.containsKey(a)) adj.put(a, new ArrayList<String>());
        if (!adj.containsKey(b)) adj.put(b, new ArrayList<String>());
        adj.get(a).add(b);
        adj.get(b).add(a);
    }
}
