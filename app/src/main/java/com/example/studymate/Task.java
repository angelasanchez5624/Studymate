package com.example.studymate;

public class Task {
    public static final int BAJA = 0, MEDIA = 1, ALTA = 2;
    // Tipo de entrega (las tareas viejas, sin este campo, quedan como "Tarea")
    public static final int TAREA = 0, EXAMEN = 1, TALLER = 2, EXPOSICION = 3, PROYECTO = 4;

    public int id;
    public String name, dueDate, description; // dueDate: "yyyy-MM-dd"
    public String dueTime = ""; // hora de entrega "HH:mm" (24 h). Vacía = 11:59 p. m.
    public int subjectId;
    public boolean done;
    public int priority = MEDIA; // las tareas viejas (sin este campo) quedan en Media
    public long completedAt = 0; // cuándo se marcó como hecha (milisegundos); 0 = no se sabe
    public int tipo = TAREA;     // Tarea, Examen, Taller, Exposición o Proyecto

    public Task() {} // requerido por Firebase

    public Task(int id, String name, int subjectId, String dueDate, String description, boolean done) {
        this(id, name, subjectId, dueDate, description, done, MEDIA);
    }

    public Task(int id, String name, int subjectId, String dueDate, String description, boolean done, int priority) {
        this.id = id;
        this.name = name;
        this.subjectId = subjectId;
        this.dueDate = dueDate;
        this.description = description;
        this.done = done;
        this.priority = priority;
    }
}
