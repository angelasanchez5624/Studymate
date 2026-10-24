package com.example.studymate;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Programa la notificación de cada tarea pendiente: un aviso, con el tiempo que el usuario eligió
 * en Notificaciones → "Avisarme" (por defecto, 1 día antes de la entrega).
 * Se vuelve a programar todo cada vez que cambian las tareas (Repo lo llama).
 * También se guarda una copia en el teléfono para reprogramar al reiniciarlo (ArranqueReceiver).
 */
public class Recordatorios {

    public static final String CANAL = "entregas";

    // El tiempo del aviso lo elige el usuario (Ajustes.AVISO1_MIN).

    private static final String PREFS = "studymate_recordatorios";
    private static final String KEY_IDS = "ids";       // códigos de alarmas programadas
    private static final String KEY_LISTA = "lista";   // copia para reprogramar al reiniciar
    private static final String KEY_CLASES = "clases";         // copia de las clases (para los avisos de clase)
    private static final String KEY_IDS_CLASES = "ids_clases"; // códigos de las alarmas de clase
    private static final int BASE_CLASES = -1000;              // códigos negativos: no chocan con las tareas
    public static final int MIN_CLASES_DEFECTO = 15;

    /** Crea el canal de notificaciones (obligatorio desde Android 8). */
    public static void crearCanal(Context c) {
        NotificationChannel canal = new NotificationChannel(CANAL, App.t(R.string.canal_nombre),
                NotificationManager.IMPORTANCE_HIGH);
        canal.setDescription(App.t(R.string.canal_desc));
        c.getSystemService(NotificationManager.class).createNotificationChannel(canal);
    }

    // ------------------------------------------------------------------
    // Avisos de clase: X minutos antes de cada clase, todas las semanas
    // ------------------------------------------------------------------

    /** Guarda las clases de todas las materias y programa el próximo aviso de cada una. */
    public static void programarClases(Context c, List<Subject> materias) {
        c = c.getApplicationContext();
        JSONArray clases = new JSONArray();
        for (Subject s : materias) {
            for (Bloque b : Horario.bloques(s)) {
                if (Horario.minutos(b.startTime) < 0) continue; // sin hora no se puede avisar
                boolean[] dias = Horario.dias(b);
                for (int d = 0; d < 7; d++) {
                    if (!dias[d]) continue;
                    try {
                        JSONObject o = new JSONObject();
                        o.put("materia", s.name);
                        o.put("salon", b.room == null ? "" : b.room);
                        o.put("dia", d);
                        o.put("inicio", b.startTime);
                        o.put("fin", b.endTime == null ? "" : b.endTime);
                        clases.put(o);
                    } catch (Exception ignorar) { }
                }
            }
        }
        prefs(c).edit().putString(KEY_CLASES, clases.toString()).apply();
        reprogramarClasesGuardadas(c);
    }

