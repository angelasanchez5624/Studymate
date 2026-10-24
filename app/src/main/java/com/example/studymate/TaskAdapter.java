package com.example.studymate;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {

    public interface OnToggle { void onToggle(Task t); }

    public interface OnClick { void onTask(Task t); }

    private final List<Task> items;
    private final OnToggle listener;
    private OnClick clickListener;
    private Seleccion sel; // para borrar varias (mantener presionado)

    public void setSeleccion(Seleccion s) { this.sel = s; }

    public void setOnClick(OnClick l) { this.clickListener = l; }

    public TaskAdapter(List<Task> items, OnToggle listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Task t = items.get(position);
        android.content.Context c = h.itemView.getContext();
        Subject s = Datos.subject(t.subjectId);

        h.nombre.setText(t.name);
        h.descripcion.setText(t.description);
        // Sin descripción no se deja el espacio vacío
        h.descripcion.setVisibility(t.description == null || t.description.trim().isEmpty() ? View.GONE : View.VISIBLE);
        if (t.done) h.nombre.setPaintFlags(h.nombre.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        else h.nombre.setPaintFlags(h.nombre.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        h.itemView.setAlpha(t.done ? 0.75f : 1f);

        h.check.setBackgroundResource(t.done ? R.drawable.bg_check_on : R.drawable.bg_check_off);
        h.check.setText(t.done ? "✓" : "");
        // Modo selección: mantener presionado marca la tarea; si ya hay algo marcado, tocar marca/desmarca
        h.check.setOnClickListener(v -> {
            if (sel != null && sel.activa()) sel.alternar(t.id);
            else listener.onToggle(t);
        });
        h.itemView.setOnClickListener(v -> {
            if (sel != null && sel.activa()) sel.alternar(t.id);
            else if (clickListener != null) clickListener.onTask(t);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (sel == null) return false;
            sel.alternar(t.id);
            return true;
        });

        // Tag de la materia
        h.tagMateria.setText(s.name);
        SubjectColors.tint(h.tagMateria, SubjectColors.bg(c, s.id));
        h.tagMateria.setTextColor(SubjectColors.text(c, s.id));

        // Tag del tipo (solo si no es una tarea normal)
        if (t.tipo != Task.TAREA) {
            int[] col = TaskUtil.tipoColores(t.tipo);
            h.tagTipo.setText(TaskUtil.tipoTexto(t.tipo));
            SubjectColors.tint(h.tagTipo, ContextCompat.getColor(c, col[0]));
            h.tagTipo.setTextColor(ContextCompat.getColor(c, col[1]));
            h.tagTipo.setVisibility(View.VISIBLE);
        } else {
            h.tagTipo.setVisibility(View.GONE);
        }

        // Tag de prioridad
        h.tagPrioridad.setText(TaskUtil.prioridadTexto(t.priority));
        int pBg, pTx;
        if (t.priority == Task.ALTA) { pBg = R.color.pink_bg; pTx = R.color.pink_text; }
        else if (t.priority == Task.BAJA) { pBg = R.color.mint_bg; pTx = R.color.mint_text; }
        else { pBg = R.color.blue_bg; pTx = R.color.blue_text; }
        SubjectColors.tint(h.tagPrioridad, ContextCompat.getColor(c, pBg));
        h.tagPrioridad.setTextColor(ContextCompat.getColor(c, pTx));
        h.tagPrioridad.setVisibility(t.done ? View.GONE : View.VISIBLE);

        // Tag de estado + tarjeta roja si está vencida
        boolean vencida = TaskUtil.vencida(t);
        boolean marcada = sel != null && sel.esta(t.id);
        h.itemView.setBackgroundResource(marcada ? R.drawable.bg_card_selected
                : (vencida ? R.drawable.bg_card_overdue : R.drawable.bg_card_18));
        if (marcada) h.itemView.setAlpha(1f);
        if (t.done && TaskUtil.entregadaTarde(t)) {
            // Se completó después de la hora de entrega
            h.tagEstado.setText(c.getString(R.string.entregada_tarde, TaskUtil.retraso(t)));
            SubjectColors.tint(h.tagEstado, ContextCompat.getColor(c, R.color.yellow_bg));
            h.tagEstado.setTextColor(ContextCompat.getColor(c, R.color.red_text));
            Iconos.alInicio(h.tagEstado, R.drawable.ic_reloj);
        } else if (t.done) {
            h.tagEstado.setText(R.string.completada);
            SubjectColors.tint(h.tagEstado, ContextCompat.getColor(c, R.color.mint_bg));
            h.tagEstado.setTextColor(ContextCompat.getColor(c, R.color.mint_text));
            Iconos.alInicio(h.tagEstado, R.drawable.ic_hecho);
        } else if (vencida) {
            h.tagEstado.setText(c.getString(R.string.vencida_tag, TaskUtil.entregaTexto(t)));
            SubjectColors.tint(h.tagEstado, ContextCompat.getColor(c, R.color.red_pastel));
            h.tagEstado.setTextColor(ContextCompat.getColor(c, R.color.red_text));
            Iconos.alInicio(h.tagEstado, R.drawable.ic_alerta);
        } else {
            h.tagEstado.setText(TaskUtil.entregaTexto(t));
            SubjectColors.tint(h.tagEstado, ContextCompat.getColor(c, R.color.yellow_bg));
            h.tagEstado.setTextColor(ContextCompat.getColor(c, R.color.yellow_text));
            Iconos.alInicio(h.tagEstado, R.drawable.ic_reloj);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView nombre, descripcion, check, tagMateria, tagEstado, tagPrioridad, tagTipo;
        VH(View v) {
            super(v);
            nombre = v.findViewById(R.id.tvNombre);
            descripcion = v.findViewById(R.id.tvDescripcion);
            check = v.findViewById(R.id.btnCheck);
            tagMateria = v.findViewById(R.id.tagMateria);
            tagEstado = v.findViewById(R.id.tagEstado);
            tagPrioridad = v.findViewById(R.id.tagPrioridad);
            tagTipo = v.findViewById(R.id.tagTipo);
        }
    }
}
