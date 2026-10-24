package com.example.studymate;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferencias de la app (se guardan en el teléfono).
 *  - Recordatorios: activados o no, y cuánto antes avisar (lo elige el usuario).
 *  - Formato de hora: 12 h (a. m./p. m.) o 24 h.
 *  - Privacidad: ocultar los detalles de la tarea en la pantalla de bloqueo.
 * (El idioma lo guarda Android, ver Idioma.java)
 */
public class Ajustes {

    private static final String PREFS = "studymate_ajustes";

    public static final String RECORDATORIOS = "recordatorios";
    public static final String AVISO_DIA = "aviso_dia";
    public static final String AVISO_HORAS = "aviso_horas";
    public static final String HORA_24 = "hora_24";
    public static final String OCULTAR_DETALLE = "ocultar_detalle";
    public static final String AVISO_CLASES = "aviso_clases";     // recordatorio antes de cada clase
    public static final String MIN_CLASES = "min_clases";         // cuántos minutos antes (número)
    public static final String TEMA = "tema";                     // 0 = teléfono, 1 = claro, 2 = oscuro
    // Cuánto antes de la entrega avisar, en minutos (0 = desactivado). Lo elige el usuario.
    public static final String AVISO1_MIN = "aviso1_min";
    public static final int AVISO1_DEFECTO = 24 * 60; // 1 día antes

    /** Copia rápida del formato de hora (Horario la usa sin tener Context). */
    public static boolean hora24 = false;

    /** Se llama al abrir la app para cargar el formato de hora. */
    public static void cargar(Context c) {
        hora24 = prefs(c).getBoolean(HORA_24, false);
    }

    public static boolean get(Context c, String clave) {
        // Todo viene activado, excepto el formato de 24 h y ocultar detalles
        boolean porDefecto = !HORA_24.equals(clave) && !OCULTAR_DETALLE.equals(clave);
        return prefs(c).getBoolean(clave, porDefecto);
    }

    public static void set(Context c, String clave, boolean valor) {
        prefs(c).edit().putBoolean(clave, valor).apply();
        if (HORA_24.equals(clave)) hora24 = valor;
    }

    /**
     * Minutos antes de la entrega para el aviso (0 = desactivado).
     * Si todavía no se ha elegido: 1 día antes (o desactivado, si en la versión anterior
     * se había apagado "1 día antes").
     */
    public static int avisoMin(Context c, String clave) {
        SharedPreferences p = prefs(c);
        if (p.contains(clave)) return p.getInt(clave, 0);
        return p.getBoolean(AVISO_DIA, true) ? AVISO1_DEFECTO : 0;
    }

    /** Números guardados (minutos antes de la clase, tema...). */
    public static int getInt(Context c, String clave, int porDefecto) {
        return prefs(c).getInt(clave, porDefecto);
    }

    public static void setInt(Context c, String clave, int valor) {
        prefs(c).edit().putInt(clave, valor).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
