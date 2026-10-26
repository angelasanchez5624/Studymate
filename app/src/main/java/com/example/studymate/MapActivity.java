package com.example.studymate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

/**
 * Mapa de mis clases (OpenStreetMap): un pin por cada salón con ubicación y tu posición.
 * Al tocar un pin se ven las clases de ese lugar; tocándolo otra vez se abre la materia.
 * Si llega con "subjectId", el mapa se centra en esa materia.
 */
public class MapActivity extends AppCompatActivity {

    private MapView mapa;
    private MyLocationNewOverlay miUbicacion;
    private Repo.Handle handle;
    private final List<Overlay> pines = new ArrayList<>();
    private boolean centrado = false;

    private final ActivityResultLauncher<String[]> pedirUbicacion =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (tienePermiso()) irAMiUbicacion();
                else Toast.makeText(this, R.string.sin_permiso_ubicacion, Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapa.configurar(this); // antes de crear el mapa
        setContentView(R.layout.activity_map);
        Ui.insets(this);

        mapa = findViewById(R.id.mapa);
        Mapa.preparar(mapa);
        mapa.getController().setZoom(Mapa.ZOOM_CIUDAD);
        mapa.getController().setCenter(new GeoPoint(Mapa.LAT_DEFECTO, Mapa.LON_DEFECTO));

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnMiUbicacion).setOnClickListener(v -> {
            if (tienePermiso()) irAMiUbicacion();
            else pedirUbicacion.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        });

        // Si ya diste permiso de ubicación antes, se muestra el punto azul de una vez
        if (tienePermiso()) mostrarMiUbicacion();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handle = Repo.listen(this::dibujar);
        dibujar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handle != null) {
            handle.remove();
            handle = null;
        }
    }

    /**
     * Un pin por cada salón con ubicación. Si una materia tiene clase en dos edificios,
     * salen dos pines; si dos clases están en el mismo edificio, comparten un pin.
     */
    private void dibujar() {
        // Se cierran las burbujas de los pines anteriores: si no, quedaba abierta una burbuja
        // "vieja" y el siguiente toque al pin no abría la materia
        org.osmdroid.views.overlay.infowindow.InfoWindow.closeAllInfoWindowsOn(mapa);
        mapa.getOverlays().removeAll(pines);
        pines.clear();
        Marker reabrir = null;
        Mapa.Lugar reabrirLugar = null;

        int enfocar = getIntent().getIntExtra("subjectId", -1);
        List<GeoPoint> puntos = new ArrayList<>();
        List<GeoPoint> foco = new ArrayList<>();
        Marker primerFoco = null;
        Mapa.Lugar primerLugar = null;

        for (Mapa.Lugar lugar : Mapa.lugares(Datos.SUBJECTS)) {
            Marker m = Mapa.marcador(mapa, lugar.punto, lugar.titulo(this), lugar.detalle());
            // 1er toque al pin: muestra las clases · 2º toque (al pin o a la burbuja): abre la materia
            m.setOnMarkerClickListener((marcador, mapView) -> {
                if (marcador.isInfoWindowShown()) abrirMateria(lugar);
                else mostrarBurbuja(marcador, lugar);
                return true;
            });
            if (clave(lugar).equals(abierto)) reabrir = m;
            if (reabrir == m) reabrirLugar = lugar;
            pines.add(m);
            puntos.add(lugar.punto);
            for (Subject s : lugar.materias) {
                if (s.id == enfocar) {
                    foco.add(lugar.punto);
                    if (primerFoco == null) { primerFoco = m; primerLugar = lugar; }
                }
            }
        }
        mapa.invalidate();
        // Si la burbuja estaba abierta antes de refrescar, se vuelve a abrir en el pin nuevo
        if (reabrir != null) mostrarBurbuja(reabrir, reabrirLugar);

        findViewById(R.id.tvMapaVacio).setVisibility(
                Datos.materiasListas && puntos.isEmpty() ? View.VISIBLE : View.GONE);

        // Encuadre: solo la primera vez (para no mover el mapa mientras lo usas)
        if (centrado || puntos.isEmpty()) return;
        centrado = true;
        List<GeoPoint> encuadre = foco.isEmpty() ? puntos : foco; // la materia pedida, o todas
        if (encuadre.size() == 1) {
            mapa.getController().setZoom(foco.isEmpty() ? Mapa.ZOOM_LUGAR - 1 : Mapa.ZOOM_LUGAR);
            mapa.getController().setCenter(encuadre.get(0));
        } else {
            BoundingBox caja = BoundingBox.fromGeoPointsSafe(encuadre);
            mapa.post(() -> mapa.zoomToBoundingBox(caja.increaseByScale(1.4f), false));
        }
        if (primerFoco != null) mostrarBurbuja(primerFoco, primerLugar);
    }

    /** Lugar con la burbuja abierta (para volver a abrirla si el mapa se refresca). */
    private String abierto = null;

    private static String clave(Mapa.Lugar l) {
        return l.punto.getLatitude() + "," + l.punto.getLongitude();
    }

    /** Muestra la burbuja del pin; tocar la burbuja abre la materia (antes solo la cerraba). */
    private void mostrarBurbuja(Marker m, Mapa.Lugar lugar) {
        m.showInfoWindow();
        abierto = clave(lugar);
        android.view.View burbuja = m.getInfoWindow() == null ? null : m.getInfoWindow().getView();
        if (burbuja == null) return;
        burbuja.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
                abrirMateria(lugar);
            }
            return true;
        });
    }

    /** Abre la materia del pin; si en ese lugar hay varias, deja elegir cuál. */
    private void abrirMateria(Mapa.Lugar lugar) {
        if (lugar.materias.size() == 1) {
            startActivity(new Intent(this, SubjectDetailActivity.class)
                    .putExtra("subjectId", lugar.materias.get(0).id));
            return;
        }
        String[] nombres = new String[lugar.materias.size()];
        for (int i = 0; i < nombres.length; i++) nombres[i] = lugar.materias.get(i).name;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.cual_materia)
                .setItems(nombres, (d, i) -> startActivity(new Intent(this, SubjectDetailActivity.class)
                        .putExtra("subjectId", lugar.materias.get(i).id)))
                .setNegativeButton(R.string.cancelar, null)
                .show();
    }

    private boolean tienePermiso() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Punto azul con tu ubicación. */
    private void mostrarMiUbicacion() {
        if (miUbicacion == null) {
            miUbicacion = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapa);
            mapa.getOverlays().add(miUbicacion);
        }
        miUbicacion.enableMyLocation();
    }

    /** Muestra tu ubicación y lleva el mapa hasta ahí. */
    private void irAMiUbicacion() {
        mostrarMiUbicacion();
        GeoPoint yo = miUbicacion.getMyLocation();
        if (yo != null) {
            mapa.getController().animateTo(yo, Mapa.ZOOM_LUGAR - 1, 800L);
        } else {
            Toast.makeText(this, R.string.buscando_ubicacion, Toast.LENGTH_SHORT).show();
            miUbicacion.runOnFirstFix(() -> runOnUiThread(() -> {
                GeoPoint p = miUbicacion.getMyLocation();
                if (p != null) mapa.getController().animateTo(p, Mapa.ZOOM_LUGAR - 1, 800L);
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
