package com.example.studymate;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Estadísticas del estudiante:
 *  - Resumen: completadas, % a tiempo y tardías.
 *  - Gráfica de barras: tareas completadas en cada una de las últimas 8 semanas.
 *  - Avance por materia: cuántas tareas de cada materia están hechas.
 */
public class StatsActivity extends AppCompatActivity {

    private static final int SEMANAS = 8;
    private Repo.Handle handle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        Ui.insets(this);
        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        handle = Repo.listen(this::dibujar);
        dibujar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    private void dibujar() {
        dibujarResumen();
        dibujarGrafica();
        dibujarMaterias();
    }

    // ------------------------------------------------------------------
    // Resumen
    // ------------------------------------------------------------------

    private void dibujarResumen() {
        int completadas = 0, tarde = 0;
        for (Task t : Datos.TASKS) {
            if (!t.done) continue;
            completadas++;
            if (TaskUtil.entregadaTarde(t)) tarde++;
        }
        String aTiempo = completadas == 0 ? "—" : Math.round((completadas - tarde) * 100f / completadas) + "%";

        LinearLayout fila = findViewById(R.id.filaResumen);
        fila.removeAllViews();
        fila.addView(tarjeta(R.drawable.ic_hecho, String.valueOf(completadas), getString(R.string.completadas), R.color.mint_bg, R.color.mint_text, 0));
        fila.addView(tarjeta(R.drawable.ic_reloj, aTiempo, getString(R.string.a_tiempo), R.color.blue_bg, R.color.blue_text, 10));
        // Promedio general (de las materias que ya tienen notas)
        int conNotas = 0;
        for (Subject s : Datos.SUBJECTS) if (GradeUtil.promedio(s.id) >= 0) conNotas++;
        double general = GradeUtil.promedioGeneral();
        TextView tvGeneral = findViewById(R.id.tvPromedioGeneral);
        tvGeneral.setText(general < 0 ? "—" : GradeUtil.fmt(general));
        tvGeneral.setTextColor(ContextCompat.getColor(this, general < 0 ? R.color.text_main
                : general >= GradeUtil.APRUEBA ? R.color.mint_text : R.color.red_text));
        ((TextView) findViewById(R.id.tvPromedioDetalle)).setText(conNotas == 0
                ? getString(R.string.promedio_sin_notas)
                : getResources().getQuantityString(R.plurals.promedio_materias, conNotas, conNotas));

        fila.addView(tarjeta(R.drawable.ic_alerta, String.valueOf(tarde), getString(R.string.tardias), R.color.pink_bg, R.color.pink_text, 10));
    }

