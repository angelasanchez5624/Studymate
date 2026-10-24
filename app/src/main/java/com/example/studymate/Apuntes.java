package com.example.studymate;

import android.content.Context;

/** Textos de apoyo para las listas de apuntes. */
public class Apuntes {

    /** "23 oct 2026", "23 oct 2026 · Con foto", "23 oct 2026 · Con foto · 2 archivos"... */
    public static String detalle(Context c, Apunte a, String fecha) {
        StringBuilder sb = new StringBuilder(fecha);
        if (a.tieneFoto()) sb.append(" · ").append(c.getString(R.string.con_foto));
        int n = a.cuantosAdjuntos();
        if (n > 0) sb.append(" · ").append(c.getResources().getQuantityString(R.plurals.n_archivos, n, n));
        return sb.toString();
    }
}
