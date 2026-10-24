package com.example.studymate;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

/** Equivale a subjectColorMap de App.tsx (1 azul, 2 lila, 3 rosa, 4 menta, 5 amarillo). */
public class SubjectColors {
    private static final int[] BG = {R.color.blue_bg, R.color.lilac_bg, R.color.pink_bg, R.color.mint_bg, R.color.yellow_bg};
    private static final int[] TEXT = {R.color.blue_text, R.color.lilac_text, R.color.pink_text, R.color.mint_text, R.color.yellow_text};

    private static int idx(int subjectId) { return ((subjectId - 1) % 5 + 5) % 5; }

    public static int bg(Context c, int subjectId) { return ContextCompat.getColor(c, BG[idx(subjectId)]); }
    public static int text(Context c, int subjectId) { return ContextCompat.getColor(c, TEXT[idx(subjectId)]); }

    /** Tiñe el fondo (shape blanco) de una vista con un color. */
    public static void tint(View v, int color) {
        ViewCompat.setBackgroundTintList(v, ColorStateList.valueOf(color));
    }

    /** "2026-09-10" -> "10 sep" */
    public static String fechaCorta(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            String f = new SimpleDateFormat("d MMM", App.locale()).format(d); // "18 oct"
            // El mes con inicial mayúscula: "18 Oct"
            int i = f.indexOf(' ') + 1;
            if (i > 0 && i < f.length()) f = f.substring(0, i) + f.substring(i, i + 1).toUpperCase(App.locale()) + f.substring(i + 1);
            return f;
        } catch (Exception e) {
            return iso;
        }
    }
}
