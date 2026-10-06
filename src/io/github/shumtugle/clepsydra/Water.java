package io.github.shumtugle.clepsydra;

import java.util.Arrays;

/**
 * Everything that falls, as a few hundred round drops. The drops live on the
 * screen; the glass turns under them. Each step a drop is carried into the
 * frame of the glass, pushed out of the walls there, and its push and its
 * bounce are carried back. A moving wall lends the drop its own speed, so the
 * glass scoops water rather than slicing through it.
 *
 * Not everything is water. Snow falls slowly; a flake that meets water in the
 * glass freezes it, and the ice sticks to the glass and turns with it until
 * it melts. Lava boils the water it meets into steam, which rises and leaves.
 * Slime makes the water it meets thick and slow. Only water that came in
 * through the neck counts when it leaves through the bottom.
 */
final class Water {
    static final int VOLUMES = 10, DROPS = 30;
    static final int SKY = 0, INSIDE = 1, POURED = 2, LOST = 3, DEAD = 4;
    static final byte WATER = 0, SNOW = 1, ICE = 2, LAVA = 3, STEAM = 4, SLIME = 5;
    static final float BURST_GAP = 0.35f;
    /** How much water a seed takes before it sprouts (drops times seconds), and how fast it grows. */
    static final float WET = 2.5f, GROW_TIME = 1.2f;
    /** How fast a float rides its rail, in flask radii a second. */
    static final float FLOAT_SPEED = 0.7f;
    private static final int MAX = VOLUMES * DROPS;
    /** How many drops each kind falls in. */
    private static final int[] SIZE = {DROPS, 22, 0, 10, 0, 16};

    final float[] x = new float[MAX], y = new float[MAX];
    final float[] vx = new float[MAX], vy = new float[MAX];
    final byte[] state = new byte[MAX], kind = new byte[MAX];
    /** Set for water that came in through the bottom: it flows, but never scores. */
    final byte[] foreign = new byte[MAX];
    /** Set for water thickened by slime. */
    final byte[] thick = new byte[MAX];
    /** Where ice sits on the glass, in the glass's own frame. */
    private final float[] fx = new float[MAX], fy = new float[MAX];
    /** Seconds left: before snow or ice melts, before steam is gone. */
    final float[] life = new float[MAX];
    /** How much more a flake, a lava drop or a blob of slime can still do to water. */
    private final byte[] charge = new byte[MAX];
    /** A name for each drop that stays with it while the others come and go. */
    final int[] id = new int[MAX];
    private int named;
    int n;

    float cx, cy, r, pr, wt, d, g, vmax;
    float ang;
    Maze maze;

    float t;
    float lastExit = -99f;
    /** The last drops that left through the bottom: where and when, kept in a ring. */
    static final int SPLASHES = 48;
    final float[] splashX = new float[SPLASHES], splashY = new float[SPLASHES], splashT = new float[SPLASHES];
    int exits;
    int burst, flaskBest, totalOut;

    private int w, h;
    private float cell, gx0, gy0, ca = 1, sa = 0, omega;
    private int gw, gh;
    private int[] head = new int[0];
    private final int[] next = new int[MAX], ci = new int[MAX], cj = new int[MAX];
    private final float[] pushX = new float[MAX], pushY = new float[MAX];

    void size(int w, int h, float cx, float cy, float r) {
        if (this.r > 0) {
            float k = r / this.r;
            for (int i = 0; i < n; i++) {
                x[i] = cx + (x[i] - this.cx) * k;
                y[i] = cy + (y[i] - this.cy) * k;
                vx[i] *= k;
                vy[i] *= k;
                fx[i] *= k;
                fy[i] *= k;
            }
        }
        this.w = w; this.h = h;
        this.cx = cx; this.cy = cy; this.r = r;
        pr = r * 0.026f;
        wt = r * 0.018f;
        d = pr + wt;
        g = r * 5.5f;
        vmax = r * 5f;
        cell = 2 * pr;
        gx0 = -80;
        gy0 = -h;
        gw = (int) ((w + 160) / cell) + 1;
        gh = (int) ((2 * h + 160) / cell) + 1;
        head = new int[gw * gh];
    }

