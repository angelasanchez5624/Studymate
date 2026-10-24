package com.example.studymate;

import android.content.Context;

/**
 * Textos de los tiempos de aviso: "30 min antes", "2 horas antes", "1 día antes", "1 semana antes"...
 * y las opciones que se ofrecen en Notificaciones.
 */
public class Avisos {

    /** Opciones rápidas (minutos). La lista también tiene "Personalizado…" y "Desactivado". */
    public static final int[] OPCIONES = {10, 30, 60, 2 * 60, 6 * 60, 12 * 60,
            24 * 60, 2 * 24 * 60, 3 * 24 * 60, 7 * 24 * 60};

    public static final int MINUTO = 1, HORA = 60, DIA = 24 * 60, SEMANA = 7 * 24 * 60;

    /** "2 horas", "1 día", "45 min"... (la cantidad con su unidad más grande exacta). */
    public static String cantidad(Context c, int min) {
        android.content.res.Resources r = c.getResources();
        if (min % SEMANA == 0) return r.getQuantityString(R.plurals.semanas, min / SEMANA, min / SEMANA);
        if (min % DIA == 0) return r.getQuantityString(R.plurals.dias, min / DIA, min / DIA);
        if (min % HORA == 0) return r.getQuantityString(R.plurals.horas, min / HORA, min / HORA);
        return r.getQuantityString(R.plurals.minutos, min, min);
    }

    /** Lo que se ve en la fila: "2 horas antes" o "Desactivado". */
    public static String antes(Context c, int min) {
        return min <= 0 ? c.getString(R.string.aviso_desactivado) : c.getString(R.string.aviso_antes, cantidad(c, min));
    }

    /** Título de la notificación: "Entrega en 2 horas" / "Examen en 3 días". */
    public static String titulo(boolean examen, int min) {
        Context c = App.ctx();
        return c.getString(examen ? R.string.recordatorio_examen_en : R.string.recordatorio_entrega_en, cantidad(c, min));
    }
}
