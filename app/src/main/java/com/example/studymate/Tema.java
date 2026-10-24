package com.example.studymate;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Tema de la app: claro, oscuro o el del teléfono. Se guarda en Ajustes y se aplica al abrir la app
 * (App.onCreate). Los colores del modo oscuro están en res/values-night/.
 */
public class Tema {

    public static final int SISTEMA = 0, CLARO = 1, OSCURO = 2;

    public static int actual(Context c) {
        return Ajustes.getInt(c, Ajustes.TEMA, SISTEMA);
    }

    /** Aplica el tema guardado. */
    public static void aplicar(Context c) {
        AppCompatDelegate.setDefaultNightMode(modo(actual(c)));
    }

    /** Cambia el tema; las pantallas abiertas se recargan solas con los nuevos colores. */
    public static void cambiar(Context c, int opcion) {
        Ajustes.setInt(c, Ajustes.TEMA, opcion);
        AppCompatDelegate.setDefaultNightMode(modo(opcion));
    }

    public static String[] opciones(Context c) {
        return new String[]{c.getString(R.string.tema_sistema), c.getString(R.string.tema_claro),
                c.getString(R.string.tema_oscuro)};
    }

    public static String nombreActual(Context c) {
        return opciones(c)[actual(c)];
    }

    private static int modo(int opcion) {
        switch (opcion) {
            case CLARO: return AppCompatDelegate.MODE_NIGHT_NO;
            case OSCURO: return AppCompatDelegate.MODE_NIGHT_YES;
            default: return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }
}
