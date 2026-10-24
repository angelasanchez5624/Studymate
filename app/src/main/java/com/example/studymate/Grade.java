package com.example.studymate;

/** Una nota de una materia. weight = porcentaje (0-100) que vale dentro de la materia. */
public class Grade {
    public int id;
    public int subjectId;
    public String name = "";
    public double score;
    public double weight;

    public Grade() {} // requerido por Firebase

    public Grade(int id, int subjectId, String name, double score, double weight) {
        this.id = id;
        this.subjectId = subjectId;
        this.name = name;
        this.score = score;
        this.weight = weight;
    }
}
