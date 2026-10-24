package com.example.studymate;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import java.io.ByteArrayOutputStream;

/**
 * Foto de perfil. Se recorta en cuadrado, se reduce a 300x300 y se guarda como texto (Base64)
 * en Realtime Database: users/{uid}/foto. Así no hace falta Firebase Storage (que pide plan de pago).
 */
public class FotoPerfil {

    private static final int LADO = 300;   // píxeles
    private static final int CALIDAD = 80; // JPEG 0-100

    /** Lee la imagen elegida, la recorta, la reduce y la devuelve en Base64 (null si falla). */
    public static String comprimir(Context c, Uri uri) {
        try {
            ImageDecoder.Source fuente = ImageDecoder.createSource(c.getContentResolver(), uri);
            Bitmap original = ImageDecoder.decodeBitmap(fuente, (decoder, info, src) -> {
                // Decodifica ya reducida para no gastar memoria con fotos enormes
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                int menor = Math.min(w, h);
                if (menor > LADO * 2) decoder.setTargetSampleSize(menor / (LADO * 2));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });

            // Recorte cuadrado desde el centro
            int lado = Math.min(original.getWidth(), original.getHeight());
            int x = (original.getWidth() - lado) / 2;
            int y = (original.getHeight() - lado) / 2;
            Bitmap cuadrada = Bitmap.createBitmap(original, x, y, lado, lado);
            Bitmap pequena = Bitmap.createScaledBitmap(cuadrada, LADO, LADO, true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pequena.compress(Bitmap.CompressFormat.JPEG, CALIDAD, out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Muestra la foto redonda en "imagen". Si no hay foto, la oculta y deja ver "respaldo"
     * (el emoji que ya estaba).
     */
    public static void mostrar(ImageView imagen, View respaldo, String base64) {
        Bitmap b = decodificar(base64);
        if (b == null) {
            imagen.setImageDrawable(null);
            imagen.setVisibility(View.GONE);
            if (respaldo != null) respaldo.setVisibility(View.VISIBLE);
            return;
        }
        RoundedBitmapDrawable redonda = RoundedBitmapDrawableFactory.create(imagen.getResources(), b);
        redonda.setCircular(true);
        imagen.setImageDrawable(redonda);
        imagen.setVisibility(View.VISIBLE);
        if (respaldo != null) respaldo.setVisibility(View.INVISIBLE);
    }

    private static Bitmap decodificar(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }
}
