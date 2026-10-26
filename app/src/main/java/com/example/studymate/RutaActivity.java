package com.example.studymate;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

/**
 * Cómo llegar, dentro de la app (OpenStreetMap): dibuja la ruta a pie desde donde estás
 * hasta el salón, con la distancia y el tiempo aproximado. No abre Google Maps.
 *
 * La ruta la calcula el servicio público de rutas de OpenStreetMap (OSRM, perfil "a pie",
 * routing.openstreetmap.de). Si no responde, se muestra una línea recta con la distancia.
 *
 * Recibe: "lat", "lon" (destino) y "nombre" (materia o salón).
 */
public class RutaActivity extends AppCompatActivity {

    /** Servicio de rutas a pie de OpenStreetMap (FOSSGIS). Formato: lon,lat;lon,lat */
    private static final String SERVICIO_RUTAS =
            "https://routing.openstreetmap.de/routed-foot/route/v1/driving/%s,%s;%s,%s?overview=full&geometries=geojson";

    private MapView mapa;
    private MyLocationNewOverlay miUbicacion;
    private GeoPoint destino;
    private Polyline linea;
    private TextView tvInfo;
    private View progreso;
    private boolean calculada = false;

    private final ActivityResultLauncher<String[]> pedirUbicacion =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (tienePermiso()) empezar();
                else {
                    mostrarInfo(getString(R.string.ruta_sin_permiso), false);
                    Toast.makeText(this, R.string.sin_permiso_ubicacion, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapa.configurar(this); // antes de crear el mapa
        setContentView(R.layout.activity_ruta);
        Ui.insets(this);

        double lat = getIntent().getDoubleExtra("lat", Mapa.LAT_DEFECTO);
        double lon = getIntent().getDoubleExtra("lon", Mapa.LON_DEFECTO);
        String nombre = getIntent().getStringExtra("nombre");
        destino = new GeoPoint(lat, lon);

        ((TextView) findViewById(R.id.tvDestino)).setText(nombre == null ? "" : nombre);
        tvInfo = findViewById(R.id.tvRutaInfo);
        progreso = findViewById(R.id.progresoRuta);
        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());

        mapa = findViewById(R.id.mapa);
        Mapa.preparar(mapa);
        mapa.getController().setZoom(Mapa.ZOOM_LUGAR - 1);
        mapa.getController().setCenter(destino);
        Mapa.marcador(mapa, destino, nombre, null); // el pin del salón (la mano)

        findViewById(R.id.btnRecalcular).setOnClickListener(v -> {
            calculada = false;
            if (tienePermiso()) empezar();
            else pedirPermiso();
        });

        if (tienePermiso()) empezar();
        else pedirPermiso();
    }

    private void pedirPermiso() {
        pedirUbicacion.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    private boolean tienePermiso() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Activa el punto azul y, cuando se sabe dónde estás, calcula la ruta. */
    private void empezar() {
        if (miUbicacion == null) {
            miUbicacion = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapa);
            mapa.getOverlays().add(miUbicacion);
        }
        miUbicacion.enableMyLocation();
        GeoPoint yo = miUbicacion.getMyLocation();
        if (yo != null) {
            calcular(yo);
        } else {
            mostrarInfo(getString(R.string.buscando_ubicacion), true);
            miUbicacion.runOnFirstFix(() -> {
                GeoPoint p = miUbicacion.getMyLocation();
                if (p != null) runOnUiThread(() -> calcular(p));
            });
        }
    }

    /** Pide la ruta a pie al servicio de OpenStreetMap (fuera del hilo principal) y la dibuja. */
    private void calcular(GeoPoint origen) {
        if (calculada || isFinishing()) return;
        calculada = true;
        mostrarInfo(getString(R.string.ruta_calculando), true);
        new Thread(() -> {
            Ruta r = pedirRuta(origen, destino);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (r != null) dibujar(r.puntos, false, r.metros, r.segundos);
                else {
                    // Sin servicio de rutas: línea recta como guía
                    List<GeoPoint> recta = new ArrayList<>();
                    recta.add(origen);
                    recta.add(destino);
                    dibujar(recta, true, origen.distanceToAsDouble(destino), -1);
                }
            });
        }).start();
    }

    private void dibujar(List<GeoPoint> puntos, boolean aproximada, double metros, double segundos) {
        if (linea != null) mapa.getOverlays().remove(linea);
        linea = new Polyline(mapa);
        linea.setPoints(puntos);
        linea.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.boton));
        linea.getOutlinePaint().setStrokeWidth(12f);
        linea.getOutlinePaint().setStrokeCap(android.graphics.Paint.Cap.ROUND);
        if (aproximada) linea.getOutlinePaint().setPathEffect(new DashPathEffect(new float[]{24f, 18f}, 0));
        linea.setInfoWindow(null);
        mapa.getOverlays().add(0, linea); // debajo de los pines

        BoundingBox caja = BoundingBox.fromGeoPointsSafe(puntos);
        mapa.post(() -> mapa.zoomToBoundingBox(caja.increaseByScale(1.5f), true));
        mapa.invalidate();

        String distancia = metros >= 1000
                ? String.format(Locale.getDefault(), "%.1f km", metros / 1000)
                : Math.round(metros) + " m";
        if (aproximada) {
            mostrarInfo(getString(R.string.ruta_aproximada, distancia), false);
        } else {
            long min = Math.max(1, Math.round(segundos / 60));
            mostrarInfo(getString(R.string.ruta_a_pie, min, distancia), false);
        }
    }

    private void mostrarInfo(String texto, boolean cargando) {
        tvInfo.setText(texto);
        progreso.setVisibility(cargando ? View.VISIBLE : View.GONE);
    }

    /** Resultado del servicio de rutas. */
    private static class Ruta {
        List<GeoPoint> puntos = new ArrayList<>();
        double metros, segundos;
    }

    /** Llama al servicio OSRM a pie. Devuelve null si no hay internet o no hay ruta. */
    private static Ruta pedirRuta(GeoPoint a, GeoPoint b) {
        HttpURLConnection con = null;
        try {
            URL url = new URL(String.format(Locale.US, SERVICIO_RUTAS,
                    a.getLongitude(), a.getLatitude(), b.getLongitude(), b.getLatitude()));
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "StudyMate/1.0 (Android; agenda universitaria; UPC Aguachica)");
            if (con.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"))) {
                String l;
                while ((l = in.readLine()) != null) sb.append(l);
            }
            JSONObject json = new JSONObject(sb.toString());
            if (!"Ok".equals(json.optString("code"))) return null;
            JSONArray rutas = json.getJSONArray("routes");
            if (rutas.length() == 0) return null;
            JSONObject ruta = rutas.getJSONObject(0);
            Ruta r = new Ruta();
            r.metros = ruta.getDouble("distance");
            r.segundos = ruta.getDouble("duration");
            JSONArray coords = ruta.getJSONObject("geometry").getJSONArray("coordinates");
            for (int i = 0; i < coords.length(); i++) {
                JSONArray c = coords.getJSONArray(i);
                r.puntos.add(new GeoPoint(c.getDouble(1), c.getDouble(0))); // viene como [lon, lat]
            }
            return r.puntos.size() < 2 ? null : r;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapa.onResume();
        if (miUbicacion != null) miUbicacion.enableMyLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapa.onPause();
        if (miUbicacion != null) miUbicacion.disableMyLocation();
    }
}
