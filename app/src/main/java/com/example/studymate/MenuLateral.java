package com.example.studymate;

import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;

/**
 * Menú lateral (☰) de Perfil. Reemplaza la antigua pantalla de Configuración:
 *  - Encabezado con foto, nombre y correo (→ Editar perfil)
 *  - Mis estadísticas, Mis notas y Mis apuntes
 *  - Preferencias: Notificaciones, Idioma y formato de 24 horas
 *  - Privacidad y Ayuda y soporte
 *  - Cuenta: cambiar contraseña y eliminar cuenta (cerrar sesión está en Perfil)
 */
public class MenuLateral {

    private final AppCompatActivity a;
    private final DrawerLayout drawer;

    public MenuLateral(AppCompatActivity activity, DrawerLayout drawer) {
        this.a = activity;
        this.drawer = drawer;

        click(R.id.menuCuenta, () -> abrir(EditProfileActivity.class));
        click(R.id.menuEstadisticas, () -> abrir(StatsActivity.class));
        click(R.id.menuNotas, () -> abrir(MisNotasActivity.class));
        click(R.id.menuApuntes, () -> abrir(ApuntesActivity.class));
        click(R.id.menuNotificaciones, () -> abrir(NotificationsActivity.class));
        click(R.id.menuPrivacidad, () -> abrir(PrivacyActivity.class));
        click(R.id.menuAyuda, () -> abrir(HelpActivity.class));
        click(R.id.menuIdioma, this::elegirIdioma);
        click(R.id.menuTema, this::elegirTema);
        click(R.id.menuCambiarClave, this::cambiarClave);
        click(R.id.menuEliminarCuenta, this::eliminarCuenta);

        // Formato de hora
        SwitchCompat swHora24 = a.findViewById(R.id.swHora24);
        swHora24.setChecked(Ajustes.get(a, Ajustes.HORA_24));
        swHora24.setOnCheckedChangeListener((b, on) -> {
            Ajustes.set(a, Ajustes.HORA_24, on);
            Recordatorios.programarTodas(a, Datos.TASKS); // el texto de las notificaciones también usa el formato
            Toast.makeText(a, on ? R.string.formato_24_on : R.string.formato_24_off, Toast.LENGTH_SHORT).show();
        });

        ((TextView) a.findViewById(R.id.tvVersionMenu)).setText(a.getString(R.string.version, Ui.version(a)));
    }

    // ------------------------------------------------------------------
    // Abrir / cerrar
    // ------------------------------------------------------------------

    public void abrirMenu() {
        refrescar();
        drawer.openDrawer(GravityCompat.START);
    }

    public boolean estaAbierto() {
        return drawer.isDrawerOpen(GravityCompat.START);
    }

    public void cerrarMenu() {
        drawer.closeDrawer(GravityCompat.START);
    }

    /** Estado de las notificaciones y el idioma (por si cambiaron en otra pantalla). */
    public void refrescar() {
        ((TextView) a.findViewById(R.id.tvNotifEstado)).setText(estadoNotificaciones());
        ((TextView) a.findViewById(R.id.tvIdiomaActual)).setText(Idioma.nombreActual(a));
        ((TextView) a.findViewById(R.id.tvTemaActual)).setText(Tema.nombreActual(a));
    }

    /** Foto, nombre y correo del encabezado. */
    public void mostrarCuenta(UserProfile p) {
        ((TextView) a.findViewById(R.id.tvNombreMenu)).setText(p.nombre.isEmpty() ? a.getString(R.string.sin_nombre) : p.nombre);
        ((TextView) a.findViewById(R.id.tvCorreoMenu)).setText(p.correo);
        Avatar.ponerEmoji(a.findViewById(R.id.tvAvatarEmojiMenu), p.emoji());
        FotoPerfil.mostrar(a.findViewById(R.id.ivAvatarMenu), a.findViewById(R.id.tvAvatarEmojiMenu), p.foto);
    }

    private void click(int id, Runnable accion) {
        View v = a.findViewById(id);
        v.setOnClickListener(x -> accion.run());
    }

    private void abrir(Class<?> pantalla) {
        cerrarMenu();
        a.startActivity(new Intent(a, pantalla));
    }

