package com.example.studymate;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.MotionEvent;
import android.widget.EditText;
import androidx.core.content.ContextCompat;

/** Pone el ícono del ojo al final de un campo de contraseña para mostrarla u ocultarla. */
public class PasswordToggle {

    public static void setup(EditText et) {
        Context c = et.getContext();
        Drawable ojoTachado = ContextCompat.getDrawable(c, R.drawable.ic_eye_off); // contraseña oculta
        Drawable ojoAbierto = ContextCompat.getDrawable(c, R.drawable.ic_eye);     // contraseña visible
        final Typeface fuente = et.getTypeface();
        final boolean[] visible = {false};
        final int margen = (int) (8 * c.getResources().getDisplayMetrics().density);

        et.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, ojoTachado, null);

        et.setOnTouchListener((v, event) -> {
            Drawable icono = et.getCompoundDrawablesRelative()[2];
            if (icono == null) return false;

            boolean sobreElIcono = event.getX() >=
                    et.getWidth() - et.getPaddingEnd() - icono.getIntrinsicWidth() - margen;
            if (!sobreElIcono) return false;

            if (event.getAction() == MotionEvent.ACTION_UP) {
                visible[0] = !visible[0];
                int cursor = Math.min(Math.max(et.getSelectionEnd(), 0), et.getText().length());

                et.setInputType(InputType.TYPE_CLASS_TEXT | (visible[0]
                        ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                et.setTypeface(fuente); // Android cambia la fuente al alternar; se restaura
                et.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                        visible[0] ? ojoAbierto : ojoTachado, null);
                et.setSelection(cursor);
                v.performClick();
            }
            return true; // el toque sobre el ícono no abre el teclado
        });
    }
}
