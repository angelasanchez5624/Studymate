package com.example.studymate;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Horarios de una materia. Una materia puede tener varios bloques (Bloque), cada uno con
 * sus días, horas y salón. Las materias viejas (sin bloques) se leen de los campos
 * days/startTime/endTime/room o, si están vacíos, del texto, ej. "Lun / Mié 08:00–10:00".
 */
public class Horario {

    // Nombres cortos de los días (strings.xml): Lun, Mar... / Mon, Tue...
    private static final int[] CORTOS = {R.string.dia_lun, R.string.dia_mar, R.string.dia_mie,
            R.string.dia_jue, R.string.dia_vie, R.string.dia_sab, R.string.dia_dom};

    /** Nombre corto del día en el idioma elegido (0 = lunes). */
    public static String corto(int i) {
        return App.t(CORTOS[i]);
    }
    private static final String[] CLAVES = {"lun", "mar", "mie", "jue", "vie", "sab", "dom"};

    // ------------------------------------------------------------------
    // Bloques
    // ------------------------------------------------------------------

    /** Todos los bloques de la materia (las materias viejas devuelven un solo bloque). */
    public static List<Bloque> bloques(Subject s) {
        if (s.blocks != null && !s.blocks.isEmpty()) return s.blocks;

        Bloque b = new Bloque();
        b.days = diasComoTexto(diasViejos(s));
        b.startTime = inicioViejo(s);
        b.endTime = finViejo(s);
        b.room = s.room == null ? "" : s.room;
        List<Bloque> lista = new ArrayList<>();
        lista.add(b);
        return lista;
    }

    /** Bloques que caen hoy, ordenados por hora de inicio. */
    public static List<Bloque> bloquesDeHoy(Subject s) {
        List<Bloque> hoy = new ArrayList<>();
        int d = hoyIndice();
        for (Bloque b : bloques(s)) if (dias(b)[d]) hoy.add(b);
        Collections.sort(hoy, (x, y) -> Integer.compare(minutos(x.startTime), minutos(y.startTime)));
        return hoy;
    }

    /** Bloques de un día cualquiera (0 = lunes ... 6 = domingo), ordenados por hora. */
    public static List<Bloque> bloquesDelDia(Subject s, int dia) {
        List<Bloque> r = new ArrayList<>();
        for (Bloque b : bloques(s)) if (dias(b)[dia]) r.add(b);
        Collections.sort(r, (x, y) -> Integer.compare(minutos(x.startTime), minutos(y.startTime)));
        return r;
    }

    /** Nombre largo del día en el idioma elegido (0 = lunes): "Lunes" / "Monday". */
    public static String largo(int dia) {
        return App.lista(R.array.dias_largos)[dia];
    }

    public static boolean esHoy(Subject s) {
        return !bloquesDeHoy(s).isEmpty();
    }

    /** Días del bloque como arreglo de 7 (índice 0 = lunes). */
    public static boolean[] dias(Bloque b) {
        return diasDeTexto(b.days);
    }

    /** Texto del bloque, ej. "Lun / Mié 08:00–10:00". */
    public static String texto(Bloque b) {
        return texto(dias(b), b.startTime == null ? "" : b.startTime, b.endTime == null ? "" : b.endTime);
    }

    /** Todos los horarios en una línea, ej. "Lun 08:00–10:00 · Jue 14:00–16:00". */
    public static String resumen(Subject s) {
        StringBuilder sb = new StringBuilder();
        for (Bloque b : bloques(s)) {
            String t = texto(b);
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(t);
        }
        return sb.toString();
    }

    /** Un horario por línea con su salón, ej. "Lun 08:00–10:00 · A-201". */
    public static String detalle(Subject s) {
        return detalle(s, "");
    }