    private View tarjeta(int icono, String valor, String etiqueta, int fondo, int color, int margenIzq) {
        LinearLayout t = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(dp(margenIzq));
        t.setLayoutParams(lp);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(8), dp(14), dp(8), dp(14));
        t.setBackgroundResource(R.drawable.bg_card_20);
        SubjectColors.tint(t, ContextCompat.getColor(this, fondo));

        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageResource(icono);
        iv.setColorFilter(ContextCompat.getColor(this, color));
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        t.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView v = texto(valor, 24, color, true);
        t.addView(v);
        t.addView(texto(etiqueta, 12, R.color.text_muted, false));
        return t;
    }

    // ------------------------------------------------------------------
    // Gráfica de barras
    // ------------------------------------------------------------------

    private void dibujarGrafica() {
        // Lunes de esta semana a las 00:00
        Calendar lunes = Calendar.getInstance();
        lunes.set(Calendar.HOUR_OF_DAY, 0);
        lunes.set(Calendar.MINUTE, 0);
        lunes.set(Calendar.SECOND, 0);
        lunes.set(Calendar.MILLISECOND, 0);
        lunes.add(Calendar.DAY_OF_MONTH, -((lunes.get(Calendar.DAY_OF_WEEK) + 5) % 7));

        long[] inicio = new long[SEMANAS];
        int[] cuenta = new int[SEMANAS];
        for (int i = 0; i < SEMANAS; i++) {
            Calendar c = (Calendar) lunes.clone();
            c.add(Calendar.WEEK_OF_YEAR, -(SEMANAS - 1 - i)); // la última es esta semana
            inicio[i] = c.getTimeInMillis();
        }
        long finUltima = inicio[SEMANAS - 1] + 7L * 24 * 60 * 60 * 1000;

        for (Task t : Datos.TASKS) {
            if (!t.done) continue;
            // Cuándo se completó; las tareas viejas (sin ese dato) cuentan en su fecha de entrega
            long cuando = t.completedAt > 0 ? t.completedAt : TaskUtil.entregaMillis(t);
            if (cuando < inicio[0] || cuando >= finUltima) continue;
            for (int i = SEMANAS - 1; i >= 0; i--) {
                if (cuando >= inicio[i]) { cuenta[i]++; break; }
            }
        }

        int max = 1;
        for (int n : cuenta) max = Math.max(max, n);

        LinearLayout grafica = findViewById(R.id.grafica);
        grafica.removeAllViews();
        SimpleDateFormat corta = new SimpleDateFormat("d MMM", App.locale());
        int altoMax = dp(130);

        for (int i = 0; i < SEMANAS; i++) {
            boolean actual = (i == SEMANAS - 1);
            LinearLayout col = new LinearLayout(this);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            col.addView(texto(String.valueOf(cuenta[i]), 12, actual ? R.color.blue_text : R.color.text_muted, true));

            View barra = new View(this);
            int alto = cuenta[i] == 0 ? dp(4) : Math.max(dp(8), altoMax * cuenta[i] / max);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), alto);
            lp.topMargin = dp(4);
            barra.setLayoutParams(lp);
            barra.setBackgroundResource(R.drawable.bg_barra);
            SubjectColors.tint(barra, ContextCompat.getColor(this,
                    actual ? R.color.boton : R.color.lilac_pastel));
            col.addView(barra);

            TextView etiqueta = texto(corta.format(new java.util.Date(inicio[i])), 10, R.color.text_muted, actual);
            etiqueta.setPadding(0, dp(6), 0, 0);
            col.addView(etiqueta);

            grafica.addView(col);
        }
    }

    // ------------------------------------------------------------------
    // Avance por materia
    // ------------------------------------------------------------------

    private void dibujarMaterias() {
        LinearLayout lista = findViewById(R.id.listMaterias);
        lista.removeAllViews();

        boolean alguna = false;
        for (Subject s : Datos.SUBJECTS) {
            int total = 0, hechas = 0;
            for (Task t : Datos.TASKS) {
                if (t.subjectId != s.id) continue;
                total++;
                if (t.done) hechas++;
            }
            if (total == 0) continue;
            alguna = true;

            // Nombre y "3/5"
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setPadding(0, dp(14), 0, dp(6));
            TextView nombre = texto(s.name, 13, R.color.text_main, true);
            nombre.setGravity(Gravity.START);
            nombre.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            fila.addView(nombre);
            fila.addView(texto(hechas + "/" + total, 13, R.color.text_muted, false));
            lista.addView(fila);

            // Barra de progreso: fondo suave + parte llena con el color de la materia
            LinearLayout fondo = new LinearLayout(this);
            fondo.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
            fondo.setBackgroundResource(R.drawable.bg_barra);
            SubjectColors.tint(fondo, SubjectColors.bg(this, s.id));
            fondo.setWeightSum(total);

            View lleno = new View(this);
            lleno.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, hechas));
            lleno.setBackgroundResource(R.drawable.bg_barra);
            SubjectColors.tint(lleno, SubjectColors.text(this, s.id));
            fondo.addView(lleno);
            lista.addView(fondo);
        }

        if (!alguna) {
            TextView vacio = texto(getString(R.string.sin_tareas_registradas), 13, R.color.text_muted, false);
            vacio.setPadding(0, dp(14), 0, 0);
            lista.addView(vacio);
        }
    }

    // ------------------------------------------------------------------

    private TextView texto(String t, int sp, int color, boolean negrita) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(sp);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(ContextCompat.getColor(this, color));
        if (negrita) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
