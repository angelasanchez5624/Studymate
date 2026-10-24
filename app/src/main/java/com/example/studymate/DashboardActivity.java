package com.example.studymate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    /** Una clase: la materia y el horario (bloque) que le toca. */
    private static class Clase {
        final Subject materia;
        final Bloque bloque;
        Clase(Subject materia, Bloque bloque) {
            this.materia = materia;
            this.bloque = bloque;
        }
    }

    private Repo.Handle handle;
    private MenuLateral menu; // menú ☰ (el mismo de Perfil)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        Ui.insets(this);
        BottomNavHelper.setup(this, BottomNavHelper.INICIO);

        // Fecha: "Jueves, 1 de octubre" / "Thursday, October 1" (el formato está en strings.xml)
        String f = new SimpleDateFormat(getString(R.string.formato_fecha_larga), App.locale()).format(new Date());
        TextView tvFecha = findViewById(R.id.tvFecha);
        tvFecha.setText(f.substring(0, 1).toUpperCase() + f.substring(1));

        // Calendario: "Ver calendario ›" junto a Próximas clases
        findViewById(R.id.btnVerCalendario).setOnClickListener(v ->
                startActivity(new Intent(this, CalendarActivity.class)));

        // Menú ☰
        menu = new MenuLateral(this, findViewById(R.id.drawerInicio));
        findViewById(R.id.btnMenu).setOnClickListener(v -> menu.abrirMenu());
        // Con el menú abierto, el botón "atrás" lo cierra en vez de salir
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (menu.estaAbierto()) {
                    menu.cerrarMenu();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        findViewById(R.id.btnPerfilHeader).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        // Botón "+": pregunta si se quiere agregar una tarea o una materia
        findViewById(R.id.fabAddTask).setOnClickListener(v -> elegirQueAgregar());

        // Tarjetas del resumen: cada una lleva a su sitio
        findViewById(R.id.cardClases).setOnClickListener(v ->
                BottomNavHelper.irA(this, new Intent(this, SubjectsActivity.class)));
        findViewById(R.id.cardPendientes).setOnClickListener(v ->
                BottomNavHelper.irA(this, new Intent(this, TasksActivity.class).putExtra("filtro", "pending")));
        findViewById(R.id.cardCompletadas).setOnClickListener(v ->
                BottomNavHelper.irA(this, new Intent(this, TasksActivity.class).putExtra("filtro", "done")));

        // Pide permiso para mostrar notificaciones (Android 13 o más nuevo). Solo pregunta una vez.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        handle = Repo.listen(this::cargar);
        cargar(); // muestra lo que ya hay (o "Cargando…") sin esperar a Firebase
        // Si ya se leyó el perfil antes, se muestra de una vez (sin "Hola, estudiante" por un segundo)
        if (Datos.perfil != null) {
            mostrarPerfil(Datos.perfil);
        } else {
            // Todavía no llega el perfil: saluda con el nombre guardado en la cuenta
            com.google.firebase.auth.FirebaseUser u = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (u != null && u.getDisplayName() != null && !u.getDisplayName().trim().isEmpty()) {
                UserProfile tmp = new UserProfile();
                tmp.nombre = u.getDisplayName();
                mostrarPerfil(tmp);
            }
        }
        Repo.leerPerfil(this::mostrarPerfil); // y se actualiza por si cambió
    }

    private void mostrarPerfil(UserProfile p) {
        if (isFinishing() || isDestroyed()) return;
        menu.mostrarCuenta(p); // foto, nombre y correo en el encabezado del menú
        Avatar.ponerEmoji(findViewById(R.id.tvAvatarHeader), p.emoji());
        FotoPerfil.mostrar(findViewById(R.id.ivAvatarHeader), findViewById(R.id.tvAvatarHeader), p.foto);
        String nombre = p.primerNombre();
        ((TextView) findViewById(R.id.tvSaludo)).setText(
                nombre.isEmpty() ? getString(R.string.saludo_anonimo) : getString(R.string.saludo, nombre));
    }

    @Override
    protected void onResume() {
        super.onResume();
        menu.refrescar(); // por si cambiaron las notificaciones, el idioma o el tema
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
        cargarClases();
        cargarPendientes();
    }

    // ------------------------------------------------------------------
    // Próximas clases
    // ------------------------------------------------------------------

    /** Clases de un día (0 = lunes ... 6 = domingo), ordenadas por hora. */
    private List<Clase> clasesDelDia(int dia) {
        List<Clase> lista = new ArrayList<>();
        for (Subject s : Datos.SUBJECTS) {
            for (Bloque b : Horario.bloquesDelDia(s, dia)) lista.add(new Clase(s, b));
        }
        Collections.sort(lista, (x, y) -> Integer.compare(
                Horario.minutos(x.bloque.startTime), Horario.minutos(y.bloque.startTime)));
        return lista;
    }

    /**
     * Muestra SIEMPRE dos grupos:
     *  1) Las clases de hoy (las terminadas se ven más claras, la actual dice "En curso").
     *  2) Debajo, las del siguiente día que tenga clases: "Clases de mañana", o "Clases del lunes" si es viernes, etc.
     */
    private void cargarClases() {
        LinearLayout lista = findViewById(R.id.listClases);
        lista.removeAllViews();
        TextView titulo = findViewById(R.id.tvTituloClases);
        titulo.setText(R.string.clases_titulo_hoy);

        int hoyIdx = Horario.hoyIndice();
        int ahora = Horario.minutosAhora();
        List<Clase> hoy = clasesDelDia(hoyIdx);
        ((TextView) findViewById(R.id.tvClases)).setText(String.valueOf(hoy.size()));

        if (!Datos.materiasListas) {
            lista.addView(mensaje(R.string.cargando)); // los datos aún no llegan de Firebase
            ((TextView) findViewById(R.id.tvClases)).setText("–");
            return;
        }
        if (Datos.SUBJECTS.isEmpty()) {
            lista.addView(mensaje(R.string.sin_materias_clases));
            return;
        }

        // 1) Clases de hoy
        for (Clase c : hoy) agregarClase(lista, c, null, ahora);
        if (hoy.isEmpty()) lista.addView(mensaje(R.string.sin_clases_hoy));

        // 2) Siguiente día con clases (mañana, pasado mañana... hasta una semana)
        for (int d = 1; d <= 7; d++) {
            int idx = (hoyIdx + d) % 7;
            List<Clase> proximas = clasesDelDia(idx);
            if (proximas.isEmpty()) continue;
            lista.addView(subtitulo(d == 1
                    ? getString(R.string.clases_titulo_manana)                 // "Clases de mañana"
                    : getString(R.string.clases_titulo_dia, nombreDia(idx)))); // "Clases del lunes"
            for (Clase c : proximas) agregarClase(lista, c, "", -1);
            break;
        }
    }

    /** Diálogo del botón "+": Nueva tarea, Nueva materia o Calcular calificación. (Los apuntes se agregan desde la materia o desde Mis apuntes.) */
    private void elegirQueAgregar() {
        String[] opciones = {
                getString(R.string.tarea_nueva),
                getString(R.string.materia_nueva),
                getString(R.string.calcular_nota)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.que_agregar)
                .setItems(opciones, (d, cual) -> {
                    if (cual == 0) startActivity(new Intent(this, AddTaskActivity.class));
                    else if (cual == 1) startActivity(new Intent(this, AddSubjectActivity.class));
                    else ElegirMateria.mostrar(this, AddGradeActivity.class);
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    /** Título del segundo grupo, con el mismo estilo que "Clases de hoy". */
    private TextView subtitulo(String texto) {
        TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextSize(16);
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_main));
        tv.setTypeface(ResourcesCompat.getFont(this, R.font.nunito_extrabold));
        float dp = getResources().getDisplayMetrics().density;
        tv.setPadding(0, Math.round(20 * dp), 0, Math.round(10 * dp));
        return tv;
    }

    /** Nombre del día para el título: en español va en minúscula ("lunes"), en inglés no ("Monday"). */
    private String nombreDia(int dia) {
        String nombre = Horario.largo(dia);
        java.util.Locale idioma = getResources().getConfiguration().getLocales().get(0);
        return "es".equals(idioma.getLanguage()) ? nombre.toLowerCase(idioma) : nombre;
    }

    /**
     * Agrega una clase a la lista. Si "cuando" es null es de hoy (muestra "En curso" / "Terminada");
     * si no, el día va antes de la hora, ej. "Mañana · 8:00–10:00 a. m.".
     */
    private void agregarClase(LinearLayout lista, Clase c, String cuando, int ahora) {
        Subject s = c.materia;
        Bloque b = c.bloque;
        View item = LayoutInflater.from(this).inflate(R.layout.item_class, lista, false);
        String ini = b.startTime == null ? "" : b.startTime;
        String fin = b.endTime == null ? "" : b.endTime;
        String hora = ini.isEmpty() ? "" : Horario.rango(ini, fin); // ej. "2:00–4:00 p. m."

        String detalle;
        boolean terminada = false;
        if (cuando == null) {
            detalle = hora.isEmpty() ? getString(R.string.hoy) : hora;
            int mIni = Horario.minutos(ini), mFin = Horario.minutos(fin);
            if (mIni >= 0 && mFin > mIni) {
                if (ahora >= mFin) {
                    // Terminada: fondo gris suave + ✓ (sin transparencia, para que el texto se siga leyendo bien)
                    detalle += " · " + getString(R.string.clase_terminada);
                    item.setBackgroundResource(R.drawable.bg_clase_terminada);
                    terminada = true;
                }
                else if (ahora >= mIni) detalle += " · " + getString(R.string.clase_en_curso);
            }
        } else if (cuando.isEmpty()) {
            detalle = hora; // el día ya va en el título ("Clases de mañana", "Clases del lunes"...)
        } else {
            detalle = hora.isEmpty() ? cuando : cuando + " · " + hora;
        }

        ((TextView) item.findViewById(R.id.tvNombre)).setText(s.name);
        TextView tvDetalle = item.findViewById(R.id.tvDetalle);
        tvDetalle.setText(detalle);
        if (terminada) {
            tvDetalle.setTextColor(ContextCompat.getColor(this, R.color.mint_text));
            Iconos.alInicio(tvDetalle, R.drawable.ic_hecho);
        }
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

    // ------------------------------------------------------------------
    // Tareas pendientes: las vencidas y las más próximas primero (3 como máximo)
    // ------------------------------------------------------------------

    private void cargarPendientes() {
        LinearLayout listPend = findViewById(R.id.listPendientes);
        listPend.removeAllViews();
        List<Task> pend = new ArrayList<>();
        int completadas = 0;
        for (Task t : Datos.TASKS) {
            if (t.done) completadas++; else pend.add(t);
        }
        Collections.sort(pend, (x, y) -> {
            int f = Long.compare(TaskUtil.entregaMillis(x), TaskUtil.entregaMillis(y)); // fecha y hora
            return f != 0 ? f : Integer.compare(y.priority, x.priority);
        });

        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < Math.min(3, pend.size()); i++) {
            Task t = pend.get(i);
            View item = inf.inflate(R.layout.item_task_pending, listPend, false);
            Subject s = Datos.subject(t.subjectId);
            boolean vencida = TaskUtil.vencida(t);
            TextView detalle = item.findViewById(R.id.tvDetalle);
            ((TextView) item.findViewById(R.id.tvNombre)).setText(
                    t.tipo != Task.TAREA ? TaskUtil.tipoTexto(t.tipo) + " · " + t.name : t.name);
            String fecha = TaskUtil.entregaTexto(t);
            detalle.setText(s.name + " · " + (vencida ? getString(R.string.vencida_fecha, fecha) : fecha));
            if (vencida) {
                item.setBackgroundResource(R.drawable.bg_task_overdue);
                detalle.setTextColor(ContextCompat.getColor(this, R.color.red_text));
                android.widget.ImageView icono = item.findViewById(R.id.tvIconoPend);
                icono.setImageResource(R.drawable.ic_alerta);
                icono.setColorFilter(ContextCompat.getColor(this, R.color.red_text));
            }
            // Al tocarla se abre para editarla
            item.setOnClickListener(v -> startActivity(
                    new Intent(this, AddTaskActivity.class).putExtra("taskId", t.id)));
            listPend.addView(item);
        }
        if (!Datos.tareasListas) {
            listPend.addView(mensaje(R.string.cargando));
            ((TextView) findViewById(R.id.tvPendientes)).setText("–");
            ((TextView) findViewById(R.id.tvCompletadas)).setText("–");
            return;
        }
        if (pend.isEmpty()) listPend.addView(mensaje(R.string.sin_pendientes));

        ((TextView) findViewById(R.id.tvPendientes)).setText(String.valueOf(pend.size()));
        ((TextView) findViewById(R.id.tvCompletadas)).setText(String.valueOf(completadas));
    }

    /** Texto gris centrado para cuando una lista está vacía. */
    private TextView mensaje(int texto) {
        TextView vacio = new TextView(this);
        vacio.setText(texto);
        vacio.setTextSize(14);
        vacio.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        vacio.setGravity(Gravity.CENTER);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        vacio.setPadding(0, pad, 0, pad);
        return vacio;
    }
}
