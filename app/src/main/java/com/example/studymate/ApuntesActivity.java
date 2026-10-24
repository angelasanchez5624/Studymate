package com.example.studymate;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Mis apuntes: los apuntes de TODAS las materias (los más recientes primero).
 *  - Cada uno muestra la materia con su color.
 *  - Los chips de arriba filtran por materia ("Todas · Cálculo · Física…").
 *  - El buscador filtra por título, texto o nombre de la materia (y se combina con el chip).
 *  - Tocar abre el apunte; mantener presionado lo selecciona para borrar.
 */
public class ApuntesActivity extends AppCompatActivity {

    private Repo.Handle handle;
    private Seleccion sel;
    private EditText etBuscar;
    private int filtroMateria = -1; // -1 = todas las materias

    private static final String K_FILTRO = "filtroMateria";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apuntes);
        Ui.insets(this);
        if (savedInstanceState != null) filtroMateria = savedInstanceState.getInt(K_FILTRO, -1);

        sel = new Seleccion(this, Seleccion.APUNTES, this::cargar);
        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        // +: abre "Nuevo apunte" de una vez; la materia se elige adentro.
        // Si hay un chip de materia marcado, ya viene elegida (se puede cambiar).
        findViewById(R.id.btnAddApunte).setOnClickListener(v -> startActivity(
                new Intent(this, AddApunteActivity.class).putExtra("subjectId", filtroMateria)));

        etBuscar = findViewById(R.id.etBuscar);
        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { cargar(); }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        handle = Repo.listen(this::cargar);
        cargar(); // lo que ya hay, sin esperar a Firebase
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    private void cargar() {
        List<Integer> existen = new ArrayList<>();
        for (Apunte a : Datos.APUNTES) existen.add(a.id);
        sel.podar(existen);

        dibujarChips();

        String q = etBuscar.getText().toString().trim().toLowerCase(Locale.ROOT);
        LinearLayout lista = findViewById(R.id.listTodosApuntes);
        lista.removeAllViews();
        DateFormat formato = DateFormat.getDateInstance(DateFormat.MEDIUM, App.locale());

        int n = 0;
        for (Apunte a : Datos.APUNTES) {
            Subject s = Datos.subject(a.subjectId);
            if (filtroMateria != -1 && a.subjectId != filtroMateria) continue; // chip de materia
            if (!q.isEmpty() && !contiene(a.titulo, q) && !contiene(a.texto, q) && !contiene(s.name, q)) continue;
            n++;
            View fila = LayoutInflater.from(this).inflate(R.layout.item_apunte, lista, false);

            // Etiqueta con el nombre y el color de la materia
            TextView tag = fila.findViewById(R.id.tvMateriaTag);
            tag.setVisibility(View.VISIBLE);
            tag.setText(s.name);
            SubjectColors.tint(tag, SubjectColors.bg(this, s.id));
            tag.setTextColor(SubjectColors.text(this, s.id));

            ((TextView) fila.findViewById(R.id.tvTituloApunte)).setText(a.titulo);
            TextView texto = fila.findViewById(R.id.tvTextoApunte);
            texto.setText(a.texto);
            texto.setVisibility(a.texto == null || a.texto.isEmpty() ? View.GONE : View.VISIBLE);
            // Fecha, y si tiene foto o archivos: "23 oct 2026 · Con foto · 2 archivos" (con ícono de clip)
            TextView tvFecha = fila.findViewById(R.id.tvFechaApunte);
            tvFecha.setText(Apuntes.detalle(this, a, formato.format(new Date(a.fecha))));
            if (a.tieneFoto() || a.cuantosAdjuntos() > 0) Iconos.alInicio(tvFecha, a.cuantosAdjuntos() > 0 ? R.drawable.ic_adjunto : R.drawable.ic_camara);

            final int aid = a.id;
            if (sel.esta(aid)) fila.setBackgroundResource(R.drawable.bg_card_selected);
            fila.setOnClickListener(v -> {
                if (sel.activa()) {
                    sel.alternar(aid);
                    return;
                }
                startActivity(new Intent(this, AddApunteActivity.class).putExtra("apunteId", aid));
            });
            fila.setOnLongClickListener(v -> {
                sel.alternar(aid);
                return true;
            });
            lista.addView(fila);
        }

        // Título con el total: "Mis apuntes (5)"
        int total = Datos.APUNTES.size();
        ((TextView) findViewById(R.id.tvTituloApuntes)).setText(total == 0
                ? getString(R.string.mis_apuntes) : getString(R.string.mis_apuntes) + " (" + total + ")");

        TextView vacio = findViewById(R.id.tvVacio);
        if (n == 0) {
            vacio.setVisibility(View.VISIBLE);
            vacio.setText(!Datos.materiasListas ? R.string.cargando
                    : total == 0 ? R.string.sin_apuntes : R.string.sin_resultados);
        } else {
            vacio.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- Chips por materia

    /** "Todas" + un chip por cada materia que tenga apuntes, con su color y cuántos tiene. */
    private void dibujarChips() {
        LinearLayout fila = findViewById(R.id.chipsMaterias);
        fila.removeAllViews();

        // Materias con apuntes, en el orden de la lista de materias
        java.util.Map<Integer, Integer> cuantos = new java.util.HashMap<>();
        for (Apunte a : Datos.APUNTES) cuantos.merge(a.subjectId, 1, Integer::sum);
        List<Subject> conApuntes = new ArrayList<>();
        for (Subject s : Datos.SUBJECTS) if (cuantos.containsKey(s.id)) conApuntes.add(s);

        // Si la materia elegida ya no tiene apuntes (o se borró), vuelve a "Todas"
        if (filtroMateria != -1 && !cuantos.containsKey(filtroMateria)) filtroMateria = -1;

        // Con una sola materia (o ninguna) no hace falta filtrar
        findViewById(R.id.scrollChips).setVisibility(conApuntes.size() >= 2 ? View.VISIBLE : View.GONE);
        if (conApuntes.size() < 2) return;

        fila.addView(chip(getString(R.string.todas), -1, filtroMateria == -1));
        for (Subject s : conApuntes) {
            fila.addView(chip(s.name + " · " + cuantos.get(s.id), s.id, filtroMateria == s.id));
        }
    }

    private TextView chip(String texto, int subjectId, boolean marcado) {
        TextView c = new TextView(this);
        c.setText(texto);
        c.setTextSize(13);
        c.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.nunito_bold));
        c.setGravity(android.view.Gravity.CENTER);
        c.setMinHeight(dp(48)); // área táctil mínima
        c.setPadding(dp(16), 0, dp(16), 0);
        c.setSingleLine(true);
        c.setForeground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_chip_ripple));

        int colorFondo, colorTexto;
        if (subjectId == -1) {
            // "Todas": borde lila; marcado = relleno lila con texto blanco
            colorFondo = androidx.core.content.ContextCompat.getColor(this, R.color.boton);
            colorTexto = androidx.core.content.ContextCompat.getColor(this, marcado ? R.color.white : R.color.texto_acento);
            c.setBackgroundResource(marcado ? R.drawable.bg_chip_filtro : R.drawable.bg_chip_todas);
            if (marcado) SubjectColors.tint(c, colorFondo);
        } else {
            // Materia: su color pastel; marcado = su color fuerte con el texto del color de la tarjeta
            colorFondo = marcado ? SubjectColors.text(this, subjectId) : SubjectColors.bg(this, subjectId);
            colorTexto = marcado ? androidx.core.content.ContextCompat.getColor(this, R.color.card_bg)
                    : SubjectColors.text(this, subjectId);
            c.setBackgroundResource(R.drawable.bg_chip_filtro);
            SubjectColors.tint(c, colorFondo);
        }
        c.setTextColor(colorTexto);
        if (marcado) Iconos.alInicio(c, R.drawable.ic_check);

        // TalkBack: "Cálculo · 3, seleccionado"
        c.setSelected(marcado);
        c.setContentDescription(texto + (marcado ? ", " + getString(R.string.seleccionado) : ""));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        c.setLayoutParams(lp);

        c.setOnClickListener(v -> {
            filtroMateria = subjectId;
            sel.limpiar(); // la selección para borrar no se mezcla entre filtros
            cargar();
        });
        return c;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(K_FILTRO, filtroMateria);
    }

    private static boolean contiene(String texto, String q) {
        return texto != null && texto.toLowerCase(Locale.ROOT).contains(q);
    }
}
