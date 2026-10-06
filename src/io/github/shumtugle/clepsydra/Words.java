package io.github.shumtugle.clepsydra;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * The string table.
 *
 * English lives in the source, keyed by a stable id. Every other language
 * lives outside the source, in a module of plain text that anyone can fill
 * in an editor and load into the game. A key the module lacks falls back to
 * English, so a module is useful from its first line.
 *
 * A module opens with its head line, names itself on a line of its own and
 * gives its short code on another, and then gives each key and its words,
 * separated by a tab; the English each line was made from may stand above it
 * as a comment. Numbers go where the English has {n} and {m}. Finnish and
 * Russian come inside the game as modules of the same kind; any other can be
 * loaded from a file.
 */
final class Words {
    static final String HEAD = "# a language for the flask game";

    static final String[] IDS = {
            "app_name", "intro", "start", "again", "best_flask", "record", "new_record",
            "over_title", "over_text", "over_none", "over_none_text", "show_way", "shown_title",
            "shown_text", "replay", "demo", "turns_left", "time_left", "set", "note_turns", "note_time",
            "note_bubble", "note_seeds", "note_breath", "note_float", "note_wild", "note_coin",
            "note_snow", "note_lava", "note_slime", "demo_note", "music", "silence", "sea", "amber",
            "moss", "english", "load_language", "not_a_language", "save_language", "language_saved",
            "not_saved", "help_title", "help_play_1", "help_play_2", "help_play_3", "help_grey",
            "help_maze_title", "help_maze", "help_troubles_title", "help_troubles", "t_turns_title",
            "t_turns", "t_time_title", "t_time", "t_bubble_title", "t_bubble", "t_seed_title",
            "t_seed", "t_float_title", "t_float", "t_wild_title", "t_wild", "t_coin_title",
            "t_coin", "t_snow_title", "t_snow", "t_lava_title", "t_lava", "t_slime_title",
            "t_slime", "t_breath_title", "t_breath", "help_keys_title", "help_keys", "help_quiet",
    };

    private static final String[] EN = {
            "Clepsydra",
            "Turn the flask with your finger. Catch the water in the neck, find the way down, and let it out of the bottom in one long pour. Water that sneaks in through the bottom turns grey and does not count.",
            "Start",
            "New flask",
            "best pour, this flask",
            "record",
            "new record",
            "Best pour: {n}",
            "{n} of {m} drops left through the bottom. Turn the flask to pour out what is left, or take a new one.",
            "Nothing got through",
            "Keep the neck up while the water falls, then find a way down to the bottom opening.",
            "Show the way",
            "One way through",
            "Play this flask again, or take a new one.",
            "Play it again",
            "Demo",
            "turns left",
            "time left",
            "the flask has set",
            "This flask turns {n} times, no more.",
            "This flask turns for {n} seconds, then sets.",
            "Bubbles of air. Some shut the way, one may hold the water in. Pop the ones that must go in one stroke; the rest stay.",
            "Seeds like pearls cling to the glass. Wet them, and a flower opens across the way.",
            "This flask breathes. Once the rain is over, its maze changes now and then.",
            "A float rides in the maze. Water lifts it to shut the gap above; when the water drains away, it sinks and shuts the gap below.",
            "This flask turns by itself. Hold it: it pulls like rubber, and it can slip out of your fingers.",
            "Now and then this flask flips over like a coin, and left becomes right.",
            "Snow is coming. Snow that meets water in the glass turns it to ice.",
            "Lava is coming. Lava that meets water boils it away into steam.",
            "Slime is coming. It makes the water thick and slow.",
            "Here is one way through. Tap to stop.",
            "music",
            "silence",
            "glass",
            "brass",
            "stone",
            "English",
            "Load a language",
            "This is not a language for this game",
            "Save this language as a file",
            "Saved. Fill it in and load it back",
            "It could not be saved",
            "How it plays",
            "Water falls from above in ten volumes. Keep the neck of the flask up and catch it.",
            "Turn the flask with one finger, the way a hand turns a dial. Water lies wherever its corridor is lowest, and slips through a gap when the gap comes underneath.",
            "Lead it down to the bottom opening and let it go. What counts is one pour: every drop that leaves with no pause longer than a blink.",
            "Water that sneaks in through the bottom turns grey and never counts.",
            "Never the same flask twice",
            "Every flask is a new maze, from gentle to knotty; the beads at the top tell how knotty. The flasks come shuffled, not climbing, and two hard ones never stand side by side. Some mazes scatter their gaps, some wind them in a spiral, some wall off chambers that hold water like cups.",
            "What can happen",
            "Some flasks keep a trouble or two, never more. Each is said under the flask as it is handed over.",
            "A count of turns",
            "The flask turns only so far, then sets fast. The count is the least the maze needs, and a good deal more.",
            "A clock",
            "The flask turns only for so long, then sets fast.",
            "Bubbles",
            "Round bubbles of air: across a corridor, in a gap, or in the glass of the flask itself. One stroke a flask: every bubble the finger passes over pops, and the rest stay. Some shut the way; one in the flask's own glass holds the water in, and popped, lets it run out.",
            "Seeds",
            "Pearls stuck to the glass. Wetted enough, one opens into a flower across the corridor: red into a rose, green into clover, gold into a daisy.",
            "A float",
            "A bob on a rail between two gaps. Water lifts it to shut the gap above; without water it sinks and shuts the gap below.",
            "A wild flask",
            "It turns by itself. A finger holds it like a rubber band, and it slips loose when pulled too hard.",
            "A coin flip",
            "Now and then the flask flips over like a coin, water and all, and left becomes right.",
            "Snow",
            "Snow that meets water freezes it. The ice turns with the glass until it melts.",
            "Lava",
            "Lava boils the water it meets into steam, which rises out of the neck.",
            "Slime",
            "On flasks with a clock, slime makes the water thick and slow.",
            "A breathing flask",
            "Once its rain is over, a plain flask may breathe, and the maze inside becomes another.",
            "The keys",
            "Along the top: sound, tone and language on the left; on the right, Demo, which plays the flask for you from the start, this page, and a new flask, at any time.",
            "No network, no permissions, no advertising. The record stays on the phone.",
    };

