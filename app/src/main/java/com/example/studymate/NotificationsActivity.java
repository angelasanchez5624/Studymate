package com.example.studymate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;

/** Configuración → Notificaciones: recordatorios y permisos del teléfono. */
public class NotificationsActivity extends AppCompatActivity {

    private SwitchCompat swRecordatorios, swClases;
    private static final int[] OPCIONES_MIN = {5, 10, 15, 30, 60}; // minutos antes de la clase

    // Pide el permiso de notificaciones. Si el usuario ya lo negó dos veces, Android no vuelve
    // a preguntar, así que se abren los ajustes de la app.
    private final ActivityResultLauncher<String> pedirPermiso =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedido -> {
                if (!concedido && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    abrirAjustesAndroid();
                }
                actualizarBanner();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        Ui.insets(this);

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());

        swRecordatorios = findViewById(R.id.swRecordatorios);

        // Estado guardado
        swRecordatorios.setChecked(Ajustes.get(this, Ajustes.RECORDATORIOS));
        actualizarAvisos();

        // "Avisarme": el usuario elige cuánto antes de la entrega
        findViewById(R.id.filaAviso1).setOnClickListener(v -> elegirAviso(Ajustes.AVISO1_MIN, R.string.aviso_primero));

        // Se guardan y se reprograman los avisos de inmediato
        swRecordatorios.setOnCheckedChangeListener((b, on) -> {
            Ajustes.set(this, Ajustes.RECORDATORIOS, on);
            actualizarAvisos();
            reprogramar();
        });

        // Avisos de clase
        swClases = findViewById(R.id.swClases);
        swClases.setChecked(Ajustes.get(this, Ajustes.AVISO_CLASES));
        swClases.setOnCheckedChangeListener((b, on) -> {
            Ajustes.set(this, Ajustes.AVISO_CLASES, on);
            actualizarClases();
            Recordatorios.reprogramarClasesGuardadas(this);
        });
        findViewById(R.id.filaMinClases).setOnClickListener(v -> elegirMinutos());
        actualizarClases();

