package com.example.studymate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

/**
 * Elegir en el mapa (OpenStreetMap) dónde es la clase.
 * Entra con "lat"/"lon" si ya había una ubicación; al guardar devuelve "lat" y "lon".
 */
public class MapPickerActivity extends AppCompatActivity {

    private MapView mapa;
    private Marker marcador;
    private MyLocationNewOverlay miUbicacion;

    // Permiso de ubicación (para el botón "Mi ubicación")
    private final ActivityResultLauncher<String[]> pedirUbicacion =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (tienePermiso()) irAMiUbicacion();
                else Toast.makeText(this, R.string.sin_permiso_ubicacion, Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapa.configurar(this); // antes de crear el mapa
        setContentView(R.layout.activity_map_picker);
        Ui.insets(this);

        mapa = findViewById(R.id.mapa);
        Mapa.preparar(mapa);

        // Si ya tenía ubicación, se muestra; si no, se centra en la ciudad
        double lat = getIntent().getDoubleExtra("lat", 0);
        double lon = getIntent().getDoubleExtra("lon", 0);
        if (lat != 0 || lon != 0) {
            GeoPoint p = new GeoPoint(lat, lon);
            mapa.getController().setZoom(Mapa.ZOOM_LUGAR);
            mapa.getController().setCenter(p);
            ponerMarcador(p);
        } else if (getIntent().hasExtra("centroLat")) {
            // Sin ubicación todavía: empieza donde está el otro horario de la materia (misma sede)
            mapa.getController().setZoom(Mapa.ZOOM_LUGAR);
            mapa.getController().setCenter(new GeoPoint(
                    getIntent().getDoubleExtra("centroLat", Mapa.LAT_DEFECTO),
                    getIntent().getDoubleExtra("centroLon", Mapa.LON_DEFECTO)));
        } else {
            mapa.getController().setZoom(Mapa.ZOOM_CIUDAD);
            mapa.getController().setCenter(new GeoPoint(Mapa.LAT_DEFECTO, Mapa.LON_DEFECTO));
        }

        // Tocar el mapa = marcar el lugar
        mapa.getOverlays().add(0, new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                ponerMarcador(p);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                ponerMarcador(p);
                return true;
            }
        }));

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnMiUbicacion).setOnClickListener(v -> {
            if (tienePermiso()) irAMiUbicacion();
            else pedirUbicacion.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        });
        findViewById(R.id.btnGuardarUbicacion).setOnClickListener(v -> {
            if (marcador == null) {
                Toast.makeText(this, R.string.marca_primero, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent r = new Intent();
            r.putExtra("lat", marcador.getPosition().getLatitude());
            r.putExtra("lon", marcador.getPosition().getLongitude());
            setResult(RESULT_OK, r);
            finish();
        });
    }

    /** Pone (o mueve) el pin en el punto tocado. */
    private void ponerMarcador(GeoPoint p) {
        if (marcador == null) {
            marcador = Mapa.marcador(mapa, p, getString(R.string.ubicacion_clase), null);
        } else {
            marcador.setPosition(p);
        }
        mapa.invalidate();
    }

    private boolean tienePermiso() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Muestra el punto azul de tu ubicación y lleva el mapa hasta ahí. */
    private void irAMiUbicacion() {
        if (miUbicacion == null) {
            miUbicacion = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapa);
            mapa.getOverlays().add(miUbicacion);
        }
        miUbicacion.enableMyLocation();
        GeoPoint yo = miUbicacion.getMyLocation();
        if (yo != null) {
            mapa.getController().animateTo(yo, Mapa.ZOOM_LUGAR, 800L);
        } else {
            Toast.makeText(this, R.string.buscando_ubicacion, Toast.LENGTH_SHORT).show();
            miUbicacion.runOnFirstFix(() -> runOnUiThread(() -> {
                GeoPoint p = miUbicacion.getMyLocation();
                if (p != null) mapa.getController().animateTo(p, Mapa.ZOOM_LUGAR, 800L);
            }));
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
