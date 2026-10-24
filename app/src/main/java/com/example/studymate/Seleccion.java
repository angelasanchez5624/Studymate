package com.example.studymate;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Modo selección para borrar uno o varios elementos (notas, materias o tareas).
 *
 *  - Mantén presionada una tarjeta para empezar a seleccionar.
 *  - Mientras hay algo seleccionado, tocar una tarjeta la marca o desmarca.
 *  - Arriba aparece UNA barra con "✕", "N seleccionadas" y el ícono de basura.
 *  - La basura pregunta antes de borrar. "✕" o el botón Atrás cancelan la selección.
 */
public class Seleccion {

    public static final int NOTAS = 0, MATERIAS = 1, TAREAS = 2, APUNTES = 3;

    private final AppCompatActivity a;
    private final int tipo;
    private final Runnable refrescar;
    private final Set<Integer> ids = new LinkedHashSet<>();
    private final OnBackPressedCallback atras;
    private View barra;
    private TextView tvCuenta;

    /** @param refrescar se llama cada vez que cambia la selección, para repintar la lista */
    public Seleccion(AppCompatActivity a, int tipo, Runnable refrescar) {
        this.a = a;
        this.tipo = tipo;
        this.refrescar = refrescar;
        atras = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { limpiar(); }
        };
        a.getOnBackPressedDispatcher().addCallback(a, atras);
    }

    public boolean activa() { return !ids.isEmpty(); }

    public boolean esta(int id) { return ids.contains(id); }

    /** Marca o desmarca un elemento. */
    public void alternar(int id) {
        if (!ids.remove(id)) ids.add(id);
        actualizar();
    }

    public void limpiar() {
        if (ids.isEmpty()) return;
        ids.clear();
        actualizar();
    }

    /** Quita de la selección lo que ya no existe (por ejemplo, si se borró desde otro teléfono). */
    public void podar(Collection<Integer> existentes) {
        if (ids.retainAll(existentes)) actualizar();
    }

    // ---------------------------------------------------------------------------------------

    private void actualizar() {
        if (barra == null) crearBarra();
        boolean on = activa();
        barra.setVisibility(on ? View.VISIBLE : View.GONE);
        atras.setEnabled(on);
        if (on) tvCuenta.setText(a.getResources().getQuantityString(R.plurals.seleccionados, ids.size(), ids.size()));
        refrescar.run();
    }

    private void crearBarra() {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackgroundResource(R.drawable.bg_card_20);
        b.setElevation(dp(8));
        b.setPadding(dp(8), dp(6), dp(8), dp(6));
        b.setClickable(true); // que los toques no pasen a la lista de abajo

        android.widget.ImageView cerrar = new android.widget.ImageView(a);
        cerrar.setImageResource(R.drawable.ic_cerrar);
        cerrar.setColorFilter(ContextCompat.getColor(a, R.color.text_main));
        cerrar.setPadding(dp(12), dp(12), dp(12), dp(12));
        cerrar.setContentDescription(a.getString(R.string.cancelar));
        cerrar.setOnClickListener(v -> limpiar());
        b.addView(cerrar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        tvCuenta = new TextView(a);
        tvCuenta.setTextSize(15);
        tvCuenta.setTextColor(ContextCompat.getColor(a, R.color.text_main));
        Typeface f = ResourcesCompat.getFont(a, R.font.nunito_bold);
        if (f != null) tvCuenta.setTypeface(f);
        LinearLayout.LayoutParams lpTxt = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTxt.setMarginStart(dp(4));
        b.addView(tvCuenta, lpTxt);

        ImageView basura = new ImageView(a);
        basura.setImageResource(R.drawable.ic_delete);
        basura.setBackgroundResource(R.drawable.bg_icon_pink);
        basura.setColorFilter(ContextCompat.getColor(a, R.color.icono_pink)); // se ve bien en claro y oscuro
        basura.setPadding(dp(12), dp(12), dp(12), dp(12));
        basura.setContentDescription(a.getString(R.string.eliminar));
        basura.setOnClickListener(v -> confirmar());
        b.addView(basura, new LinearLayout.LayoutParams(dp(48), dp(48)));

        // Se pone encima de la pantalla, arriba (android.R.id.content ya respeta la barra de estado)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        lp.setMargins(dp(16), dp(10), dp(16), 0);
        ((ViewGroup) a.findViewById(android.R.id.content)).addView(b, lp);
        barra = b;
    }

    private void confirmar() {
        int n = ids.size();
        int titulo, pregunta;
        if (tipo == NOTAS) { titulo = R.string.nota_eliminar; pregunta = R.plurals.eliminar_notas; }
        else if (tipo == MATERIAS) { titulo = R.string.materia_eliminar; pregunta = R.plurals.eliminar_materias; }
        else if (tipo == APUNTES) { titulo = R.string.apunte_eliminar; pregunta = R.plurals.eliminar_apuntes; }
        else { titulo = R.string.tarea_eliminar; pregunta = R.plurals.eliminar_tareas; }

        String msg = a.getResources().getQuantityString(pregunta, n, n);
        if (tipo == MATERIAS) {
            // Avisar cuántas tareas se van con esas materias
            int tareas = 0;
            for (Task t : Datos.TASKS) if (ids.contains(t.subjectId)) tareas++;
            if (tareas > 0) msg += "\n\n" + a.getString(tareas == 1 ? R.string.eliminar_tareas_1 : R.string.eliminar_tareas_n, tareas);
        }
        msg += "\n\n" + a.getResources().getQuantityString(R.plurals.permanente, n);

        new AlertDialog.Builder(a)
                .setTitle(titulo)
                .setMessage(msg)
                .setPositiveButton(R.string.eliminar, (d, w) -> borrar())
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void borrar() {
        List<Integer> lista = new ArrayList<>(ids);
        for (int id : lista) {
            if (tipo == NOTAS) Repo.eliminarNota(id);
            else if (tipo == MATERIAS) Repo.eliminarMateria(id);
            else if (tipo == APUNTES) Repo.eliminarApunte(id);
            else Repo.eliminarTarea(id);
        }
        int hecho = tipo == NOTAS ? R.plurals.notas_eliminadas
                : tipo == APUNTES ? R.plurals.apuntes_eliminados
                : tipo == MATERIAS ? R.plurals.materias_eliminadas : R.plurals.tareas_eliminadas;
        Toast.makeText(a, a.getResources().getQuantityString(hecho, lista.size(), lista.size()), Toast.LENGTH_SHORT).show();
        ids.clear();
        actualizar();
    }

    private int dp(int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
