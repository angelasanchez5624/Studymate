package com.example.studymate;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TasksActivity extends AppCompatActivity {

    private static final String PREFS = "studymate_tareas";

    private String filtro = "all";  // all | pending | done
    private String orden = "fecha"; // fecha | materia | prioridad (se recuerda)
    private String busqueda = "";
    private final List<Task> mostradas = new ArrayList<>();
    private TaskAdapter adapter;
    private Seleccion sel;
    private Repo.Handle handle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);
        Ui.insets(this);
        BottomNavHelper.setup(this, BottomNavHelper.TAREAS);

        orden = getSharedPreferences(PREFS, MODE_PRIVATE).getString("orden", "fecha");
        // Desde Inicio: "pending" (Pendientes) o "done" (Completadas)
        String filtroInicial = getIntent().getStringExtra("filtro");
        if (filtroInicial != null) filtro = filtroInicial;

        RecyclerView rv = findViewById(R.id.rvTareas);
        rv.setLayoutManager(new LinearLayoutManager(this));
        // Al marcar una tarea se guarda en Firebase; el listener refresca la pantalla
        adapter = new TaskAdapter(mostradas, t -> {
            // Si la marcas como hecha cuando ya pasó la hora de entrega, te avisa
            if (!t.done && TaskUtil.vencida(t)) {
                Toast.makeText(this, getString(R.string.entrega_tardia, TaskUtil.retraso(t)), Toast.LENGTH_LONG).show();
            }
            Repo.marcarTarea(t.id, !t.done);
        });
        adapter.setOnClick(t -> {
            Intent i = new Intent(this, AddTaskActivity.class);
            i.putExtra("taskId", t.id);
            startActivity(i);
        });
        rv.setAdapter(adapter);
        // Mantén presionada una tarea para seleccionar una o varias y borrarlas
        sel = new Seleccion(this, Seleccion.TAREAS, this::refrescar);
        adapter.setSeleccion(sel);

        // Filtros
        findViewById(R.id.btnFiltroTodas).setOnClickListener(v -> { filtro = "all"; refrescar(); });
        findViewById(R.id.btnFiltroPendientes).setOnClickListener(v -> { filtro = "pending"; refrescar(); });
        findViewById(R.id.btnFiltroCompletadas).setOnClickListener(v -> { filtro = "done"; refrescar(); });

        // Ordenar
        findViewById(R.id.btnOrdenar).setOnClickListener(this::menuOrdenar);

        // Buscar mientras escribes
        EditText etBuscar = findViewById(R.id.etBuscar);
        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                busqueda = normalizar(s.toString().trim());
                refrescar();
            }
        });

        findViewById(R.id.btnAddTarea).setOnClickListener(v -> startActivity(new Intent(this, AddTaskActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        handle = Repo.listen(this::refrescar);
        refrescar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    /** Menú que se despliega debajo del ícono: Fecha / Materia / Prioridad (marca el actual). */
    private void menuOrdenar(View ancla) {
        String[] claves = {"fecha", "materia", "prioridad"};
        int[] textos = {R.string.orden_fecha, R.string.orden_materia, R.string.orden_prioridad};
        android.widget.PopupMenu menu = new android.widget.PopupMenu(this, ancla);
        menu.getMenu().add(1, -1, 0, R.string.ordenar).setEnabled(false); // título
        for (int i = 0; i < claves.length; i++) {
            menu.getMenu().add(0, i, i + 1, textos[i]).setChecked(claves[i].equals(orden));
        }
        menu.getMenu().setGroupCheckable(0, true, true); // se ve como opciones para marcar
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() < 0) return false;
            cambiarOrden(claves[item.getItemId()]);
            return true;
        });
        menu.show();
    }

    private void cambiarOrden(String nuevo) {
        orden = nuevo;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("orden", nuevo).apply();
        refrescar();
    }

    private void refrescar() {
        mostradas.clear();
        for (Task t : Datos.TASKS) {
            boolean pasaFiltro = filtro.equals("all")
                    || (filtro.equals("pending") && !t.done)
                    || (filtro.equals("done") && t.done);
            if (pasaFiltro && coincide(t)) mostradas.add(t);
        }
        // Solo quedan marcadas las tareas que se ven (si cambias el filtro o la búsqueda, o si se borró algo)
        java.util.List<Integer> existen = new java.util.ArrayList<>();
        for (Task t : mostradas) existen.add(t.id);
        if (sel != null) sel.podar(existen);

        // Siempre: pendientes primero y hechas al final. Dentro de cada grupo, según el orden elegido.
        Collections.sort(mostradas, (a, b) -> {
            if (a.done != b.done) return a.done ? 1 : -1;
            int r;
            switch (orden) {
                case "materia":
                    r = normalizar(Datos.subject(a.subjectId).name)
                            .compareTo(normalizar(Datos.subject(b.subjectId).name));
                    if (r != 0) return r;
                    return porFecha(a, b);
                case "prioridad":
                    r = Integer.compare(b.priority, a.priority); // alta primero
                    if (r != 0) return r;
                    return porFecha(a, b);
                default: // fecha
                    r = porFecha(a, b);
                    if (r != 0) return r;
                    return Integer.compare(b.priority, a.priority);
            }
        });
        adapter.notifyDataSetChanged();

        // "Ordenar: Prioridad ▾"
        int textoOrden = orden.equals("materia") ? R.string.orden_materia
                : orden.equals("prioridad") ? R.string.orden_prioridad : R.string.orden_fecha;
        ((TextView) findViewById(R.id.tvOrden)).setText(getString(R.string.ordenar_actual, getString(textoOrden)));

        TextView vacio = findViewById(R.id.tvVacio);
        vacio.setText(!Datos.tareasListas ? R.string.cargando
                : busqueda.isEmpty() ? R.string.sin_tareas_categoria : R.string.sin_coincidencias);
        vacio.setVisibility(mostradas.isEmpty() ? View.VISIBLE : View.GONE);

        estiloChip(R.id.btnFiltroTodas, filtro.equals("all"));
        estiloChip(R.id.btnFiltroPendientes, filtro.equals("pending"));
        estiloChip(R.id.btnFiltroCompletadas, filtro.equals("done"));
    }

    /** Por fecha y hora de entrega (la más próxima primero). */
    private static int porFecha(Task a, Task b) {
        return Long.compare(TaskUtil.entregaMillis(a), TaskUtil.entregaMillis(b));
    }

    /** ¿La tarea contiene el texto buscado en su nombre, descripción o materia? */
    private boolean coincide(Task t) {
        if (busqueda.isEmpty()) return true;
        return normalizar(t.name).contains(busqueda)
                || normalizar(t.description).contains(busqueda)
                || normalizar(Datos.subject(t.subjectId).name).contains(busqueda);
    }

    /** Minúsculas y sin tildes, para que "calculo" encuentre "Cálculo". */
    private static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private void estiloChip(int id, boolean activo) {
        TextView tv = findViewById(id);
        tv.setBackgroundResource(activo ? R.drawable.bg_filter_on : R.drawable.bg_filter_off);
        tv.setTextColor(ContextCompat.getColor(this, activo ? R.color.white : R.color.text_muted));
    }
}
