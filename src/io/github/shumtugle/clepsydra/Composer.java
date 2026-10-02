package io.github.shumtugle.clepsydra;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Random;

/**
 * Writes a new piece of music each time it is asked: slow drops of a plucked
 * glassy voice over a few soft low notes, the way water falls in a still
 * room. Every piece is different, so the music never comes round again.
 *
 * The rules are few. A major pentatonic scale on a key chosen by chance, so
 * no two notes ever clash; a slow tempo; bars built from a handful of
 * rhythms, most of them the long-long-short of three, three and two
 * sixteenths; a melody that walks mostly by step and now and then leaps, and
 * rests at the end of each four bars on a note that sounds like home. Phrases
 * come back with small changes, as a tune does: A, A again but altered, B,
 * B altered, A once more, a new C, C altered, and A to close. Now and then a
 * note drips twice. The piece is a standard MIDI file of one track.
 */
final class Composer {
    private static final int TPB = 480, SIXTEENTH = TPB / 4, BAR = TPB * 4;
    private static final int[] SCALE = {0, 2, 4, 7, 9};
    private static final int[] KEYS = {60, 62, 64, 65, 67, 69};
    /** Bars as onsets, in sixteenths. */
    private static final int[][] RHYTHMS = {
            {0, 6, 12}, {0, 6, 12}, {0, 6, 12, 14}, {0, 4, 8, 12},
            {0, 6, 10, 12}, {0, 8, 12}, {0, 3, 6, 12}, {0, 6},
    };
    /** Glassy pluck for the drops, harp for the low notes. */
    private static final int DROPS = 8, LOW = 46;

    private final Random rnd;

    Composer(Random rnd) {
        this.rnd = rnd;
    }

    /** One piece, about two minutes long, ready to be written to a file. */
    byte[] piece() {
        int key = KEYS[rnd.nextInt(KEYS.length)];
        int bpm = 56 + rnd.nextInt(11);
        ArrayList<int[]> notes = new ArrayList<int[]>();

        int[][] a = phrase(), b = phrase(), c = phrase();
        int[][][] form = {a, vary(a), b, vary(b), vary(a), c, vary(c), a};
        for (int p = 0; p < form.length; p++) {
            int barAt = p * 4;
            for (int[] note : form[p]) {
                int tick = (barAt * BAR) + note[0];
                int pitch = pitch(key, note[1]);
                // now and then a drop falls twice: a short note and its echo
                boolean twice = note[3] >= 2 * SIXTEENTH && rnd.nextFloat() < 0.12f;
                notes.add(new int[] {tick, 0, pitch, note[2], twice ? SIXTEENTH : note[3]});
                if (twice) {
                    notes.add(new int[] {tick + SIXTEENTH, 0, pitch, Math.max(20, note[2] / 2), SIXTEENTH});
                }
            }
            // a soft low note under every two bars: home, or the fifth
            for (int k = 0; k < 4; k += 2) {
                int degree = rnd.nextFloat() < 0.65f ? 0 : 3;
                notes.add(new int[] {(barAt + k) * BAR, 1, key - 12 + SCALE[degree], 30 + rnd.nextInt(6), BAR});
            }
        }
        // the last drop rings home, long
        int end = form.length * 4 * BAR;
        notes.add(new int[] {end, 0, key + 12, 44, TPB * 2});
        notes.add(new int[] {end, 1, key - 12, 30, TPB * 3});
        return write(notes, bpm, end + BAR * 2);
    }

    /**
     * Four bars of melody as degrees on a scale that goes on past the octave:
     * 5 is the octave of 0. Each note is onset, degree, loudness, length.
     */
    private int[][] phrase() {
        ArrayList<int[]> out = new ArrayList<int[]>();
        int degree = 4 + rnd.nextInt(5);
        for (int bar = 0; bar < 4; bar++) {
            int[] rhythm = RHYTHMS[rnd.nextInt(RHYTHMS.length)];
            boolean last = bar == 3;
            for (int i = 0; i < rhythm.length; i++) {
                if (!last && i > 0 && rnd.nextFloat() < 0.1f) continue;
                int at = bar * BAR + rhythm[i] * SIXTEENTH;
                int next = i + 1 < rhythm.length ? rhythm[i + 1] : 16;
                int length = Math.min(next - rhythm[i], 3) * SIXTEENTH;
                float step = rnd.nextFloat();
                if (step < 0.6f) degree += rnd.nextBoolean() ? 1 : -1;
                else if (step < 0.85f) degree += rnd.nextBoolean() ? 2 : -2;
                else if (step < 0.95f) degree += rnd.nextBoolean() ? 3 : -3;
                degree = Math.max(2, Math.min(10, degree));
                if (last && i == rhythm.length - 1) {
                    // the phrase comes to rest on home or the fifth
                    degree = Math.abs(degree - 5) <= Math.abs(degree - 8) ? 5 : 8;
                    length = (16 - rhythm[i]) * SIXTEENTH;
                }
                int loud = 40 + rnd.nextInt(14) + (rhythm[i] == 0 ? 4 : 0);
                out.add(new int[] {at, degree, loud, length});
                if (last && i == rhythm.length - 1) break;
            }
        }
        return out.toArray(new int[0][]);
    }

