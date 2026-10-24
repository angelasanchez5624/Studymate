package com.example.studymate;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Mis notas: el promedio general y una tarjeta por materia con su promedio,
 * lo que falta para pasar y todas sus notas.
 *  - Tocar el encabezado de una materia la abre.
 *  - Tocar una nota la edita; mantenerla presionada la selecciona para borrar.
 */
public class MisNotasActivity extends AppCompatActivity {

    private Repo.Handle handle;
    private Seleccion sel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_notas);
        Ui.insets(this);

        sel = new Seleccion(this, Seleccion.NOTAS, this::cargar);
        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddNota).setOnClickListener(v ->
                ElegirMateria.mostrar(this, AddGradeActivity.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        handle = Repo.listen(this::cargar);
        cargar(); // lo que ya hay, sin esperar a Firebase
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    private void cargar() {
        List<Integer> existen = new ArrayList<>();
        for (Grade g : Datos.GRADES) existen.add(g.id);
        sel.podar(existen);

        // ---- Promedio general ----
        int conNotas = 0;
        for (Subject s : Datos.SUBJECTS) if (GradeUtil.promedio(s.id) >= 0) conNotas++;
        double general = GradeUtil.promedioGeneral();
        TextView tvGeneral = findViewById(R.id.tvPromedioGeneral);
        tvGeneral.setText(general < 0 ? "—" : GradeUtil.fmt(general));
        tvGeneral.setTextColor(color(general < 0 ? R.color.text_main
                : general >= GradeUtil.APRUEBA ? R.color.mint_text : R.color.red_text));
        ((TextView) findViewById(R.id.tvPromedioDetalle)).setText(conNotas == 0
                ? getString(R.string.promedio_sin_notas)
                : getResources().getQuantityString(R.plurals.promedio_materias, conNotas, conNotas));

        // ---- Una tarjeta por materia ----
        LinearLayout lista = findViewById(R.id.listMaterias);
        lista.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (Subject s : Datos.SUBJECTS) {
            View card = inf.inflate(R.layout.item_materia_notas, lista, false);

            TextView tag = card.findViewById(R.id.tvMateriaTag);
            tag.setText(s.name);
            SubjectColors.tint(tag, SubjectColors.bg(this, s.id));
            tag.setTextColor(SubjectColors.text(this, s.id));

            double prom = GradeUtil.promedio(s.id);
            TextView tvProm = card.findViewById(R.id.tvPromedio);
            tvProm.setText(prom < 0 ? "—" : GradeUtil.fmt(prom));
            tvProm.setTextColor(color(prom < 0 ? R.color.text_muted
                    : prom >= GradeUtil.APRUEBA ? R.color.mint_text : R.color.red_text));
            ((TextView) card.findViewById(R.id.tvMensaje)).setText(GradeUtil.mensaje(s.id));
            TextView tvEval = card.findViewById(R.id.tvEvaluado);
            if (prom < 0) {
                tvEval.setVisibility(View.GONE);
            } else {
                tvEval.setText(getString(R.string.evaluado, Math.round(GradeUtil.pesoEvaluado(s.id)),
                        GradeUtil.fmt(GradeUtil.acumulado(s.id))));
            }
            card.findViewById(R.id.cabMateria).setOnClickListener(v -> {
                if (sel.activa()) return;
                startActivity(new Intent(this, SubjectDetailActivity.class).putExtra("subjectId", s.id));
            });

            LinearLayout notas = card.findViewById(R.id.listNotasMateria);
            for (Grade g : Datos.GRADES) if (g.subjectId == s.id) agregarNota(notas, g);
            notas.setVisibility(notas.getChildCount() == 0 ? View.GONE : View.VISIBLE);

            lista.addView(card);
        }

        TextView vacio = findViewById(R.id.tvVacio);
        if (Datos.SUBJECTS.isEmpty()) {
            vacio.setVisibility(View.VISIBLE);
            vacio.setText(Datos.materiasListas ? R.string.sin_materias : R.string.cargando);
        } else {
            vacio.setVisibility(View.GONE);
        }
    }

    /** Fila de una nota dentro de la tarjeta (sin sombra, porque ya está dentro de una tarjeta). */
    private void agregarNota(LinearLayout notas, Grade g) {
        View fila = LayoutInflater.from(this).inflate(R.layout.item_grade, notas, false);
        fila.setElevation(0);
        fila.setBackgroundResource(sel.esta(g.id) ? R.drawable.bg_card_selected : R.drawable.bg_round_14);
        if (!sel.esta(g.id)) SubjectColors.tint(fila, color(R.color.cream));
        ((TextView) fila.findViewById(R.id.tvNombreNota)).setText(g.name);
        ((TextView) fila.findViewById(R.id.tvPesoNota)).setText(getString(R.string.vale, Math.round(g.weight)));
        TextView valor = fila.findViewById(R.id.tvValorNota);
        boolean aprobada = g.score >= GradeUtil.APRUEBA;
        valor.setText(GradeUtil.fmt(g.score));
        SubjectColors.tint(valor, color(aprobada ? R.color.mint_bg : R.color.red_bg));
        valor.setTextColor(color(aprobada ? R.color.mint_text : R.color.red_text));

        final int gid = g.id;
        fila.setOnClickListener(v -> {
            if (sel.activa()) {
                sel.alternar(gid);
                return;
            }
            startActivity(new Intent(this, AddGradeActivity.class).putExtra("gradeId", gid));
        });
        fila.setOnLongClickListener(v -> {
            sel.alternar(gid);
            return true;
        });
        notas.addView(fila);
    }

    private int color(int id) {
        return ContextCompat.getColor(this, id);
    }
}
