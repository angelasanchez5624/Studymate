package com.example.studymate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SubjectsActivity extends AppCompatActivity {

    private SubjectAdapter adapter;
    private Seleccion sel;
    private Repo.Handle handle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subjects);
        Ui.insets(this);
        BottomNavHelper.setup(this, BottomNavHelper.MATERIAS);

        RecyclerView rv = findViewById(R.id.rvMaterias);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectAdapter(Datos.SUBJECTS, s -> {
            Intent i = new Intent(this, SubjectDetailActivity.class);
            i.putExtra("subjectId", s.id);
            startActivity(i);
        });
        rv.setAdapter(adapter);
        // Mantén presionada una materia para seleccionar una o varias y borrarlas
        sel = new Seleccion(this, Seleccion.MATERIAS, () -> adapter.notifyDataSetChanged());
        adapter.setSeleccion(sel);

        findViewById(R.id.btnMapa).setOnClickListener(v ->
                startActivity(new Intent(this, MapActivity.class)));
        findViewById(R.id.btnAddMateria).setOnClickListener(v ->
                startActivity(new Intent(this, AddSubjectActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        handle = Repo.listen(this::refrescar);
        refrescar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    private void refrescar() {
        int n = Datos.SUBJECTS.size();
        String texto = !Datos.materiasListas ? getString(R.string.cargando)
                : n == 0 ? getString(R.string.sin_materias)
                : getString(n == 1 ? R.string.materias_n_1 : R.string.materias_n, n);
        ((TextView) findViewById(R.id.tvCantidad)).setText(texto);
        java.util.List<Integer> existen = new java.util.ArrayList<>();
        for (Subject s : Datos.SUBJECTS) existen.add(s.id);
        sel.podar(existen);
        adapter.notifyDataSetChanged();
    }
}
