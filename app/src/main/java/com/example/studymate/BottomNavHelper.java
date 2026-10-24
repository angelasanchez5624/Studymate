package com.example.studymate;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

/**
 * Uso: BottomNavHelper.setup(this, BottomNavHelper.INICIO); en el onCreate de cada pantalla con barra.
 *
 * Navegación (Material Design): Inicio es la pantalla base y siempre queda debajo.
 * Desde Materias, Tareas o Perfil, el botón Atrás vuelve a Inicio; desde Inicio, sale de la app.
 */
public class BottomNavHelper {
    public static final int INICIO = 0, MATERIAS = 1, TAREAS = 2, PERFIL = 3;

    /** Cambia de pestaña (como la barra de abajo), por ejemplo desde las tarjetas de Inicio. */
    public static void irA(Activity a, Intent destino) {
        boolean esInicio = destino.getComponent() != null
                && DashboardActivity.class.getName().equals(destino.getComponent().getClassName());
        cambiar(a, destino, esInicio);
    }

    public static void setup(Activity a, int active) {
        ViewGroup nav = a.findViewById(R.id.bottomNav);
        Class<?>[] destinos = {DashboardActivity.class, SubjectsActivity.class, TasksActivity.class, ProfileActivity.class};

        for (int i = 0; i < 4; i++) {
            LinearLayout item = (LinearLayout) nav.getChildAt(i);
            TextView label = (TextView) item.getChildAt(1);
            View indicador = item.getChildAt(2);
            boolean on = (i == active);

            label.setTextColor(ContextCompat.getColor(a, on ? R.color.texto_acento : R.color.text_label));
            label.setTypeface(ResourcesCompat.getFont(a, on ? R.font.nunito_bold : R.font.nunito_medium));
            indicador.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            // Ícono vectorial: lila si la pestaña está activa, gris si no
            ((android.widget.ImageView) item.getChildAt(0)).setColorFilter(
                    ContextCompat.getColor(a, on ? R.color.texto_acento : R.color.text_label));
            // TalkBack: "Tareas, pestaña, seleccionada"
            item.setSelected(on);
            item.setContentDescription(label.getText());
            item.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.TabWidget");
                }
            });

            final int destino = i;
            item.setOnClickListener(v -> {
                if (destino == active) return;
                cambiar(a, new Intent(a, destinos[destino]), destino == INICIO);
            });
        }
    }

    /**
     * Abre la pestaña sin animación. Inicio no se cierra nunca (queda como base);
     * las demás pestañas se cierran al cambiar, para no apilar pantallas repetidas.
     */
    @SuppressWarnings("deprecation") // overridePendingTransition se usa solo en Android 13
    private static void cambiar(Activity a, Intent destino, boolean destinoEsInicio) {
        if (destinoEsInicio) {
            // Vuelve al Inicio que ya está abierto debajo (o lo crea si no existe)
            destino.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        a.startActivity(destino);
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            a.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
        } else {
            a.overridePendingTransition(0, 0);
        }
        if (!(a instanceof DashboardActivity)) a.finish();
    }
}
