package com.example.studymate;

/** Datos del usuario guardados en users/{uid}. */
public class UserProfile {
    public String nombre = "";
    public String correo = "";
    public String carrera = "";
    public String universidad = "";
    public String semestre = "";   // ej. "5° Semestre"
    public String foto = "";       // foto de perfil en Base64 (ver FotoPerfil)
    public String genero = "";     // "f", "m", "n" o "" (ver Avatar)

    /** Emoji del estudiante según su género (o su nombre si no lo eligió). */
    public String emoji() {
        return Avatar.estudiante(genero, nombre);
    }

    /** Primer nombre, para saludar. */
    public String primerNombre() {
        String n = nombre == null ? "" : nombre.trim();
        if (n.isEmpty()) return "";
        return n.split("\\s+")[0];
    }

    /** Así se guarda el semestre en Firebase (siempre en español, para no romper datos viejos). */
    public static final String[] SEMESTRES_GUARDADOS = {"1° Semestre", "2° Semestre", "3° Semestre",
            "4° Semestre", "5° Semestre", "6° Semestre", "7° Semestre", "8° Semestre", "9° Semestre", "10° Semestre"};

    /** Posición del semestre en la lista (0 = 1°), o -1 si no está. */
    public int semestreIndice() {
        for (int i = 0; i < SEMESTRES_GUARDADOS.length; i++) {
            if (SEMESTRES_GUARDADOS[i].equals(semestre)) return i;
        }
        return -1;
    }

    /** Semestre para mostrar en el idioma elegido, ej. "5° Semestre" o "5th semester". */
    public String semestreTexto() {
        int i = semestreIndice();
        if (i < 0) return semestre == null ? "" : semestre;
        return App.lista(R.array.semestres)[i];
    }

    /** "5° Semestre" -> "5°" */
    public String semestreCorto() {
        if (semestre == null || semestre.trim().isEmpty()) return "—";
        return semestre.trim().split("\\s+")[0];
    }
}