    void reset(Maze m) {
        maze = m;
        n = 0;
        t = 0;
        ang = 0;
        lastExit = -99f;
        burst = flaskBest = totalOut = 0;
        Arrays.fill(splashT, -99f);
    }

    /** One volume of whatever falls, a round blob just above the top of the screen. */
    void spawn(float x0, byte what) {
        int count = SIZE[what];
        float c = pr * (what == SNOW ? 1.6f : 1.15f);
        float blob = c * (float) Math.sqrt(count);
        float y0 = -blob - pr * 2;
        for (int i = 0; i < count && n < MAX; i++) {
            float rr = c * (float) Math.sqrt(i + 0.5f), a = i * 2.39996f;
            x[n] = x0 + (float) Math.cos(a) * rr;
            y[n] = y0 + (float) Math.sin(a) * rr;
            vx[n] = 0;
            vy[n] = r * (what == SNOW ? 0.2f : 0.5f);
            state[n] = SKY;
            kind[n] = what;
            foreign[n] = 0;
            thick[n] = 0;
            life[n] = 0;
            charge[n] = (byte) (what == SNOW ? 1 : what == LAVA ? 2 : what == SLIME ? 5 : 0);
            id[n] = named++;
            n++;
        }
    }

    /** Anything still in the air, not yet in the flask or past it. */
    boolean falling() {
        for (int i = 0; i < n; i++) if (state[i] == SKY) return true;
        return false;
    }

    /** Anything that can still move in the flask: falling, or held and not frozen. */
    boolean live() {
        for (int i = 0; i < n; i++) {
            if (state[i] == SKY) return true;
            if (state[i] == INSIDE && kind[i] != ICE && kind[i] != STEAM) return true;
        }
        return false;
    }

    /** The flask flips over like a coin: what is inside flips with it, the sky does not. */
    void mirror() {
        for (int i = 0; i < n; i++) {
            if (state[i] != INSIDE) continue;
            x[i] = 2 * cx - x[i];
            vx[i] = -vx[i];
            fx[i] = -fx[i];
        }
    }

