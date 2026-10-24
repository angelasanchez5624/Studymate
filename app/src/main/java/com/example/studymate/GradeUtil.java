package com.example.studymate;

import java.util.Locale;

/** Cálculo de promedio ponderado. Escala 0–5, aprueba con 3.0 (cámbialo aquí si tu universidad usa otra). */
public class GradeUtil {
    public static final double MAX = 5.0;
    public static final double APRUEBA = 3.0;

    /** Suma de los porcentajes ya evaluados. */
    public static double pesoEvaluado(int subjectId) {
        double w = 0;
        for (Grade g : Datos.GRADES) if (g.subjectId == subjectId) w += g.weight;
        return w;
    }

    /** Promedio ponderado de lo evaluado hasta ahora; -1 si no hay notas. */
    public static double promedio(int subjectId) {
        double sum = 0, w = 0;
        for (Grade g : Datos.GRADES) {
            if (g.subjectId != subjectId) continue;
            sum += g.score * g.weight;
            w += g.weight;
        }
        return w <= 0 ? -1 : sum / w;
    }

    /** Lo que ya aportó a la nota final (nota × peso / 100). */
    public static double acumulado(int subjectId) {
        double sum = 0;
        for (Grade g : Datos.GRADES) if (g.subjectId == subjectId) sum += g.score * g.weight;
        return sum / 100.0;
    }

    /** Promedio general de todas las materias que tienen notas; -1 si no hay. */
    public static double promedioGeneral() {
        double sum = 0;
        int n = 0;
        for (Subject s : Datos.SUBJECTS) {
            double p = promedio(s.id);
            if (p >= 0) { sum += p; n++; }
        }
        return n == 0 ? -1 : sum / n;
    }

    public static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    /** Mensaje sobre lo que falta para aprobar. */
    public static String mensaje(int subjectId) {
        return mensaje(pesoEvaluado(subjectId), acumulado(subjectId));
    }

    /** Mismo mensaje, pero con un % evaluado y un acumulado dados (vista previa al agregar una nota). */
    public static String mensaje(double w, double acum) {
        if (w <= 0) return App.t(R.string.nota_sin);
        double resta = 100 - w;
        if (resta <= 0.0001) {
            return acum >= APRUEBA ? App.t(R.string.nota_aprobada, fmt(acum))
                                   : App.t(R.string.nota_reprobada, fmt(acum));
        }
        double necesaria = (APRUEBA - acum) * 100.0 / resta;
        if (necesaria <= 0) return App.t(R.string.nota_asegurada, fmt(APRUEBA));
        if (necesaria > MAX) return App.t(R.string.nota_imposible, fmt(APRUEBA), Math.round(resta));
        return App.t(R.string.nota_necesitas, fmt(necesaria), Math.round(resta));
    }
}
