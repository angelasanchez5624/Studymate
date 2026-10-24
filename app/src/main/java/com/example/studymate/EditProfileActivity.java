package com.example.studymate;

import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etNombre, etCorreo, etCarrera, etUniversidad;
    private Spinner spSemestre;
    private boolean cargado = false; // para no pisar lo que ya estás escribiendo
    private String genero = ""; // se elige al crear la cuenta; aquí solo se usa para el emoji

    // Foto: la que ya tenía, y la nueva elegida aquí (se guarda al tocar "Guardar cambios")
    private String fotoActual = "";
    private String fotoNueva = null; // null = sin cambios, "" = quitar foto, otro = foto nueva (Base64)

    // Selector de fotos de Android (no necesita pedir permisos)
    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::fotoElegida);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        Ui.insets(this);

        etNombre = findViewById(R.id.etNombre);
        etCorreo = findViewById(R.id.etCorreo);
        etCarrera = findViewById(R.id.etCarrera);
        etUniversidad = findViewById(R.id.etUniversidad);
        spSemestre = findViewById(R.id.spSemestre);

        // El correo es el de tu cuenta: no se edita desde aquí
        etCorreo.setEnabled(false);
        etCorreo.setAlpha(0.6f);

        // Lista del spinner en el idioma elegido; en Firebase se guarda siempre en español (UserProfile)
        String[] nombres = getResources().getStringArray(R.array.semestres);
        String[] opciones = new String[nombres.length + 1];
        opciones[0] = getString(R.string.semestre_elegir);
        System.arraycopy(nombres, 0, opciones, 1, nombres.length);
        Ui.lista(spSemestre, opciones);

        // Si no eligió género al registrarse, el emoji cambia con el nombre
        etNombre.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { actualizarEmoji(); }
        });

        Repo.leerPerfil(p -> {
            if (cargado || isFinishing() || isDestroyed()) return;
            cargado = true;
            etNombre.setText(p.nombre);
            etCorreo.setText(p.correo);
            etCarrera.setText(p.carrera);
            etUniversidad.setText(p.universidad);
            spSemestre.setSelection(p.semestreIndice() + 1); // -1 + 1 = "Selecciona semestre"
            genero = p.genero;
            actualizarEmoji();
            fotoActual = p.foto;
            if (fotoNueva == null) mostrarFoto(fotoActual);
        });

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());

        // Cambiar foto: tocando la foto o el texto "Cambiar foto"
        findViewById(R.id.avatarEditar).setOnClickListener(v -> opcionesFoto());
        findViewById(R.id.btnCambiarFoto).setOnClickListener(v -> opcionesFoto());

        findViewById(R.id.btnGuardarPerfil).setOnClickListener(v -> {
            String nombre = etNombre.getText().toString().trim();
            if (nombre.isEmpty()) {
                etNombre.setError(getString(R.string.nombre_error));
                return;
            }
            int pos = spSemestre.getSelectedItemPosition();
            String semestre = pos > 0 ? UserProfile.SEMESTRES_GUARDADOS[pos - 1] : "";

            Repo.guardarPerfil(nombre,
                    etCarrera.getText().toString().trim(),
                    etUniversidad.getText().toString().trim(),
                    semestre,
                    genero);
            if (fotoNueva != null) Repo.guardarFoto(fotoNueva); // "" la borra
            Toast.makeText(this, R.string.perfil_guardado, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    /** Foto que se ve ahora en pantalla (la nueva si ya elegiste una). */
    private String fotoEnPantalla() {
        return fotoNueva != null ? fotoNueva : fotoActual;
    }

    /** Sin foto abre la galería; con foto pregunta si cambiarla o quitarla. */
    private void opcionesFoto() {
        if (fotoEnPantalla() == null || fotoEnPantalla().isEmpty()) {
            abrirGaleria();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.foto_perfil)
                .setItems(new String[]{getString(R.string.foto_cambiar), getString(R.string.foto_quitar)}, (d, cual) -> {
                    if (cual == 0) {
                        abrirGaleria();
                    } else {
                        fotoNueva = "";
                        mostrarFoto("");
                    }
                })
                .show();
    }

    private void abrirGaleria() {
        elegirFoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    /** Se llama cuando el usuario escoge una imagen (uri es null si canceló). */
    private void fotoElegida(Uri uri) {
        if (uri == null) return;
        // Reducir la imagen puede tardar un poco: se hace fuera de la pantalla principal
        new Thread(() -> {
            String base64 = FotoPerfil.comprimir(this, uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (base64 == null) {
                    Toast.makeText(this, R.string.foto_error, Toast.LENGTH_LONG).show();
                    return;
                }
                fotoNueva = base64;
                mostrarFoto(base64);
            });
        }).start();
    }

    /** Emoji según el género elegido al registrarse (o el nombre, si no eligió). */
    private void actualizarEmoji() {
        Avatar.ponerEmoji(findViewById(R.id.tvAvatarEmojiEdit),
                Avatar.estudiante(genero, etNombre.getText().toString()));
    }

    private void mostrarFoto(String base64) {
        FotoPerfil.mostrar(findViewById(R.id.ivAvatarEdit), findViewById(R.id.tvAvatarEmojiEdit), base64);
    }
}
