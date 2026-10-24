package com.example.studymate;

import java.util.ArrayList;
import java.util.List;

/** Copia local de lo que hay en Firebase. Repo.java la mantiene actualizada. */
public class Datos {
    public static final List<Subject> SUBJECTS = new ArrayList<>();
    public static final List<Task> TASKS = new ArrayList<>();
    public static final List<Grade> GRADES = new ArrayList<>();
    public static final List<Apunte> APUNTES = new ArrayList<>();

    // ¿Ya llegaron los datos de Firebase? (mientras tanto las pantallas muestran "Cargando…")
    public static boolean materiasListas = false;
    public static boolean tareasListas = false;

    // Último perfil leído (nombre, foto...). Así Inicio y Perfil saludan con tu nombre al instante
    // en vez de mostrar "Hola, estudiante" mientras Firebase responde. null = todavía no se ha leído.
    public static UserProfile perfil = null;

    public static Subject subject(int id) {
        for (Subject s : SUBJECTS) if (s.id == id) return s;
        return new Subject(id, App.t(R.string.sin_materia), "", "", "");
    }

    /**
     * Número para una tarea nueva. Se basa en la hora actual (en segundos), así dos teléfonos
     * con la misma cuenta no generan el mismo número aunque creen tareas a la vez, y las tareas
     * nuevas siempre quedan después de las anteriores.
     */
    public static int nextTaskId() {
        int max = 0;
        for (Task t : TASKS) max = Math.max(max, t.id);
        return Math.max(max + 1, idPorHora());
    }

    /** Segundos desde 2026-09 aprox. (cabe en un int por muchos años; ver Recordatorios). */
    private static int idPorHora() {
        return (int) (System.currentTimeMillis() / 1000L - 1_790_000_000L);
    }

    public static int nextSubjectId() {
        int max = 0;
        for (Subject s : SUBJECTS) max = Math.max(max, s.id);
        return max + 1;
    }

    /** Número para una nota nueva (igual que las tareas). */
    public static int nextGradeId() {
        int max = 0;
        for (Grade g : GRADES) max = Math.max(max, g.id);
        return Math.max(max + 1, idPorHora());
    }

    /** Número para un apunte nuevo (igual que las tareas). */
    public static int nextApunteId() {
        int max = 0;
        for (Apunte a : APUNTES) max = Math.max(max, a.id);
        return Math.max(max + 1, idPorHora());
    }

    /** Se llama al cerrar sesión para que el siguiente usuario no vea datos ajenos. */
    public static void limpiar() {
        SUBJECTS.clear();
        TASKS.clear();
        GRADES.clear();
        APUNTES.clear();
        materiasListas = false;
        tareasListas = false;
        perfil = null;
    }
}
