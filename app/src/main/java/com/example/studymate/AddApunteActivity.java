package com.example.studymate;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Crea un apunte o lo edita (extra "apunteId"). La materia se elige dentro, en el campo "Materia"
 * (si llega el extra "subjectId", ya viene elegida).
 * El apunte puede llevar una foto (cámara o galería) y archivos adjuntos (PDF, Word, PowerPoint...).
 */
public class AddApunteActivity extends AppCompatActivity {

    private static final String K_FOTO = "foto", K_URI_CAMARA = "uriCamara",
            K_ADJ = "adjuntos", K_NUEVOS = "nuevos", K_QUITADOS = "quitados", K_MATERIA = "materia";

    private Apunte editando = null;
    private int subjectId;

    private String foto = "";       // foto actual del apunte (Base64); "" = sin foto
    private Uri uriCamara = null;   // dónde la cámara deja la foto que se está tomando
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();

    private ImageView ivFoto;
    private View cajaFoto, btnQuitarFoto, progresoFoto;
    private TextView btnCamara, btnGaleria;

    // Archivos adjuntos
    private ArrayList<Adjunto> adjuntos = new ArrayList<>();
    private ArrayList<String> subidosNuevos = new ArrayList<>(); // subidos en esta pantalla (se borran si cancelas)
    private ArrayList<String> quitados = new ArrayList<>();      // ya guardados que quitaste (se borran al guardar)
    private boolean guardado = false;
    private LinearLayout listArchivos;
    private View progresoArchivo, btnAdjuntar;

