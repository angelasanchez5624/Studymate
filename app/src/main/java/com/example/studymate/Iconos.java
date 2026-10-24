package com.example.studymate;

import android.graphics.drawable.Drawable;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.appcompat.content.res.AppCompatResources;

/**
 * Íconos vectoriales (Material) junto al texto, en lugar de emojis.
 * El ícono toma el tamaño y el color del texto, así se ve igual en todos los teléfonos
 * y respeta el modo oscuro.
 */
public class Iconos {

    /** Pone el ícono a la izquierda del texto (llamar después de setTextColor). */
    public static void alInicio(TextView tv, @DrawableRes int icono) {
        Drawable d = AppCompatResources.getDrawable(tv.getContext(), icono);
        if (d == null) return;
        d = d.mutate();
        int lado = Math.round(tv.getTextSize() * 1.2f);
        d.setBounds(0, 0, lado, lado);
        d.setTint(tv.getCurrentTextColor());
        tv.setCompoundDrawablesRelative(d, null, null, null);
        tv.setCompoundDrawablePadding(Math.round(4 * tv.getResources().getDisplayMetrics().density));
    }

    /** Quita el ícono (por si la vista se recicla en una lista). */
    public static void quitar(TextView tv) {
        tv.setCompoundDrawablesRelative(null, null, null, null);
    }
}
