package io.github.shumtugle.clepsydra;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Choreographer;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import java.util.Random;

/**
 * The whole game on one view: the flask in the middle, two numbers and the
 * flask's difficulty at the top, a thin line at the bottom, and a card
 * between flasks. A flask is over when its water has fallen and nothing has
 * poured for a while; even then it can still be turned and emptied under the
 * card. Some flasks turn only so far, or only for so long, and then set
 * fast; the thin line then shows what is left of the turning or the time.
 */
final class Board extends View {
    private static final int MENU = 0, PLAY = 1, OVER = 2;
    private static final float SPAWN_EVERY = 2.8f, FIRST_SPAWN = 1f, IDLE = 6f;
    /** The most the glass turns in one small step: more, and a wall could pass through a drop. */
    private static final float MAX_ROT = 0.018f;
    private static final int SUB = 4;
    /**
     * A flask that turns by itself: the pull of its drift, the rubber of a
     * holding finger and its damping, how far the glass may lag the finger
     * before it slips away, and how a loose flask slows. The finger always
     * wins against the drift if it holds steady: the spring at the slip is
     * more than twice the strongest pull.
     */
    private static final float DRIFT = 8f, SPRING = 30f, HOLD_DAMP = 9f, SLIP = 0.7f, FREE_DAMP = 2.6f;
    /** How long a coin flip takes. */
    private static final float FLIP = 0.7f;

    /** Touch targets on the card. */
    private static final int NONE = -1, ACTION = 0, SOUND = 1, TONE = 2, LANGUAGE = 3, SKIP = 4,
            ENGLISH = 5, LOAD = 6, FINNISH = 7, RUSSIAN = 8, SAVE = 9, HELP = 10;
    /** How long one breath of the flask takes. */
    private static final float BREATH = 1.2f;

    /**
     * Three tones, each by day and by night: ground top and bottom, ink, quiet
     * ink, glass, glass light, glass tint, water, foreign water, card.
     */
    private static final int[][][] TONES = {
            { // sea
                    {0xFFE6EDF0, 0xFFCFDBE1, 0xFF1B3040, 0xFF5A6F7E, 0x6B203E54, 0xF2FFFFFF,
                            0x61FFFFFF, 0xFF1D74B8, 0xBF606C76, 0xEBECF2F5},
                    {0xFF22303D, 0xFF121A23, 0xFFE3EDF3, 0xFF8EA3B2, 0x66C4E4F8, 0x8CFFFFFF,
                            0x0FAAD2EB, 0xFF4CB3E4, 0x8C96A0AA, 0xEB16202A},
            },
            { // amber
                    {0xFFF2E9DB, 0xFFE2D1B9, 0xFF3A2A1A, 0xFF7A6450, 0x6B5A3C1E, 0xF2FFFFFF,
                            0x61FFFFFF, 0xFFC0721A, 0xBF7A7068, 0xEBF7F0E5},
                    {0xFF2E241B, 0xFF17110C, 0xFFF2E6D6, 0xFFB09A82, 0x66F0D7B4, 0x8CFFFFFF,
                            0x0FF0D2AA, 0xFFE9A13B, 0x8CA0968C, 0xEB251D16},
            },
            { // moss
                    {0xFFE4ECE4, 0xFFCCD9CD, 0xFF1C3326, 0xFF5A7363, 0x6B1E4630, 0xF2FFFFFF,
                            0x61FFFFFF, 0xFF2A8765, 0xBF66706A, 0xEBEBF2EC},
                    {0xFF1E2B24, 0xFF101813, 0xFFE1EEE6, 0xFF8FA89A, 0x66BEE8D0, 0x8CFFFFFF,
                            0x0FAAE6C8, 0xFF4FC59A, 0x8C96A09A, 0xEB16211B},
            },
    };

    private final Water water = new Water();
    private final Random random = new Random();
    private final Course course = new Course(new Random());
    /** Flasks tried and ready, dealt from a thread of their own. */
    private final java.util.concurrent.ArrayBlockingQueue<Maze> ready =
            new java.util.concurrent.ArrayBlockingQueue<Maze>(2);
    private Thread dealer;
    private final Music music;
    private final SharedPreferences prefs;
    private final float dp;

    private int mode = MENU, record, spawned, lastTen, tone;
    private float lastSpawn = -99f, lastTurn = -99f, pendingRot, lastTouch;
    private boolean dragging, running;
    private int pressed = NONE;
    private long lastFrame;
    private int insetTop, insetBottom;
    private int shownBest = -1, shownOut = -1;
    /** What the rule leaves: turning in radians, or the moment the flask sets. */
    private float turnBudget, turnLeft, setAt;
    private boolean set;
    private int pops;
    private float downX, downY;
    private boolean moved;
    private StaticLayout noteLayout;
    private final TextPaint noteText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    /** Pearl and flower colours: rose, clover, daisy. */
    private static final int[] PEARLS = {0xFFD9485A, 0xFF4FA35A, 0xFFE2B23A};
    private float spin, target, phase1, phase2, flipAt, flipStart = -1f;
    private boolean grabbed, flipped;
    /** The flask as it was handed out, kept to be shown again from the start. */
    private Maze original;
    private boolean demo;
    private Hand hand;
    private float breathAt, breathStart = -1f;
    private boolean breathed;
    private final RectF skipKey = new RectF(), helpKey = new RectF();
    /** The page about the game: open or not, how far scrolled, and how fast it still glides. */
    private boolean helpOpen;
    private float helpScroll, helpGlide, helpDownY, helpDownScroll, helpLastY;
    private long helpLastAt;
    private boolean helpMoved;
    private final RectF helpSheet = new RectF(), helpClose = new RectF();
    private Help help;
    private Canvas canvasNow;
    /** The flask's own wall, neck and lips, drawn apart from the maze while it breathes. */
    private final Path rimPath = new Path(), speaker = new Path();
    private float tapX, tapY, tapAt = -99f;
    private final byte[] falls = new byte[Water.VOLUMES];
    private int waterDrops = Water.VOLUMES * Water.DROPS;
    private final Path thickP = new Path(), iceP = new Path(), snowP = new Path(),
            lavaP = new Path(), steamP = new Path(), slimeP = new Path();