    /** The phrase again with two or three notes moved a step; the end stays where it was. */
    private int[][] vary(int[][] phrase) {
        int[][] out = new int[phrase.length][];
        for (int i = 0; i < phrase.length; i++) out[i] = phrase[i].clone();
        int changes = 2 + rnd.nextInt(2);
        for (int k = 0; k < changes && out.length > 1; k++) {
            int i = rnd.nextInt(out.length - 1);
            out[i][1] = Math.max(2, Math.min(10, out[i][1] + (rnd.nextBoolean() ? 1 : -1)));
            out[i][2] = Math.max(32, Math.min(60, out[i][2] + rnd.nextInt(9) - 4));
        }
        return out;
    }

    private static int pitch(int key, int degree) {
        return key + 12 * (degree / 5) + SCALE[degree % 5];
    }

    // ---------- the file ----------

    private static byte[] write(ArrayList<int[]> notes, int bpm, int length) {
        // every note becomes an on and an off; offs before ons at the same tick
        ArrayList<int[]> events = new ArrayList<int[]>();
        for (int[] n : notes) {
            events.add(new int[] {n[0], 1, n[1], n[2], n[3]});
            events.add(new int[] {n[0] + n[4], 0, n[1], n[2], 0});
        }
        java.util.Collections.sort(events, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] x, int[] y) {
                if (x[0] != y[0]) return x[0] < y[0] ? -1 : 1;
                return x[1] - y[1];
            }
        });
        ByteArrayOutputStream track = new ByteArrayOutputStream();
        int tempo = 60000000 / bpm;
        meta(track, 0x51, new byte[] {(byte) (tempo >> 16), (byte) (tempo >> 8), (byte) tempo});
        meta(track, 0x58, new byte[] {4, 2, 24, 8});
        program(track, 0, DROPS, 100, 72);
        program(track, 1, LOW, 70, 84);
        int now = 0;
        for (int[] e : events) {
            vlq(track, e[0] - now);
            now = e[0];
            track.write((e[1] == 1 ? 0x90 : 0x80) | e[2]);
            track.write(e[3] & 0x7F);
            track.write(e[1] == 1 ? e[4] & 0x7F : 0);
        }
        vlq(track, Math.max(0, length - now));
        track.write(0xFF);
        track.write(0x2F);
        track.write(0);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        byte[] body = track.toByteArray();
        file.write('M'); file.write('T'); file.write('h'); file.write('d');
        int32(file, 6);
        int16(file, 0);
        int16(file, 1);
        int16(file, TPB);
        file.write('M'); file.write('T'); file.write('r'); file.write('k');
        int32(file, body.length);
        file.write(body, 0, body.length);
        return file.toByteArray();
    }

    private static void program(ByteArrayOutputStream out, int channel, int program, int volume, int room) {
        vlq(out, 0);
        out.write(0xC0 | channel);
        out.write(program);
        vlq(out, 0);
        out.write(0xB0 | channel);
        out.write(7);
        out.write(volume);
        vlq(out, 0);
        out.write(0xB0 | channel);
        out.write(91);
        out.write(room);
    }

    private static void meta(ByteArrayOutputStream out, int type, byte[] data) {
        vlq(out, 0);
        out.write(0xFF);
        out.write(type);
        vlq(out, data.length);
        out.write(data, 0, data.length);
    }

    private static void vlq(ByteArrayOutputStream out, int value) {
        int buffer = value & 0x7F;
        while ((value >>= 7) > 0) {
            buffer <<= 8;
            buffer |= ((value & 0x7F) | 0x80);
        }
        while (true) {
            out.write(buffer & 0xFF);
            if ((buffer & 0x80) != 0) buffer >>= 8;
            else break;
        }
    }

    private static void int32(ByteArrayOutputStream out, int v) {
        out.write(v >> 24); out.write(v >> 16); out.write(v >> 8); out.write(v);
    }

    private static void int16(ByteArrayOutputStream out, int v) {
        out.write(v >> 8); out.write(v);
    }
}
