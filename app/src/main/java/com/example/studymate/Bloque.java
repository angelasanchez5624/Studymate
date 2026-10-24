package com.example.studymate;

/**
 * Un bloque de clase de una materia: qué días, a qué hora y en qué salón.
 * Una materia puede tener varios (ej. Lun 08:00–10:00 en A-201 y Jue 14:00–16:00 en B-105).
 */
public class Bloque {
    public String days = "";      // días ISO separados por coma: 1=Lun ... 7=Dom, ej. "1,3"
    public String startTime = ""; // "08:00"
    public String endTime = "";   // "10:00"
    public String room = "";      // "A-201"

    // Ubicación de ESTE salón en el mapa (0, 0 = sin ubicación).
    // Así, si la materia tiene clase en dos edificios distintos, cada horario tiene su pin.
    public double lat = 0;
    public double lon = 0;

    public Bloque() {} // requerido por Firebase

    /** true si se marcó en el mapa dónde es este salón. */
    public boolean tieneUbicacion() {
        return lat != 0 || lon != 0;
    }
}
