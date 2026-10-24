package com.example.studymate;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Calendario mensual. Cada día con entregas muestra puntos de colores:
 * amarillo = pendiente, rojo = vencida, verde = completada.
 * Al tocar un día se listan sus entregas abajo.
 */
public class CalendarActivity extends AppCompatActivity {

    private final Calendar mes = Calendar.getInstance();   // mes que se está viendo
    private String diaElegido = TaskUtil.hoy();            // "yyyy-MM-dd"
    private final List<Task> tareasDelDia = new ArrayList<>();
    private TaskAdapter adapter;
    private Seleccion sel;
    private Repo.Handle handle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        Ui.insets(this);

        mes.set(Calendar.DAY_OF_MONTH, 1);

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnMesAnterior).setOnClickListener(v -> { mes.add(Calendar.MONTH, -1); dibujar(); });
        findViewById(R.id.btnMesSiguiente).setOnClickListener(v -> { mes.add(Calendar.MONTH, 1); dibujar(); });

        // Lun Mar Mié Jue Vie Sáb Dom
        LinearLayout fila = findViewById(R.id.filaDiasSemana);
        for (int k = 0; k < 7; k++) {
            String d = Horario.corto(k);
            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            tv.setGravity(Gravity.CENTER);
            tv.setText(d);
            tv.setTextSize(12);
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            fila.addView(tv);
        }

        // Lista de entregas del día (misma tarjeta que en Tareas)
        RecyclerView rv = findViewById(R.id.rvDia);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(tareasDelDia, t -> {
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
        // Mantén presionada una entrega para seleccionar una o varias y borrarlas
        sel = new Seleccion(this, Seleccion.TAREAS, this::mostrarDia);
        adapter.setSeleccion(sel);
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

    /** Dibuja el mes completo y la lista del día elegido. */
    private void dibujar() {
        ((TextView) findViewById(R.id.tvMes)).setText(getResources().getStringArray(R.array.meses)[mes.get(Calendar.MONTH)] + " " + mes.get(Calendar.YEAR));

        LinearLayout grid = findViewById(R.id.gridCalendario);
        grid.removeAllViews();

        Calendar c = (Calendar) mes.clone();
        int diasDelMes = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        int huecos = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // celdas vacías antes del día 1 (semana empieza lunes)
        String hoy = TaskUtil.hoy();
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        LinearLayout semana = null;
        int total = huecos + diasDelMes;
        int celdas = ((total + 6) / 7) * 7;
        for (int i = 0; i < celdas; i++) {
            if (i % 7 == 0) {
                semana = new LinearLayout(this);
                semana.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(semana);
            }
            int dia = i - huecos + 1;
            if (dia < 1 || dia > diasDelMes) {
                semana.addView(celdaVacia());
                continue;
            }
            c.set(Calendar.DAY_OF_MONTH, dia);
            String fecha = iso.format(c.getTime());
            semana.addView(celda(dia, fecha, fecha.equals(hoy), fecha.equals(diaElegido)));
        }

        mostrarDia();
    }

    private View celdaVacia() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
        return v;
    }

    /** Un día: número arriba y hasta 3 puntos de colores abajo. */
    private View celda(int dia, String fecha, boolean esHoy, boolean elegido) {
        LinearLayout cel = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        cel.setLayoutParams(lp);
        cel.setOrientation(LinearLayout.VERTICAL);
        cel.setGravity(Gravity.CENTER);
        if (elegido) cel.setBackgroundResource(R.drawable.bg_dia_seleccionado);
        else if (esHoy) cel.setBackgroundResource(R.drawable.bg_dia_hoy);

        TextView num = new TextView(this);
        num.setText(String.valueOf(dia));
        num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        num.setGravity(Gravity.CENTER);
        num.setTextColor(ContextCompat.getColor(this, elegido ? R.color.white : R.color.text_main));
        if (esHoy || elegido) num.setTypeface(num.getTypeface(), Typeface.BOLD);
        cel.addView(num);

        // Puntos: uno por estado que haya ese día (pendiente, vencida, completada)
        boolean pendiente = false, vencida = false, hecha = false;
        for (Task t : Datos.TASKS) {
            if (!fecha.equals(t.dueDate)) continue;
            if (t.done) hecha = true;
            else if (TaskUtil.vencida(t)) vencida = true;
            else pendiente = true;
        }
        LinearLayout puntos = new LinearLayout(this);
        // Solo del ancho de las marcas (así el fondito del día seleccionado es una pastilla pequeña)
        LinearLayout.LayoutParams lpPuntos = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpPuntos.gravity = Gravity.CENTER_HORIZONTAL;
        lpPuntos.topMargin = dp(1);
        puntos.setLayoutParams(lpPuntos);
        puntos.setOrientation(LinearLayout.HORIZONTAL);
        puntos.setGravity(Gravity.CENTER);
        puntos.setMinimumHeight(dp(11));
        // Cada estado tiene su color y su forma: punto amarillo, círculo rojo con ! y círculo verde con ✓
        // (no depende solo del color, WCAG 1.4.1)
        if (pendiente) puntos.addView(punto(R.drawable.ic_estado_pendiente));
        if (vencida) puntos.addView(punto(R.drawable.ic_estado_vencida));
        if (hecha) puntos.addView(punto(R.drawable.ic_estado_completada));
        if (elegido && (pendiente || vencida || hecha)) {
            puntos.setBackgroundResource(R.drawable.bg_marcas_dia);
            puntos.setPadding(dp(2), dp(1), dp(2), dp(1));
        }
        cel.addView(puntos);

        // TalkBack: "15. Tiene entregas pendientes, vencidas"
        StringBuilder cd = new StringBuilder(String.valueOf(dia));
        if (pendiente || vencida || hecha) {
            cd.append(". ").append(getString(R.string.cal_tiene));
            java.util.List<String> estados = new java.util.ArrayList<>();
            if (pendiente) estados.add(getString(R.string.cal_pendientes));
            if (vencida) estados.add(getString(R.string.cal_vencidas));
            if (hecha) estados.add(getString(R.string.cal_completadas));
            cd.append(' ').append(android.text.TextUtils.join(", ", estados));
        }
        cel.setContentDescription(cd);
        cel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        num.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        cel.setOnClickListener(v -> {
            diaElegido = fecha;
            dibujar();
        });
        return cel;
    }

    /** Marca de estado bajo el número del día: punto amarillo, círculo rojo con ! o círculo verde con ✓. */
    private View punto(int icono) {
        android.widget.ImageView p = new android.widget.ImageView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(1), 0, dp(1), 0);
        lp.gravity = Gravity.CENTER_VERTICAL;
        p.setLayoutParams(lp);
        p.setImageResource(icono);
        p.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return p;
    }

    /** Lista abajo las entregas del día elegido. */
    /** Clases del día elegido (según el horario de cada materia), ordenadas por hora. */
    private void mostrarClasesDelDia() {
        Calendar c = Calendar.getInstance();
        try {
            c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(diaElegido));
        } catch (Exception ignorar) { }
        int dia = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // 0 = lunes

        List<Subject> materias = new ArrayList<>();
        List<Bloque> bloques = new ArrayList<>();
        for (Subject s : Datos.SUBJECTS) {
            for (Bloque b : Horario.bloquesDelDia(s, dia)) {
                materias.add(s);
                bloques.add(b);
            }
        }
        // Ordenar por hora de inicio
        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < bloques.size(); i++) orden.add(i);
        Collections.sort(orden, (x, y) -> Integer.compare(
                Horario.minutos(bloques.get(x).startTime), Horario.minutos(bloques.get(y).startTime)));

        String titulo = diaElegido.equals(TaskUtil.hoy()) ? getString(R.string.clases_hoy_cal)
                : getString(R.string.clases_del, nombreDia(dia), SubjectColors.fechaCorta(diaElegido));
        ((TextView) findViewById(R.id.tvTituloClasesDia)).setText(titulo);

        LinearLayout lista = findViewById(R.id.listClasesDia);
        lista.removeAllViews();
        for (int i : orden) {
            Subject s = materias.get(i);
            Bloque b = bloques.get(i);
            View item = getLayoutInflater().inflate(R.layout.item_class, lista, false);
            ((TextView) item.findViewById(R.id.tvNombre)).setText(s.name);
            String hora = Horario.rango(b.startTime, b.endTime);
            ((TextView) item.findViewById(R.id.tvDetalle)).setText(hora.isEmpty() ? getString(R.string.sin_horario) : hora);
            SubjectColors.tint(item.findViewById(R.id.tvIcono), SubjectColors.bg(this, s.id));
            ((android.widget.ImageView) item.findViewById(R.id.tvIcono)).setColorFilter(SubjectColors.text(this, s.id));
            TextView tag = item.findViewById(R.id.tvSalon);
            tag.setText(b.room);
            tag.setVisibility(b.room == null || b.room.isEmpty() ? View.GONE : View.VISIBLE);
            SubjectColors.tint(tag, SubjectColors.bg(this, s.id));
            tag.setTextColor(SubjectColors.text(this, s.id));
            // Al tocarla se abre la materia
            item.setOnClickListener(v -> startActivity(
                    new Intent(this, SubjectDetailActivity.class).putExtra("subjectId", s.id)));
            lista.addView(item);
        }
        if (orden.isEmpty()) {
            TextView vacio = new TextView(this);
            vacio.setText(R.string.cal_sin_clases);
            vacio.setTextSize(14);
            vacio.setGravity(Gravity.CENTER);
            vacio.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            vacio.setPadding(0, dp(8), 0, dp(8));
            lista.addView(vacio);
        }
    }

    /** Nombre del día con inicial mayúscula: "Lunes" / "Monday". */
    private String nombreDia(int dia) {
        String n = Horario.largo(dia);
        if (n.isEmpty()) return n;
        return n.substring(0, 1).toUpperCase(App.locale()) + n.substring(1); // "Domingo"
    }

    private void mostrarDia() {
        mostrarClasesDelDia();
        tareasDelDia.clear();
        for (Task t : Datos.TASKS) if (diaElegido.equals(t.dueDate)) tareasDelDia.add(t);
        // Al cambiar de día (o si se borró algo) solo quedan marcadas las tareas de este día
        java.util.List<Integer> existen = new java.util.ArrayList<>();
        for (Task t : tareasDelDia) existen.add(t.id);
        if (sel != null) sel.podar(existen);
        Collections.sort(tareasDelDia, (a, b) -> Long.compare(TaskUtil.entregaMillis(a), TaskUtil.entregaMillis(b)));
        adapter.notifyDataSetChanged();

        String titulo = diaElegido.equals(TaskUtil.hoy()) ? getString(R.string.entregas_hoy)
                : getString(R.string.entregas_del, SubjectColors.fechaCorta(diaElegido));
        ((TextView) findViewById(R.id.tvTituloDia)).setText(titulo);
        findViewById(R.id.tvSinEntregas).setVisibility(tareasDelDia.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