    /** Un horario por línea con su salón y un prefijo, ej. "⏰ Lun 4:00–6:00 p. m. · 📍 A-201". */
    public static String detalle(Subject s, String prefijo) {
        StringBuilder sb = new StringBuilder();
        for (Bloque b : bloques(s)) {
            String t = texto(b);
            String salon = b.room == null ? "" : b.room;
            if (t.isEmpty() && salon.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(prefijo).append(t.isEmpty() ? App.t(R.string.sin_horario) : t);
            if (!salon.isEmpty()) sb.append(" · ").append(App.t(R.string.salon)).append(' ').append(salon);
        }
        return sb.toString();
    }

    /** Salones sin repetir, ej. "A-201, B-105". */
    public static String salones(Subject s) {
        List<String> vistos = new ArrayList<>();
        for (Bloque b : bloques(s)) {
            if (b.room != null && !b.room.isEmpty() && !vistos.contains(b.room)) vistos.add(b.room);
        }
        return android.text.TextUtils.join(", ", vistos);
    }

    // ------------------------------------------------------------------
    // Lectura de materias viejas (antes de existir los bloques)
    // ------------------------------------------------------------------

    private static boolean[] diasViejos(Subject s) {
        if (s.days != null && !s.days.trim().isEmpty()) return diasDeTexto(s.days);
        boolean[] r = new boolean[7];
        String txt = normalizar(s.schedule);
        Matcher m = Pattern.compile("\\b(lun|mar|mie|jue|vie|sab|dom)[a-z]*").matcher(txt);
        while (m.find()) {
            for (int i = 0; i < 7; i++) if (CLAVES[i].equals(m.group(1))) r[i] = true;
        }
        return r;
    }

    private static String inicioViejo(Subject s) {
        if (s.startTime != null && !s.startTime.isEmpty()) return s.startTime;
        return horaDelTexto(s, 0);
    }

    private static String finViejo(Subject s) {
        if (s.endTime != null && !s.endTime.isEmpty()) return s.endTime;
        return horaDelTexto(s, 1);
    }

    private static String horaDelTexto(Subject s, int n) {
        if (s.schedule == null) return "";
        Matcher m = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(s.schedule);
        int i = 0;
        while (m.find()) {
            if (i == n) return String.format("%02d:%s", Integer.parseInt(m.group(1)), m.group(2));
            i++;
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** "1,3" -> lunes y miércoles marcados. */
    public static boolean[] diasDeTexto(String days) {
        boolean[] r = new boolean[7];
        if (days == null) return r;
        for (String p : days.split(",")) {
            try {
                int d = Integer.parseInt(p.trim());
                if (d >= 1 && d <= 7) r[d - 1] = true;
            } catch (NumberFormatException ignorar) { }
        }
        return r;
    }

    /** Texto para mostrar, ej. "Lun / Mié 08:00–10:00". */
    public static String texto(boolean[] dias, String ini, String fin) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (!dias[i]) continue;
            if (sb.length() > 0) sb.append(" / ");
            sb.append(corto(i));
        }
        if (!ini.isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(rango(ini, fin));
        }
        return sb.toString();
    }

    /**
     * Hora guardada en 24 h -> texto con a. m. / p. m.
     * "08:00" -> "8:00 a. m.", "14:30" -> "2:30 p. m.", "12:00" -> "12:00 p. m.", "00:15" -> "12:15 a. m."
     */
    public static String ampm(String hhmm) {
        int min = minutos(hhmm);
        if (min < 0) return hhmm == null ? "" : hhmm;
        int h = min / 60, m = min % 60;
        // Si en Configuración se eligió 24 horas: "14:30"
        if (Ajustes.hora24) return String.format(java.util.Locale.US, "%02d:%02d", h, m);
        String sufijo = App.t(h < 12 ? R.string.am : R.string.pm);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(java.util.Locale.US, "%d:%02d %s", h12, m, sufijo);
    }

    /**
     * Rango de horas para mostrar. Si las dos son de la mañana o de la tarde, el sufijo va una vez:
     * "14:00","16:00" -> "2:00–4:00 p. m." ; "11:00","13:00" -> "11:00 a. m.–1:00 p. m."
     */
    public static String rango(String ini, String fin) {
        if (ini == null || ini.isEmpty()) return "";
        if (fin == null || fin.isEmpty()) return ampm(ini);
        String a = ampm(ini), b = ampm(fin);
        if (Ajustes.hora24) return a + "–" + b; // "14:00–16:00"
        String sufA = a.substring(a.indexOf(' ') + 1), sufB = b.substring(b.indexOf(' ') + 1);
        if (minutos(ini) >= 0 && minutos(fin) >= 0 && sufA.equals(sufB)) {
            return a.substring(0, a.indexOf(' ')) + "–" + b;
        }
        return a + "–" + b;
    }

    public static String diasComoTexto(boolean[] dias) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (!dias[i]) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(i + 1);
        }
        return sb.toString();
    }

    /** Hoy como índice 0 = lunes ... 6 = domingo. */
    public static int hoyIndice() {
        int d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK); // domingo = 1
        return (d + 5) % 7;
    }

    /** "08:00" -> 480 (minutos desde medianoche); -1 si no hay hora. */
    public static int minutos(String hhmm) {
        if (hhmm == null) return -1;
        Matcher m = Pattern.compile("^(\\d{1,2}):(\\d{2})$").matcher(hhmm.trim());
        if (!m.matches()) return -1;
        return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
    }

    public static int minutosAhora() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private static String normalizar(String t) {
        if (t == null) return "";
        String n = Normalizer.normalize(t.toLowerCase(), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }
}
