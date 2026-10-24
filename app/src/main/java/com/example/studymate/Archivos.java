package com.example.studymate;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.util.Base64;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Archivos adjuntos de los apuntes. Como la foto de perfil, se guardan como texto Base64 en
 * Realtime Database, así no hace falta Firebase Storage (que pide plan de pago):
 *
 *   users/{uid}/notes/{id}/adjuntos  → nombre, tipo y tamaño (lista liviana)
 *   users/{uid}/archivos/{idArchivo} → el contenido del archivo (solo se descarga al abrirlo)
 *
 * Límite: 5 MB por archivo (Realtime Database no está hecha para archivos grandes).
 */
public class Archivos {

    public static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    /** Lo que se leyó del archivo elegido. */
    public static class Leido {
        public String nombre, tipo;
        public byte[] bytes;
    }

    /** Error al leer: el archivo pasa de 5 MB. */
    public static class MuyGrande extends Exception {}

    private static DatabaseReference nodo() {
        DatabaseReference r = Repo.root();
        return r == null ? null : r.child("archivos");
    }

    /** Clave nueva para un archivo (null si no hay sesión). */
    public static String nuevoId() {
        DatabaseReference n = nodo();
        return n == null ? null : n.push().getKey();
    }

    /**
     * Lee el archivo elegido con el selector de Android: nombre, tipo y contenido.
     * Llamar fuera del hilo principal.
     */
    public static Leido leer(Context c, Uri uri) throws Exception {
        Leido l = new Leido();
        l.nombre = "archivo";
        long tamano = -1;
        try (Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int iNombre = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int iTam = cur.getColumnIndex(OpenableColumns.SIZE);
                if (iNombre >= 0 && cur.getString(iNombre) != null) l.nombre = cur.getString(iNombre);
                if (iTam >= 0 && !cur.isNull(iTam)) tamano = cur.getLong(iTam);
            }
        }
        if (tamano > MAX_BYTES) throw new MuyGrande();

        String tipo = c.getContentResolver().getType(uri);
        l.tipo = tipo == null ? "application/octet-stream" : tipo;

        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("No se pudo abrir");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new MuyGrande(); // por si el tamaño no venía
            }
            l.bytes = out.toByteArray();
        }
        return l;
    }

    /** Sube el contenido. Firebase lo envía en segundo plano (y lo guarda en cola si no hay internet). */
    public static void subir(String id, byte[] bytes) {
        DatabaseReference n = nodo();
        if (n != null) n.child(id).setValue(Base64.encodeToString(bytes, Base64.NO_WRAP));
    }

    /** Borra el contenido de un archivo. */
    public static void borrar(String id) {
        DatabaseReference n = nodo();
        if (n != null && id != null && !id.isEmpty()) n.child(id).removeValue();
    }

    /** Borra el contenido de todos los archivos de un apunte. */
    public static void borrarDe(Apunte a) {
        if (a == null || a.adjuntos == null) return;
        for (Adjunto adj : a.adjuntos) borrar(adj.id);
    }

    public interface Listo { void onListo(boolean ok); }

    /**
     * Abre el archivo con la app que corresponda (lector de PDF, Word, galería...).
     * La primera vez lo descarga; después lo abre desde la memoria del teléfono.
     */
    public static void abrir(Activity a, Adjunto adj, Listo cb) {
        File archivo = archivoLocal(a, adj);
        if (archivo.exists() && archivo.length() > 0) {
            cb.onListo(true);
            lanzar(a, adj, archivo);
            return;
        }
        DatabaseReference n = nodo();
        if (n == null) { cb.onListo(false); return; }
        n.child(adj.id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                String b64 = snap.getValue(String.class);
                if (b64 == null || b64.isEmpty()) {
                    cb.onListo(false);
                    Toast.makeText(a, R.string.archivo_no_disponible, Toast.LENGTH_LONG).show();
                    return;
                }
                new Thread(() -> {
                    boolean ok;
                    try (FileOutputStream out = new FileOutputStream(archivo)) {
                        out.write(Base64.decode(b64, Base64.DEFAULT));
                        ok = true;
                    } catch (Exception e) {
                        ok = false;
                    }
                    final boolean listo = ok;
                    a.runOnUiThread(() -> {
                        if (a.isFinishing() || a.isDestroyed()) return;
                        cb.onListo(listo);
                        if (listo) lanzar(a, adj, archivo);
                        else Toast.makeText(a, R.string.archivo_error, Toast.LENGTH_LONG).show();
                    });
                }).start();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                cb.onListo(false);
                Toast.makeText(a, R.string.archivo_no_disponible, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Copia local del archivo: cache/adjuntos/{id}/{nombre}. */
    private static File archivoLocal(Context c, Adjunto adj) {
        File carpeta = new File(new File(c.getCacheDir(), "adjuntos"), adj.id);
        if (!carpeta.exists()) carpeta.mkdirs();
        String nombre = adj.nombre == null || adj.nombre.trim().isEmpty() ? "archivo" : adj.nombre;
        return new File(carpeta, nombre.replaceAll("[\\\\/:*?\"<>|]", "_"));
    }

    private static void lanzar(Activity a, Adjunto adj, File archivo) {
        Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", archivo);
        Intent ver = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, adj.tipo == null || adj.tipo.isEmpty() ? "*/*" : adj.tipo)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(ver, adj.nombre));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, R.string.archivo_sin_app, Toast.LENGTH_LONG).show();
        }
    }

    /** Ícono según el tipo de archivo. */
    public static int icono(String tipo, String nombre) {
        String t = tipo == null ? "" : tipo;
        String n = nombre == null ? "" : nombre.toLowerCase(java.util.Locale.ROOT);
        if (t.equals("application/pdf") || n.endsWith(".pdf")) return R.drawable.ic_pdf;
        if (t.startsWith("image/")) return R.drawable.ic_imagen;
        if (t.contains("sheet") || t.contains("excel") || n.endsWith(".xls") || n.endsWith(".xlsx")) return R.drawable.ic_tabla;
        if (t.contains("presentation") || t.contains("powerpoint") || n.endsWith(".ppt") || n.endsWith(".pptx")) return R.drawable.ic_presentacion;
        return R.drawable.ic_documento; // Word, texto y demás
    }

    /** Tamaño legible, ej. "1,2 MB". */
    public static String tamano(Context c, long bytes) {
        return Formatter.formatShortFileSize(c, bytes);
    }
}
