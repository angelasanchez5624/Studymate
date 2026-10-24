package com.example.studymate;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Lógica de tareas vencidas y prioridad. */
public class TaskUtil {

    public static String hoy() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Hora de entrega en 24 h; si la tarea no tiene, se toma el final del día. */
    public static String horaEntrega(Task t) {
        return (t.dueTime == null || t.dueTime.isEmpty()) ? "23:59" : t.dueTime;
    }

    /** Fecha y hora de entrega en milisegundos (-1 si la fecha no es válida). */
    public static long entregaMillis(Task t) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(t.dueDate);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            int min = Horario.minutos(horaEntrega(t));
            c.set(Calendar.HOUR_OF_DAY, min / 60);
            c.set(Calendar.MINUTE, min % 60);
            c.set(Calendar.SECOND, 0);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Vencida = no está hecha y ya pasó su fecha y hora de entrega. */
    public static boolean vencida(Task t) {
        if (t.done || t.dueDate == null || t.dueDate.isEmpty()) return false;
        long entrega = entregaMillis(t);
        return entrega > 0 && entrega < System.currentTimeMillis();
    }

    /** true si se marcó como hecha DESPUÉS de su fecha y hora de entrega. */
    public static boolean entregadaTarde(Task t) {
        if (!t.done || t.completedAt <= 0) return false;
        long entrega = entregaMillis(t);
        return entrega > 0 && t.completedAt > entrega;
    }

    /** Cuánto tarde se entregó, ej. "2 días tarde", "3 horas tarde", "20 min tarde". */
    public static String retraso(Task t) {
        long ms = (t.completedAt > 0 ? t.completedAt : System.currentTimeMillis()) - entregaMillis(t);
        long min = Math.max(1, ms / 60_000);
        if (min < 60) return App.t(R.string.retraso_min, min);
        long horas = min / 60;
        if (horas < 24) return App.t(horas == 1 ? R.string.retraso_hora : R.string.retraso_horas, horas);
        long dias = horas / 24;
        return App.t(dias == 1 ? R.string.retraso_dia : R.string.retraso_dias, dias);
    }

    /** Texto corto de la entrega, ej. "10 sep · 2:00 p. m." (sin hora si no la tiene). */
    public static String entregaTexto(Task t) {
        String f = SubjectColors.fechaCorta(t.dueDate);
        if (t.dueTime == null || t.dueTime.isEmpty()) return f;
        return f + " · " + Horario.ampm(t.dueTime);
    }

    public static boolean paraHoy(Task t) {
        return !t.done && hoy().equals(t.dueDate);
    }

    /** "📝 Examen", "🛠️ Taller"... en el idioma elegido. */
    public static String tipoTexto(int tipo) {
        String[] t = App.lista(R.array.tipos_tarea);
        return t[Math.max(0, Math.min(t.length - 1, tipo))];
    }

    /** Colores (fondo, texto) de la etiqueta de cada tipo. */
    public static int[] tipoColores(int tipo) {
        switch (tipo) {
            case Task.EXAMEN: return new int[]{R.color.red_bg, R.color.red_text};
            case Task.TALLER: return new int[]{R.color.blue_bg, R.color.blue_text};
            case Task.EXPOSICION: return new int[]{R.color.lilac_bg, R.color.lilac_text};
            case Task.PROYECTO: return new int[]{R.color.mint_bg, R.color.mint_text};
            default: return new int[]{R.color.yellow_bg, R.color.yellow_text};
        }
    }

    public static String prioridadTexto(int p) {
        switch (p) {
            case Task.ALTA: return App.t(R.string.prio_alta);
            case Task.BAJA: return App.t(R.string.prio_baja);
            default: return App.t(R.string.prio_media);
        }
    }
}
