package com.example.studymate;

import java.util.ArrayList;
import java.util.List;

public class Subject {
    public int id;
    public String name, professor, schedule, room;

    // Ubicación de la clase en el mapa (OpenStreetMap). 0, 0 = sin ubicación
    public double lat = 0;
    public double lon = 0;

    /** true si la materia o alguno de sus horarios tiene ubicación en el mapa. */
    public boolean tieneUbicacion() {
        if (lat != 0 || lon != 0) return true;
        if (blocks != null) for (Bloque b : blocks) if (b.tieneUbicacion()) return true;
        return false;
    }

    // Horarios de la materia (puede tener varios, cada uno con su salón)
    public List<Bloque> blocks = new ArrayList<>();

    // Campos viejos: las materias creadas antes de "blocks" usan estos.
    // Horario.bloques() los convierte en un bloque cuando "blocks" está vacío.
    public String days = "";
    public String startTime = "";
    public String endTime = "";

    public Subject() {} // requerido por Firebase

    public Subject(int id, String name, String professor, String schedule, String room) {
        this.id = id;
        this.name = name;
        this.professor = professor;
        this.schedule = schedule;
        this.room = room;
    }
}
