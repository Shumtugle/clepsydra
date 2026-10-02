package io.github.shumtugle.clepsydra;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/** One screen, edge to edge, awake while it is open. */
public final class Main extends Activity {
    private static final int LANGUAGE = 1, SAVE = 2;
    private Board board;

    /** Opens the phone's own file picker for a language module, a plain text file. */
    void pickLanguage() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("text/*");
        try {
            startActivityForResult(pick, LANGUAGE);
        } catch (android.content.ActivityNotFoundException e) {
            // no picker on this phone: the game stays in the language it speaks
        }
    }

    /**
     * Asks the phone where to keep the language spoken now as a module file,
     * to be filled in or corrected and loaded back.
     */
    void saveLanguage(String code) {
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("text/plain");
        save.putExtra(Intent.EXTRA_TITLE, "clepsydra-" + ("en".equals(code) ? "template" : code) + ".txt");
        try {
            startActivityForResult(save, SAVE);
        } catch (android.content.ActivityNotFoundException e) {
            board.saved(false);
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == LANGUAGE) board.language(read(data.getData()));
        else if (request == SAVE) board.saved(write(data.getData(), Words.write()));
    }

    private boolean write(Uri uri, String text) {
        java.io.OutputStream out = null;
        try {
            out = getContentResolver().openOutputStream(uri, "wt");
            if (out == null) return false;
            out.write(text.getBytes("UTF-8"));
            return true;
        } catch (java.io.IOException e) {
            return false;
        } finally {
            if (out != null) try { out.close(); } catch (java.io.IOException e) { /* nothing more to do */ }
        }
    }

    private String read(Uri uri) {
        java.io.InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            // a module is small; anything past a quarter of a megabyte is not one
            while ((n = in.read(buf)) > 0 && out.size() < 262144) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } catch (java.io.IOException e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (java.io.IOException e) { /* nothing more to do */ }
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        board = new Board(this);
        setContentView(board);
    }

    @Override
    protected void onResume() {
        super.onResume();
        immerse();
        board.resume();
    }

    @Override
    public void onBackPressed() {
        if (!board.back()) super.onBackPressed();
    }

    @Override
    protected void onPause() {
        board.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        board.release();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) immerse();
    }

    /** The system bars step aside; a swipe from the edge brings them back for a moment. */
    private void immerse() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
