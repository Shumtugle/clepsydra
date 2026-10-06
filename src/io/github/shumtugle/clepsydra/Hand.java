package io.github.shumtugle.clepsydra;

/**
 * The hand that knows the way. It keeps the neck up while the rain falls,
 * leans it aside while snow or lava falls at the neck, pops the bubbles
 * that shut the way and keeps the rest, and then walks the maze's own plan, one stop after another, with
 * a pause at each for the water to pass. If nothing has come out by the end
 * (ice in the way, thick water slow on its way), it waits and walks the plan
 * once more from upright, twice at most.
 *
 * The same hand tries every flask before it is handed out, and plays a flask
 * from the start, at the game's own pace, when asked to show the way.
 */
final class Hand {
    /** The fastest it turns the flask, per small step. */
    static final float TURN = 0.008f;

    private int step = 1, tries;
    private float hold, end = -1f;
    private boolean popped;
    private final float lean;

    Hand(Maze m) {
        lean = m.plan.length > 1 && m.plan[1] < 0 ? -1f : 1f;
    }

    /** The plan has been walked to its end at least once. */
    boolean walked() {
        return end > 0;
    }

    float end() {
        return end;
    }

    /** Whether the bubbles that have to go are to be popped now; true once. */
    boolean pop(boolean rained) {
        if (!rained || popped) return false;
        popped = true;
        return true;
    }

    /** Where the flask should stand now. */
    float goal(Water w, boolean rained, float dt) {
        Maze m = w.maze;
        if (!rained) return danger(w) ? lean * 0.6f : 0f;
        if (step >= m.plan.length) {
            if (w.totalOut == 0 && w.t - end > 6f && tries < 2) {
                tries++;
                step = 0;
                end = -1f;
            } else {
                return w.ang;
            }
        }
        float goal = m.plan[step];
        float near = m.wild ? 0.08f : 0.002f;
        // thick water needs longer at each gap
        float pause = m.weather == Maze.SLIME ? 3.5f : 1.6f;
        if (Math.abs(goal - w.ang) < near) {
            hold += dt;
            if (hold > pause) {
                step++;
                hold = 0;
                if (step >= m.plan.length) end = w.t;
            }
        }
        return goal;
    }

    /** Snow or lava in the air, right above the neck. */
    private static boolean danger(Water w) {
        for (int i = 0; i < w.n; i++) {
            if (w.state[i] != Water.SKY) continue;
            if (w.kind[i] != Water.SNOW && w.kind[i] != Water.LAVA) continue;
            if (w.y[i] < w.cy - w.r * 0.9f && Math.abs(w.x[i] - w.cx) < w.r * 0.6f) return true;
        }
        return false;
    }

    /**
     * Plays a flask from the start, out of sight, by the flask's own rules,
     * and says whether any water left through the bottom. Rain falls as in
     * the game; the turning allowed and the time are the flask's own.
     */
    static boolean tries(Maze made, java.util.Random rnd) {
        Maze m = made.fresh();
        Water w = new Water();
        w.size(1080, 2400, 540, 1272, 453);
        w.reset(m);
        Hand hand = new Hand(m);
        float dt = 1f / 240, lastSpawn = -99f;
        float left = m.rule == Maze.TURNS ? m.turnAllowance() : Float.MAX_VALUE;
        float setAt = m.rule == Maze.TIME ? Maze.FIRST_SPAWN + m.timeAllowance() : Float.MAX_VALUE;
        int spawned = 0;
        boolean set = false;
        for (int k = 0; k < 240 * 120; k++) {
            w.t += dt;
            if (spawned < Water.VOLUMES && w.t >= Maze.FIRST_SPAWN + spawned * Maze.SPAWN_EVERY) {
                w.spawn(w.cx + (rnd.nextFloat() * 0.2f - 0.1f) * w.r, m.falls[spawned]);
                spawned++;
                lastSpawn = w.t;
            }
            boolean rained = spawned == Water.VOLUMES && w.t > lastSpawn + 3f;
            if (hand.pop(rained)) popAll(w.maze, m.pops);
            float rot = hand.goal(w, rained, dt) - w.ang;
            rot = rot < -TURN ? -TURN : rot > TURN ? TURN : rot;
            if (!set && w.t >= setAt) set = true;
            if (!set && Math.abs(rot) > left) { rot = Math.signum(rot) * left; }
            if (!set && left != Float.MAX_VALUE) { left -= Math.abs(rot); if (left <= 1e-4f) set = true; }
            if (set) rot = 0;
            w.step(dt, rot);
            if (w.totalOut > 0) return true;
            if (hand.walked() && w.t - hand.end() > 8f && hand.tries >= 2) return false;
            if (set && w.t - Math.max(lastSpawn, w.lastExit) > 10f) return false;
        }
        return false;
    }

    /** Pops every bubble marked to go. */
    static void popAll(Maze m, boolean[] pops) {
        for (int i = 0; i < pops.length && i < m.obstacles.length; i++) if (pops[i]) m.obstacles[i].active = false;
    }

    /**
     * Plays a flask through, out of sight, with the given bubbles popped once
     * the rain is over, and gives back how much water left through the
     * bottom and the longest pour. Nothing holds the hand back here, neither
     * a count of turns nor a clock: it measures what the bubbles do.
     */
    static int[] play(Maze made, boolean[] pops, java.util.Random rnd) {
        Maze m = made.fresh();
        Water w = new Water();
        w.size(1080, 2400, 540, 1272, 453);
        w.reset(m);
        Hand hand = new Hand(m);
        float dt = 1f / 240, lastSpawn = -99f;
        int spawned = 0;
        for (int k = 0; k < 240 * 100; k++) {
            w.t += dt;
            if (spawned < Water.VOLUMES && w.t >= Maze.FIRST_SPAWN + spawned * Maze.SPAWN_EVERY) {
                w.spawn(w.cx + (rnd.nextFloat() * 0.2f - 0.1f) * w.r, m.falls[spawned]);
                spawned++;
                lastSpawn = w.t;
            }
            boolean rained = spawned == Water.VOLUMES && w.t > lastSpawn + 3f;
            if (hand.pop(rained)) popAll(w.maze, pops);
            float rot = hand.goal(w, rained, dt) - w.ang;
            rot = rot < -TURN ? -TURN : rot > TURN ? TURN : rot;
            w.step(dt, rot);
            if (hand.walked() && w.t - hand.end() > 6f) break;
        }
        return new int[] {w.totalOut, w.flaskBest};
    }
}