    /** The languages that come inside the game, by their codes; each has a module among the resources. */
    static final String[] BUILT_IN = {"fi", "ru"};

    private static final Map<String, String> TABLE = new HashMap<String, String>();
    private static final Map<String, String> MINE = new HashMap<String, String>();
    private static String moduleName = "", moduleCode = "";

    static {
        for (int i = 0; i < IDS.length; i++) TABLE.put(IDS[i], EN[i]);
    }

    private Words() {
    }

    /** The words as the player should see them: the module first, English behind it. */
    static String s(String id) {
        String s = MINE.get(id);
        if (s != null && s.length() > 0) return s;
        s = TABLE.get(id);
        return s == null ? id : s;
    }

    static String f(String id, Object n) {
        return s(id).replace("{n}", String.valueOf(n));
    }

    static String f(String id, Object n, Object m) {
        return f(id, n).replace("{m}", String.valueOf(m));
    }

    /** The module's own name for its language, or empty while English is spoken. */
    static String name() {
        return moduleName;
    }

    /** The short code of the language spoken: en, fi, ru, or whatever a loaded module says. */
    static String code() {
        if (MINE.isEmpty()) return "en";
        if (moduleCode.length() > 0) return moduleCode;
        return moduleName.length() >= 2 ? moduleName.substring(0, 2).toLowerCase(java.util.Locale.ROOT) : "??";
    }

    static boolean active() {
        return !MINE.isEmpty();
    }

    /** The name a module gives its language, read from its text without taking it in. */
    static String nameIn(String text) {
        if (text == null) return "";
        for (String raw : text.split("\n")) {
            String line = raw.replace("\r", "");
            if (line.startsWith("module ")) return line.substring(7).trim();
        }
        return "";
    }

