package com.example.studymate;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Crea una tarea, o la edita si se abre con el extra "taskId". */
public class AddTaskActivity extends AppCompatActivity {

    private EditText etNombre, etFecha, etHora, etDescripcion;
    private Spinner spMateria, spPrioridad, spTipo;
    private Task editando = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);
        Ui.insets(this);

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelarTarea).setOnClickListener(v -> finish());

        // Una tarea necesita una materia
        if (Datos.SUBJECTS.isEmpty()) {
            Toast.makeText(this, R.string.primero_materia, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // ¿Modo edición?
        int taskId = getIntent().getIntExtra("taskId", -1);
        if (taskId != -1) {
            for (Task t : Datos.TASKS) if (t.id == taskId) editando = t;
            if (editando == null) {
                finish();
                return;
            }
        }

        etNombre = findViewById(R.id.etNombreTarea);
        etFecha = findViewById(R.id.etFecha);
        etHora = findViewById(R.id.etHora);
        etDescripcion = findViewById(R.id.etDescripcion);
        spMateria = findViewById(R.id.spMateria);
        spPrioridad = findViewById(R.id.spPrioridad);
        spTipo = findViewById(R.id.spTipo);
        Ui.lista(spTipo, getResources().getStringArray(R.array.tipos_tarea), false);
        Ui.lista(spPrioridad, new String[]{getString(R.string.prio_baja), getString(R.string.prio_media),
                getString(R.string.prio_alta)}, false);
        spPrioridad.setSelection(Task.MEDIA);

        List<String> nombres = new ArrayList<>();
        for (Subject s : Datos.SUBJECTS) nombres.add(s.name);
        Ui.lista(spMateria, nombres.toArray(new String[0]), false);

        // Fecha de entrega: se guarda "2026-10-17" en el tag y se muestra "Sáb, 17 oct"
        etFecha.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            try {
                c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(leerFecha()));
            } catch (Exception ignorar) { }
            new DatePickerDialog(this, (view, y, m, d) ->
                    ponerFecha(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Hora de entrega: reloj de 12 h; se guarda en 24 h en el tag y se muestra con a. m. / p. m.
        etHora.setOnClickListener(v -> {
            int min = Horario.minutos(leerHora());
            int h = min >= 0 ? min / 60 : 23;
            int m = min >= 0 ? min % 60 : 59;
            new TimePickerDialog(this, (view, hh, mm) ->
                    ponerHora(String.format(Locale.US, "%02d:%02d", hh, mm)), h, m, Ajustes.hora24).show();
        });

        if (editando != null) {
            ((TextView) findViewById(R.id.tvTituloTarea)).setText(R.string.tarea_editar);
            ((TextView) findViewById(R.id.btnGuardarTarea)).setText(R.string.guardar_cambios);
            etNombre.setText(editando.name);
            ponerFecha(editando.dueDate);
            spTipo.setSelection(Math.max(0, Math.min(Task.PROYECTO, editando.tipo)));
            ponerHora(editando.dueTime);
            etDescripcion.setText(editando.description);
            spPrioridad.setSelection(Math.max(0, Math.min(2, editando.priority)));
            for (int i = 0; i < Datos.SUBJECTS.size(); i++) {
                if (Datos.SUBJECTS.get(i).id == editando.subjectId) spMateria.setSelection(i);
            }
        } else {
            // Fecha por defecto: hoy
            ponerFecha(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
            ponerHora("23:59"); // hora por defecto: 11:59 p. m.
            // Si se abrió desde una materia, esa materia ya viene elegida
            int materia = getIntent().getIntExtra("subjectId", -1);
            for (int i = 0; i < Datos.SUBJECTS.size(); i++) {
                if (Datos.SUBJECTS.get(i).id == materia) spMateria.setSelection(i);
            }
        }

        findViewById(R.id.btnGuardarTarea).setOnClickListener(v -> guardar());
    }

    private void guardar() {
        String nombre = etNombre.getText().toString().trim();
        if (nombre.isEmpty()) {
            etNombre.setError(getString(R.string.tarea_nombre_error));
            return;
        }
        Subject materia = Datos.SUBJECTS.get(spMateria.getSelectedItemPosition());
        int id = editando != null ? editando.id : Datos.nextTaskId();
        boolean hecha = editando != null && editando.done;

        Task t = new Task(id, nombre, materia.id,
                leerFecha(), etDescripcion.getText().toString().trim(), hecha,
                spPrioridad.getSelectedItemPosition());
        t.dueTime = leerHora();
        t.tipo = spTipo.getSelectedItemPosition();
        if (editando != null) t.completedAt = editando.completedAt; // no perder cuándo se completó
        Repo.guardarTarea(t);
        Toast.makeText(this, editando != null ? R.string.cambios_guardados : R.string.tarea_guardada, Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Guarda la fecha "yyyy-MM-dd" en el tag y la muestra bonita: "Sáb, 17 oct". */
    private void ponerFecha(String iso) {
        etFecha.setTag(iso);
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            String txt = new SimpleDateFormat(getString(R.string.formato_fecha_media), App.locale()).format(d);
            etFecha.setText(txt.substring(0, 1).toUpperCase() + txt.substring(1));
        } catch (Exception e) {
            etFecha.setText(iso);
        }
    }

    private String leerFecha() {
        Object t = etFecha.getTag();
        return t == null ? "" : t.toString();
    }

    /** Guarda la hora en 24 h (tag) y muestra "2:00 p. m.". */
    private void ponerHora(String hhmm) {
        String valor = hhmm == null ? "" : hhmm.trim();
        etHora.setTag(valor);
        etHora.setText(valor.isEmpty() ? "" : Horario.ampm(valor));
    }

    private String leerHora() {
        Object t = etHora.getTag();
        return t == null ? "" : t.toString();
    }
}
