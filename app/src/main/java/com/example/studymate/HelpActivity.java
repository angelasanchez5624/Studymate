package com.example.studymate;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/** Configuración → Ayuda y soporte: preguntas frecuentes, contacto y "acerca de". */
public class HelpActivity extends AppCompatActivity {

    /** ✏️ Cambia este correo por el de soporte de tu app. */
    private static final String CORREO_SOPORTE = "soporte.studymate@gmail.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        Ui.insets(this);

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());

        llenarPreguntas();

        String version = Ui.version(this);
        ((TextView) findViewById(R.id.tvVersionAyuda)).setText(getString(R.string.version, version));

        findViewById(R.id.rowEscribir).setOnClickListener(v ->
                enviarCorreo(getString(R.string.ayuda_asunto_soporte), ""));

        findViewById(R.id.rowReportar).setOnClickListener(v ->
                enviarCorreo(getString(R.string.ayuda_asunto_problema),
                        getString(R.string.ayuda_cuerpo_problema, version, Build.VERSION.RELEASE,
                                Build.MANUFACTURER + " " + Build.MODEL)));

        findViewById(R.id.rowAcerca).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.ayuda_acerca)
                        .setMessage(getString(R.string.ayuda_acerca_texto, version))
                        .setPositiveButton(R.string.aceptar, null)
                        .show());
    }

    /** Crea una fila por pregunta. Al tocarla se abre o se cierra la respuesta. */
    private void llenarPreguntas() {
        String[] preguntas = getResources().getStringArray(R.array.faq_preguntas);
        String[] respuestas = getResources().getStringArray(R.array.faq_respuestas);
        LinearLayout lista = findViewById(R.id.listaFaq);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < preguntas.length && i < respuestas.length; i++) {
            if (i > 0) {
                View linea = new View(this);
                linea.setBackgroundResource(R.color.divider);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, Math.round(getResources().getDisplayMetrics().density));
                lp.setMarginStart(Math.round(16 * getResources().getDisplayMetrics().density));
                lista.addView(linea, lp);
            }
            View item = inflater.inflate(R.layout.item_faq, lista, false);
            TextView tvRespuesta = item.findViewById(R.id.tvRespuesta);
            android.view.View tvFlecha = item.findViewById(R.id.tvFlecha);
            ((TextView) item.findViewById(R.id.tvPregunta)).setText(preguntas[i]);
            tvRespuesta.setText(respuestas[i]);
            item.setOnClickListener(v -> {
                boolean abrir = tvRespuesta.getVisibility() != View.VISIBLE;
                tvRespuesta.setVisibility(abrir ? View.VISIBLE : View.GONE);
                tvFlecha.animate().rotation(abrir ? 90f : 0f).setDuration(150).start();
            });
            lista.addView(item);
        }
    }

    /** Abre la app de correo con el destinatario y el asunto ya escritos. */
    private void enviarCorreo(String asunto, String cuerpo) {
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{CORREO_SOPORTE});
        i.putExtra(Intent.EXTRA_SUBJECT, asunto);
        if (!cuerpo.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, cuerpo);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.ayuda_sin_correo, CORREO_SOPORTE), Toast.LENGTH_LONG).show();
        }
    }
}