    /**
     * The language as chosen last time. Nothing chosen yet: the phone's own
     * language, if the game carries it, else English. A built-in language is
     * read afresh from the game each time, so a newer game brings newer words.
     */
    static void load(Context context, Builtin builtin) {
        SharedPreferences p = context.getSharedPreferences("words", Context.MODE_PRIVATE);
        String chosen = p.getString("!lang", "");
        if (chosen.length() == 0) {
            String phone = java.util.Locale.getDefault().getLanguage();
            for (String code : BUILT_IN) if (code.equals(phone)) chosen = code;
        }
        MINE.clear();
        moduleName = moduleCode = "";
        if (chosen.equals("custom")) {
            moduleName = p.getString("!name", "");
            moduleCode = p.getString("!code", "");
            for (String id : IDS) {
                String v = p.getString(id, "");
                if (v.length() > 0) MINE.put(id, v);
            }
        } else if (!chosen.equals("en") && chosen.length() > 0) {
            take(builtin.text(chosen));
        }
    }

    /** English, or one of the languages inside the game, chosen by hand. */
    static void choose(Context context, String code, Builtin builtin) {
        MINE.clear();
        moduleName = moduleCode = "";
        if (!code.equals("en")) take(builtin.text(code));
        context.getSharedPreferences("words", Context.MODE_PRIVATE).edit().clear().putString("!lang", code).apply();
    }

    /** Where the languages inside the game are read from. */
    interface Builtin {
        String text(String code);
    }

    private static void save(Context context) {
        SharedPreferences.Editor e = context.getSharedPreferences("words", Context.MODE_PRIVATE).edit();
        e.clear();
        e.putString("!lang", "custom");
        e.putString("!name", moduleName);
        e.putString("!code", moduleCode);
        for (Map.Entry<String, String> entry : MINE.entrySet()) e.putString(entry.getKey(), entry.getValue());
        e.apply();
    }

    /** Whether a text is a module at all, judged by its first line. */
    static boolean isModule(String text) {
        if (text == null) return false;
        String t = text.startsWith("\uFEFF") ? text.substring(1) : text;
        return t.trim().startsWith(HEAD);
    }

    /** Takes in a module loaded from a file, and keeps it; false if the text is not one. */
    static boolean read(Context context, String text) {
        if (!isModule(text)) return false;
        take(text);
        save(context);
        return true;
    }

    private static void take(String text) {
        MINE.clear();
        moduleName = moduleCode = "";
        if (!isModule(text)) return;
        for (String raw : text.split("\n")) {
            String line = raw.replace("\r", "");
            if (line.trim().length() == 0 || line.trim().startsWith("#")) continue;
            if (line.startsWith("module ")) {
                moduleName = line.substring(7).trim();
                continue;
            }
            if (line.startsWith("code ")) {
                moduleCode = line.substring(5).trim().toLowerCase(java.util.Locale.ROOT);
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String id = line.substring(0, tab).trim(), words = line.substring(tab + 1).trim();
            if (TABLE.containsKey(id) && words.length() > 0) MINE.put(id, words.replace("\\n", "\n"));
        }
    }

    /**
     * The language spoken now, written out whole as a module: every key, the
     * English above it, and the words of this language where it has them,
     * empty where it has not. Spoken in English, it is a blank template; in
     * Finnish, a Finnish module to correct or to start another from.
     */
    static String write() {
        StringBuilder b = new StringBuilder();
        b.append(HEAD).append('\n');
        b.append("# fill the empty lines and load the file back\n\n");
        b.append("module ").append(moduleName.length() > 0 ? moduleName : "untitled").append('\n');
        b.append("code ").append(MINE.isEmpty() ? "xx" : code()).append("\n\n");
        for (int i = 0; i < IDS.length; i++) {
            String mine = MINE.get(IDS[i]);
            b.append("# ").append(EN[i]).append('\n');
            b.append(IDS[i]).append('\t').append(mine == null ? "" : mine.replace("\n", "\\n")).append("\n\n");
        }
        return b.toString();
    }

    /** A blank module: every key with its English above it, to be filled in. */
    static String template() {
        StringBuilder b = new StringBuilder();
        b.append(HEAD).append('\n');
        b.append("# fill the empty lines and load the file back\n\n");
        b.append("module untitled\n");
        b.append("code xx\n\n");
        for (int i = 0; i < IDS.length; i++) {
            b.append("# ").append(EN[i]).append('\n');
            b.append(IDS[i]).append('\t').append("\n\n");
        }
        return b.toString();
    }
}
