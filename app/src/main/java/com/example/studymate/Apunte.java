package com.example.studymate;

/** Un apunte de una materia: título, texto libre, foto y archivos opcionales, y fecha de la última modificación. */
public class Apunte {
    public int id;
    public int subjectId;
    public String titulo = "";
    public String texto = "";
    public long fecha; // milisegundos
    public String foto = ""; // foto opcional (JPEG en Base64), tomada con la cámara o de la galería
    // Archivos adjuntos (PDF, Word...). Solo nombre/tipo/tamaño; el contenido está en users/{uid}/archivos
    public java.util.List<Adjunto> adjuntos = new java.util.ArrayList<>();

    public Apunte() {} // requerido por Firebase

    public Apunte(int id, int subjectId, String titulo, String texto, long fecha) {
        this.id = id;
        this.subjectId = subjectId;
        this.titulo = titulo;
        this.texto = texto;
        this.fecha = fecha;
    }

    public Apunte(int id, int subjectId, String titulo, String texto, long fecha, String foto) {
        this(id, subjectId, titulo, texto, fecha);
        this.foto = foto == null ? "" : foto;
    }

    /** Cuántos archivos adjuntos tiene. */
    public int cuantosAdjuntos() {
        return adjuntos == null ? 0 : adjuntos.size();
    }

    /** true si el apunte tiene foto. */
    public boolean tieneFoto() {
        return foto != null && !foto.isEmpty();
    }
}