    private int bgTop, bgBottom, ink, muted, glass, glassHi, tint, waterInk, murk, panel;
    private Shader background;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path mazePath = new Path(), clean = new Path(), murky = new Path();
    private final RectF oval = new RectF(), card = new RectF(), button = new RectF(), seg = new RectF();
    /** The keys along the top: sound, tone, language on the left, a new flask on the right. */
    private final RectF soundKey = new RectF(), toneKey = new RectF(), langKey = new RectF();
    private final RectF langEnglish = new RectF(), langLoad = new RectF(), langMenu = new RectF(),
            langSave = new RectF();
    private final RectF[] langBuilt = {new RectF(), new RectF()};
    /** The names the languages inside the game give themselves, read from their modules. */
    private final String[] builtNames = new String[Words.BUILT_IN.length];
    /** The languages inside the game, read from the game's own resources. */
    private final Words.Builtin builtin = new Words.Builtin() {
        @Override
        public String text(String code) {
            int res = code.equals("fi") ? R.raw.lang_fi : code.equals("ru") ? R.raw.lang_ru : 0;
            if (res == 0) return null;
            java.io.InputStream in = null;
            try {
                in = getResources().openRawResource(res);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), "UTF-8");
            } catch (java.io.IOException e) {
                return null;
            } finally {
                if (in != null) try { in.close(); } catch (java.io.IOException e) { /* nothing more to do */ }
            }
        }
    };
    private boolean langOpen;
    /** A word said briefly under the keys when one is pressed. */
    private String said = "";
    private float saidAt = -99f, recordAt = -99f;
    private int recordBefore;
    /** Light moving through the glass, and motes in the air behind it. */
    private Shader caustic;
    private final android.graphics.Matrix causticAt = new android.graphics.Matrix();
    private final float[] moteX = new float[16], moteY = new float[16], moteS = new float[16];
    private final Typeface serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
    private final Typeface serifItalic = Typeface.create(Typeface.SERIF, Typeface.ITALIC);
    private final Typeface sans = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private String cardTitle, cardBody, cardAction;
    private StaticLayout bodyLayout;
    private String laidBody;
    private int laidWidth = -1;

    private final Choreographer.FrameCallback tick = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            float dt = lastFrame == 0 ? 1f / 60 : Math.min((now - lastFrame) / 1e9f, 1f / 30);
            lastFrame = now;
            // while the page about the game is open, the flask waits
            if (helpOpen) glide(dt);
            else {
                update(dt);
                // the way through is shown at twice the pace
                if (demo) update(dt);
            }
            invalidate();
            if (running) Choreographer.getInstance().postFrameCallback(this);
        }
    };

    Board(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        prefs = context.getSharedPreferences("pour", Context.MODE_PRIVATE);
        record = prefs.getInt("record", 0);
        tone = Math.max(0, Math.min(TONES.length - 1, prefs.getInt("tone", 0)));
        music = new Music(context);
        music.choose(prefs.getInt("sound", Music.MUSIC) == Music.SILENCE ? Music.SILENCE : Music.MUSIC);
        Words.load(context, builtin);
        for (int i = 0; i < builtNames.length; i++) builtNames[i] = Words.nameIn(builtin.text(Words.BUILT_IN[i]));
        for (int i = 0; i < moteX.length; i++) {
            moteX[i] = random.nextFloat();
            moteY[i] = random.nextFloat();
            moteS[i] = 0.4f + random.nextFloat() * 0.8f;
        }
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        palette();
        original = course.next();
        water.reset(original.fresh());
        rules();
        deal();
        cardTitle = Words.s("app_name");
        cardBody = Words.s("intro");
        cardAction = Words.s("start");
        setHapticFeedbackEnabled(true);
    }

    void resume() {
        music.resume();
        if (running) return;
        running = true;
        lastFrame = 0;
        Choreographer.getInstance().postFrameCallback(tick);
    }

    void pause() {
        music.pause();
        running = false;
        Choreographer.getInstance().removeFrameCallback(tick);
    }

    void release() {
        music.release();
        if (dealer != null) dealer.interrupt();
    }

    /**
     * Every flask is played once out of sight before it is handed over, which
     * takes a moment; so the next two are made and tried ahead, on a thread
     * of their own, while the one in hand is played.
     */
    private void deal() {
        dealer = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                try {
                    while (true) ready.put(course.next());
                } catch (InterruptedException e) {
                    // the game is closing
                }
            }
        });
        dealer.setDaemon(true);
        dealer.start();
    }

    /** The next tried flask; if none is ready yet, a gentle one at once rather than a wait. */
    private Maze dealt() {
        Maze m = null;
        try {
            m = ready.poll(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // fall through to a gentle flask
        }
        return m != null ? m : Maze.make(1, Maze.SCATTER, 0, 0, Maze.FREE, random);
    }

    // ---------- colour ----------

    private boolean night() {
        int ui = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    private void palette() {
        int[] p = TONES[tone][night() ? 1 : 0];
        bgTop = p[0]; bgBottom = p[1]; ink = p[2]; muted = p[3]; glass = p[4];
        glassHi = p[5]; tint = p[6]; waterInk = p[7]; murk = p[8]; panel = p[9];
        boolean dark = night();
        background = null;
        noteLayout = null;
    }

    @Override
    protected void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        palette();
        invalidate();
    }

    // ---------- size ----------

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        int top = in.getSystemWindowInsetTop(), bottom = in.getSystemWindowInsetBottom();
        if (Build.VERSION.SDK_INT >= 28 && in.getDisplayCutout() != null) {
            top = Math.max(top, in.getDisplayCutout().getSafeInsetTop());
            bottom = Math.max(bottom, in.getDisplayCutout().getSafeInsetBottom());
        }
        insetTop = top;
        insetBottom = bottom;
        return in;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float r = Math.min(w * 0.42f, h * 0.27f);
        water.size(w, h, w / 2f, h * 0.56f, r);
        caustic = null;
        buildMaze();
        background = null;
    }

    private void buildMaze() {
        mazePath.rewind();
        rimPath.rewind();
        float r = water.r;
        Maze.Ring[] rings = water.maze.rings;
        for (int j = 0; j < rings.length; j++) {
            Maze.Ring ring = rings[j];
            float rr = ring.r * r;
            oval.set(-rr, -rr, rr, rr);
            for (int i = 0; i < ring.arcFrom.length; i++) {
                float from = (float) Math.toDegrees(ring.arcFrom[i]);
                float sweep = (float) Math.toDegrees(ring.arcTo[i] - ring.arcFrom[i]);
                mazePath.addArc(oval, from, sweep);
                if (j == 0) rimPath.addArc(oval, from, sweep);
            }
        }
        float[] s = water.maze.segs;
        for (int k = 0; k < s.length; k += 4) {
            mazePath.moveTo(s[k] * r, s[k + 1] * r);
            mazePath.lineTo(s[k + 2] * r, s[k + 3] * r);
            // the last four are the neck and the lips
            if (k >= s.length - 16) {
                rimPath.moveTo(s[k] * r, s[k + 1] * r);
                rimPath.lineTo(s[k + 2] * r, s[k + 3] * r);
            }
        }
    }

    // ---------- flasks ----------

    private void newFlask() {
        demo = false;
        original = dealt();
        water.reset(original.fresh());
        rules();
        buildMaze();
        begin(true);
    }

    /**
     * A flask poured poorly or not at all is played once more by the game
     * itself, from the start and twice as fast, to show one way through;
     * then a new flask comes. A tap skips it.
     */
    private void showTheWay() {
        demo = true;
        water.reset(original.fresh());
        hand = new Hand(water.maze);
        rules();
        buildMaze();
        begin(false);
        noteBody = Words.s("demo_note");
        noteLayout = null;
    }

    private boolean weak() {
        return water.flaskBest < 5 || water.totalOut < waterDrops / 10;
    }

    /** The flask in hand starts: the rain begins, and with the recorded tunes, a tune. */
    private void begin(boolean tune) {
        spawned = 0;
        lastSpawn = -99f;
        lastTurn = -99f;
        lastTen = 0;
        shownBest = shownOut = -1;
        spin = 0;
        grabbed = false;
        flipStart = -1f;
        flipAt = FIRST_SPAWN + 8 + random.nextFloat() * 6;
        breathStart = -1f;
        breathAt = FIRST_SPAWN + (Water.VOLUMES - 1) * SPAWN_EVERY + 4 + random.nextFloat() * 4;
        phase1 = random.nextFloat() * Maze.TAU;
        phase2 = random.nextFloat() * Maze.TAU;
        mode = PLAY;
    }

    /**
     * The flask's own rules. The turning allowed is the least the maze needs,
     * a third more, and a little over a quarter turn to spare; the time is as
     * long as the rain, and as much again for each gap on the way and for the
     * turning. Either way one drop can always be led out by a careful hand.
     */
    private void rules() {
        Maze m = water.maze;
        set = false;
        turnBudget = turnLeft = m.rule == Maze.TURNS ? m.turnAllowance() : 0;
        setAt = m.rule == Maze.TIME ? FIRST_SPAWN + m.timeAllowance() : 0;
        int plugs = 0, seedCount = 0, floatCount = 0;
        for (Maze.Obstacle o : m.obstacles) {
            if (o.kind == Maze.BUBBLE) plugs++;
            else if (o.kind == Maze.SEED) seedCount++;
            else floatCount++;
        }
        pops = plugs > 0 ? 1 : 0;

        System.arraycopy(m.falls, 0, falls, 0, Water.VOLUMES);
        waterDrops = 0;
        for (byte f : falls) if (f == Water.WATER) waterDrops += Water.DROPS;

        StringBuilder note = new StringBuilder();
        if (m.rule == Maze.TURNS) {
            note.append(Words.f("note_turns", String.format(java.util.Locale.US, "%.1f", turnBudget / Maze.TAU)));
        } else if (m.rule == Maze.TIME) {
            note.append(Words.f("note_time", Math.round(setAt - FIRST_SPAWN)));
        }
        String[] more = {
                plugs > 1 ? "note_bubble_two" : plugs > 0 ? "note_bubble" : null,
                seedCount > 0 ? "note_seeds" : null,
                m.breathing ? "note_breath" : null,
                floatCount > 0 ? "note_float" : null,
                m.wild ? "note_wild" : null,
                m.coin ? "note_coin" : null,
                m.weather == Maze.SNOW ? "note_snow" : m.weather == Maze.LAVA ? "note_lava"
                        : m.weather == Maze.SLIME ? "note_slime" : null,
        };
        for (String id : more) {
            if (id == null) continue;
            if (note.length() > 0) note.append('\n');
            note.append(Words.s(id));
        }
        noteBody = note.toString();
        noteLayout = null;
    }

    private String noteBody = "";

    /** The card after a flask; written again whenever the numbers under it change. */
    private void tell() {
        if (water.flaskBest == shownBest && water.totalOut == shownOut) return;
        shownBest = water.flaskBest;
        shownOut = water.totalOut;
        if (water.flaskBest > 0) {
            cardTitle = Words.f("over_title", water.flaskBest);
            cardBody = Words.f("over_text", water.totalOut, waterDrops);
        } else {
            cardTitle = Words.s("over_none");
            cardBody = Words.s("over_none_text");
        }
        cardAction = Words.s("again");
    }

    private void update(float dt) {
        float sub = dt / SUB;
        for (int k = 0; k < SUB; k++) {
            water.t += sub;
            if (mode == PLAY && spawned < Water.VOLUMES && water.t >= FIRST_SPAWN + spawned * SPAWN_EVERY) {
                water.spawn(water.cx + (random.nextFloat() * 0.2f - 0.1f) * water.r, falls[spawned]);
                spawned++;
                lastSpawn = water.t;
            }
            float rot;
            if (demo) {
                boolean rained = spawned == Water.VOLUMES && water.t > lastSpawn + 3f;
                int plug = hand.pop(water, rained);
                if (plug >= 0) popOut(water.maze.obstacles[plug]);
                float goal = hand.goal(water, rained, sub);
                if (water.maze.wild && !set) {
                    grabbed = true;
                    target = goal;
                    rot = wildStep(sub);
                } else {
                    rot = goal - water.ang;
                    rot = rot < -Hand.TURN ? -Hand.TURN : rot > Hand.TURN ? Hand.TURN : rot;
                }
            } else if (water.maze.wild && !set && mode != MENU) {
                rot = wildStep(sub);
            } else {
                rot = pendingRot < -MAX_ROT ? -MAX_ROT : pendingRot > MAX_ROT ? MAX_ROT : pendingRot;
                pendingRot -= rot;
            }
            if (!set && water.maze.rule == Maze.TIME && water.t >= setAt) setFast();
            if (!set && water.maze.rule == Maze.TURNS) {
                if (Math.abs(rot) > turnLeft) rot = Math.signum(rot) * turnLeft;
                turnLeft -= Math.abs(rot);
                if (turnLeft <= 1e-4f) setFast();
            }
            if (set) {
                rot = 0;
                pendingRot = 0;
                spin = 0;
            }
            water.step(sub, rot);
        }
        if (water.maze.coin && mode != MENU) flipping();
        if (water.maze.breathing && mode != MENU && !demo && spawned == Water.VOLUMES) breathing();
        if (mode == MENU) return;
        int ten = water.burst / 10;
        if (ten > lastTen) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        lastTen = ten;
        if (!demo && water.flaskBest > record) {
            if (recordAt < 0 || water.t - recordAt > 3f) {
                if (record > 0) {
                    recordAt = water.t;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            }
            record = water.flaskBest;
            prefs.edit().putInt("record", record).apply();
        }
        if (mode == PLAY && spawned == Water.VOLUMES) {
            // drops come to rest on the outside of the glass and never fall in:
            // a while after the last volume, the sky counts as empty
            boolean airborne = water.falling() && water.t - lastSpawn < 8f;
            if (!airborne) {
                boolean empty = !water.live();
                float since = Math.max(water.lastExit, lastSpawn);
                // a set flask cannot be turned, so touching it is not playing
                if (!set && !demo) since = Math.max(since, lastTurn);
                boolean still = water.t - since > IDLE;
                if (demo) {
                    boolean shown = hand.walked() && water.totalOut > 0 && water.t - Math.max(water.lastExit, hand.end()) > 2.5f;
                    if (shown || empty || (set && still) || water.t > 150f) newFlask();
                } else if (empty || still) {
                    if (weak()) showTheWay(); else mode = OVER;
                }
            }
        }
        if (mode == OVER) tell();
    }

    /**
     * One small step of a flask that turns by itself. The drift wanders, two
     * slow waves laid over each other; a holding finger is a rubber band to
     * the glass; pulled too far, the glass slips out and spins loose until
     * it is caught again.
     */
    private float wildStep(float sub) {
        float t = water.t;
        float torque = DRIFT * (0.65f * (float) Math.sin(0.45f * t + phase1)
                + 0.35f * (float) Math.sin(1.25f * t + phase2));
        if (grabbed) {
            float off = target - water.ang;
            if (Math.abs(off) > SLIP) {
                grabbed = false;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } else {
                torque += SPRING * off - HOLD_DAMP * spin;
            }
        }
        if (!grabbed) torque -= FREE_DAMP * spin;
        spin += torque * sub;
        float rot = spin * sub;
        if (Math.abs(rot) > MAX_ROT) {
            rot = Math.signum(rot) * MAX_ROT;
            spin = rot / sub;
        }
        return rot;
    }

    /**
     * A breath: the glass swells a little and its walls fade; at the deepest
     * point the maze inside becomes another of the same knottiness, and the
     * walls come back. The flask wall, the neck and the bottom stay; water
     * caught where a new wall stands is pushed aside.
     */
    private void breathing() {
        if (breathStart < 0 && water.t >= breathAt) {
            breathStart = water.t;
            breathed = false;
        }
        if (breathStart < 0) return;
        float k = (water.t - breathStart) / BREATH;
        if (k >= 0.5f && !breathed) {
            breathed = true;
            Maze old = water.maze;
            Maze m = Maze.make(old.tier, random.nextInt(Maze.STYLES), 0, 0, Maze.FREE, random);
            m.breathing = true;
            water.maze = m;
            buildMaze();
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        if (k >= 1) {
            breathStart = -1f;
            breathAt = water.t + 9 + random.nextFloat() * 5;
        }
    }

    /** Now and then the flask flips over like a coin; halfway, left and right change places. */
    private void flipping() {
        if (flipStart < 0 && water.t >= flipAt) {
            flipStart = water.t;
            flipped = false;
        }
        if (flipStart < 0) return;
        float k = (water.t - flipStart) / FLIP;
        if (k >= 0.5f && !flipped) {
            flipped = true;
            water.maze = water.maze.mirrored();
            water.ang = -water.ang;
            water.mirror();
            pendingRot = -pendingRot;
            spin = -spin;
            target = -target;
            buildMaze();
        }
        if (k >= 1) {
            flipStart = -1f;
            flipAt = water.t + 9 + random.nextFloat() * 7;
        }
    }

    /** A bubble popped, with a ring where the finger would have touched. */
    private void popOut(Maze.Obstacle o) {
        Water wa = water;
        o.active = false;
        o.gone = wa.t;
        pops = 0;
        float mid = (o.r0 + o.r1) / 2 * wa.r, a = o.a + wa.ang;
        tapX = wa.cx + (float) Math.cos(a) * mid;
        tapY = wa.cy + (float) Math.sin(a) * mid;
        tapAt = wa.t;
    }

    private void setFast() {
        set = true;
        turnLeft = 0;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    /** A tap on a bubble pops it, if the flask's one pop is still there. */
    private void pop(float x, float y) {
        if (pops <= 0) return;
        Water wa = water;
        for (Maze.Obstacle o : wa.maze.obstacles) {
            if (o.kind != Maze.BUBBLE || !o.active) continue;
            float mid = (o.r0 + o.r1) / 2 * wa.r, a = o.a + wa.ang;
            float px = wa.cx + (float) Math.cos(a) * mid, py = wa.cy + (float) Math.sin(a) * mid;
            float reach = Math.max(32 * dp, Math.abs(o.r1 - o.r0) * wa.r * 0.7f);
            if (Math.hypot(x - px, y - py) < reach) {
                popOut(o);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return;
            }
        }
    }

    // ---------- touch ----------

    private float angleAt(float x, float y) {
        return (float) Math.atan2(y - water.cy, x - water.cx);
    }

    /** Which key or card button lies under the finger. */
    private int target(float x, float y) {
        if (langOpen) {
            if (langEnglish.contains(x, y)) return ENGLISH;
            if (langBuilt[0].contains(x, y)) return FINNISH;
            if (langBuilt[1].contains(x, y)) return RUSSIAN;
            if (langLoad.contains(x, y)) return LOAD;
            if (langSave.contains(x, y)) return SAVE;
        }
        if (soundKey.contains(x, y)) return SOUND;
        if (toneKey.contains(x, y)) return TONE;
        if (langKey.contains(x, y)) return LANGUAGE;
        if (mode == PLAY && !skipKey.isEmpty() && skipKey.contains(x, y)) return SKIP;
        if (helpKey.contains(x, y)) return HELP;
        if (mode != PLAY && button.contains(x, y)) return ACTION;
        return NONE;
    }

    private void say(String words) {
        said = words;
        saidAt = water.t;
    }

    private void act(int what) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (what != LANGUAGE) langOpen = false;
        switch (what) {
            case HELP:
                helpOpen = true;
                helpScroll = 0;
                helpGlide = 0;
                help = null;
                break;
            case SKIP:
                newFlask();
                break;
            case ACTION:
                if (mode == MENU) begin(true); else newFlask();
                break;
            case SOUND: {
                int next = music.which() == Music.MUSIC ? Music.SILENCE : Music.MUSIC;
                music.choose(next);
                prefs.edit().putInt("sound", next).apply();
                say(Words.s(next == Music.MUSIC ? "music" : "silence"));
                break;
            }
            case TONE:
                tone = (tone + 1) % TONES.length;
                prefs.edit().putInt("tone", tone).apply();
                palette();
                say(Words.s(tone == 0 ? "sea" : tone == 1 ? "amber" : "moss"));
                break;
            case LANGUAGE:
                langOpen = !langOpen;
                break;
            case ENGLISH:
                Words.choose(getContext(), "en", builtin);
                relabel();
                break;
            case FINNISH:
            case RUSSIAN:
                Words.choose(getContext(), Words.BUILT_IN[what - FINNISH], builtin);
                relabel();
                break;
            case SAVE:
                if (getContext() instanceof Main) ((Main) getContext()).saveLanguage(Words.code());
                break;
            case LOAD:
                if (getContext() instanceof Main) ((Main) getContext()).pickLanguage();
                break;
            default:
                break;
        }
    }

    /** Whether the language went out to a file. */
    void saved(boolean ok) {
        say(Words.s(ok ? "language_saved" : "not_saved"));
    }

    /** The back key closes the page about the game, if it is open. */
    boolean back() {
        if (!helpOpen) return false;
        helpOpen = false;
        return true;
    }

    /** A language module came in from the picker: take it, or say it is not one. */
    void language(String text) {
        if (Words.read(getContext(), text)) relabel();
        else say(Words.s("not_a_language"));
    }

    /** Every word on the screen once more, in the language now spoken. */
    private void relabel() {
        rules();
        if (mode == MENU) {
            cardTitle = Words.s("app_name");
            cardBody = Words.s("intro");
            cardAction = Words.s("start");
        } else if (mode == OVER) {
            shownBest = shownOut = -1;
            tell();
        }
        if (demo) noteBody = Words.s("demo_note");
        help = null;
        laidBody = null;
        noteLayout = null;
        say(Words.active() ? Words.name() : Words.s("english"));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float ex = e.getX(), ey = e.getY();
        if (helpOpen) return helpTouch(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int on = target(ex, ey);
                if (demo && on != SOUND && on != TONE && on != HELP) return true;
                if (on != NONE) {
                    // keys are not glass: a touch on them never turns the flask
                    pressed = on;
                    return true;
                }
                if (langOpen) {
                    langOpen = false;
                    return true;
                }
                if (mode != PLAY && card.contains(ex, ey)) return true;
                dragging = true;
                moved = false;
                downX = ex;
                downY = ey;
                lastTouch = angleAt(ex, ey);
                // a flask that turns by itself is caught where it is
                grabbed = true;
                target = water.ang;
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging && !demo) {
                    if (Math.hypot(ex - downX, ey - downY) > 8 * dp) moved = true;
                    float a = angleAt(ex, ey);
                    float d = a - lastTouch;
                    if (d > Math.PI) d -= Maze.TAU;
                    else if (d < -Math.PI) d += Maze.TAU;
                    float far = (float) Math.hypot(ex - water.cx, ey - water.cy);
                    // near the centre the angle jumps with every pixel: ignore it there
                    if (far > water.r * 0.15f) {
                        if (water.maze.wild) target += d; else pendingRot += d;
                        if (!set) lastTurn = water.t;
                        // turning a flask that still holds water takes the card away again
                        if (mode == OVER && water.live() && !set) mode = PLAY;
                    }
                    lastTouch = a;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (pressed != NONE) {
                    if (target(ex, ey) == pressed) act(pressed);
                } else if (demo) {
                    newFlask();
                } else if (dragging && !moved) {
                    pop(ex, ey);
                }
                pressed = NONE;
                dragging = false;
                grabbed = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = NONE;
                dragging = false;
                grabbed = false;
                return true;
            default:
                return true;
        }
    }

    // ---------- drawing ----------

    @Override
    protected void onDraw(Canvas c) {
        canvasNow = c;
        int w = getWidth(), h = getHeight();
        Water wa = water;
        float cx = wa.cx, cy = wa.cy, r = wa.r;

        if (background == null) background = new LinearGradient(0, 0, 0, h, bgTop, bgBottom, Shader.TileMode.CLAMP);
        fill.setShader(background);
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        motes(c, w, h);

        // a coin flip: the flask narrows to its edge and opens again, mirrored
        float flat = 1f;
        if (flipStart >= 0) flat = Math.max(0.02f, Math.abs((float) Math.cos(Math.PI * (wa.t - flipStart) / FLIP)));
        // a breath: the glass swells a little and its inner walls fade and return
        float swell = 1f, walls = 1f;
        if (breathStart >= 0) {
            float k = Math.min(1f, (wa.t - breathStart) / BREATH);
            swell = 1 + 0.035f * (float) Math.sin(Math.PI * k);
            walls = Math.abs(1 - 2 * k);
        }
        c.save();
        c.scale(flat * swell, swell, cx, cy);

        fill.setColor(tint);
        c.drawCircle(cx, cy, r, fill);
        light(c);
        drops(c);

        c.save();
        c.translate(cx, cy);
        c.rotate((float) Math.toDegrees(wa.ang));
        line.setStrokeWidth(wa.wt * 2);
        line.setColor(glass);
        line.setAlpha((int) (Color.alpha(glass) * walls));
        c.drawPath(mazePath, line);
        line.setStrokeWidth(Math.max(1, wa.wt * 0.45f));
        line.setColor(glassHi);
        line.setAlpha((int) (Color.alpha(glassHi) * 0.55f * walls));
        c.drawPath(mazePath, line);
        if (walls < 1) {
            // the flask's own wall, the neck and the bottom never fade
            line.setStrokeWidth(wa.wt * 2);
            line.setColor(glass);
            c.drawPath(rimPath, line);
        }
        for (Maze.Obstacle o : wa.maze.obstacles) {
            if (o.kind == Maze.BUBBLE) bubble(c, o);
            else if (o.kind == Maze.SEED) flower(c, o);
            else bobber(c, o);
        }
        c.restore();
        c.restore();

        // where the hand that shows the way touched the bubble
        float since = wa.t - tapAt;
        if (since >= 0 && since < 0.8f) {
            line.setColor(waterInk);
            line.setAlpha((int) (200 * (1 - since / 0.8f)));
            line.setStrokeWidth(2.5f * dp);
            c.drawCircle(tapX, tapY, (14 + 30 * since) * dp, line);
        }

        // the light does not turn with the glass
        oval.set(cx - r * 0.92f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f);
        line.setColor(glassHi);
        line.setAlpha((int) (Color.alpha(glassHi) * 0.5f));
        line.setStrokeWidth(Math.max(2, r * 0.022f));
        c.drawArc(oval, 201.6f, 43.2f, false, line);
        line.setAlpha((int) (Color.alpha(glassHi) * 0.3f));
        line.setStrokeWidth(Math.max(1.5f, r * 0.012f));
        c.drawArc(oval, 21.6f, 18f, false, line);

        splashes(c);

        float age = wa.t - wa.lastExit;
        if (wa.burst > 0 && age < Water.BURST_GAP + 0.9f) {
            float o = age < Water.BURST_GAP ? 1f : Math.max(0f, 1 - (age - Water.BURST_GAP) / 0.9f);
            // every drop that leaves gives the number a little kick, and it glows
            float pop = 1 + 0.22f * (float) Math.exp(-age * 14);
            text.setTypeface(serifItalic);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(Math.min(96, 34 + wa.burst * 0.9f) * dp * pop);
            text.setColor(waterInk);
            text.setAlpha((int) (255 * o));
            text.setShadowLayer(14 * dp, 0, 0, (waterInk & 0x00FFFFFF) | ((int) (150 * o) << 24));
            float y = cy + r * 1.16f + 34 * dp;
            c.drawText(String.valueOf(wa.burst), cx, y, text);
            text.clearShadowLayer();
            float sinceRecord = wa.t - recordAt;
            if (recordAt >= 0 && sinceRecord < 2.2f) {
                text.setTypeface(sansMedium);
                text.setTextSize(13 * dp);
                text.setColor(ink);
                text.setAlpha((int) (255 * Math.min(1f, (2.2f - sinceRecord) * 2)));
                c.drawText(Words.s("new_record"), cx, y + 22 * dp, text);
            }
            text.setAlpha(255);
        }

        top(c, w);
        meter(c, w, h);
        if (mode != PLAY) card(c, w, h);
        if (langOpen) languages(c);
        if (helpOpen) helpPage(c, w, h);
    }

    /**
     * Everything that falls. Overlapping circles in one path fill as one body,
     * with no darker seams, so each kind is gathered into its own path.
     */
    private void drops(Canvas c) {
        Water wa = water;
        float rr = wa.pr * 1.65f;
        clean.rewind(); murky.rewind(); thickP.rewind(); iceP.rewind();
        snowP.rewind(); lavaP.rewind(); steamP.rewind(); slimeP.rewind();
        for (int i = 0; i < wa.n; i++) {
            Path p;
            float size = rr;
            switch (wa.kind[i]) {
                case Water.SNOW: p = snowP; size = wa.pr * 1.1f; break;
                case Water.ICE: p = iceP; size = wa.pr * 1.35f; break;
                case Water.LAVA: p = lavaP; size = wa.pr * 1.5f; break;
                case Water.STEAM: p = steamP; size = wa.pr * (1.6f + 1.4f * (1 - Math.min(1f, wa.life[i] / 3f))); break;
                case Water.SLIME: p = slimeP; break;
                default: p = wa.thick[i] != 0 ? thickP : wa.foreign[i] != 0 ? murky : clean; break;
            }
            p.addCircle(wa.x[i], wa.y[i], size, Path.Direction.CW);
        }
        fill.setColor(murk);
        c.drawPath(murky, fill);
        fill.setColor(waterInk);
        fill.setAlpha(219);
        c.drawPath(clean, fill);
        // a glint on every drop of clean water, up and to the left, where the light is
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(night() ? 24 : 40);
        float glint = wa.pr * 0.55f;
        for (int i = 0; i < wa.n; i++) {
            if (wa.kind[i] != Water.WATER || wa.foreign[i] != 0 || wa.thick[i] != 0) continue;
            c.drawCircle(wa.x[i] - wa.pr * 0.45f, wa.y[i] - wa.pr * 0.55f, glint, fill);
        }
        fill.setColor(0xFF5E9440);
        fill.setAlpha(225);
        c.drawPath(thickP, fill);
        fill.setColor(0xFF3F7A2C);
        c.drawPath(slimeP, fill);
        fill.setColor(0xFFD8F1FF);
        fill.setAlpha(235);
        c.drawPath(iceP, fill);
        line.setStrokeWidth(Math.max(1, dp * 0.8f));
        line.setColor(glass);
        c.drawPath(iceP, line);
        fill.setColor(0xFFFFFFFF);
        c.drawPath(snowP, fill);
        c.drawPath(snowP, line);
        fill.setColor(0xFFFF5A1A);
        fill.setAlpha(70);
        c.drawPath(lavaP, fill);
        fill.setColor(0xFFFF7A22);
        fill.setAlpha(255);
        for (int i = 0; i < wa.n; i++) {
            if (wa.kind[i] == Water.LAVA) c.drawCircle(wa.x[i], wa.y[i], wa.pr * 1.05f, fill);
        }
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(night() ? 60 : 110);
        c.drawPath(steamP, fill);
        fill.setAlpha(255);
    }

    /**
     * Motes of dust drifting up through the room behind the flask, slow and
     * faint, so the screen is never quite still.
     */
    private void motes(Canvas c, int w, int h) {
        float t = water.t;
        fill.setColor(ink);
        for (int i = 0; i < moteX.length; i++) {
            float y = ((moteY[i] - t * 0.012f * moteS[i]) % 1f + 1f) % 1f;
            float x = moteX[i] + 0.015f * (float) Math.sin(t * 0.3f * moteS[i] + i);
            fill.setAlpha((int) (16 + 14 * moteS[i]));
            c.drawCircle(x * w, y * h, (1.2f + moteS[i]) * dp, fill);
        }
        fill.setAlpha(255);
    }

    /**
     * Light through water and glass: two soft patches wander slowly about
     * inside the flask, as light does at the bottom of a pool.
     */
    private void light(Canvas c) {
        Water wa = water;
        float r = wa.r;
        if (caustic == null) {
            caustic = new android.graphics.RadialGradient(0, 0, r * 0.55f,
                    new int[] {0x22FFFFFF, 0x0FFFFFFF, 0x00FFFFFF}, new float[] {0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
        }
        c.save();
        android.graphics.Path round = speaker;
        round.rewind();
        round.addCircle(wa.cx, wa.cy, r, Path.Direction.CW);
        c.clipPath(round);
        fill.setShader(caustic);
        for (int i = 0; i < 2; i++) {
            float t = wa.t * (i == 0 ? 0.17f : 0.11f) + i * 2.1f;
            float px = wa.cx + r * 0.45f * (float) Math.sin(t * 1.3f);
            float py = wa.cy + r * 0.4f * (float) Math.cos(t);
            causticAt.setTranslate(px, py);
            caustic.setLocalMatrix(causticAt);
            c.drawCircle(px, py, r * 0.55f, fill);
        }
        fill.setShader(null);
        c.restore();
    }

    /** Where drops leave the bottom: each throws three droplets that arc away and fade. */
    private void splashes(Canvas c) {
        Water wa = water;
        float life = 0.55f;
        fill.setColor(waterInk);
        for (int k = 0; k < Water.SPLASHES; k++) {
            float age = wa.t - wa.splashT[k];
            if (age < 0 || age > life) continue;
            float fade = 1 - age / life;
            for (int j = 0; j < 3; j++) {
                float spread = (j - 1) * 0.9f + ((k * 7 + j * 3) % 5 - 2) * 0.12f;
                float vx = spread * wa.r * 0.5f, vy = -wa.r * (0.35f + 0.1f * ((k + j) % 3));
                float x = wa.splashX[k] + vx * age, y = wa.splashY[k] + vy * age + wa.g * 0.5f * age * age;
                fill.setAlpha((int) (200 * fade));
                c.drawCircle(x, y, wa.pr * 0.5f * fade + dp, fill);
            }
        }
        fill.setAlpha(255);
    }

    /**
     * A bubble of air across a corridor: a few round bubbles crowded
     * together, clear, with a bright rim and a glint. Popped, they swell a
     * little and are gone.
     */
    private void bubble(Canvas c, Maze.Obstacle o) {
        Water wa = water;
        float k = 0;
        if (!o.active) {
            k = (wa.t - o.gone) / 0.35f;
            if (o.gone < 0 || k >= 1) return;
        }
        float across = (wa.pr + wa.wt) * 1.1f;
        if (o.active && pops > 0) {
            // a faint pulse says the bubble can still be popped
            float pulse = 0.5f + 0.5f * (float) Math.sin(wa.t * 3.0f);
            line.setColor(waterInk);
            line.setAlpha((int) (16 + 26 * pulse));
            line.setStrokeWidth(across * 2.8f);
            c.drawLine(o.cs * o.r0 * wa.r, o.sn * o.r0 * wa.r, o.cs * o.r1 * wa.r, o.sn * o.r1 * wa.r, line);
        }
        float fade = 1 - k;
        for (int i = 0; i < o.grainU.length; i++) {
            float rad = (o.r0 + (o.r1 - o.r0) * o.grainU[i]) * wa.r;
            float off = o.grainV[i] * across;
            float bx = o.cs * rad - o.sn * off, by = o.sn * rad + o.cs * off;
            float size = o.grainS[i] * across * (1 + 0.5f * k);
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha((int) (34 * fade));
            c.drawCircle(bx, by, size, fill);
            line.setColor(glassHi);
            line.setAlpha((int) (210 * fade));
            line.setStrokeWidth(Math.max(1, 1.4f * dp));
            c.drawCircle(bx, by, size, line);
            fill.setAlpha((int) (200 * fade));
            c.drawCircle(bx - size * 0.38f, by - size * 0.38f, size * 0.2f, fill);
        }
        fill.setAlpha(255);
    }

    /** A pearl with its sheen: a base colour, a soft light above, a bright point. */
    private void pearl(Canvas c, float x, float y, float r, int colour) {
        fill.setColor(colour);
        c.drawCircle(x, y, r, fill);
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(70);
        c.drawCircle(x - r * 0.18f, y - r * 0.22f, r * 0.62f, fill);
        fill.setAlpha(210);
        c.drawCircle(x - r * 0.36f, y - r * 0.4f, r * 0.2f, fill);
        fill.setAlpha(255);
        line.setColor(0xFF000000);
        line.setAlpha(50);
        line.setStrokeWidth(Math.max(1, dp));
        c.drawCircle(x, y, r, line);
    }

    /**
     * A seed is a pearl on the wall. Wetted, it opens straight into its
     * flower, no stem: a red pearl into a rose, a green one into clover of
     * three round leaves, a golden one into a daisy. The flower is drawn big
     * and round, in the middle of the corridor it shuts, and it may spill
     * over the walls on either side.
     */
    private void flower(Canvas c, Maze.Obstacle o) {
        Water wa = water;
        float dir = Math.signum(o.r1 - o.r0), dx = o.cs * dir, dy = o.sn * dir;
        float bx = o.cs * o.r0 * wa.r, by = o.sn * o.r0 * wa.r;
        int colour = PEARLS[o.variant];
        if (o.grow <= 0) {
            float swell = Math.min(1f, o.wet / Water.WET);
            float r = wa.pr * (1.25f + 0.35f * swell);
            pearl(c, bx + dx * r * 0.7f, by + dy * r * 0.7f, r, colour);
            return;
        }
        float full = Math.abs(o.r1 - o.r0) * wa.r, len = full * o.grow;
        float fx = bx + dx * len / 2, fy = by + dy * len / 2;
        float size = Math.max(wa.pr * 1.6f, full * 0.62f * o.grow);
        if (o.variant == 0) rose(c, fx, fy, size, colour);
        else if (o.variant == 1) clover(c, fx, fy, size, o.a, colour);
        else daisy(c, fx, fy, size, o.a, colour);
    }

    private void rose(Canvas c, float x, float y, float r, int colour) {
        fill.setColor(colour);
        for (int i = 0; i < 5; i++) {
            double a = i * Maze.TAU / 5 - Math.PI / 2;
            c.drawCircle(x + (float) Math.cos(a) * r * 0.42f, y + (float) Math.sin(a) * r * 0.42f, r * 0.55f, fill);
        }
        fill.setColor(0xFF9E2236);
        fill.setAlpha(110);
        c.drawCircle(x, y, r * 0.62f, fill);
        fill.setAlpha(255);
        // the petals furl in towards the heart
        line.setColor(0xFF7A1628);
        line.setAlpha(170);
        line.setStrokeWidth(Math.max(1.5f, r * 0.07f));
        for (int i = 0; i < 3; i++) {
            float rr = r * (0.48f - 0.14f * i);
            oval.set(x - rr, y - rr, x + rr, y + rr);
            c.drawArc(oval, 200 + 70 * i, 230, false, line);
        }
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(80);
        c.drawCircle(x - r * 0.35f, y - r * 0.4f, r * 0.18f, fill);
        fill.setAlpha(255);
    }

    private void clover(Canvas c, float x, float y, float r, float a, int colour) {
        fill.setColor(colour);
        for (int i = 0; i < 3; i++) {
            double b = a + i * Maze.TAU / 3;
            c.drawCircle(x + (float) Math.cos(b) * r * 0.46f, y + (float) Math.sin(b) * r * 0.46f, r * 0.52f, fill);
        }
        line.setColor(0xFF2F6E37);
        line.setAlpha(170);
        line.setStrokeWidth(Math.max(1.5f, r * 0.06f));
        for (int i = 0; i < 3; i++) {
            double b = a + i * Maze.TAU / 3;
            c.drawLine(x, y, x + (float) Math.cos(b) * r * 0.7f, y + (float) Math.sin(b) * r * 0.7f, line);
        }
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(60);
        for (int i = 0; i < 3; i++) {
            double b = a + i * Maze.TAU / 3;
            c.drawCircle(x + (float) Math.cos(b) * r * 0.55f - r * 0.12f, y + (float) Math.sin(b) * r * 0.55f - r * 0.14f,
                    r * 0.16f, fill);
        }
        fill.setAlpha(255);
    }

    private void daisy(Canvas c, float x, float y, float r, float a, int colour) {
        fill.setColor(0xFFF8F6EE);
        for (int i = 0; i < 11; i++) {
            double b = a + i * Maze.TAU / 11;
            c.drawCircle(x + (float) Math.cos(b) * r * 0.66f, y + (float) Math.sin(b) * r * 0.66f, r * 0.3f, fill);
        }
        line.setColor(0xFF000000);
        line.setAlpha(28);
        line.setStrokeWidth(Math.max(1, dp));
        for (int i = 0; i < 11; i++) {
            double b = a + i * Maze.TAU / 11;
            c.drawCircle(x + (float) Math.cos(b) * r * 0.66f, y + (float) Math.sin(b) * r * 0.66f, r * 0.3f, line);
        }
        fill.setColor(colour);
        c.drawCircle(x, y, r * 0.42f, fill);
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(90);
        c.drawCircle(x - r * 0.13f, y - r * 0.15f, r * 0.16f, fill);
        fill.setAlpha(255);
    }

    /**
     * A float on its rail: a thin line from gap to gap, and a round bob, red
     * above and white below, as on a fishing line.
     */
    private void bobber(Canvas c, Maze.Obstacle o) {
        Water wa = water;
        line.setColor(glass);
        line.setAlpha((int) (Color.alpha(glass) * 0.6f));
        line.setStrokeWidth(Math.max(1, dp));
        c.drawLine(o.cs * o.r0 * wa.r, o.sn * o.r0 * wa.r, o.cs * o.r1 * wa.r, o.sn * o.r1 * wa.r, line);
        float bx = o.cs * o.s * wa.r, by = o.sn * o.s * wa.r, rr = o.size * wa.r;
        fill.setColor(0xFFF4F1EA);
        c.drawCircle(bx, by, rr, fill);
        // the red half faces the centre of the flask, so it reads as the top when the rail stands upright
        oval.set(bx - rr, by - rr, bx + rr, by + rr);
        fill.setColor(0xFFD9483B);
        c.drawArc(oval, (float) Math.toDegrees(o.a) + 90, 180, true, fill);
        line.setColor(0xFF3A2A22);
        line.setAlpha(150);
        line.setStrokeWidth(Math.max(1, dp));
        c.drawCircle(bx, by, rr, line);
    }

    private void top(Canvas c, int w) {
        float side = 20 * dp, key = 19 * dp, keysY = insetTop + 12 * dp + key;
        float x = side + key;
        soundKey.set(x - key, keysY - key, x + key, keysY + key);
        x += 2 * key + 10 * dp;
        toneKey.set(x - key, keysY - key, x + key, keysY + key);
        x += 2 * key + 10 * dp;
        langKey.set(x - key, keysY - key, x + key, keysY + key);
        if (mode == PLAY && !demo) skipKey.set(w - side - 2 * key, keysY - key, w - side, keysY + key);
        else skipKey.setEmpty();
        float helpRight = skipKey.isEmpty() ? w - side : skipKey.left - 10 * dp;
        helpKey.set(helpRight - 2 * key, keysY - key, helpRight, keysY + key);

        roundKey(soundKey, SOUND);
        drawSound(c, soundKey.centerX(), soundKey.centerY(), music.which() == Music.MUSIC);
        roundKey(toneKey, TONE);
        fill.setColor(waterInk);
        c.drawCircle(toneKey.centerX(), toneKey.centerY(), 8 * dp, fill);
        line.setColor(glassHi);
        line.setAlpha(140);
        line.setStrokeWidth(1.5f * dp);
        c.drawArc(new RectF(toneKey.centerX() - 5 * dp, toneKey.centerY() - 5 * dp,
                toneKey.centerX() + 5 * dp, toneKey.centerY() + 5 * dp), 200, 70, false, line);
        roundKey(langKey, LANGUAGE);
        text.setTypeface(sansMedium);
        text.setTextSize(14 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(pressed == LANGUAGE || langOpen ? waterInk : ink);
        String tag = Words.code();
        c.drawText(tag, langKey.centerX(), langKey.centerY() + 5 * dp, text);
        if (!skipKey.isEmpty()) {
            roundKey(skipKey, SKIP);
            drawTurn(c, skipKey.centerX(), skipKey.centerY());
        }
        roundKey(helpKey, HELP);
        text.setTypeface(serif);
        text.setTextSize(22 * dp);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(pressed == HELP ? waterInk : ink);
        c.drawText("?", helpKey.centerX(), helpKey.centerY() + 7.5f * dp, text);

        float since = water.t - saidAt;
        if (since >= 0 && since < 1.4f && said.length() > 0) {
            text.setTypeface(sans);
            text.setTextSize(13 * dp);
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(muted);
            text.setAlpha((int) (255 * Math.min(1f, (1.4f - since) * 3)));
            c.drawText(said, langKey.right + 12 * dp, keysY + 5 * dp, text);
            text.setAlpha(255);
        }

        float row = keysY + key + 10 * dp;
        float numBase = row + 34 * dp, lblBase = numBase + 20 * dp;
        text.setColor(ink);
        text.setTypeface(serif);
        text.setTextSize(40 * dp);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(String.valueOf(water.flaskBest), side, numBase, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(String.valueOf(record), w - side, numBase, text);
        text.setColor(muted);
        text.setTypeface(sans);
        text.setTextSize(13 * dp);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(Words.s("best_flask"), side, lblBase, text);
        text.setTextAlign(Paint.Align.RIGHT);
        c.drawText(Words.s("record"), w - side, lblBase, text);
        float top = row;

        // how knotty this flask is: five beads, as many filled as its tier
        float bead = 3.5f * dp, step = 13 * dp, y = top + 22 * dp;
        float x0 = w / 2f - step * (Maze.TIERS - 1) / 2f;
        for (int i = 0; i < Maze.TIERS; i++) {
            fill.setColor(i < water.maze.tier ? waterInk : glass);
            c.drawCircle(x0 + i * step, y, bead, fill);
        }

        // the flask's rules, said once as it is handed over
        if (mode == PLAY && noteBody.length() > 0 && (demo || water.t < 7f)) {
            float o = demo || water.t < 6f ? 1f : 7f - water.t;
            noteText.setTypeface(sans);
            noteText.setTextSize(14 * dp);
            noteText.setColor(ink);
            int width = (int) (w - 64 * dp);
            if (noteLayout == null || noteLayout.getWidth() != width) {
                noteLayout = StaticLayout.Builder.obtain(noteBody, 0, noteBody.length(), noteText, width)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setLineSpacing(0, 1.3f)
                        .build();
            }
            noteText.setAlpha((int) (255 * o));
            c.save();
            // under the flask, where the pour is shown later
            c.translate(32 * dp, water.cy + water.r * 1.12f + 6 * dp);
            noteLayout.draw(c);
            c.restore();
        }
    }

    private void roundKey(RectF k, int which) {
        fill.setColor(panel);
        canvasNow.drawCircle(k.centerX(), k.centerY(), k.width() / 2, fill);
        if (pressed == which) {
            fill.setColor(waterInk);
            fill.setAlpha(50);
            canvasNow.drawCircle(k.centerX(), k.centerY(), k.width() / 2, fill);
            fill.setAlpha(255);
        }
    }

    /** A speaker; with two waves while the music plays, with a cross while it is quiet. */
    private void drawSound(Canvas c, float tx, float ty, boolean on) {
        fill.setColor(ink);
        Path p = speaker;
        p.rewind();
        p.moveTo(tx - 9 * dp, ty - 3 * dp);
        p.lineTo(tx - 5 * dp, ty - 3 * dp);
        p.lineTo(tx, ty - 8 * dp);
        p.lineTo(tx, ty + 8 * dp);
        p.lineTo(tx - 5 * dp, ty + 3 * dp);
        p.lineTo(tx - 9 * dp, ty + 3 * dp);
        p.close();
        c.drawPath(p, fill);
        line.setColor(ink);
        line.setStrokeWidth(1.8f * dp);
        if (on) {
            for (int i = 1; i <= 2; i++) {
                float rr = (2 + 4 * i) * dp;
                oval.set(tx - rr, ty - rr, tx + rr, ty + rr);
                c.drawArc(oval, -40, 80, false, line);
            }
        } else {
            c.drawLine(tx + 3 * dp, ty - 4 * dp, tx + 10 * dp, ty + 4 * dp, line);
            c.drawLine(tx + 3 * dp, ty + 4 * dp, tx + 10 * dp, ty - 4 * dp, line);
        }
    }

    /** A turning arrow: most of a ring, and its head. */
    private void drawTurn(Canvas c, float kx, float ky) {
        line.setColor(pressed == SKIP ? waterInk : ink);
        line.setStrokeWidth(2 * dp);
        float ar = 8 * dp;
        oval.set(kx - ar, ky - ar, kx + ar, ky + ar);
        c.drawArc(oval, -60, 290, false, line);
        double end = Math.toRadians(-60);
        float hx = kx + (float) Math.cos(end) * ar, hy = ky + (float) Math.sin(end) * ar;
        c.drawLine(hx, hy, hx - 5 * dp, hy - 1 * dp, line);
        c.drawLine(hx, hy, hx + 1 * dp, hy + 5 * dp, line);
    }

    /**
     * Under the language key: English, the languages inside the game, and a
     * row to load another from a file. The one spoken now is in the water's
     * colour, with its code beside it.
     */
    private void languages(Canvas c) {
        float rowH = 44 * dp, width = 240 * dp;
        int rows = 3 + Words.BUILT_IN.length;
        float left = Math.max(12 * dp, langKey.left - 10 * dp), topY = langKey.bottom + 8 * dp;
        langMenu.set(left, topY, left + width, topY + rows * rowH);
        fill.setColor(panel);
        c.drawRoundRect(langMenu, 14 * dp, 14 * dp, fill);
        text.setTypeface(sans);
        text.setTextSize(15 * dp);
        String now = Words.code();
        langEnglish.set(left, topY, left + width, topY + rowH);
        row(c, langEnglish, Words.s("english"), "en", now.equals("en"));
        for (int i = 0; i < Words.BUILT_IN.length; i++) {
            langBuilt[i].set(left, topY + (i + 1) * rowH, left + width, topY + (i + 2) * rowH);
            String code = Words.BUILT_IN[i];
            row(c, langBuilt[i], builtNames[i], code, now.equals(code) && !custom());
        }
        langLoad.set(left, topY + (rows - 2) * rowH, left + width, topY + (rows - 1) * rowH);
        String load = custom() ? Words.name() + "  ·  " + Words.s("load_language") : Words.s("load_language");
        row(c, langLoad, load, custom() ? now : "", custom());
        langSave.set(left, topY + (rows - 1) * rowH, left + width, topY + rows * rowH);
        row(c, langSave, Words.s("save_language"), "", false);
    }

    /** A language spoken now that came from a file, not from inside the game. */
    private boolean custom() {
        if (!Words.active()) return false;
        for (int i = 0; i < Words.BUILT_IN.length; i++) {
            if (Words.BUILT_IN[i].equals(Words.code()) && builtNames[i].equals(Words.name())) return false;
        }
        return true;
    }

    private void row(Canvas c, RectF at, String label, String code, boolean chosen) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(chosen ? waterInk : ink);
        c.drawText(label, at.left + 18 * dp, at.centerY() + 5 * dp, text);
        if (code.length() > 0) {
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(chosen ? waterInk : muted);
            c.drawText(code, at.right - 18 * dp, at.centerY() + 5 * dp, text);
        }
    }

    private void meter(Canvas c, int w, int h) {
        float y = h - insetBottom - 18 * dp, side = 20 * dp;
        float left = mode == PLAY ? (Water.VOLUMES - spawned) / (float) Water.VOLUMES : 0;
        int rule = water.maze.rule;
        if (mode == PLAY && rule != Maze.FREE) {
            String label, value = "";
            if (set) {
                left = 0;
                label = Words.s("set");
            } else if (rule == Maze.TURNS) {
                left = turnLeft / turnBudget;
                label = Words.s("turns_left");
                value = String.format(java.util.Locale.US, "%.1f", turnLeft / Maze.TAU);
            } else {
                float rest = Math.max(0, setAt - Math.max(water.t, FIRST_SPAWN));
                left = rest / (setAt - FIRST_SPAWN);
                label = Words.s("time_left");
                int sec = (int) Math.ceil(rest);
                value = String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60);
            }
            text.setTypeface(sans);
            text.setTextSize(12 * dp);
            text.setColor(muted);
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText(label, side, y - 8 * dp, text);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setColor(ink);
            c.drawText(value, w - side, y - 8 * dp, text);
        }
        line.setStrokeCap(Paint.Cap.BUTT);
        line.setStrokeWidth(2 * dp);
        line.setColor(glass);
        c.drawLine(side, y, w - side, y, line);
        if (left > 0) {
            line.setColor(waterInk);
            c.drawLine(side, y, side + (w - 2 * side) * left, y, line);
        }
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    private void card(Canvas c, int w, int h) {
        float width = Math.min(w - 32 * dp, 420 * dp), pad = 24 * dp;
        int inner = (int) (width - 2 * pad);
        text.setTypeface(sans);
        text.setTextSize(15 * dp);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(muted);
        if (bodyLayout == null || inner != laidWidth || !cardBody.equals(laidBody)) {
            bodyLayout = StaticLayout.Builder.obtain(cardBody, 0, cardBody.length(), text, inner)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0, 1.35f)
                    .build();
            laidWidth = inner;
            laidBody = cardBody;
        }
        float titleSize = 40 * dp, buttonH = 48 * dp;
        float height = pad + titleSize + 10 * dp + bodyLayout.getHeight() + 20 * dp + buttonH + 20 * dp;
        float left = (w - width) / 2, bottom = h - insetBottom - 36 * dp;
        card.set(left, bottom - height, left + width, bottom);
        fill.setColor(panel);
        c.drawRoundRect(card, 22 * dp, 22 * dp, fill);

        float x = left + pad, y = card.top + pad;
        text.setTypeface(serif);
        text.setTextSize(titleSize);
        text.setColor(ink);
        c.drawText(cardTitle, x, y + titleSize * 0.8f, text);
        y += titleSize + 10 * dp;

        c.save();
        c.translate(x, y);
        text.setTypeface(sans);
        text.setTextSize(15 * dp);
        text.setColor(muted);
        bodyLayout.draw(c);
        c.restore();
        y += bodyLayout.getHeight() + 20 * dp;

        text.setTypeface(sansMedium);
        text.setTextSize(17 * dp);
        float bw = text.measureText(cardAction) + 52 * dp;
        button.set(x, y, x + bw, y + buttonH);
        fill.setColor(waterInk);
        if (pressed == ACTION) fill.setAlpha(200);
        c.drawRoundRect(button, buttonH / 2, buttonH / 2, fill);
        fill.setAlpha(255);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(cardAction, button.centerX(), button.centerY() + text.getTextSize() * 0.35f, text);
    }



    // ---------- the page about the game ----------

    /**
     * What the page says, in order. H a heading, P a paragraph, N a quieter
     * paragraph, T one of the troubles: its glyph, its name and a line about it.
     */
    private static final String[] HELP_ORDER = {
            "H:help_title", "P:help_play_1", "P:help_play_2", "P:help_play_3", "N:help_grey",
            "H:help_maze_title", "P:help_maze",
            "H:help_troubles_title", "P:help_troubles",
            "T:turns", "T:time", "T:bubble", "T:seed", "T:float", "T:wild", "T:coin",
            "T:snow", "T:lava", "T:slime", "T:breath",
            "H:help_keys_title", "P:help_keys", "N:help_quiet",
    };

    /** The page laid out for one width and one language: its blocks and how tall it is. */
    private final class Help {
        final String[] kinds;
        final String[] glyphs;
        final StaticLayout[] titles, bodies;
        final float[] tops;
        final float height, width;

        Help(float width) {
            this.width = width;
            int n = HELP_ORDER.length;
            kinds = new String[n];
            glyphs = new String[n];
            titles = new StaticLayout[n];
            bodies = new StaticLayout[n];
            tops = new float[n];
            float y = 0;
            for (int i = 0; i < n; i++) {
                String kind = HELP_ORDER[i].substring(0, 1), id = HELP_ORDER[i].substring(2);
                kinds[i] = kind;
                tops[i] = y;
                if (kind.equals("H")) {
                    y += i == 0 ? 0 : 22 * dp;
                    tops[i] = y;
                    titles[i] = lay(Words.s(id), helpHead, (int) width);
                    y += titles[i].getHeight() + 8 * dp;
                } else if (kind.equals("T")) {
                    glyphs[i] = id;
                    int inner = (int) (width - 56 * dp);
                    titles[i] = lay(Words.s("t_" + id + "_title"), helpItem, inner);
                    bodies[i] = lay(Words.s("t_" + id), helpSmall, inner);
                    y += Math.max(44 * dp, titles[i].getHeight() + 2 * dp + bodies[i].getHeight()) + 14 * dp;
                } else {
                    bodies[i] = lay(Words.s(id), kind.equals("N") ? helpNote : helpBody, (int) width);
                    y += bodies[i].getHeight() + 10 * dp;
                }
            }
            height = y + 24 * dp;
        }
    }

    private final TextPaint helpHead = new TextPaint(Paint.ANTI_ALIAS_FLAG), helpBody = new TextPaint(Paint.ANTI_ALIAS_FLAG),
            helpNote = new TextPaint(Paint.ANTI_ALIAS_FLAG), helpItem = new TextPaint(Paint.ANTI_ALIAS_FLAG),
            helpSmall = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private static StaticLayout lay(String words, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(words, 0, words.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0, 1.3f)
                .build();
    }

    private void helpPaints() {
        helpHead.setTypeface(serif);
        helpHead.setTextSize(26 * dp);
        helpHead.setColor(ink);
        helpBody.setTypeface(sans);
        helpBody.setTextSize(15 * dp);
        helpBody.setColor(ink);
        helpNote.setTypeface(sans);
        helpNote.setTextSize(15 * dp);
        helpNote.setColor(muted);
        helpItem.setTypeface(sansMedium);
        helpItem.setTextSize(15 * dp);
        helpItem.setColor(ink);
        helpSmall.setTypeface(sans);
        helpSmall.setTextSize(14 * dp);
        helpSmall.setColor(muted);
    }

    /** The page: a sheet over the game, a heading and a close key on top, and the rest scrolling under. */
    private void helpPage(Canvas c, int w, int h) {
        helpPaints();
        helpSheet.set(10 * dp, insetTop + 8 * dp, w - 10 * dp, h - insetBottom - 8 * dp);
        fill.setColor(panel);
        fill.setAlpha(Math.min(255, Color.alpha(panel) + 20));
        c.drawRoundRect(helpSheet, 22 * dp, 22 * dp, fill);
        fill.setAlpha(255);
        float pad = 22 * dp, head = 64 * dp;
        float width = helpSheet.width() - 2 * pad;
        if (help == null || help.width != width) help = new Help(width);

        text.setTypeface(serif);
        text.setTextSize(30 * dp);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(ink);
        c.drawText(Words.s("app_name"), helpSheet.left + pad, helpSheet.top + 42 * dp, text);
        float key = 18 * dp;
        helpClose.set(helpSheet.right - pad - 2 * key, helpSheet.top + 14 * dp, helpSheet.right - pad, helpSheet.top + 14 * dp + 2 * key);
        fill.setColor(waterInk);
        fill.setAlpha(40);
        c.drawCircle(helpClose.centerX(), helpClose.centerY(), key, fill);
        fill.setAlpha(255);
        line.setColor(ink);
        line.setStrokeWidth(2 * dp);
        float x6 = 6 * dp;
        c.drawLine(helpClose.centerX() - x6, helpClose.centerY() - x6, helpClose.centerX() + x6, helpClose.centerY() + x6, line);
        c.drawLine(helpClose.centerX() - x6, helpClose.centerY() + x6, helpClose.centerX() + x6, helpClose.centerY() - x6, line);

        float viewTop = helpSheet.top + head, viewBottom = helpSheet.bottom - 10 * dp;
        float most = Math.max(0, help.height - (viewBottom - viewTop));
        helpScroll = Math.max(0, Math.min(most, helpScroll));
        c.save();
        c.clipRect(helpSheet.left, viewTop, helpSheet.right, viewBottom);
        float x = helpSheet.left + pad, y0 = viewTop - helpScroll;
        for (int i = 0; i < HELP_ORDER.length; i++) {
            float y = y0 + help.tops[i];
            if (y > viewBottom) break;
            String kind = help.kinds[i];
            if (kind.equals("H")) {
                c.save();
                c.translate(x, y);
                help.titles[i].draw(c);
                c.restore();
            } else if (kind.equals("T")) {
                float glyphAt = y + 20 * dp;
                if (glyphAt > viewTop - 30 * dp) glyph(c, help.glyphs[i], x + 20 * dp, glyphAt, 18 * dp);
                c.save();
                c.translate(x + 56 * dp, y);
                help.titles[i].draw(c);
                c.translate(0, help.titles[i].getHeight() + 2 * dp);
                help.bodies[i].draw(c);
                c.restore();
            } else {
                c.save();
                c.translate(x, y);
                help.bodies[i].draw(c);
                c.restore();
            }
        }
        c.restore();
        // a thin bar at the side tells how far down the page is
        if (most > 0) {
            float track = viewBottom - viewTop, bar = Math.max(30 * dp, track * track / help.height);
            float at = viewTop + (track - bar) * helpScroll / most;
            line.setColor(muted);
            line.setAlpha(110);
            line.setStrokeWidth(3 * dp);
            c.drawLine(helpSheet.right - 6 * dp, at, helpSheet.right - 6 * dp, at + bar, line);
        }
    }

    /** The small pictures of the troubles, drawn the way the game draws them. */
    private void glyph(Canvas c, String kind, float x, float y, float s) {
        line.setColor(ink);
        line.setStrokeWidth(2.2f * dp);
        if (kind.equals("turns") || kind.equals("wild")) {
            oval.set(x - s * 0.7f, y - s * 0.7f, x + s * 0.7f, y + s * 0.7f);
            c.drawArc(oval, -60, 290, false, line);
            double end = Math.toRadians(-60);
            float hx = x + (float) Math.cos(end) * s * 0.7f, hy = y + (float) Math.sin(end) * s * 0.7f;
            c.drawLine(hx, hy, hx - 6 * dp, hy - 1 * dp, line);
            c.drawLine(hx, hy, hx + 1 * dp, hy + 6 * dp, line);
            if (kind.equals("turns")) c.drawLine(x - s * 0.3f, y, x + s * 0.3f, y, line);
            else c.drawLine(x, y, x + s * 0.4f, y - s * 0.2f, line);
        } else if (kind.equals("time")) {
            c.drawCircle(x, y, s * 0.75f, line);
            c.drawLine(x, y, x, y - s * 0.45f, line);
            c.drawLine(x, y, x + s * 0.3f, y + s * 0.2f, line);
        } else if (kind.equals("bubble")) {
            float[][] b = {{-0.35f, 0.3f, 0.45f}, {0.25f, -0.3f, 0.38f}, {0.4f, 0.4f, 0.3f}};
            for (float[] q : b) {
                float bx = x + q[0] * s, by = y + q[1] * s, r = q[2] * s;
                fill.setColor(waterInk);
                fill.setAlpha(40);
                c.drawCircle(bx, by, r, fill);
                line.setColor(ink);
                line.setAlpha(170);
                line.setStrokeWidth(1.6f * dp);
                c.drawCircle(bx, by, r, line);
                fill.setColor(ink);
                fill.setAlpha(170);
                c.drawCircle(bx - r * 0.38f, by - r * 0.38f, r * 0.2f, fill);
            }
            fill.setAlpha(255);
        } else if (kind.equals("seed")) {
            pearl(c, x - s * 0.45f, y + s * 0.3f, s * 0.38f, PEARLS[0]);
            pearl(c, x, y - s * 0.35f, s * 0.38f, PEARLS[1]);
            pearl(c, x + s * 0.45f, y + s * 0.3f, s * 0.38f, PEARLS[2]);
        } else if (kind.equals("float")) {
            float r = s * 0.6f;
            line.setColor(glass);
            line.setStrokeWidth(1.4f * dp);
            c.drawLine(x, y - s, x, y + s, line);
            fill.setColor(0xFFF4F1EA);
            c.drawCircle(x, y, r, fill);
            oval.set(x - r, y - r, x + r, y + r);
            fill.setColor(0xFFD9483B);
            c.drawArc(oval, 180, 180, true, fill);
            line.setColor(0xFF3A2A22);
            line.setAlpha(150);
            c.drawCircle(x, y, r, line);
        } else if (kind.equals("coin")) {
            oval.set(x - s * 0.35f, y - s * 0.75f, x + s * 0.35f, y + s * 0.75f);
            c.drawOval(oval, line);
            c.drawLine(x - s, y, x - s * 0.55f, y, line);
            c.drawLine(x + s * 0.55f, y, x + s, y, line);
        } else if (kind.equals("snow")) {
            line.setColor(0xFF8FC8EE);
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI / 3 + Math.PI / 2;
                float dx = (float) Math.cos(a) * s * 0.75f, dy = (float) Math.sin(a) * s * 0.75f;
                c.drawLine(x - dx, y - dy, x + dx, y + dy, line);
            }
        } else if (kind.equals("lava")) {
            Path p = speaker;
            p.rewind();
            p.moveTo(x, y - s * 0.85f);
            p.cubicTo(x + s * 0.4f, y - s * 0.2f, x + s * 0.65f, y + s * 0.1f, x + s * 0.6f, y + s * 0.35f);
            p.cubicTo(x + s * 0.5f, y + s * 0.85f, x - s * 0.5f, y + s * 0.85f, x - s * 0.6f, y + s * 0.35f);
            p.cubicTo(x - s * 0.65f, y + s * 0.1f, x - s * 0.4f, y - s * 0.2f, x, y - s * 0.85f);
            fill.setColor(0xFFFF7A22);
            c.drawPath(p, fill);
        } else if (kind.equals("slime")) {
            fill.setColor(0xFF3F7A2C);
            c.drawCircle(x - s * 0.3f, y + s * 0.15f, s * 0.45f, fill);
            c.drawCircle(x + s * 0.3f, y + s * 0.1f, s * 0.5f, fill);
            c.drawCircle(x, y - s * 0.25f, s * 0.42f, fill);
            fill.setColor(0xFF9ED17E);
            c.drawCircle(x - s * 0.15f, y - s * 0.2f, s * 0.12f, fill);
        } else if (kind.equals("breath")) {
            c.drawCircle(x, y, s * 0.8f, line);
            line.setPathEffect(new android.graphics.DashPathEffect(new float[] {3 * dp, 3 * dp}, 0));
            c.drawCircle(x, y, s * 0.45f, line);
            line.setPathEffect(null);
        }
        line.setAlpha(255);
        fill.setAlpha(255);
    }

    /** Scrolling the page with a finger, and letting it glide on after the finger lets go. */
    private boolean helpTouch(MotionEvent e) {
        float ey = e.getY();
        long now = e.getEventTime();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                helpDownY = helpLastY = ey;
                helpDownScroll = helpScroll;
                helpLastAt = now;
                helpGlide = 0;
                helpMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ey - helpDownY) > 8 * dp) helpMoved = true;
                helpScroll = helpDownScroll - (ey - helpDownY);
                if (now > helpLastAt) helpGlide = -(ey - helpLastY) / ((now - helpLastAt) / 1000f);
                helpLastY = ey;
                helpLastAt = now;
                return true;
            case MotionEvent.ACTION_UP:
                if (!helpMoved) {
                    helpGlide = 0;
                    float ex = e.getX();
                    float grow = 10 * dp;
                    boolean onClose = ex > helpClose.left - grow && ex < helpClose.right + grow
                            && ey > helpClose.top - grow && ey < helpClose.bottom + grow;
                    if (onClose || !helpSheet.contains(ex, ey)) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        helpOpen = false;
                    }
                } else if (now - helpLastAt > 80) {
                    helpGlide = 0;
                }
                return true;
            default:
                return true;
        }
    }

    private void glide(float dt) {
        if (Math.abs(helpGlide) < 5) return;
        helpScroll += helpGlide * dt;
        helpGlide *= (float) Math.exp(-3.5f * dt);
    }
}