    void step(float dt, float rot) {
        ang += rot;
        omega = rot / dt;
        ca = (float) Math.cos(ang);
        sa = (float) Math.sin(ang);
        float vmax2 = vmax * vmax;
        for (int i = 0; i < n; i++) {
            if (kind[i] == ICE) {
                // ice is stuck to the glass and goes where the glass goes
                x[i] = cx + ca * fx[i] - sa * fy[i];
                y[i] = cy + sa * fx[i] + ca * fy[i];
                vx[i] = -omega * (y[i] - cy);
                vy[i] = omega * (x[i] - cx);
                continue;
            }
            float fall = g, drag = 0;
            switch (kind[i]) {
                case SNOW:
                    fall = g * 0.3f;
                    drag = 1.6f;
                    vx[i] += (float) Math.sin(t * 2.7f + i * 1.7f) * r * 0.9f * dt;
                    break;
                case LAVA:
                    fall = g * 1.15f;
                    break;
                case STEAM:
                    fall = -g * 0.35f;
                    drag = 1.2f;
                    break;
                case SLIME:
                    fall = g * 0.85f;
                    drag = 2f;
                    break;
                default:
                    if (thick[i] != 0) { fall = g * 0.8f; drag = 2.5f; }
                    break;
            }
            vy[i] += fall * dt;
            if (drag > 0) {
                float k = 1 - drag * dt;
                vx[i] *= k;
                vy[i] *= k;
            }
            float v2 = vx[i] * vx[i] + vy[i] * vy[i];
            if (v2 > vmax2) {
                float k = vmax / (float) Math.sqrt(v2);
                vx[i] *= k;
                vy[i] *= k;
            }
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
        }
        separate();
        settle();
        for (int i = 0; i < n; i++) {
            if (state[i] <= INSIDE && kind[i] != ICE) collide(i, ca, sa, omega);
            classify(i, ca, sa);
            age(i, dt);
        }
        seeds(dt, ca, sa);
        floats(dt, ca, sa);
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (state[i] != DEAD && y[i] < h + 60 && y[i] > -h + 20 && x[i] > -60 && x[i] < w + 60) {
                if (m != i) move(i, m);
                m++;
            }
        }
        n = m;
    }

    private void move(int from, int to) {
        x[to] = x[from]; y[to] = y[from];
        vx[to] = vx[from]; vy[to] = vy[from];
        state[to] = state[from]; kind[to] = kind[from];
        foreign[to] = foreign[from]; thick[to] = thick[from];
        fx[to] = fx[from]; fy[to] = fy[from];
        life[to] = life[from]; charge[to] = charge[from];
        id[to] = id[from];
    }

    /** Snow and ice melt in their time; steam thins away. */
    private void age(int i, float dt) {
        if (state[i] != INSIDE) return;
        byte k = kind[i];
        if (k != SNOW && k != ICE && k != STEAM) return;
        if (life[i] <= 0) life[i] = k == SNOW ? 12f : k == ICE ? 25f + (i % 7) : 3f;
        life[i] -= dt;
        if (life[i] > 0) return;
        if (k == STEAM) {
            state[i] = DEAD;
        } else {
            // melt water is the flask's own, but it never came in through the neck as water
            if (k == SNOW) foreign[i] = 1;
            kind[i] = WATER;
            life[i] = 0;
        }
    }

    /**
     * A seed counts the water that washes over it. Enough of it, and it
     * sprouts; the flower takes a little over a second to cross the corridor.
     */
    private void seeds(float dt, float ca, float sa) {
        for (Maze.Obstacle o : maze.obstacles) {
            if (o.kind != Maze.SEED) continue;
            if (o.sprouting) {
                o.grow = Math.min(1f, o.grow + dt / GROW_TIME);
                continue;
            }
            float px = o.cs * o.r0 * r, py = o.sn * o.r0 * r, near = 3 * pr + wt, near2 = near * near;
            int wetting = 0;
            for (int i = 0; i < n; i++) {
                if (state[i] != INSIDE || kind[i] != WATER) continue;
                float dx = x[i] - cx, dy = y[i] - cy;
                float lx = ca * dx + sa * dy - px, ly = -sa * dx + ca * dy - py;
                if (lx * lx + ly * ly < near2) wetting++;
            }
            o.wet += wetting * dt;
            if (o.wet > WET) o.sprouting = true;
        }
    }

    /**
     * The pushes between drops are gathered first and done together, each
     * drop's capped at half its radius a step. Done one pair at a time and
     * uncapped, a deep pool pressed its lowest drops clean through the glass
     * into sealed chambers: under enough weight a drop went past the middle
     * of a wall, and the wall then pushed it out on the far side.
     */
    private void settle() {
        float cap = 0.5f * pr, cap2 = cap * cap;
        for (int i = 0; i < n; i++) {
            float m2 = pushX[i] * pushX[i] + pushY[i] * pushY[i];
            if (m2 > cap2) {
                float k = cap / (float) Math.sqrt(m2);
                pushX[i] *= k;
                pushY[i] *= k;
            }
            x[i] += pushX[i];
            y[i] += pushY[i];
        }
    }

    /**
     * A float feels the water round it. With enough water about, it rises
     * along its rail to the upper end, whichever end is upper as the flask
     * stands now; with too little, it sinks to the lower end. It moves at an
     * unhurried pace, and on a rail lying level it stays where it is.
     */
    private void floats(float dt, float ca, float sa) {
        for (Maze.Obstacle o : maze.obstacles) {
            if (o.kind != Maze.FLOAT) continue;
            float px = o.cs * o.s * r, py = o.sn * o.s * r;
            float near = o.size * r + 2.5f * pr, near2 = near * near;
            int round = 0;
            for (int i = 0; i < n; i++) {
                if (state[i] != INSIDE || kind[i] == ICE || kind[i] == STEAM) continue;
                float dx = x[i] - cx, dy = y[i] - cy;
                float lx = ca * dx + sa * dy - px, ly = -sa * dx + ca * dy - py;
                if (lx * lx + ly * ly < near2) round++;
            }
            if (round >= 7) o.up = true;
            else if (round <= 2) o.up = false;
            // how much the rail points down, outward: sine of where it points on the screen
            float fall = (float) Math.sin(o.a + ang);
            if (Math.abs(fall) < 0.15f) continue;
            float outer = Math.max(o.r0, o.r1), inner = Math.min(o.r0, o.r1);
            boolean outwardDown = fall > 0;
            float to = o.up == outwardDown ? inner : outer;
            float step = FLOAT_SPEED * dt;
            o.s = o.s < to ? Math.min(to, o.s + step) : Math.max(to, o.s - step);
        }
    }

    /** Drops do not overlap, and drops of different kinds act on each other when they touch. */
    private void separate() {
        Arrays.fill(head, -1);
        for (int i = 0; i < n; i++) {
            int a = clamp((int) ((x[i] - gx0) / cell), 0, gw - 1);
            int b = clamp((int) ((y[i] - gy0) / cell), 0, gh - 1);
            ci[i] = a;
            cj[i] = b;
            int k = b * gw + a;
            next[i] = head[k];
            head[k] = i;
        }
        Arrays.fill(pushX, 0, n, 0f);
        Arrays.fill(pushY, 0, n, 0f);
        float min = 2 * pr, min2 = min * min, touch = 2.3f * pr, touch2 = touch * touch;
        for (int i = 0; i < n; i++) {
            for (int oy = -1; oy <= 1; oy++) {
                int b = cj[i] + oy;
                if (b < 0 || b >= gh) continue;
                for (int ox = -1; ox <= 1; ox++) {
                    int a = ci[i] + ox;
                    if (a < 0 || a >= gw) continue;
                    for (int j = head[b * gw + a]; j != -1; j = next[j]) {
                        if (j <= i) continue;
                        if (state[i] == DEAD || state[j] == DEAD) continue;
                        if (kind[i] == STEAM || kind[j] == STEAM) continue;
                        float dx = x[j] - x[i], dy = y[j] - y[i], d2 = dx * dx + dy * dy;
                        if (d2 >= touch2 || d2 < 1e-9f) continue;
                        if (kind[i] != kind[j] || kind[i] == ICE) react(i, j);
                        if (d2 >= min2 || state[i] == DEAD || state[j] == DEAD) continue;
                        if (kind[i] == STEAM || kind[j] == STEAM) continue;
                        boolean iceI = kind[i] == ICE, iceJ = kind[j] == ICE;
                        if (iceI && iceJ) continue;
                        float dd = (float) Math.sqrt(d2), nx = dx / dd, ny = dy / dd;
                        float push = (min - dd) * 0.4f;
                        // ice does not give way: the other drop takes the whole push
                        float pi = iceI ? 0 : iceJ ? 2 * push : push;
                        float pj = iceJ ? 0 : iceI ? 2 * push : push;
                        pushX[i] -= nx * pi; pushY[i] -= ny * pi;
                        pushX[j] += nx * pj; pushY[j] += ny * pj;
                        float vn = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny;
                        if (vn < 0) {
                            float imp = vn * 0.45f;
                            if (!iceI) { vx[i] += nx * imp * (iceJ ? 2 : 1); vy[i] += ny * imp * (iceJ ? 2 : 1); }
                            if (!iceJ) { vx[j] -= nx * imp * (iceI ? 2 : 1); vy[j] -= ny * imp * (iceI ? 2 : 1); }
                        }
                    }
                }
            }
        }
    }

    /** What happens when two different things touch inside the flask. */
    private void react(int i, int j) {
        if (state[i] != INSIDE || state[j] != INSIDE) return;
        byte a = kind[i], b = kind[j];
        if (a == WATER) { react(j, i); return; }
        // from here on, i is the active one when one of the two is water
        if (b == WATER) {
            if (a == SNOW || (a == ICE && charge[i] > 0)) {
                if (a == SNOW) freeze(i, (byte) 1, (byte) 1);
                charge[i]--;
                freeze(j, (byte) 0, foreign[j]);
            } else if (a == LAVA) {
                kind[j] = STEAM;
                life[j] = 3f;
                if (--charge[i] <= 0) state[i] = DEAD;
            } else if (a == SLIME && charge[i] > 0 && thick[j] == 0) {
                thick[j] = 1;
                charge[i]--;
            }
        } else if (a == LAVA && (b == ICE || b == SNOW)) {
            kind[j] = WATER;
            foreign[j] = 1;
            life[j] = 0;
            if (--charge[i] <= 0) state[i] = DEAD;
        } else if (b == LAVA && (a == ICE || a == SNOW)) {
            react(j, i);
        }
    }

    private void freeze(int i, byte power, byte stranger) {
        kind[i] = ICE;
        charge[i] = power;
        foreign[i] = stranger;
        life[i] = 0;
        float dx = x[i] - cx, dy = y[i] - cy;
        fx[i] = ca * dx + sa * dy;
        fy[i] = -sa * dx + ca * dy;
    }

    private void collide(int i, float ca, float sa, float omega) {
        float dx = x[i] - cx, dy = y[i] - cy;
        float lx = ca * dx + sa * dy, ly = -sa * dx + ca * dy;
        float lim = r * 1.35f;
        if (lx * lx + ly * ly > lim * lim) return;

        for (Maze.Ring ring : maze.rings) {
            float rk = ring.r * r;
            float rho = (float) Math.sqrt(lx * lx + ly * ly);
            if (Math.abs(rho - rk) > d + 1) continue;
            float th = (float) Math.atan2(ly, lx);
            if (!ring.open(th) && rho > 1e-6f) {
                float dist = rho - rk, s = dist >= 0 ? 1f : -1f, pen = d - Math.abs(dist);
                if (pen > 0) {
                    float nx = lx / rho * s, ny = ly / rho * s;
                    lx += nx * pen;
                    ly += ny * pen;
                    contact(i, nx, ny, pen, ca, sa, omega);
                }
            }
            for (int c = 0; c < ring.capX.length; c++) {
                float ex = lx - ring.capX[c] * r, ey = ly - ring.capY[c] * r;
                float dd = (float) Math.sqrt(ex * ex + ey * ey);
                if (dd < d && dd > 1e-6f) {
                    float pen = d - dd, nx = ex / dd, ny = ey / dd;
                    lx += nx * pen;
                    ly += ny * pen;
                    contact(i, nx, ny, pen, ca, sa, omega);
                }
            }
        }
        for (Maze.Obstacle o : maze.obstacles) {
            float reach;
            if (o.kind == Maze.FLOAT) {
                // a float is round and solid; water goes round it
                float fx0 = o.cs * o.s * r, fy0 = o.sn * o.s * r;
                float ex = lx - fx0, ey = ly - fy0, dd = (float) Math.sqrt(ex * ex + ey * ey);
                float reachF = o.size * r + pr;
                if (dd < reachF && dd > 1e-6f) {
                    float pen = reachF - dd, nx = ex / dd, ny = ey / dd;
                    lx += nx * pen;
                    ly += ny * pen;
                    contact(i, nx, ny, pen, ca, sa, omega);
                }
                continue;
            }
            if (o.kind == Maze.BUBBLE) {
                // one round bubble, across a corridor or corking a gap
                if (!o.active) continue;
                float mid = o.mid() * r, bx = o.cs * mid, by = o.sn * mid;
                float ex = lx - bx, ey = ly - by, dd = (float) Math.sqrt(ex * ex + ey * ey);
                float reachB = o.size * r + pr;
                if (dd < reachB && dd > 1e-6f) {
                    float pen = reachB - dd, nx = ex / dd, ny = ey / dd;
                    lx += nx * pen;
                    ly += ny * pen;
                    contact(i, nx, ny, pen, ca, sa, omega);
                }
                continue;
            } else {
                if (o.grow <= 0) continue;
                reach = o.grow;
            }
            float ax = o.cs * o.r0 * r, ay = o.sn * o.r0 * r;
            float len = (o.r1 - o.r0) * reach * r;
            float sx = o.cs * len, sy = o.sn * len;
            float u = ((lx - ax) * sx + (ly - ay) * sy) / (sx * sx + sy * sy + 1e-6f);
            u = u < 0 ? 0 : u > 1 ? 1 : u;
            float ex = lx - (ax + sx * u), ey = ly - (ay + sy * u);
            float dd = (float) Math.sqrt(ex * ex + ey * ey), reachD = d;
            if (dd < reachD && dd > 1e-6f) {
                float pen = reachD - dd, nx = ex / dd, ny = ey / dd;
                lx += nx * pen;
                ly += ny * pen;
                contact(i, nx, ny, pen, ca, sa, omega);
            }
        }
        float[] s = maze.segs;
        for (int k = 0; k < s.length; k += 4) {
            float ax = s[k] * r, ay = s[k + 1] * r, sx = s[k + 2] * r - ax, sy = s[k + 3] * r - ay;
            float u = ((lx - ax) * sx + (ly - ay) * sy) / (sx * sx + sy * sy);
            u = u < 0 ? 0 : u > 1 ? 1 : u;
            float ex = lx - (ax + sx * u), ey = ly - (ay + sy * u);
            float dd = (float) Math.sqrt(ex * ex + ey * ey);
            if (dd < d && dd > 1e-6f) {
                float pen = d - dd, nx = ex / dd, ny = ey / dd;
                lx += nx * pen;
                ly += ny * pen;
                contact(i, nx, ny, pen, ca, sa, omega);
            }
        }
    }

    /** The push and the bounce, given in the frame of the glass, done on the screen. */
    private void contact(int i, float nlx, float nly, float pen, float ca, float sa, float omega) {
        float nx = ca * nlx - sa * nly, ny = sa * nlx + ca * nly;
        x[i] += nx * pen;
        y[i] += ny * pen;
        float wvx = -omega * (y[i] - cy), wvy = omega * (x[i] - cx);
        float rx = vx[i] - wvx, ry = vy[i] - wvy;
        float vn = rx * nx + ry * ny;
        if (vn < 0) {
            // thick water and slime cling to the glass
            float grip = thick[i] != 0 || kind[i] == SLIME ? 0.3f : 0.04f;
            rx -= nx * vn * 1.08f;
            ry -= ny * vn * 1.08f;
            float vn2 = rx * nx + ry * ny;
            rx -= (rx - nx * vn2) * grip;
            ry -= (ry - ny * vn2) * grip;
        }
        vx[i] = rx + wvx;
        vy[i] = ry + wvy;
    }

    /** Where a drop is now: still falling, inside, out through the bottom, or lost. */
    private void classify(int i, float ca, float sa) {
        if (kind[i] == ICE || state[i] == DEAD) return;
        float dx = x[i] - cx, dy = y[i] - cy;
        float lx = ca * dx + sa * dy, ly = -sa * dx + ca * dy;
        float rho = (float) Math.sqrt(lx * lx + ly * ly);
        if (state[i] == SKY) {
            if (rho < r * 0.97f) {
                state[i] = INSIDE;
                foreign[i] = (byte) (ly > 0 ? 1 : 0);
            }
            return;
        }
        if (state[i] != INSIDE) return;
        boolean inTube = ly < 0 && Math.abs(lx) < r * (float) Math.sin(Maze.NECK_H) + pr
                && rho < r * Maze.NECK_LEN + 2 * pr;
        boolean inLips = ly > 0 && Math.abs(lx) < r * (float) Math.sin(Maze.BOTTOM_H) + pr
                && rho < r * (Maze.LIP_LEN + 0.04f);
        // past the glass and not in the neck or between the lips: it has left the flask
        if (rho > r + d && !inTube && !inLips) {
            float th = (float) Math.atan2(ly, lx);
            if (Maze.adist(th, Maze.BOTTOM) < Maze.BOTTOM_H + 0.3f && foreign[i] == 0 && kind[i] == WATER) {
                state[i] = POURED;
                poured(i);
            } else {
                state[i] = LOST;
            }
        }
    }

    /** One pour is every drop that leaves the bottom with no pause longer than a blink. */
    private void poured(int i) {
        // where it left, for the splash drawn there
        int k = exits++ % SPLASHES;
        splashX[k] = x[i];
        splashY[k] = y[i];
        splashT[k] = t;
        burst = (t - lastExit < BURST_GAP) ? burst + 1 : 1;
        lastExit = t;
        totalOut++;
        if (burst > flaskBest) flaskBest = burst;
    }

    private static int clamp(int v, int a, int b) {
        return v < a ? a : v > b ? b : v;
    }
}
