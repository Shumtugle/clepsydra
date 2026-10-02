package io.github.shumtugle.clepsydra;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;

import java.util.Random;

/**
 * The music behind the game, or nothing. The music is written as it plays:
 * a new piece of plucked drops each time the last one ends, so it never comes
 * round again. It asks the phone for the sound before it plays, steps aside
 * when something else needs it, and comes back when it may.
 */
final class Music implements AudioManager.OnAudioFocusChangeListener {
    static final int MUSIC = 0, SILENCE = 1;

    private final Context context;
    private final AudioManager audio;
    private final Composer composer = new Composer(new Random());
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    private final AudioFocusRequest request;
    private MediaPlayer player;
    private int which = SILENCE;
    /** Two files taken in turn, so a new piece is never written over one playing. */
    private int sheet;
    private boolean shown, focused;

    Music(Context context) {
        this.context = context.getApplicationContext();
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this)
                .setWillPauseWhenDucked(false)
                .build();
    }

    int which() {
        return which;
    }

    void choose(int kind) {
        if (kind == which) return;
        drop();
        which = kind;
        if (shown) start();
    }

    void resume() {
        shown = true;
        start();
    }

    void pause() {
        shown = false;
        if (player != null && player.isPlaying()) player.pause();
        letGo();
    }

    void release() {
        shown = false;
        drop();
    }

    private void start() {
        if (which == SILENCE) { letGo(); return; }
        if (!focused) {
            focused = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!focused) return;
        }
        if (player == null) {
            player = written();
            if (player == null) return;
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // the piece is over: the next one is written and begins
                    if (mp == player) player = null;
                    mp.release();
                    if (shown) start();
                }
            });
        }
        if (!player.isPlaying()) player.start();
    }

    /** A new piece, written to a file and opened; null if the phone will not play it. */
    private MediaPlayer written() {
        java.io.File file = new java.io.File(context.getCacheDir(), sheet == 0 ? "drops-a.mid" : "drops-b.mid");
        sheet ^= 1;
        java.io.FileOutputStream out = null;
        try {
            out = new java.io.FileOutputStream(file);
            out.write(composer.piece());
            out.close();
            out = null;
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(attributes);
            mp.setDataSource(file.getPath());
            mp.prepare();
            return mp;
        } catch (java.io.IOException e) {
            return null;
        } finally {
            if (out != null) try { out.close(); } catch (java.io.IOException e) { /* nothing more to do */ }
        }
    }

    private void drop() {
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException e) { /* already stopped */ }
            player.release();
            player = null;
        }
        letGo();
    }

    private void letGo() {
        if (focused) {
            audio.abandonAudioFocusRequest(request);
            focused = false;
        }
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (player == null) return;
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                player.setVolume(1f, 1f);
                if (shown && !player.isPlaying()) player.start();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                player.setVolume(0.25f, 0.25f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (player.isPlaying()) player.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                if (player.isPlaying()) player.pause();
                focused = false;
                break;
            default:
                break;
        }
    }
}
