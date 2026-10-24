package com.example.studymate;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/**
 * Idioma de la app. Android lo guarda solo (Android 13+), así que se mantiene al cerrar la app
 * y también se puede cambiar desde Ajustes del teléfono → Apps → StudyMate → Idioma.
 * Los textos están en res/values/strings.xml (español) y res/values-en/strings.xml (inglés).
 */
public class Idioma {

    /** Opciones del diálogo, en orden: 0 = español, 1 = inglés, 2 = predeterminado del teléfono. */
    public static final int ESPANOL = 0, INGLES = 1, SISTEMA = 2;
    private static final String[] CODIGOS = {"es", "en", ""};

    /** Opción elegida actualmente. */
    public static int actual() {
        LocaleListCompat lista = AppCompatDelegate.getApplicationLocales();
        if (lista.isEmpty() || lista.get(0) == null) return SISTEMA;
        String idioma = lista.get(0).getLanguage();
        if ("en".equals(idioma)) return INGLES;
        if ("es".equals(idioma)) return ESPANOL;
        return SISTEMA;
    }

    /** Nombres para mostrar en el diálogo. */
    public static String[] opciones(Context c) {
        return new String[]{
                c.getString(R.string.idioma_es),
                c.getString(R.string.idioma_en),
                c.getString(R.string.idioma_sistema)};
    }

    /** Nombre corto de la opción actual (se muestra a la derecha en Configuración). */
    public static String nombreActual(Context c) {
        return opciones(c)[actual()];
    }

    /** Cambia el idioma. Android vuelve a crear la pantalla abierta con los nuevos textos. */
    public static void cambiar(int opcion) {
        if (opcion == actual()) return;
        AppCompatDelegate.setApplicationLocales(opcion == SISTEMA
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(CODIGOS[opcion]));
    }
}