    private int estadoNotificaciones() {
        if (!NotificationManagerCompat.from(a).areNotificationsEnabled()) return R.string.notif_sin_permiso;
        return Ajustes.get(a, Ajustes.RECORDATORIOS) ? R.string.notif_activadas : R.string.notif_desactivadas;
    }

    // ------------------------------------------------------------------
    // Idioma
    // ------------------------------------------------------------------

    private void elegirIdioma() {
        new AlertDialog.Builder(a)
                .setTitle(R.string.idioma_titulo)
                .setSingleChoiceItems(Idioma.opciones(a), Idioma.actual(), (d, cual) -> {
                    d.dismiss();
                    Idioma.cambiar(cual); // la app se recarga sola con el nuevo idioma
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    // ------------------------------------------------------------------
    // Tema
    // ------------------------------------------------------------------

    private void elegirTema() {
        new AlertDialog.Builder(a)
                .setTitle(R.string.tema_titulo)
                .setSingleChoiceItems(Tema.opciones(a), Tema.actual(a), (d, cual) -> {
                    d.dismiss();
                    Tema.cambiar(a, cual); // la pantalla se recarga sola con los nuevos colores
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    // ------------------------------------------------------------------
    // Cuenta
    // ------------------------------------------------------------------

    /** Firebase envía un correo con el enlace para escribir la nueva contraseña. */
    private void cambiarClave() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || u.getEmail() == null) return;
        String correo = u.getEmail();
        new AlertDialog.Builder(a)
                .setTitle(R.string.cambiar_clave)
                .setMessage(a.getString(R.string.cambiar_clave_msg, correo))
                .setPositiveButton(R.string.enviar, (d, w) ->
                        FirebaseAuth.getInstance().sendPasswordResetEmail(correo).addOnCompleteListener(t ->
                                Toast.makeText(a, t.isSuccessful() ? R.string.revisa_correo : R.string.error_conexion,
                                        Toast.LENGTH_LONG).show()))
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    /**
     * Eliminar cuenta: Firebase exige haber iniciado sesión hace poco, por eso se pide la contraseña.
     * Orden: 1) confirmar contraseña  2) borrar los datos de la base  3) borrar el usuario.
     */
    private void eliminarCuenta() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || u.getEmail() == null) return;

        EditText etClave = new EditText(a);
        etClave.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etClave.setHint(R.string.contrasena);
        FrameLayout caja = new FrameLayout(a);
        int pad = Math.round(20 * a.getResources().getDisplayMetrics().density);
        caja.setPadding(pad, 0, pad, 0);
        caja.addView(etClave);

        new AlertDialog.Builder(a)
                .setTitle(R.string.eliminar_cuenta)
                .setMessage(R.string.eliminar_cuenta_msg)
                .setView(caja)
                .setPositiveButton(R.string.eliminar, (d, w) -> {
                    String clave = etClave.getText().toString();
                    if (clave.isEmpty()) {
                        Toast.makeText(a, R.string.clave_incorrecta, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    confirmarYEliminar(u, clave);
                })
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private void confirmarYEliminar(FirebaseUser u, String clave) {
        AlertDialog progreso = new AlertDialog.Builder(a)
                .setMessage(R.string.eliminando)
                .setCancelable(false)
                .show();

        AuthCredential credencial = EmailAuthProvider.getCredential(u.getEmail(), clave);
        u.reauthenticate(credencial).addOnCompleteListener(auth -> {
            if (!auth.isSuccessful()) {
                progreso.dismiss();
                boolean claveMala = auth.getException() instanceof FirebaseAuthInvalidCredentialsException;
                Toast.makeText(a, claveMala ? R.string.clave_incorrecta : R.string.error_conexion, Toast.LENGTH_LONG).show();
                return;
            }
            Repo.borrarTodo(ok -> {
                if (!ok) {
                    progreso.dismiss();
                    Toast.makeText(a, R.string.error_conexion, Toast.LENGTH_LONG).show();
                    return;
                }
                u.delete().addOnCompleteListener(borrado -> {
                    progreso.dismiss();
                    if (!borrado.isSuccessful()) {
                        Toast.makeText(a, R.string.error_conexion, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(a, R.string.cuenta_eliminada, Toast.LENGTH_LONG).show();
                    Sesion.cerrar(a);
                    Intent i = new Intent(a, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    a.startActivity(i);
                });
            });
        });
    }
}
