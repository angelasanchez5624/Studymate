package com.example.studymate;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

/** Configuración → Privacidad: notificaciones discretas, qué datos se guardan y borrar datos. */
public class PrivacyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);
        Ui.insets(this);

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());

        // Ocultar detalles en la pantalla de bloqueo (lo usa RecordatorioReceiver)
        SwitchCompat swOcultar = findViewById(R.id.swOcultarDetalle);
        swOcultar.setChecked(Ajustes.get(this, Ajustes.OCULTAR_DETALLE));
        swOcultar.setOnCheckedChangeListener((b, on) -> Ajustes.set(this, Ajustes.OCULTAR_DETALLE, on));

        findViewById(R.id.rowQueDatos).setOnClickListener(v ->
                mostrarTexto(R.string.priv_que_datos, R.string.priv_que_datos_texto));
        findViewById(R.id.rowPolitica).setOnClickListener(v ->
                mostrarTexto(R.string.priv_politica, R.string.priv_politica_texto));

        findViewById(R.id.rowBorrarDatos).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.priv_borrar)
                        .setMessage(R.string.priv_borrar_msg)
                        .setPositiveButton(R.string.priv_borrar_ok, (d, w) -> borrarDatos())
                        .setNegativeButton(R.string.cancelar, null)
                        .show());
    }

    private void mostrarTexto(int titulo, int texto) {
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(texto)
                .setPositiveButton(R.string.entendido, null)
                .show();
    }

    private void borrarDatos() {
        Repo.borrarDatosAcademicos(ok -> {
            if (!ok) {
                Toast.makeText(this, R.string.error_conexion, Toast.LENGTH_LONG).show();
                return;
            }
            // Sin tareas no debe quedar ningún recordatorio pendiente
            Recordatorios.cancelarTodoDelUsuario(this);
            Datos.limpiar();
            Toast.makeText(this, R.string.priv_borrado, Toast.LENGTH_LONG).show();
        });
    }
}
