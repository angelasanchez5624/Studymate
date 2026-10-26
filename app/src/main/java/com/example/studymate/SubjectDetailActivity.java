package com.example.studymate;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SubjectDetailActivity extends AppCompatActivity {

    private int subjectId;
    private Seleccion selNotas; // para borrar una o varias notas (mantener presionado)
    private Seleccion selApuntes; // lo mismo para los apuntes
    private Repo.Handle handle;
    private org.osmdroid.views.MapView mapa;          // mapa pequeño de la ubicación
    private final java.util.List<org.osmdroid.views.overlay.Marker> marcadores = new java.util.ArrayList<>(); // un pin por salón

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapa.configurar(this); // antes de crear el mapa
        setContentView(R.layout.activity_subject_detail);
        Ui.insets(this);

        subjectId = getIntent().getIntExtra("subjectId", 1);
        selNotas = new Seleccion(this, Seleccion.NOTAS, this::cargar);
        selApuntes = new Seleccion(this, Seleccion.APUNTES, this::cargar);
        findViewById(R.id.btnAddApunte).setOnClickListener(v -> startActivity(
                new Intent(this, AddApunteActivity.class).putExtra("subjectId", subjectId)));
        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        // + de Tareas: nueva tarea con esta materia ya elegida
        findViewById(R.id.btnAddTarea).setOnClickListener(v -> startActivity(
                new Intent(this, AddTaskActivity.class).putExtra("subjectId", subjectId)));
        findViewById(R.id.btnAddNota).setOnClickListener(v -> {
            Intent i = new Intent(this, AddGradeActivity.class);
            i.putExtra("subjectId", subjectId);
            startActivity(i);
        });

        // Mapa pequeño (solo para ver): al tocarlo se abre "Mapa de mis clases" centrado en esta materia
        mapa = findViewById(R.id.mapaMateria);
        Mapa.preparar(mapa);
        mapa.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        findViewById(R.id.tapaMapa).setOnClickListener(v -> startActivity(
                new Intent(this, MapActivity.class).putExtra("subjectId", subjectId)));
        findViewById(R.id.btnEditarMateria).setOnClickListener(v -> {
            Intent i = new Intent(this, AddSubjectActivity.class);
            i.putExtra("subjectId", subjectId);
            startActivity(i);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        handle = Repo.listen(this::cargar);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    /**
     * Muestra la tarjeta del mapa solo si la materia tiene ubicación.
     * Si tiene clase en varios salones con ubicación distinta, sale un pin por cada uno.
     */
    private void mostrarUbicacion(Subject s) {
        View card = findViewById(R.id.cardUbicacion);
        java.util.List<Mapa.Lugar> lugares = Mapa.lugares(s);
        if (lugares.isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);

        mapa.getOverlays().removeAll(marcadores);
        marcadores.clear();
        java.util.List<org.osmdroid.util.GeoPoint> puntos = new java.util.ArrayList<>();
        for (Mapa.Lugar l : lugares) {
            marcadores.add(Mapa.marcador(mapa, l.punto, s.name, l.detalle()));
            puntos.add(l.punto);
        }
        if (puntos.size() == 1) {
            mapa.getController().setZoom(Mapa.ZOOM_LUGAR - 1);
            mapa.getController().setCenter(puntos.get(0));
        } else {
            org.osmdroid.util.BoundingBox caja = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(puntos);
            mapa.post(() -> mapa.zoomToBoundingBox(caja.increaseByScale(1.6f), false));
        }
        mapa.invalidate();

        // "Cómo llegar": directo si hay un solo lugar; si hay varios, pregunta a qué salón
        findViewById(R.id.btnComoLlegar).setOnClickListener(v -> {
            if (lugares.size() == 1) {
                Mapa.Lugar l = lugares.get(0);
                Mapa.comoLlegar(this, l.punto.getLatitude(), l.punto.getLongitude(), s.name);
                return;
            }
            String[] opciones = new String[lugares.size()];
            for (int i = 0; i < opciones.length; i++) {
                Mapa.Lugar l = lugares.get(i);
                // "AULA 112\nLun 8:00–10:00": el salón arriba y sus horarios debajo
                java.util.List<String> partes = new java.util.ArrayList<>();
                for (String sal : l.salones) if (!sal.isEmpty() && !partes.contains(sal)) partes.add(sal);
                for (String li : l.lineas) if (!li.isEmpty()) partes.add(li);
                opciones[i] = partes.isEmpty() ? getString(R.string.lugar_n, i + 1)
                        : android.text.TextUtils.join("\n", partes);
            }
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.a_que_salon)
                    .setItems(opciones, (d, i) -> {
                        Mapa.Lugar l = lugares.get(i);
                        Mapa.comoLlegar(this, l.punto.getLatitude(), l.punto.getLongitude(), s.name);
                    })
                    .setNegativeButton(R.string.cancelar, null)
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapa.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapa.onPause();
    }

    private void cargar() {
        // Si la materia fue eliminada, se cierra esta pantalla
        boolean existe = false;
        for (Subject x : Datos.SUBJECTS) if (x.id == subjectId) existe = true;
        if (!existe) {
            finish();
            return;
        }
        Subject s = Datos.subject(subjectId);
        ((TextView) findViewById(R.id.tvNombreMateria)).setText(s.name);
        mostrarUbicacion(s);
        // "Docente: Andrea Gómez"
        ((TextView) findViewById(R.id.tvProfesor)).setText(getString(R.string.docente_nombre,
                s.professor == null || s.professor.trim().isEmpty() ? "—" : s.professor.trim()));
        // Un horario por línea y los salones sin repetir. Si todas las clases son en el
        // mismo salón, no se repite en el horario (ya sale en la fila "Salón")
        String horarios = Horario.detalleSinSalonRepetido(s);
        String salones = Horario.salones(s);
        ((TextView) findViewById(R.id.tvHorario)).setText(horarios.isEmpty() ? "—" : horarios);
        ((TextView) findViewById(R.id.tvSalon)).setText(salones.isEmpty() ? "—" : salones);

        LinearLayout lista = findViewById(R.id.listTareasMateria);
        lista.removeAllViews();
        int total = 0;
        for (Task t : Datos.TASKS) {
            if (t.subjectId != s.id) continue;
            total++;
            View item = LayoutInflater.from(this).inflate(R.layout.item_task_mini, lista, false);
            TextView estado = item.findViewById(R.id.tvEstado);
            ((TextView) item.findViewById(R.id.tvNombre)).setText(
                    t.tipo != Task.TAREA ? TaskUtil.tipoTexto(t.tipo) + " · " + t.name : t.name);
            boolean vencida = TaskUtil.vencida(t);
            item.setBackgroundResource(t.done ? R.drawable.bg_task_mint
                    : (vencida ? R.drawable.bg_task_overdue : R.drawable.bg_task_yellow));
            boolean tarde = TaskUtil.entregadaTarde(t);
            estado.setText(t.done ? (tarde ? getString(R.string.entregada_tarde, TaskUtil.retraso(t)) : getString(R.string.completada))
                    : getString(vencida ? R.string.estado_vencida : R.string.estado_entrega, TaskUtil.entregaTexto(t)));
            estado.setTextColor(ContextCompat.getColor(this,
                    t.done ? (tarde ? R.color.red_text : R.color.mint_text)
                           : (vencida ? R.color.red_text : R.color.yellow_text)));
            // Al tocarla se abre para editarla
            item.setOnClickListener(v -> startActivity(
                    new Intent(this, AddTaskActivity.class).putExtra("taskId", t.id)));
            lista.addView(item);
        }
        ((TextView) findViewById(R.id.tvTituloTareas)).setText(getString(R.string.tareas_n, total));
        findViewById(R.id.tvSinTareas).setVisibility(total == 0 ? View.VISIBLE : View.GONE);

        // ---- Calificaciones ----
        double prom = GradeUtil.promedio(s.id);
        TextView tvProm = findViewById(R.id.tvPromedio);
        tvProm.setText(prom < 0 ? "—" : GradeUtil.fmt(prom));
        tvProm.setTextColor(ContextCompat.getColor(this,
                prom < 0 ? R.color.text_main : (prom >= GradeUtil.APRUEBA ? R.color.mint_text : R.color.red_text)));
        ((TextView) findViewById(R.id.tvMensajeNota)).setText(GradeUtil.mensaje(s.id));
        double evaluado = GradeUtil.pesoEvaluado(s.id);
        ((TextView) findViewById(R.id.tvPesoEvaluado)).setText(
                getString(R.string.evaluado, Math.round(evaluado), GradeUtil.fmt(GradeUtil.acumulado(s.id))));

        java.util.List<Integer> notasExisten = new java.util.ArrayList<>();
        for (Grade g : Datos.GRADES) if (g.subjectId == s.id) notasExisten.add(g.id);
        selNotas.podar(notasExisten);

        LinearLayout listaNotas = findViewById(R.id.listNotas);
        listaNotas.removeAllViews();
        int nNotas = 0;
        for (Grade g : Datos.GRADES) {
            if (g.subjectId != s.id) continue;
            nNotas++;
            agregarNota(listaNotas, g);
        }
        findViewById(R.id.tvSinNotas).setVisibility(nNotas == 0 ? View.VISIBLE : View.GONE);

        cargarApuntes(s);
    }

    /** Una fila de nota: nombre, cuánto vale y la nota en verde (aprobada) o rojo. */
    private void agregarNota(LinearLayout listaNotas, Grade g) {
        View fila = LayoutInflater.from(this).inflate(R.layout.item_grade, listaNotas, false);
        ((TextView) fila.findViewById(R.id.tvNombreNota)).setText(g.name);
        ((TextView) fila.findViewById(R.id.tvPesoNota)).setText(getString(R.string.vale, Math.round(g.weight)));
        TextView valor = fila.findViewById(R.id.tvValorNota);
        boolean aprobada = g.score >= GradeUtil.APRUEBA;
        valor.setText(GradeUtil.fmt(g.score));
        SubjectColors.tint(valor, ContextCompat.getColor(this, aprobada ? R.color.mint_bg : R.color.red_bg));
        valor.setTextColor(ContextCompat.getColor(this, aprobada ? R.color.mint_text : R.color.red_text));
        final int gid = g.id;
        // Mantener presionado marca la nota; si ya hay algo marcado, tocar marca/desmarca
        if (selNotas.esta(gid)) fila.setBackgroundResource(R.drawable.bg_card_selected);
        fila.setOnClickListener(v -> {
            if (selNotas.activa()) {
                selNotas.alternar(gid);
                return;
            }
            Intent i = new Intent(this, AddGradeActivity.class);
            i.putExtra("gradeId", gid);
            startActivity(i);
        });
        fila.setOnLongClickListener(v -> {
            selNotas.alternar(gid);
            return true;
        });
        listaNotas.addView(fila);
    }

    /** Lista de apuntes de la materia (los más recientes primero). */
    private void cargarApuntes(Subject s) {
        java.util.List<Integer> existen = new java.util.ArrayList<>();
        for (Apunte a : Datos.APUNTES) if (a.subjectId == s.id) existen.add(a.id);
        selApuntes.podar(existen);

        LinearLayout lista = findViewById(R.id.listApuntes);
        lista.removeAllViews();
        java.text.DateFormat formato = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, App.locale());
        int n = 0;
        for (Apunte a : Datos.APUNTES) {
            if (a.subjectId != s.id) continue;
            n++;
            View fila = LayoutInflater.from(this).inflate(R.layout.item_apunte, lista, false);
            ((TextView) fila.findViewById(R.id.tvTituloApunte)).setText(a.titulo);
            TextView texto = fila.findViewById(R.id.tvTextoApunte);
            texto.setText(a.texto);
            texto.setVisibility(a.texto == null || a.texto.isEmpty() ? View.GONE : View.VISIBLE);
            // Fecha, y si tiene foto o archivos: "23 oct 2026 · Con foto · 2 archivos" (con ícono de clip)
            TextView tvFecha = fila.findViewById(R.id.tvFechaApunte);
            tvFecha.setText(Apuntes.detalle(this, a, formato.format(new java.util.Date(a.fecha))));
            if (a.tieneFoto() || a.cuantosAdjuntos() > 0) Iconos.alInicio(tvFecha, a.cuantosAdjuntos() > 0 ? R.drawable.ic_adjunto : R.drawable.ic_camara);

            final int aid = a.id;
            // Igual que las notas: tocar abre el apunte, mantener presionado lo selecciona para borrar
            if (selApuntes.esta(aid)) fila.setBackgroundResource(R.drawable.bg_card_selected);
            fila.setOnClickListener(v -> {
                if (selApuntes.activa()) {
                    selApuntes.alternar(aid);
                    return;
                }
                startActivity(new Intent(this, AddApunteActivity.class).putExtra("apunteId", aid));
            });
            fila.setOnLongClickListener(v -> {
                selApuntes.alternar(aid);
                return true;
            });
            lista.addView(fila);
        }
        ((TextView) findViewById(R.id.tvTituloApuntes)).setText(n == 0
                ? getString(R.string.apuntes) : getString(R.string.apuntes_n, n));
        findViewById(R.id.tvSinApuntes).setVisibility(n == 0 ? View.VISIBLE : View.GONE);
    }
}
