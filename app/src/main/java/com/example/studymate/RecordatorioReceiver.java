package com.example.studymate;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/** Recibe la alarma programada por Recordatorios y muestra la notificación. */
public class RecordatorioReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        boolean esClase = intent.getBooleanExtra("esClase", false);
        mostrar(c, intent.getIntExtra("codigo", 0),
                intent.getStringExtra("titulo"), intent.getStringExtra("texto"), esClase);
        // Los avisos de clase se repiten: se programa el de la próxima semana
        if (esClase) Recordatorios.reprogramarClasesGuardadas(c);
    }

    /** Muestra una notificación (de entrega o de clase). Devuelve false si no hay permiso. */
    public static boolean mostrar(Context c, int codigo, String titulo, String texto, boolean esClase) {
        // Sin permiso de notificaciones no se puede mostrar nada
        if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;

        Recordatorios.crearCanal(c);

        // Al tocar la notificación se abre la app (el Login decide si entra directo)
        Intent abrir = new Intent(c, LoginActivity.class);
        abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent alTocar = PendingIntent.getActivity(c, codigo, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder n = new NotificationCompat.Builder(c, Recordatorios.CANAL)
                .setSmallIcon(R.drawable.ic_notificacion)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(alTocar)
                .setAutoCancel(true);

        // Privacidad: en la pantalla de bloqueo se ve un texto genérico en vez del nombre de la tarea
        if (Ajustes.get(c, Ajustes.OCULTAR_DETALLE)) {
            NotificationCompat.Builder publica = new NotificationCompat.Builder(c, Recordatorios.CANAL)
                    .setSmallIcon(R.drawable.ic_notificacion)
                    .setContentTitle(c.getString(R.string.notif_generica_titulo))
                    .setContentText(App.t(esClase ? R.string.notif_generica_clase : R.string.notif_generica_texto));
            n.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publica.build());
        } else {
            n.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }

        NotificationManagerCompat.from(c).notify(codigo, n.build());
        return true;
    }
}
