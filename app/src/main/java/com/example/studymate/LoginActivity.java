package com.example.studymate;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        Ajustes.cargar(this); // formato de hora elegido en Configuración

        // Solo entra directo si inició sesión desde este Login antes
        if (Sesion.activa(this)) {
            // Confirma que la cuenta todavía existe en Firebase
            auth.getCurrentUser().reload().addOnCompleteListener(t -> {
                if (t.isSuccessful() || t.getException() instanceof FirebaseNetworkException) {
                    entrarConPerfil(); // sin internet se deja entrar con la sesión guardada
                } else {
                    Sesion.cerrar(this);
                    mostrarLogin();
                }
            });
            return;
        }

        // Había una sesión de Firebase que no vino del Login (p. ej. recién registrado): se cierra
        if (auth.getCurrentUser() != null) Sesion.cerrar(this);

        mostrarLogin();
    }

    // Muestra la pantalla de Login y configura sus botones
    private void mostrarLogin() {
        setContentView(R.layout.activity_login);
        Ui.insets(this);

        EditText etCorreo = findViewById(R.id.etCorreo);
        EditText etPassword = findViewById(R.id.etPassword);
        MaterialButton btnIngresar = findViewById(R.id.btnIngresar);
        android.view.View progreso = findViewById(R.id.progresoLogin);
        android.widget.TextView tvError = findViewById(R.id.tvErrorLogin);
        // El aviso rojo se quita apenas la persona vuelve a escribir
        android.text.TextWatcher quitarError = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { tvError.setVisibility(android.view.View.GONE); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        };
        etCorreo.addTextChangedListener(quitarError);
        etPassword.addTextChangedListener(quitarError);

        btnIngresar.setOnClickListener(v -> {
            String correo = etCorreo.getText().toString().trim();
            String pass = etPassword.getText().toString();

            if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                etCorreo.setError(getString(R.string.correo_invalido));
                return;
            }
            if (pass.length() < 6) {
                mostrarError(tvError, getString(R.string.min_6));
                etPassword.requestFocus();
                return;
            }

            btnIngresar.setEnabled(false);
            btnIngresar.setText(R.string.iniciando_sesion);
            progreso.setVisibility(android.view.View.VISIBLE);
            auth.signInWithEmailAndPassword(correo, pass).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Sesion.iniciar(this); // ahora sí puede entrar directo la próxima vez
                    entrarConPerfil(); // la barra de carga sigue hasta que abre Inicio
                } else {
                    btnIngresar.setEnabled(true);
                    btnIngresar.setText(R.string.iniciar_sesion);
                    progreso.setVisibility(android.view.View.GONE);
                    // El error se muestra junto al campo de contraseña (y se conserva el correo)
                    // "⚠ Correo o contraseña incorrectos" en rojo debajo del campo (el correo se conserva)
                    mostrarError(tvError, mensajeError(task.getException()));
                    etPassword.requestFocus();
                }
            });
        });

        findViewById(R.id.tvCrearCuenta).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        // Restablecer contraseña por correo
        findViewById(R.id.tvOlvidaste).setOnClickListener(v -> {
            String correo = etCorreo.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                etCorreo.setError(getString(R.string.correo_para_enlace));
                return;
            }
            auth.sendPasswordResetEmail(correo).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, R.string.enlace_enviado, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, mensajeError(task.getException()), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * Lee el perfil ANTES de abrir Inicio, para que salga "Hola, <nombre>" desde el primer momento
     * (tanto al iniciar sesión como al abrir la app con la sesión ya iniciada).
     * Si Firebase tarda más de 3 segundos (o no hay internet), entra igual.
     */
    private void entrarConPerfil() {
        final boolean[] entro = {false};
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable entrar = () -> { if (!entro[0]) { entro[0] = true; irAlInicio(); } };
        h.postDelayed(entrar, 3000);
        Repo.leerPerfil(p -> { h.removeCallbacks(entrar); entrar.run(); });
    }

    private void irAlInicio() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }

    private void mostrarError(android.widget.TextView tv, String mensaje) {
        tv.setText(mensaje);
        tv.setVisibility(android.view.View.VISIBLE);
    }

    private String mensajeError(Exception e) {
        if (e instanceof FirebaseNetworkException) return getString(R.string.sin_internet);
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) return getString(R.string.credenciales_mal);
        return getString(R.string.error_generico);
    }
}
