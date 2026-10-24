package com.example.studymate;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.util.Locale;

/**
 * Clase de la aplicación (se registra en el AndroidManifest con android:name=".App").
 * Permite leer textos de strings.xml en el idioma elegido desde cualquier parte,
 * incluso en clases que no tienen Context (Horario, GradeUtil, TaskUtil, Recordatorios...).
 *
 * Uso:  App.t(R.string.sin_horario)   ·   App.t(R.string.saludo, nombre)
 */
public class App extends Application {

    private static App instancia;

    // Contexto con el idioma elegido (se vuelve a crear solo si cambia el idioma)
    private static Context contextoIdioma;
    private static String idiomaCacheado = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instancia = this;
        // Sin internet: Firebase guarda una copia de tus datos en el teléfono y sube los cambios
        // cuando vuelve la conexión. Debe activarse antes de cualquier otro uso de la base.
        try {
            com.google.firebase.database.FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception ignorar) { }
        // Tema claro / oscuro elegido en el menú
        Tema.aplicar(this);
    }

    /** Contexto cuyos textos salen en el idioma elegido en Configuración. */
    public static synchronized Context ctx() {
        String tags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        if (contextoIdioma == null || !tags.equals(idiomaCacheado)) {
            idiomaCacheado = tags;
            if (tags.isEmpty()) {
                contextoIdioma = instancia; // idioma del teléfono
            } else {
                Configuration conf = new Configuration(instancia.getResources().getConfiguration());
                conf.setLocales(LocaleList.forLanguageTags(tags));
                contextoIdioma = instancia.createConfigurationContext(conf);
            }
        }
        return contextoIdioma;
    }

    /** Texto traducido. */
    public static String t(int id) {
        return ctx().getString(id);
    }

    /** Texto traducido con datos, ej. App.t(R.string.saludo, "Ana") -> "Hola, Ana 👋". */
    public static String t(int id, Object... datos) {
        return ctx().getString(id, datos);
    }

    /** Lista de textos (string-array) traducida. */
    public static String[] lista(int id) {
        return ctx().getResources().getStringArray(id);
    }

    /** Idioma actual, para formatear fechas ("10 sep" o "10 Sep"). */
    public static Locale locale() {
        LocaleListCompat l = AppCompatDelegate.getApplicationLocales();
        if (!l.isEmpty() && l.get(0) != null) return l.get(0);
        return Locale.getDefault();
    }
}
