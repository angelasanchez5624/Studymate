package com.example.studymate;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Evita que el contenido quede debajo de la barra de estado / navegación (Android 15+ es edge-to-edge). */
public class Ui {
    public static void insets(Activity a) {
        View content = a.findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, wi) -> {
            Insets b = wi.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Llena una lista desplegable con el estilo de la app. La opción 0 es el "Selecciona…"
     * y se ve en gris, como el texto de ayuda de los campos.
     */
    public static void lista(android.widget.Spinner sp, String[] opciones) {
        lista(sp, opciones, true);
    }

    /** Igual, pero si "indicacion" es false todas las opciones se ven normales (no hay "Selecciona…"). */
    public static void lista(android.widget.Spinner sp, String[] opciones, boolean indicacion) {
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                sp.getContext(), R.layout.item_spinner, opciones) {
            @Override
            public View getView(int pos, View convertView, android.view.ViewGroup parent) {
                android.widget.TextView tv = (android.widget.TextView) super.getView(pos, convertView, parent);
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(),
                        indicacion && pos == 0 ? R.color.text_hint : R.color.text_main));
                return tv;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        sp.setAdapter(adapter);
    }

    /** Versión de la app (versionName del build.gradle), ej. "1.0". */
    public static String version(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