        findViewById(R.id.btnPermitir).setOnClickListener(v -> permitir());
        findViewById(R.id.btnPermisoNotif).setOnClickListener(v -> abrirAjustesAndroid());
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualizarBanner(); // por si volvió de los ajustes de Android
    }

    /** El aviso amarillo aparece solo si Android tiene bloqueadas las notificaciones. */
    private void actualizarBanner() {
        boolean bloqueadas = !NotificationManagerCompat.from(this).areNotificationsEnabled();
        findViewById(R.id.bannerPermiso).setVisibility(bloqueadas ? View.VISIBLE : View.GONE);
    }

    private void permitir() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            abrirAjustesAndroid(); // el permiso está, pero se apagaron desde los ajustes
        }
    }

    /** Abre los ajustes de notificaciones de Android para esta app. */
    private void abrirAjustesAndroid() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(i);
    }

    /** Muestra el tiempo elegido; la fila solo se puede tocar si los recordatorios están activos. */
    private void actualizarAvisos() {
        boolean on = swRecordatorios.isChecked();
        View fila = findViewById(R.id.filaAviso1);
        fila.setEnabled(on);
        fila.setAlpha(on ? 1f : 0.45f);
        ((android.widget.TextView) findViewById(R.id.tvAviso1)).setText(Avisos.antes(this, Ajustes.avisoMin(this, Ajustes.AVISO1_MIN)));
    }

    /**
     * Lista para elegir cuánto antes avisar: 10 min … 1 semana, "Personalizado…" o "Desactivado".
     * Se guarda en minutos y se reprograman los avisos de inmediato.
     */
    private void elegirAviso(String clave, int titulo) {
        if (!swRecordatorios.isChecked()) return;
        int actual = Ajustes.avisoMin(this, clave);
        int n = Avisos.OPCIONES.length;
        String[] textos = new String[n + 2];
        int marcado = -1;
        for (int i = 0; i < n; i++) {
            textos[i] = Avisos.antes(this, Avisos.OPCIONES[i]);
            if (Avisos.OPCIONES[i] == actual) marcado = i;
        }
        boolean esPersonalizado = actual > 0 && marcado == -1;
        textos[n] = esPersonalizado
                ? getString(R.string.aviso_personalizado_valor, Avisos.antes(this, actual))
                : getString(R.string.aviso_personalizado);
        textos[n + 1] = getString(R.string.aviso_desactivado);
        if (esPersonalizado) marcado = n;
        if (actual <= 0) marcado = n + 1;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(titulo)
                .setSingleChoiceItems(textos, marcado, (d, cual) -> {
                    d.dismiss();
                    if (cual == n) {
                        elegirPersonalizado(clave, titulo, actual);
                        return;
                    }
                    guardarAviso(clave, cual == n + 1 ? 0 : Avisos.OPCIONES[cual]);
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    /** "Personalizado…": un número (1–99) y la unidad (minutos, horas, días o semanas). */
    private void elegirPersonalizado(String clave, int titulo, int actual) {
        int[] unidades = {Avisos.MINUTO, Avisos.HORA, Avisos.DIA, Avisos.SEMANA};
        String[] nombres = {getString(R.string.unidad_minutos), getString(R.string.unidad_horas),
                getString(R.string.unidad_dias), getString(R.string.unidad_semanas)};

        // Valor inicial: el actual, con su unidad más grande exacta
        int u = 0;
        for (int i = unidades.length - 1; i >= 0; i--) {
            if (actual > 0 && actual % unidades[i] == 0 && actual / unidades[i] <= 99) { u = i; break; }
        }
        int valor = actual > 0 ? Math.max(1, Math.min(99, actual / unidades[u])) : 1;

        android.widget.NumberPicker npValor = new android.widget.NumberPicker(this);
        npValor.setMinValue(1);
        npValor.setMaxValue(99);
        npValor.setValue(valor);
        npValor.setContentDescription(getString(R.string.aviso_cantidad));

        android.widget.NumberPicker npUnidad = new android.widget.NumberPicker(this);
        npUnidad.setMinValue(0);
        npUnidad.setMaxValue(nombres.length - 1);
        npUnidad.setDisplayedValues(nombres);
        npUnidad.setValue(u);
        npUnidad.setWrapSelectorWheel(false);
        npUnidad.setContentDescription(getString(R.string.aviso_unidad));

        android.widget.LinearLayout caja = new android.widget.LinearLayout(this);
        caja.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        caja.setGravity(android.view.Gravity.CENTER);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        caja.setPadding(pad, pad, pad, 0);
        caja.addView(npValor);
        android.widget.TextView espacio = new android.widget.TextView(this);
        espacio.setWidth(pad);
        caja.addView(espacio);
        caja.addView(npUnidad);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(R.string.aviso_personalizado_msg)
                .setView(caja)
                .setPositiveButton(R.string.guardar, (d, w) ->
                        guardarAviso(clave, npValor.getValue() * unidades[npUnidad.getValue()]))
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void guardarAviso(String clave, int minutos) {
        Ajustes.setInt(this, clave, minutos);
        actualizarAvisos();
        reprogramar();
    }

    /** "Avisar antes": 5, 10, 15, 30 min o 1 hora. */
    private void elegirMinutos() {
        if (!swClases.isChecked()) return;
        int actual = Ajustes.getInt(this, Ajustes.MIN_CLASES, Recordatorios.MIN_CLASES_DEFECTO);
        String[] textos = new String[OPCIONES_MIN.length];
        int marcado = 0;
        for (int i = 0; i < OPCIONES_MIN.length; i++) {
            textos[i] = getString(R.string.min_antes, OPCIONES_MIN[i]);
            if (OPCIONES_MIN[i] == actual) marcado = i;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.notif_clases_min)
                .setSingleChoiceItems(textos, marcado, (d, cual) -> {
                    d.dismiss();
                    Ajustes.setInt(this, Ajustes.MIN_CLASES, OPCIONES_MIN[cual]);
                    actualizarClases();
                    Recordatorios.reprogramarClasesGuardadas(this);
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void actualizarClases() {
        boolean on = swClases.isChecked();
        ((android.widget.TextView) findViewById(R.id.tvMinClases)).setText(getString(R.string.min_antes,
                Ajustes.getInt(this, Ajustes.MIN_CLASES, Recordatorios.MIN_CLASES_DEFECTO)));
        findViewById(R.id.filaMinClases).setAlpha(on ? 1f : 0.45f);
    }

    private void reprogramar() {
        Recordatorios.programarTodas(this, Datos.TASKS);
    }
}
