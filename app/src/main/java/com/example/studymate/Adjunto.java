package com.example.studymate;

/**
 * Un archivo adjunto a un apunte (PDF, Word, PowerPoint, Excel, imagen...).
 * Aquí solo va la información del archivo; el contenido se guarda aparte en
 * users/{uid}/archivos/{id} para que la lista de apuntes cargue rápido (ver Archivos.java).
 */
public class Adjunto implements java.io.Serializable {
    public String id = "";     // clave en users/{uid}/archivos
    public String nombre = ""; // ej. "Taller 2.pdf"
    public String tipo = "";   // tipo MIME, ej. "application/pdf"
    public long tamano;        // bytes

    public Adjunto() {} // requerido por Firebase

    public Adjunto(String id, String nombre, String tipo, long tamano) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.tamano = tamano;
    }
}