    // Selector de archivos de Android (Drive, Descargas, etc.). No pide permisos.
    private final ActivityResultLauncher<String[]> elegirArchivo =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) adjuntar(uri);
            });

    // Cámara: la app de cámara del teléfono guarda la foto en uriCamara.
    // No hace falta el permiso CAMERA porque la foto la toma la app de cámara, no StudyMate.
    private final ActivityResultLauncher<Uri> tomarFoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && uriCamara != null) procesar(uriCamara);
            });

    // Galería: selector de fotos de Android (no pide permisos)
    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) procesar(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_apunte);
        Ui.insets(this);

        EditText etTitulo = findViewById(R.id.etTituloApunte);
        EditText etTexto = findViewById(R.id.etTextoApunte);

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelarApunte).setOnClickListener(v -> finish());

        subjectId = getIntent().getIntExtra("subjectId", -1);
        int apunteId = getIntent().getIntExtra("apunteId", -1);
        if (apunteId != -1) {
            for (Apunte a : Datos.APUNTES) if (a.id == apunteId) editando = a;
            if (editando == null) {
                finish();
                return;
            }
            subjectId = editando.subjectId;
            ((TextView) findViewById(R.id.tvTituloPantalla)).setText(R.string.apunte_editar);
            ((TextView) findViewById(R.id.btnGuardarApunte)).setText(R.string.guardar_cambios);
            if (savedInstanceState == null) {
                etTitulo.setText(editando.titulo);
                etTexto.setText(editando.texto);
            }
            foto = editando.foto == null ? "" : editando.foto;
            if (editando.adjuntos != null) adjuntos = new ArrayList<>(editando.adjuntos);
        } else {
            // Apunte nuevo: si la materia que llegó no existe, se deja sin elegir
            boolean existe = false;
            for (Subject s : Datos.SUBJECTS) if (s.id == subjectId) existe = true;
            if (!existe) subjectId = -1;
        }

        // Si Android recreó la pantalla (girar el teléfono o volver de la cámara), se recupera la foto
        if (savedInstanceState != null) {
            foto = savedInstanceState.getString(K_FOTO, foto);
            String u = savedInstanceState.getString(K_URI_CAMARA);
            if (u != null) uriCamara = Uri.parse(u);
            ArrayList<Adjunto> a = leerLista(savedInstanceState, K_ADJ);
            if (a != null) adjuntos = a;
            ArrayList<String> nu = savedInstanceState.getStringArrayList(K_NUEVOS);
            if (nu != null) subidosNuevos = nu;
            ArrayList<String> qu = savedInstanceState.getStringArrayList(K_QUITADOS);
            if (qu != null) quitados = qu;
        }

        // Campo "Materia": al tocarlo se elige (o se cambia) la materia del apunte
        if (savedInstanceState != null) subjectId = savedInstanceState.getInt(K_MATERIA, subjectId);
        TextView tvMateria = findViewById(R.id.tvMateriaApunte);
        mostrarMateria(tvMateria);
        tvMateria.setOnClickListener(v -> cambiarMateria(tvMateria));

        prepararFoto();
        prepararArchivos();

        findViewById(R.id.btnGuardarApunte).setOnClickListener(v -> {
            String titulo = etTitulo.getText().toString().trim();
            String texto = etTexto.getText().toString().trim();
            if (subjectId == -1) {
                // Falta elegir la materia: se avisa en el campo y se abre la lista
                TextView tvM = findViewById(R.id.tvMateriaApunte);
                tvM.setError(getString(R.string.materia_elige_error));
                Toast.makeText(this, R.string.materia_elige_error, Toast.LENGTH_SHORT).show();
                cambiarMateria(tvM);
                return;
            }
            if (titulo.isEmpty() && texto.isEmpty() && foto.isEmpty() && adjuntos.isEmpty()) {
                etTexto.setError(getString(R.string.apunte_vacio_error));
                return;
            }
            // Sin título: se usa la primera línea del texto, o "Foto del <fecha>" si solo hay foto
            if (titulo.isEmpty()) {
                if (!texto.isEmpty()) {
                    titulo = texto.split("\n", 2)[0];
                    if (titulo.length() > 40) titulo = titulo.substring(0, 40) + "…";
                } else if (foto.isEmpty() && !adjuntos.isEmpty()) {
                    titulo = adjuntos.get(0).nombre; // solo archivos: el nombre del primero
                } else {
                    String fecha = DateFormat.getDateInstance(DateFormat.MEDIUM, App.locale()).format(new Date());
                    titulo = getString(R.string.apunte_foto_titulo, fecha);
                }
            }
            int id = editando != null ? editando.id : Datos.nextApunteId();
            Apunte nuevo = new Apunte(id, subjectId, titulo, texto, System.currentTimeMillis(), foto);
            nuevo.adjuntos = new ArrayList<>(adjuntos);
            Repo.guardarApunte(nuevo);
            for (String q : quitados) Archivos.borrar(q); // los que quitaste ya no se necesitan
            guardado = true;
            Toast.makeText(this, editando != null ? R.string.cambios_guardados : R.string.apunte_guardado,
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    // ---------------------------------------------------------------- Materia

    /**
     * Campo "Materia": un puntico con el color de la materia, el nombre y la flecha ▾
     * (como una lista desplegable; al tocarlo se elige otra materia).
     */
    private void mostrarMateria(TextView tv) {
        if (subjectId == -1) {
            // Todavía no se ha elegido: texto de ayuda en gris y solo la flecha
            tv.setText("");
            tv.setHint(R.string.materia_elige);
            android.graphics.drawable.Drawable f = androidx.appcompat.content.res.AppCompatResources
                    .getDrawable(this, R.drawable.ic_flecha_abajo);
            if (f != null) {
                f = f.mutate();
                int l = Math.round(tv.getTextSize() * 1.4f);
                f.setBounds(0, 0, l, l);
                f.setTint(androidx.core.content.ContextCompat.getColor(this, R.color.text_label));
            }
            tv.setCompoundDrawablesRelative(null, null, f, null);
            tv.setContentDescription(getString(R.string.materia_elige));
            return;
        }
        tv.setError(null);
        tv.setText(Datos.subject(subjectId).name);
        int lado = Math.round(tv.getTextSize() * 1.4f);
        android.graphics.drawable.Drawable punto = androidx.appcompat.content.res.AppCompatResources
                .getDrawable(this, R.drawable.bg_punto);
        if (punto != null) {
            punto = punto.mutate();
            int p = Math.round(lado * 0.6f);
            punto.setBounds(0, 0, p, p);
            punto.setTint(SubjectColors.text(this, subjectId));
        }
        android.graphics.drawable.Drawable flecha = androidx.appcompat.content.res.AppCompatResources
                .getDrawable(this, R.drawable.ic_flecha_abajo);
        if (flecha != null) {
            flecha = flecha.mutate();
            flecha.setBounds(0, 0, lado, lado);
            flecha.setTint(androidx.core.content.ContextCompat.getColor(this, R.color.text_label));
        }
        tv.setCompoundDrawablesRelative(punto, null, flecha, null);
        tv.setContentDescription(getString(R.string.materia_cambiar_cd, Datos.subject(subjectId).name));
    }

    /** Lista de materias para mover el apunte a otra (se guarda al tocar Guardar). */
    private void cambiarMateria(TextView tv) {
        if (Datos.SUBJECTS.isEmpty()) {
            // Sin materias no hay dónde guardar el apunte: ofrece crear una
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(R.string.elegir_sin_materias)
                    .setPositiveButton(R.string.materia_nueva, (d, w) ->
                            startActivity(new android.content.Intent(this, AddSubjectActivity.class)))
                    .setNegativeButton(R.string.cancelar, null)
                    .show();
            return;
        }
        String[] nombres = new String[Datos.SUBJECTS.size()];
        int marcada = -1;
        for (int i = 0; i < nombres.length; i++) {
            nombres[i] = Datos.SUBJECTS.get(i).name;
            if (Datos.SUBJECTS.get(i).id == subjectId) marcada = i;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.materia_cambiar)
                .setSingleChoiceItems(nombres, marcada, (d, cual) -> {
                    d.dismiss();
                    subjectId = Datos.SUBJECTS.get(cual).id;
                    mostrarMateria(tv);
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    // ---------------------------------------------------------------- Foto

    private void prepararFoto() {
        cajaFoto = findViewById(R.id.cajaFotoApunte);
        ivFoto = findViewById(R.id.ivFotoApunte);
        btnQuitarFoto = findViewById(R.id.btnQuitarFoto);
        progresoFoto = findViewById(R.id.progresoFoto);
        btnCamara = findViewById(R.id.btnCamara);
        btnGaleria = findViewById(R.id.btnGaleria);

        btnCamara.setOnClickListener(v -> abrirCamara());
        btnGaleria.setOnClickListener(v -> elegirFoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        btnQuitarFoto.setOnClickListener(v -> {
            foto = "";
            mostrarFoto();
        });
        ivFoto.setOnClickListener(v -> verGrande());

        mostrarFoto();
    }

    private void abrirCamara() {
        try {
            uriCamara = FotoApunte.uriTemporal(this);
            tomarFoto.launch(uriCamara);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.sin_app_camara, Toast.LENGTH_LONG).show();
        }
    }

    /** Comprime la foto en segundo plano (para que la pantalla no se congele) y la muestra. */
    private void procesar(Uri uri) {
        cargando(true);
        hilo.execute(() -> {
            String base64 = FotoApunte.comprimir(getApplicationContext(), uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                cargando(false);
                if (base64 == null) {
                    Toast.makeText(this, R.string.foto_error, Toast.LENGTH_LONG).show();
                    return;
                }
                foto = base64;
                mostrarFoto();
            });
        });
    }

    private void cargando(boolean si) {
        progresoFoto.setVisibility(si ? View.VISIBLE : View.GONE);
        btnCamara.setEnabled(!si);
        btnGaleria.setEnabled(!si);
    }

    /** Muestra la vista previa si hay foto; si no, solo los botones de cámara y galería. */
    private void mostrarFoto() {
        Bitmap b = FotoApunte.decodificar(foto);
        if (b == null) {
            ivFoto.setImageDrawable(null);
            cajaFoto.setVisibility(View.GONE);
            btnCamara.setText(R.string.foto_camara);
        } else {
            ivFoto.setImageBitmap(b);
            cajaFoto.setVisibility(View.VISIBLE);
            btnCamara.setText(R.string.foto_otra);
        }
    }

    /** Abre la foto en pantalla completa; se cierra tocándola. */
    private void verGrande() {
        Bitmap b = FotoApunte.decodificar(foto);
        if (b == null) return;
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(b);
        iv.setContentDescription(getString(R.string.foto_apunte_cd));
        iv.setOnClickListener(v -> d.dismiss());
        d.setContentView(iv);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        d.show();
    }

    // ---------------------------------------------------------------- Archivos

    private void prepararArchivos() {
        listArchivos = findViewById(R.id.listArchivos);
        progresoArchivo = findViewById(R.id.progresoArchivo);
        btnAdjuntar = findViewById(R.id.btnAdjuntar);
        // "*/*" = cualquier tipo: PDF, Word, PowerPoint, Excel, imágenes, texto...
        btnAdjuntar.setOnClickListener(v -> elegirArchivo.launch(new String[]{"*/*"}));
        mostrarArchivos();
    }

    /** Lee el archivo en segundo plano, lo sube y lo agrega a la lista. */
    private void adjuntar(Uri uri) {
        String id = Archivos.nuevoId();
        if (id == null) return;
        progresoArchivo.setVisibility(View.VISIBLE);
        btnAdjuntar.setEnabled(false);
        hilo.execute(() -> {
            Archivos.Leido leido = null;
            int error = 0;
            try {
                leido = Archivos.leer(getApplicationContext(), uri);
                Archivos.subir(id, leido.bytes);
            } catch (Archivos.MuyGrande e) {
                error = R.string.archivo_muy_grande;
            } catch (Exception e) {
                error = R.string.archivo_error;
            }
            final Archivos.Leido l = leido;
            final int err = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progresoArchivo.setVisibility(View.GONE);
                btnAdjuntar.setEnabled(true);
                if (err != 0) {
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                    return;
                }
                adjuntos.add(new Adjunto(id, l.nombre, l.tipo, l.bytes.length));
                subidosNuevos.add(id);
                mostrarArchivos();
            });
        });
    }

    private void mostrarArchivos() {
        listArchivos.removeAllViews();
        for (Adjunto adj : adjuntos) {
            View fila = getLayoutInflater().inflate(R.layout.item_archivo, listArchivos, false);
            ((ImageView) fila.findViewById(R.id.tvIconoArchivo)).setImageResource(Archivos.icono(adj.tipo, adj.nombre));
            ((TextView) fila.findViewById(R.id.tvNombreArchivo)).setText(adj.nombre);
            ((TextView) fila.findViewById(R.id.tvTamanoArchivo)).setText(Archivos.tamano(this, adj.tamano));
            fila.setContentDescription(getString(R.string.archivo_abrir_cd, adj.nombre));
            fila.setOnClickListener(v -> {
                progresoArchivo.setVisibility(View.VISIBLE);
                Archivos.abrir(this, adj, ok -> progresoArchivo.setVisibility(View.GONE));
            });
            fila.findViewById(R.id.btnQuitarArchivo).setOnClickListener(v -> {
                adjuntos.remove(adj);
                if (subidosNuevos.remove(adj.id)) Archivos.borrar(adj.id); // recién subido: se borra ya
                else quitados.add(adj.id); // ya estaba guardado: se borra al guardar
                mostrarArchivos();
            });
            listArchivos.addView(fila);
        }
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<Adjunto> leerLista(Bundle b, String clave) {
        Object o = b.getSerializable(clave, ArrayList.class);
        return o == null ? null : (ArrayList<Adjunto>) o;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(K_FOTO, foto);
        out.putInt(K_MATERIA, subjectId);
        if (uriCamara != null) out.putString(K_URI_CAMARA, uriCamara.toString());
        out.putSerializable(K_ADJ, adjuntos);
        out.putStringArrayList(K_NUEVOS, subidosNuevos);
        out.putStringArrayList(K_QUITADOS, quitados);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hilo.shutdown();
        if (isFinishing()) {
            FotoApunte.limpiarTemporales(this);
            // Saliste sin guardar: los archivos que subiste aquí no se quedan ocupando espacio
            if (!guardado) for (String id : subidosNuevos) Archivos.borrar(id);
        }
    }
}
