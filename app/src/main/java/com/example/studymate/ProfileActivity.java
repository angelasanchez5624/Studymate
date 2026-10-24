package com.example.studymate;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private Repo.Handle handle;
    private String fotoActual = "";
    private MenuLateral menu; // menú ☰ con estadísticas y configuración

    // Selector de fotos de Android (no necesita pedir permisos)
    private final ActivityResultLauncher<PickVisualMediaRequest> elegirFoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::fotoElegida);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Ui.insets(this);
        BottomNavHelper.setup(this, BottomNavHelper.PERFIL);

        // Al tocar el avatar: elegir foto (o quitarla si ya hay una)
        findViewById(R.id.avatarPerfil).setOnClickListener(v -> {
            if (fotoActual.isEmpty()) {
                abrirGaleria();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.foto_perfil)
                    .setItems(new String[]{getString(R.string.foto_cambiar), getString(R.string.foto_quitar)}, (d, cual) -> {
                        if (cual == 0) abrirGaleria();
                        else {
                            Repo.guardarFoto(null);
                            fotoActual = "";
                            FotoPerfil.mostrar(findViewById(R.id.ivAvatar), findViewById(R.id.tvAvatarEmoji), "");
                        }
                    })
                    .show();
        });

        // Menú ☰
        menu = new MenuLateral(this, findViewById(R.id.drawerPerfil));
        findViewById(R.id.btnMenu).setOnClickListener(v -> menu.abrirMenu());

        // Con el menú abierto, el botón "atrás" lo cierra en vez de salir
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (menu.estaAbierto()) {
                    menu.cerrarMenu();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        findViewById(R.id.btnEditarPerfil).setOnClickListener(v ->
                startActivity(new Intent(this, EditProfileActivity.class)));

        // Cerrar sesión: primero pregunta si de verdad quiere salir
        findViewById(R.id.btnCerrarSesion).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.cerrar_sesion)
                        .setMessage(R.string.cerrar_sesion_msg)
                        .setPositiveButton(R.string.salir, (d, w) -> {
                            Sesion.cerrar(this);
                            Intent i = new Intent(this, LoginActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                        })
                        .setNegativeButton(R.string.cancelar, null)
                        .show());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Datos.perfil != null) mostrarPerfil(Datos.perfil); // al instante, sin esperar a Firebase
        Repo.leerPerfil(this::mostrarPerfil);
        handle = Repo.listen(this::mostrarConteos);
    }

    @Override
    protected void onResume() {
        super.onResume();
        menu.refrescar(); // por si cambiaron las notificaciones o el idioma
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    private void abrirGaleria() {
        elegirFoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    /** Se llama cuando el usuario escoge una imagen (uri es null si canceló). */
    private void fotoElegida(Uri uri) {
        if (uri == null) return;
        Toast.makeText(this, R.string.foto_subiendo, Toast.LENGTH_SHORT).show();
        // Reducir la imagen puede tardar un poco: se hace fuera de la pantalla principal
        new Thread(() -> {
            String base64 = FotoPerfil.comprimir(this, uri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (base64 == null) {
                    Toast.makeText(this, R.string.foto_error, Toast.LENGTH_LONG).show();
                    return;
                }
                Repo.guardarFoto(base64);
                fotoActual = base64;
                FotoPerfil.mostrar(findViewById(R.id.ivAvatar), findViewById(R.id.tvAvatarEmoji), base64);
                Toast.makeText(this, R.string.foto_ok, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void mostrarPerfil(UserProfile p) {
        if (isFinishing() || isDestroyed()) return;
        fotoActual = p.foto;
        Avatar.ponerEmoji(findViewById(R.id.tvAvatarEmoji), p.emoji());
        FotoPerfil.mostrar(findViewById(R.id.ivAvatar), findViewById(R.id.tvAvatarEmoji), p.foto);
        menu.mostrarCuenta(p);
        ((TextView) findViewById(R.id.tvNombrePerfil)).setText(p.nombre.isEmpty() ? getString(R.string.sin_nombre) : p.nombre);
        ((TextView) findViewById(R.id.tvCorreoPerfil)).setText(p.correo);
        ((TextView) findViewById(R.id.tvStatSemestre)).setText(p.semestreCorto());
        ((TextView) findViewById(R.id.tvCarrera)).setText(p.carrera.isEmpty() ? "—" : p.carrera);
        ((TextView) findViewById(R.id.tvSemestre)).setText(p.semestre.isEmpty() ? "—" : p.semestreTexto());
        ((TextView) findViewById(R.id.tvUniversidad)).setText(p.universidad.isEmpty() ? "—" : p.universidad);
    }

    private void mostrarConteos() {
        ((TextView) findViewById(R.id.tvStatMaterias)).setText(String.valueOf(Datos.SUBJECTS.size()));
        ((TextView) findViewById(R.id.tvStatTareas)).setText(String.valueOf(Datos.TASKS.size()));
    }
}