    /**
     * Programa el próximo aviso de cada clase guardada. Se llama al cambiar las materias,
     * al encender el teléfono y cada vez que suena un aviso de clase (para la semana siguiente).
     */
    public static void reprogramarClasesGuardadas(Context c) {
        c = c.getApplicationContext();
        crearCanal(c);
        cancelarClases(c);
        if (!Ajustes.get(c, Ajustes.AVISO_CLASES)) return;
        Ajustes.cargar(c);
        int antes = Ajustes.getInt(c, Ajustes.MIN_CLASES, MIN_CLASES_DEFECTO);

        AlarmManager am = c.getSystemService(AlarmManager.class);
        Set<String> codigos = new HashSet<>();
        long ahora = System.currentTimeMillis();
        try {
            JSONArray clases = new JSONArray(prefs(c).getString(KEY_CLASES, "[]"));
            for (int i = 0; i < clases.length(); i++) {
                JSONObject o = clases.getJSONObject(i);
                long disparo = proximoAviso(o.getInt("dia"), Horario.minutos(o.getString("inicio")), antes, ahora);
                if (disparo < 0) continue;

                String salon = o.getString("salon");
                String texto = o.getString("materia") + " · " + Horario.rango(o.getString("inicio"), o.getString("fin"))
                        + (salon.isEmpty() ? "" : " · " + salon);
                int codigo = BASE_CLASES - i;
                Intent datos = new Intent(c, RecordatorioReceiver.class);
                datos.putExtra("titulo", App.t(R.string.clase_en_min, antes));
                datos.putExtra("texto", texto);
                datos.putExtra("codigo", codigo);
                datos.putExtra("esClase", true);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, pendiente(c, codigo, datos));
                codigos.add(String.valueOf(codigo));
            }
        } catch (Exception ignorar) { }
        prefs(c).edit().putStringSet(KEY_IDS_CLASES, codigos).apply();
    }

    /** Momento del próximo aviso: la próxima vez que toque esa clase, "antes" minutos antes. */
    private static long proximoAviso(int dia, int minInicio, int antes, long ahora) {
        if (minInicio < 0) return -1;
        for (int k = 0; k <= 7; k++) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(ahora);
            cal.add(java.util.Calendar.DAY_OF_MONTH, k);
            int idx = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7; // 0 = lunes
            if (idx != dia) continue;
            cal.set(java.util.Calendar.HOUR_OF_DAY, minInicio / 60);
            cal.set(java.util.Calendar.MINUTE, minInicio % 60);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long disparo = cal.getTimeInMillis() - antes * 60_000L;
            if (disparo > ahora) return disparo;
        }
        return -1;
    }

    /** Quita los avisos de clase (no borra la lista de clases guardada). */
    public static void cancelarClases(Context c) {
        c = c.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (String codigo : prefs(c).getStringSet(KEY_IDS_CLASES, new HashSet<>())) {
            try {
                am.cancel(pendiente(c, Integer.parseInt(codigo), null));
            } catch (NumberFormatException ignorar) { }
        }
        prefs(c).edit().remove(KEY_IDS_CLASES).apply();
    }

    /** Al cerrar sesión: quita todos los avisos (tareas y clases) y olvida las clases guardadas. */
    public static void cancelarTodoDelUsuario(Context c) {
        cancelarTodas(c);
        cancelarClases(c);
        prefs(c.getApplicationContext()).edit().remove(KEY_CLASES).apply();
    }

    /** Cancela lo anterior y programa los avisos de todas las tareas pendientes. */
    public static void programarTodas(Context c, List<Task> tareas) {
        c = c.getApplicationContext();
        crearCanal(c);
        cancelarTodas(c);
        Ajustes.cargar(c);

        JSONArray lista = new JSONArray();
        // Configuración: si los recordatorios están apagados, no se programa nada
        if (!Ajustes.get(c, Ajustes.RECORDATORIOS)) {
            guardarYProgramar(c, lista);
            return;
        }
        int[] antesMin = {Ajustes.avisoMin(c, Ajustes.AVISO1_MIN)}; // un solo aviso por tarea
        long ahora = System.currentTimeMillis();
        for (Task t : tareas) {
            if (t.done) continue;
            long entrega = TaskUtil.entregaMillis(t);
            if (entrega <= ahora) continue; // ya pasó

            String materia = Datos.subject(t.subjectId).name;
            String cuando = SubjectColors.fechaCorta(t.dueDate) + " · " + Horario.ampm(TaskUtil.horaEntrega(t));
            for (int k = 0; k < antesMin.length; k++) {
                if (antesMin[k] <= 0) continue; // aviso desactivado
                long disparo = entrega - antesMin[k] * 60_000L;
                if (disparo <= ahora) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("codigo", t.id * 10 + k);
                    o.put("disparo", disparo);
                    o.put("titulo", Avisos.titulo(t.tipo == Task.EXAMEN, antesMin[k]));
                    o.put("texto", t.name + " (" + materia + ") — " + cuando);
                    o.put("taskId", t.id);
                    lista.put(o);
                } catch (Exception ignorar) { }
            }
        }
        guardarYProgramar(c, lista);
    }

    /** Vuelve a programar la copia guardada (después de reiniciar el teléfono). */
    public static void reprogramarGuardadas(Context c) {
        c = c.getApplicationContext();
        crearCanal(c);
        try {
            JSONArray lista = new JSONArray(prefs(c).getString(KEY_LISTA, "[]"));
            JSONArray vigentes = new JSONArray();
            long ahora = System.currentTimeMillis();
            for (int i = 0; i < lista.length(); i++) {
                if (lista.getJSONObject(i).getLong("disparo") > ahora) vigentes.put(lista.getJSONObject(i));
            }
            guardarYProgramar(c, vigentes);
        } catch (Exception ignorar) { }
    }

    /** Quita todos los avisos (por ejemplo al cerrar sesión). */
    public static void cancelarTodas(Context c) {
        c = c.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (String codigo : prefs(c).getStringSet(KEY_IDS, new HashSet<>())) {
            try {
                am.cancel(pendiente(c, Integer.parseInt(codigo), null));
            } catch (NumberFormatException ignorar) { }
        }
        prefs(c).edit().remove(KEY_IDS).remove(KEY_LISTA).apply();
    }

    // ------------------------------------------------------------------

    private static void guardarYProgramar(Context c, JSONArray lista) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        Set<String> codigos = new HashSet<>();
        for (int i = 0; i < lista.length(); i++) {
            try {
                JSONObject o = lista.getJSONObject(i);
                int codigo = o.getInt("codigo");
                Intent datos = new Intent(c, RecordatorioReceiver.class);
                datos.putExtra("titulo", o.getString("titulo"));
                datos.putExtra("texto", o.getString("texto"));
                datos.putExtra("codigo", codigo);
                // Alarma "inexacta": Android puede retrasarla unos minutos para ahorrar batería
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, o.getLong("disparo"), pendiente(c, codigo, datos));
                codigos.add(String.valueOf(codigo));
            } catch (Exception ignorar) { }
        }
        prefs(c).edit().putStringSet(KEY_IDS, codigos).putString(KEY_LISTA, lista.toString()).apply();
    }

    private static PendingIntent pendiente(Context c, int codigo, Intent datos) {
        Intent i = datos != null ? datos : new Intent(c, RecordatorioReceiver.class);
        return PendingIntent.getBroadcast(c, codigo, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
