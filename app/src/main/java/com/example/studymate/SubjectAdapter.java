package com.example.studymate;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.VH> {

    public interface OnClick { void onSubject(Subject s); }

    private final List<Subject> items;
    private final OnClick listener;
    private Seleccion sel; // para borrar varias (mantener presionado)

    public void setSeleccion(Seleccion s) { this.sel = s; }

    public SubjectAdapter(List<Subject> items, OnClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Subject s = items.get(position);
        Context c = h.itemView.getContext();
        h.nombre.setText(s.name);
        boolean hayProfe = s.professor != null && !s.professor.trim().isEmpty();
        h.profesor.setText(hayProfe ? c.getString(R.string.docente_nombre, s.professor.trim()) : "");
        h.profesor.setVisibility(hayProfe ? View.VISIBLE : View.GONE);
        // Modo selección: mantener presionado marca la materia; si ya hay algo marcado, tocar marca/desmarca
        boolean marcada = sel != null && sel.esta(s.id);
        h.itemView.setBackgroundResource(marcada ? R.drawable.bg_card_selected : R.drawable.bg_card_20);
        h.icono.setImageResource(marcada ? R.drawable.ic_check : R.drawable.ic_libro);
        h.icono.setColorFilter(SubjectColors.text(c, s.id));
        SubjectColors.tint(h.icono, SubjectColors.bg(c, s.id));
        h.itemView.setOnClickListener(v -> {
            if (sel != null && sel.activa()) sel.alternar(s.id);
            else listener.onSubject(s);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (sel == null) return false;
            sel.alternar(s.id);
            return true;
        });

        // Un cuadrito por horario: la hora arriba y el salón debajo
        h.listHorarios.removeAllViews();
        List<Bloque> conDatos = new ArrayList<>();
        for (Bloque b : Horario.bloques(s)) {
            boolean vacio = Horario.texto(b).isEmpty() && (b.room == null || b.room.isEmpty());
            if (!vacio) conDatos.add(b);
        }
        for (Bloque b : conDatos) {
            View chip = LayoutInflater.from(c).inflate(R.layout.item_horario_chip, h.listHorarios, false);
            SubjectColors.tint(chip, SubjectColors.bg(c, s.id));

            TextView hora = chip.findViewById(R.id.tvChipHora);
            hora.setText(textoHora(b));
            hora.setTextColor(SubjectColors.text(c, s.id));
            Iconos.alInicio(hora, R.drawable.ic_reloj);

            TextView salon = chip.findViewById(R.id.tvChipSalon);
            boolean haySalon = b.room != null && !b.room.isEmpty();
            salon.setText(haySalon ? b.room : "");
            salon.setVisibility(haySalon ? View.VISIBLE : View.GONE);
            Iconos.alInicio(salon, R.drawable.ic_lugar);

            h.listHorarios.addView(chip);
        }

        // Si no tiene ningún horario, se muestra un texto sencillo
        h.horario.setVisibility(conDatos.isEmpty() ? View.VISIBLE : View.GONE);
        h.horario.setText(c.getString(R.string.sin_horario));
        h.horario.setTextColor(SubjectColors.text(c, s.id));
        Iconos.alInicio(h.horario, R.drawable.ic_reloj);
    }

    /** "Lun / Jue · 6:00–8:00 a. m." (o lo que haya: solo días, solo hora, o "Horario sin definir"). */
    private static String textoHora(Bloque b) {
        boolean[] dias = Horario.dias(b);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (!dias[i]) continue;
            if (sb.length() > 0) sb.append(" / ");
            sb.append(Horario.corto(i));
        }
        String horas = Horario.rango(b.startTime, b.endTime);
        if (!horas.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(horas);
        }
        return sb.length() == 0 ? App.t(R.string.horario_sin_definir) : sb.toString();
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView nombre, profesor, horario;
        android.widget.ImageView icono;
        LinearLayout listHorarios;
        VH(View v) {
            super(v);
            nombre = v.findViewById(R.id.tvNombre);
            profesor = v.findViewById(R.id.tvProfesor);
            horario = v.findViewById(R.id.tvHorario);
            icono = v.findViewById(R.id.tvIcono);
            listHorarios = v.findViewById(R.id.listHorarios);
        }
    }
}
