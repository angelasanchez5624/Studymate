package com.example.studymate;

import android.view.View;
import android.widget.TextView;
import java.text.Normalizer;

/**
 * Emoji del estudiante según su género (👩‍🎓 👨‍🎓 🧑‍🎓).
 *
 * Valores que se guardan en Firebase:
 *   "f" = femenino · "m" = masculino · "n" = prefiere no decirlo · "" = no lo ha elegido
 * Si no lo ha elegido, se intenta adivinar por el nombre (ej. "Laura" → 👩‍🎓, "Carlos" → 👨‍🎓).
 */
public class Avatar {

    public static final String FEMENINO = "f", MASCULINO = "m", NEUTRO = "n";

    // Nombres que no siguen la regla de la terminación (-a mujer, -o hombre...)
    private static final java.util.Set<String> MUJERES = new java.util.HashSet<>(java.util.Arrays.asList(
            "isabel", "raquel", "maribel", "mabel", "ines", "dolores", "mercedes", "carmen", "pilar", "luz",
            "beatriz", "ruth", "abigail", "rocio", "consuelo", "rosario", "socorro", "amparo", "nicole", "ivonne",
            "yuliet", "lizeth", "lisbeth", "elizabeth", "ingrid", "astrid", "karen", "yaneth", "janeth", "marisol",
            "sol", "flor", "noemi", "belen", "esther", "judith", "miriam", "evelyn", "jennifer", "yolanda"));
    private static final java.util.Set<String> HOMBRES = new java.util.HashSet<>(java.util.Arrays.asList(
            "luca", "joshua", "bautista", "elias", "matias", "tobias", "jeremias", "nicolas", "jesus", "luis",
            "jose", "felipe", "jorge", "andres", "enrique", "dante", "vicente", "jaime", "guillermo", "william"));

    public static String estudiante(String genero, String nombre) {
        switch (resolver(genero, nombre)) {
            case FEMENINO: return "👩‍🎓";
            case MASCULINO: return "👨‍🎓";
            default: return "🧑‍🎓";
        }
    }

    /** Género elegido; si no hay, el que se adivina por el nombre. */
    public static String resolver(String genero, String nombre) {
        if (genero != null && !genero.isEmpty()) return genero;
        return adivinar(nombre);
    }

    /**
     * Adivina por el título o la terminación del primer nombre (funciona con la mayoría de nombres
     * en español; si falla, la persona lo corrige con los botones).
     */
    public static String adivinar(String nombre) {
        if (nombre == null) return NEUTRO;
        String[] partes = normalizar(nombre).split("\\s+");
        for (String p : partes) {
            String w = p.replace(".", "");
            if (w.isEmpty()) continue;
            // Títulos
            if (w.equals("dra") || w.equals("profesora") || w.equals("profa") || w.equals("sra")
                    || w.equals("senora")) return FEMENINO;
            if (w.equals("dr") || w.equals("profesor") || w.equals("prof") || w.equals("sr") || w.equals("senor")) return MASCULINO;
            if (w.equals("ing") || w.equals("lic") || w.equals("mg") || w.equals("msc") || w.equals("phd")
                    || w.equals("esp") || w.equals("docente")) continue; // títulos sin género: mirar la siguiente palabra
            // Primer nombre: primero las excepciones más comunes
            if (MUJERES.contains(w)) return FEMENINO;
            if (HOMBRES.contains(w)) return MASCULINO;
            if (w.endsWith("a")) return FEMENINO;
            if (w.endsWith("o") || w.endsWith("os") || w.endsWith("el") || w.endsWith("an") || w.endsWith("on")
                    || w.endsWith("es") || w.endsWith("er") || w.endsWith("in") || w.endsWith("ar")) return MASCULINO;
            return NEUTRO;
        }
        return NEUTRO;
    }

    private static String normalizar(String t) {
        return Normalizer.normalize(t.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /**
     * Respaldo cuando no hay foto: el ícono de persona (ImageView en los layouts).
     * Se mantiene la firma para no cambiar las pantallas que lo llaman.
     */
    public static void ponerEmoji(View respaldo, String emoji) {
        if (respaldo instanceof android.widget.ImageView) {
            ((android.widget.ImageView) respaldo).setImageResource(R.drawable.ic_persona);
        } else if (respaldo instanceof TextView) {
            ((TextView) respaldo).setText(emoji);
        }
    }
}
