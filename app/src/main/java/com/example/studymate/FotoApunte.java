package com.example.studymate;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.util.Base64;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Foto de un apunte (tablero, cuaderno, diapositiva...). Igual que la foto de perfil, se guarda
 * como texto Base64 dentro del apunte en Realtime Database (users/{uid}/notes/{id}/foto),
 * así no hace falta Firebase Storage.
 *
 * Se reduce a máximo 1280 px por lado y JPEG 70 (unos 100–200 KB): se lee bien la letra del
 * tablero y no se llena la base de datos.
 */
public class FotoApunte {

    private static final int LADO_MAX = 1280; // píxeles del lado más largo
    private static final int CALIDAD = 70;    // JPEG 0-100

    /**
     * Archivo temporal donde la app de cámara deja la foto a tamaño completo.
     * Se comparte con la cámara por medio del FileProvider (ver AndroidManifest y res/xml/file_paths.xml).
     */
    public static Uri uriTemporal(Context c) {
        File carpeta = new File(c.getCacheDir(), "camara");
        if (!carpeta.exists()) carpeta.mkdirs();
        File archivo = new File(carpeta, "apunte_" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", archivo);
    }

    /** Borra las fotos temporales de la cámara (ya se guardaron comprimidas en el apunte). */
    public static void limpiarTemporales(Context c) {
        File carpeta = new File(c.getCacheDir(), "camara");
        File[] archivos = carpeta.listFiles();
        if (archivos != null) for (File f : archivos) f.delete();
    }

    /**
     * Lee la foto (de la cámara o de la galería), la reduce y la devuelve en Base64 (null si falla).
     * ImageDecoder ya corrige la orientación (foto vertical u horizontal).
     * Llamar fuera del hilo principal: con fotos grandes puede tardar un poco.
     */
    public static String comprimir(Context c, Uri uri) {
        try {
            ImageDecoder.Source fuente = ImageDecoder.createSource(c.getContentResolver(), uri);
            Bitmap foto = ImageDecoder.decodeBitmap(fuente, (decoder, info, src) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                int mayor = Math.max(w, h);
                if (mayor > LADO_MAX) {
                    float escala = (float) LADO_MAX / mayor;
                    decoder.setTargetSize(Math.round(w * escala), Math.round(h * escala));
                }
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            foto.compress(Bitmap.CompressFormat.JPEG, CALIDAD, out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /** Convierte el Base64 guardado en imagen (null si no hay foto o está dañada). */
    public static Bitmap decodificar(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }
}
