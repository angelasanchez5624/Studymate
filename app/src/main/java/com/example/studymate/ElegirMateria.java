package com.example.studymate;

import android.app.Activity;
import android.content.Intent;
import androidx.appcompat.app.AlertDialog;

/**
 * Diálogo "¿De qué materia?". Al elegir una, abre la pantalla indicada
 * (AddGradeActivity o AddApunteActivity) con esa materia.
 * Lo usan el botón + del Inicio y el + de Mis apuntes.
 */
public class ElegirMateria {

    public static void mostrar(Activity a, Class<?> pantalla) {
        if (Datos.SUBJECTS.isEmpty()) {
            // Sin materias no hay dónde guardar: ofrece crear una
            new AlertDialog.Builder(a)
                    .setMessage(R.string.elegir_sin_materias)
                    .setPositiveButton(R.string.materia_nueva, (d, w) ->
                            a.startActivity(new Intent(a, AddSubjectActivity.class)))
                    .setNegativeButton(R.string.cancelar, null)
                    .show();
            return;
        }
        String[] nombres = new String[Datos.SUBJECTS.size()];
        for (int i = 0; i < nombres.length; i++) nombres[i] = Datos.SUBJECTS.get(i).name;
        new AlertDialog.Builder(a)
                .setTitle(R.string.calcular_elige_materia)
                .setItems(nombres, (d, cual) -> a.startActivity(new Intent(a, pantalla)
                        .putExtra("subjectId", Datos.SUBJECTS.get(cual).id)))
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }
}
