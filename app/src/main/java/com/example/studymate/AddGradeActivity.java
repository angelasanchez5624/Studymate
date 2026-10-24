package com.example.studymate;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/** Crea una nota (extra "subjectId") o la edita (extra "gradeId"). */
public class AddGradeActivity extends AppCompatActivity {

    private Grade editando = null;
    private int subjectId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_grade);
        Ui.insets(this);

        EditText etNombre = findViewById(R.id.etNombreNota);
        EditText etNota = findViewById(R.id.etNota);
        EditText etPeso = findViewById(R.id.etPeso);

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelarNota).setOnClickListener(v -> finish());

        subjectId = getIntent().getIntExtra("subjectId", -1);
        int gradeId = getIntent().getIntExtra("gradeId", -1);
        if (gradeId != -1) {
            for (Grade g : Datos.GRADES) if (g.id == gradeId) editando = g;
            if (editando == null) {
                finish();
                return;
            }
            subjectId = editando.subjectId;
            ((TextView) findViewById(R.id.tvTituloNota)).setText(R.string.nota_editar);
            ((TextView) findViewById(R.id.btnGuardarNota)).setText(R.string.guardar_cambios);
            etNombre.setText(editando.name);
            etNota.setText(GradeUtil.fmt(editando.score));
            etPeso.setText(trim(editando.weight));
        } else {
            boolean existe = false;
            for (Subject s : Datos.SUBJECTS) if (s.id == subjectId) existe = true;
            if (!existe) {
                finish();
                return;
            }
        }

        // Vista previa: cada vez que cambias la nota o el porcentaje
        android.text.TextWatcher alEscribir = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { vistaPrevia(etNota, etPeso); }
        };
        etNota.addTextChangedListener(alEscribir);
        etPeso.addTextChangedListener(alEscribir);
        vistaPrevia(etNota, etPeso);

        findViewById(R.id.btnGuardarNota).setOnClickListener(v -> {
            String nombre = etNombre.getText().toString().trim();
            if (nombre.isEmpty()) {
                etNombre.setError(getString(R.string.nota_nombre_error));
                return;
            }
            Double nota = numero(etNota);
            if (nota == null || nota < 0 || nota > GradeUtil.MAX) {
                etNota.setError(getString(R.string.nota_rango_error, GradeUtil.fmt(GradeUtil.MAX)));
                return;
            }
            Double peso = numero(etPeso);
            if (peso == null || peso <= 0 || peso > 100) {
                etPeso.setError(getString(R.string.peso_error));
                return;
            }
            // Que los porcentajes de la materia no pasen de 100
            double otros = 0;
            for (Grade g : Datos.GRADES) {
                if (g.subjectId == subjectId && (editando == null || g.id != editando.id)) otros += g.weight;
            }
            if (otros + peso > 100.0001) {
                etPeso.setError(getString(R.string.peso_max_error, trim(otros), trim(100 - otros)));
                return;
            }
            int id = editando != null ? editando.id : Datos.nextGradeId();
            Repo.guardarNota(new Grade(id, subjectId, nombre, nota, peso));
            Toast.makeText(this, editando != null ? R.string.cambios_guardados : R.string.nota_guardada, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    /**
     * Muestra cómo quedaría la materia con la nota que estás escribiendo:
     * lo que llevarías acumulado y lo que necesitarías en lo que falta para pasar.
     */
    private void vistaPrevia(EditText etNota, EditText etPeso) {
        // Lo que ya está registrado en la materia (sin contar la nota que se está editando)
        double otrosPeso = 0, otrosAcum = 0;
        for (Grade g : Datos.GRADES) {
            if (g.subjectId != subjectId || (editando != null && g.id == editando.id)) continue;
            otrosPeso += g.weight;
            otrosAcum += g.score * g.weight / 100.0;
        }
        Double nota = numero(etNota);
        Double peso = numero(etPeso);
        boolean valida = nota != null && peso != null && nota >= 0 && nota <= GradeUtil.MAX
                && peso > 0 && otrosPeso + peso <= 100.0001;

        View card = findViewById(R.id.cardVistaPrevia);
        if (!valida && otrosPeso <= 0) { // sin datos todavía: no se muestra nada
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        double peso2 = valida ? otrosPeso + peso : otrosPeso;
        double acum2 = valida ? otrosAcum + nota * peso / 100.0 : otrosAcum;
        ((TextView) findViewById(R.id.tvVistaAcumulado)).setText(getString(
                valida ? R.string.vista_llevarias : R.string.vista_llevas, GradeUtil.fmt(acum2)));
        ((TextView) findViewById(R.id.tvVistaEvaluado)).setText(getString(R.string.vista_evaluado, trim(peso2)));
        ((TextView) findViewById(R.id.tvVistaMensaje)).setText(GradeUtil.mensaje(peso2, acum2));
    }

    private Double numero(EditText et) {
        try {
            return Double.parseDouble(et.getText().toString().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : GradeUtil.fmt(v);
    }
}
