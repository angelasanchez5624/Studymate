package com.example.studymate;

import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Collections;

/**
 * Lee y guarda en Realtime Database.
 * Estructura:  users/{uid}/subjects/{id}, tasks/{id}, grades/{id}, notes/{id} (apuntes)
 *              y archivos/{id} (contenido de los archivos adjuntos de los apuntes)
 */
public class Repo {

    /** Nodo del usuario que inició sesión (null si no hay sesión). */
    public static DatabaseReference root() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return null;
        return FirebaseDatabase.getInstance().getReference("users").child(u.getUid());
    }

    /** Permite dejar de escuchar (se llama en onStop). */
    public static class Handle {
        private final java.util.List<DatabaseReference> refs = new java.util.ArrayList<>();
        private final java.util.List<ValueEventListener> listeners = new java.util.ArrayList<>();

        void add(DatabaseReference r, ValueEventListener l) {
            refs.add(r);
            listeners.add(l);
        }

        public void remove() {
            for (int i = 0; i < refs.size(); i++) refs.get(i).removeEventListener(listeners.get(i));
        }
    }

    /**
     * Escucha materias, tareas y notas. Cada vez que algo cambia (también por tus propios
     * guardados) actualiza Datos y ejecuta onChange en el hilo principal.
     */
    public static Handle listen(Runnable onChange) {
        DatabaseReference base = root();
        if (base == null) return null;
        Handle h = new Handle();

        DatabaseReference subRef = base.child("subjects");
        ValueEventListener subL = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Datos.SUBJECTS.clear();
                for (DataSnapshot c : snapshot.getChildren()) {
                    Subject s = c.getValue(Subject.class);
                    if (s != null) Datos.SUBJECTS.add(s);
                }
                Collections.sort(Datos.SUBJECTS, (a, b) -> Integer.compare(a.id, b.id));
                Datos.materiasListas = true;
                // Cada vez que cambian las materias se reprograman los avisos de clase
                Recordatorios.programarClases(FirebaseApp.getInstance().getApplicationContext(), Datos.SUBJECTS);
                onChange.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        DatabaseReference taskRef = base.child("tasks");
        ValueEventListener taskL = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Datos.TASKS.clear();
                for (DataSnapshot c : snapshot.getChildren()) {
                    Task t = c.getValue(Task.class);
                    if (t != null) Datos.TASKS.add(t);
                }
                Collections.sort(Datos.TASKS, (a, b) -> Integer.compare(a.id, b.id));
                // Cada vez que cambian las tareas se reprograman los recordatorios
                Datos.tareasListas = true;
                Recordatorios.programarTodas(FirebaseApp.getInstance().getApplicationContext(), Datos.TASKS);
                onChange.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        DatabaseReference gradeRef = base.child("grades");
        ValueEventListener gradeL = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Datos.GRADES.clear();
                for (DataSnapshot c : snapshot.getChildren()) {
                    Grade g = c.getValue(Grade.class);
                    if (g != null) Datos.GRADES.add(g);
                }
                Collections.sort(Datos.GRADES, (a, b) -> Integer.compare(a.id, b.id));
                onChange.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        DatabaseReference apunteRef = base.child("notes");
        ValueEventListener apunteL = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Datos.APUNTES.clear();
                for (DataSnapshot c : snapshot.getChildren()) {
                    Apunte a = c.getValue(Apunte.class);
                    if (a != null) Datos.APUNTES.add(a);
                }
                // Los más recientes primero
                Collections.sort(Datos.APUNTES, (a, b) -> Long.compare(b.fecha, a.fecha));
                onChange.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        subRef.addValueEventListener(subL);
        taskRef.addValueEventListener(taskL);
        gradeRef.addValueEventListener(gradeL);
        apunteRef.addValueEventListener(apunteL);
        h.add(subRef, subL);
        h.add(taskRef, taskL);
        h.add(gradeRef, gradeL);
        h.add(apunteRef, apunteL);
        return h;
    }

    public static void guardarMateria(Subject s) {
        DatabaseReference r = root();
        if (r != null) r.child("subjects").child(String.valueOf(s.id)).setValue(s);
    }

    public static void guardarTarea(Task t) {
        DatabaseReference r = root();
        if (r != null) r.child("tasks").child(String.valueOf(t.id)).setValue(t);
    }

    public static void guardarNota(Grade g) {
        DatabaseReference r = root();
        if (r != null) r.child("grades").child(String.valueOf(g.id)).setValue(g);
    }

    public static void eliminarNota(int id) {
        DatabaseReference r = root();
        if (r != null) r.child("grades").child(String.valueOf(id)).removeValue();
    }

    public static void guardarApunte(Apunte a) {
        DatabaseReference r = root();
        if (r != null) r.child("notes").child(String.valueOf(a.id)).setValue(a);
    }

    public static void eliminarApunte(int id) {
        DatabaseReference r = root();
        if (r == null) return;
        for (Apunte a : Datos.APUNTES) if (a.id == id) Archivos.borrarDe(a); // sus archivos también
        r.child("notes").child(String.valueOf(id)).removeValue();
    }

    /** Marca o desmarca una tarea y guarda cuándo se completó (para saber si fue tarde). */
    public static void marcarTarea(int id, boolean done) {
        DatabaseReference r = root();
        if (r == null) return;
        java.util.Map<String, Object> cambios = new java.util.HashMap<>();
        cambios.put("done", done);
        cambios.put("completedAt", done ? System.currentTimeMillis() : 0);
        r.child("tasks").child(String.valueOf(id)).updateChildren(cambios);
    }

    // ------------------------------------------------------------------
    // Perfil del usuario: users/{uid}/nombre, correo, carrera, universidad, semestre
    // ------------------------------------------------------------------

    public interface PerfilCallback { void onPerfil(UserProfile perfil); }

    private static final String[] CAMPOS_PERFIL =
            {"nombre", "correo", "carrera", "universidad", "semestre", "foto", "genero"};

    /**
     * Lee el perfil una sola vez. Se lee campo por campo (y no todo users/{uid}) para no
     * descargar también las materias, los apuntes con fotos y los archivos cada vez que entras.
     */
    public static void leerPerfil(PerfilCallback cb) {
        DatabaseReference r = root();
        if (r == null) return;
        java.util.Map<String, String> valores = new java.util.HashMap<>();
        int[] faltan = {CAMPOS_PERFIL.length};
        boolean[] fallo = {false};
        for (String campo : CAMPOS_PERFIL) {
            r.child(campo).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snap) {
                    if (fallo[0]) return;
                    Object v = snap.getValue();
                    valores.put(campo, v == null ? "" : String.valueOf(v));
                    if (--faltan[0] == 0) armarPerfil(valores, cb);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    if (fallo[0]) return;
                    fallo[0] = true;
                    // Sin permiso o sin conexión: al menos saluda con el nombre de la cuenta
                    FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                    if (u == null) return;
                    UserProfile p = new UserProfile();
                    p.nombre = u.getDisplayName() == null ? "" : u.getDisplayName();
                    p.correo = u.getEmail() == null ? "" : u.getEmail();
                    cb.onPerfil(p);
                }
            });
        }
    }

    private static void armarPerfil(java.util.Map<String, String> v, PerfilCallback cb) {
        UserProfile p = new UserProfile();
        p.nombre = v.get("nombre");
        p.correo = v.get("correo");
        p.carrera = v.get("carrera");
        p.universidad = v.get("universidad");
        p.semestre = v.get("semestre");
        p.foto = v.get("foto");
        p.genero = v.get("genero");
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (p.correo.isEmpty() && u != null && u.getEmail() != null) p.correo = u.getEmail();
        if (p.nombre.trim().isEmpty() && u != null && u.getDisplayName() != null) p.nombre = u.getDisplayName();
        // Guarda el nombre en la cuenta (cuentas creadas antes de este cambio)
        if (u != null && !p.nombre.trim().isEmpty() && !p.nombre.equals(u.getDisplayName())) {
            u.updateProfile(new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(p.nombre).build());
        }
        Datos.perfil = p; // se recuerda para mostrarlo al instante la próxima vez
        cb.onPerfil(p);
    }

    private static String texto(DataSnapshot snap, String clave) {
        String v = snap.child(clave).getValue(String.class);
        return v == null ? "" : v;
    }

    /** Guarda solo los campos editables (el correo no se cambia desde aquí). */
    public static void guardarPerfil(String nombre, String carrera, String universidad, String semestre, String genero) {
        DatabaseReference r = root();
        if (r == null) return;
        java.util.Map<String, Object> cambios = new java.util.HashMap<>();
        cambios.put("nombre", nombre);
        cambios.put("carrera", carrera);
        cambios.put("universidad", universidad);
        cambios.put("semestre", semestre);
        cambios.put("genero", genero);
        r.updateChildren(cambios);
        if (Datos.perfil != null) { // que Inicio muestre el nombre nuevo de una vez
            Datos.perfil.nombre = nombre;
            Datos.perfil.carrera = carrera;
            Datos.perfil.universidad = universidad;
            Datos.perfil.semestre = semestre;
            Datos.perfil.genero = genero;
        }
    }

    /** Guarda la foto de perfil (Base64). Con null o "" la borra. */
    public static void guardarFoto(String base64) {
        DatabaseReference r = root();
        if (r == null) return;
        if (base64 == null || base64.isEmpty()) r.child("foto").removeValue();
        else r.child("foto").setValue(base64);
        if (Datos.perfil != null) Datos.perfil.foto = base64 == null ? "" : base64;
    }

    // ------------------------------------------------------------------
    // Eliminar
    // ------------------------------------------------------------------

    public static void eliminarTarea(int id) {
        DatabaseReference r = root();
        if (r != null) r.child("tasks").child(String.valueOf(id)).removeValue();
    }

    /** Borra la materia y todas sus tareas en una sola operación. */
    public static void eliminarMateria(int id) {
        DatabaseReference r = root();
        if (r == null) return;
        java.util.Map<String, Object> borrar = new java.util.HashMap<>();
        borrar.put("subjects/" + id, null);
        for (Task t : Datos.TASKS) {
            if (t.subjectId == id) borrar.put("tasks/" + t.id, null);
        }
        for (Grade g : Datos.GRADES) {
            if (g.subjectId == id) borrar.put("grades/" + g.id, null);
        }
        for (Apunte a : Datos.APUNTES) {
            if (a.subjectId != id) continue;
            borrar.put("notes/" + a.id, null);
            if (a.adjuntos != null) for (Adjunto adj : a.adjuntos) borrar.put("archivos/" + adj.id, null);
        }
        r.updateChildren(borrar);
    }

    // ------------------------------------------------------------------
    // Privacidad y cuenta
    // ------------------------------------------------------------------

    /** Avisa si una operación terminó bien (true) o falló (false). */
    public interface Listo { void onListo(boolean ok); }

    /** Borra materias, tareas, notas y apuntes. El perfil (nombre, foto…) se mantiene. */
    public static void borrarDatosAcademicos(Listo cb) {
        DatabaseReference r = root();
        if (r == null) { cb.onListo(false); return; }
        java.util.Map<String, Object> borrar = new java.util.HashMap<>();
        borrar.put("subjects", null);
        borrar.put("tasks", null);
        borrar.put("grades", null);
        borrar.put("notes", null);
        borrar.put("archivos", null);
        r.updateChildren(borrar).addOnCompleteListener(t -> cb.onListo(t.isSuccessful()));
    }

    /** Borra todo el nodo del usuario (perfil incluido). Se usa antes de eliminar la cuenta. */
    public static void borrarTodo(Listo cb) {
        DatabaseReference r = root();
        if (r == null) { cb.onListo(false); return; }
        r.removeValue().addOnCompleteListener(t -> cb.onListo(t.isSuccessful()));
    }
}
