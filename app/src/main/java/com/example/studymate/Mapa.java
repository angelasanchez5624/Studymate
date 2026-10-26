package com.example.studymate;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;

/**
 * Ayudas para los mapas de OpenStreetMap (librería osmdroid).
 * Se usan en: Nueva materia (elegir ubicación), detalle de la materia y "Mapa de mis clases".
 */
public class Mapa {

    /** ✏️ Centro por defecto del mapa: Aguachica, Cesar. Cámbialo por las coordenadas de tu sede. */
    public static final double LAT_DEFECTO = 8.3101;
    public static final double LON_DEFECTO = -73.6156;
    public static final double ZOOM_CIUDAD = 15.0;
    public static final double ZOOM_LUGAR = 18.0;

    /*
     * Capa "Humanitaria" de OpenStreetMap (una de las capas oficiales de openstreetmap.org),
     * servida por OpenStreetMap Francia. Mismos datos de OpenStreetMap y sin clave.
     * (El servidor principal tile.openstreetmap.org bloquea muchas apps: "Access blocked / 403").
     */
    private static final OnlineTileSourceBase MAPA_OSM = new XYTileSource("OSMHumanitario",
            0, 19, 256, ".png", new String[]{
                    "https://a.tile.openstreetmap.fr/hot/",
                    "https://b.tile.openstreetmap.fr/hot/",
                    "https://c.tile.openstreetmap.fr/hot/"},
            "© OpenStreetMap contributors");

    /**
     * OpenStreetMap pide que cada app se identifique al descargar el mapa.
     * Se llama antes de crear cualquier MapView (antes de setContentView).
     */
    public static void configurar(Context c) {
        IConfigurationProvider conf = Configuration.getInstance();
        conf.load(c, c.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        // Nombre con el que la app se identifica ante los servidores de OpenStreetMap.
        // OpenStreetMap BLOQUEA ("Access blocked / 403") las apps que usan un nombre genérico
        // o de ejemplo como "com.example...", por eso se pone uno propio.
        conf.setUserAgentValue("StudyMate/1.0 (Android; agenda universitaria; UPC Aguachica)");
        // Carpeta nueva para guardar el mapa: así no se muestran imágenes de error guardadas antes
        java.io.File base = new java.io.File(c.getCacheDir(), "mapas_v3");
        conf.setOsmdroidBasePath(base);
        conf.setOsmdroidTileCache(new java.io.File(base, "tiles"));
    }

    /** Estilo básico: mapa de OpenStreetMap, zoom con dos dedos o con botones + / −, y créditos abajo. */
    public static void preparar(MapView map) {
        map.setTileSource(MAPA_OSM);
        // En modo oscuro el mapa se ve con colores invertidos (oscuro)
        int modo = map.getContext().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (modo == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            map.getOverlayManager().getTilesOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        }
        map.setMultiTouchControls(true);
        // Botones + / − siempre visibles: alternativa de un toque al zoom con dos dedos (WCAG 2.5.1)
        map.getZoomController().setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.ALWAYS);
        map.setTilesScaledToDpi(true);
        map.getOverlays().add(new CopyrightOverlay(map.getContext())); // "© OpenStreetMap contributors"
    }

    /** Agrega un marcador (el pin) en el mapa. */
    public static Marker marcador(MapView map, GeoPoint punto, String titulo, String detalle) {
        Marker m = new Marker(map);
        m.setPosition(punto);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(titulo);
        if (detalle != null && !detalle.isEmpty()) m.setSnippet(detalle);
        map.getOverlays().add(m);
        return m;
    }

    /** Abre la app de mapas del teléfono (Google Maps u otra) para llegar al lugar. */
    public static void comoLlegar(Context c, double lat, double lon, String nombre) {
        String q = lat + "," + lon + "(" + Uri.encode(nombre) + ")";
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + q));
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(c, R.string.sin_app_mapas, Toast.LENGTH_LONG).show();
        }
    }
    // ------------------------------------------------------------------
    // Lugares de las clases (un pin por salón, y varias clases en el mismo pin si están juntas)
    // ------------------------------------------------------------------

    /** Clases que están a menos de esta distancia (metros) se juntan en un solo pin. */
    private static final double JUNTAR_METROS = 25;

    /** Un pin del mapa: el punto y las clases que se dan ahí (de una o varias materias). */
    public static class Lugar {
        public GeoPoint punto;
        public final java.util.List<Subject> materias = new java.util.ArrayList<>();
        public final java.util.List<String> lineas = new java.util.ArrayList<>(); // una por clase

        /** Título del pin: el nombre de la materia, o "4 clases aquí" (una por horario) si hay varias. */
        public String titulo(Context c) {
            return materias.size() == 1 ? materias.get(0).name
                    : c.getString(R.string.clases_aqui, lineas.size());
        }

        /** Salón de cada renglón de "lineas" (mismo orden), para agruparlos en la burbuja. */
        public final java.util.List<String> salones = new java.util.ArrayList<>();

        /**
         * Detalle del pin, organizado por salón (la burbuja de osmdroid acepta HTML):
         *   AULA 112
         *   Programación III · Mié / Vie 8:00–10:00 a. m.
         *   LAB REDES
         *   Programación Móviles · Lun 4:00–6:00 p. m.
         */
        public String detalle() {
            java.util.LinkedHashMap<String, java.util.List<String>> porSalon = new java.util.LinkedHashMap<>();
            for (int i = 0; i < lineas.size(); i++) {
                String salon = i < salones.size() ? salones.get(i) : "";
                java.util.List<String> l = porSalon.get(salon);
                if (l == null) { l = new java.util.ArrayList<>(); porSalon.put(salon, l); }
                l.add(lineas.get(i));
            }
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, java.util.List<String>> e : porSalon.entrySet()) {
                if (!e.getKey().isEmpty()) {
                    if (sb.length() > 0) sb.append("<br>");
                    sb.append("<b><font color=\"#6551B5\">").append(android.text.TextUtils.htmlEncode(e.getKey())).append("</font></b>");
                }
                for (String l : e.getValue()) {
                    if (sb.length() > 0) sb.append("<br>");
                    sb.append(android.text.TextUtils.htmlEncode(l));
                }
            }
            return sb.toString();
        }
    }

    /**
     * Punto de un horario en el mapa. Si algún horario de la materia tiene su propia ubicación,
     * solo cuentan esos. Si ninguno tiene (materias creadas antes de este cambio), todos usan
     * la ubicación general de la materia.
     */
    public static GeoPoint punto(Subject s, Bloque b) {
        if (b != null && b.tieneUbicacion()) return new GeoPoint(b.lat, b.lon);
        // Mismo salón que otro horario con ubicación: se usa esa
        if (b != null && b.room != null && !b.room.trim().isEmpty()) {
            for (Bloque otro : Horario.bloques(s)) {
                if (otro.tieneUbicacion() && b.room.trim().equalsIgnoreCase(otro.room == null ? "" : otro.room.trim()))
                    return new GeoPoint(otro.lat, otro.lon);
            }
        }
        for (Bloque otro : Horario.bloques(s)) if (otro.tieneUbicacion()) return null;
        if (s.lat != 0 || s.lon != 0) return new GeoPoint(s.lat, s.lon);
        return null;
    }

    /** Texto de una clase para el pin, ej. "Programación III · Lun / Mié 08:00–10:00" (el salón va de título). */
    private static String linea(Subject s, Bloque b, boolean conMateria) {
        StringBuilder sb = new StringBuilder();
        if (conMateria) sb.append(s.name);
        String hora = b == null ? "" : Horario.texto(b);
        if (!hora.isEmpty()) sb.append(sb.length() > 0 ? " · " : "").append(hora);
        return sb.toString();
    }

    private static String salon(Bloque b) {
        return b == null || b.room == null ? "" : b.room.trim();
    }

    /**
     * Arma los pines de las materias: un lugar por cada salón con ubicación. Si dos clases
     * (de la misma o de distintas materias) quedan en el mismo sitio, comparten pin.
     */
    public static java.util.List<Lugar> lugares(java.util.List<Subject> materias) {
        java.util.List<Lugar> lugares = new java.util.ArrayList<>();
        for (Subject s : materias) {
            for (Bloque b : Horario.bloques(s)) {
                GeoPoint p = punto(s, b);
                if (p == null) continue;
                Lugar lugar = null;
                for (Lugar l : lugares) {
                    if (l.punto.distanceToAsDouble(p) <= JUNTAR_METROS) { lugar = l; break; }
                }
                if (lugar == null) {
                    lugar = new Lugar();
                    lugar.punto = p;
                    lugares.add(lugar);
                }
                if (!lugar.materias.contains(s)) lugar.materias.add(s);
                lugar.lineas.add(linea(s, b, true));
                lugar.salones.add(salon(b));
            }
        }
        // Si el pin es de una sola materia, no hace falta repetir su nombre en cada renglón
        for (Lugar l : lugares) {
            if (l.materias.size() != 1) continue;
            Subject s = l.materias.get(0);
            l.lineas.clear();
            l.salones.clear();
            for (Bloque b : Horario.bloques(s)) {
                GeoPoint p = punto(s, b);
                if (p != null && l.punto.distanceToAsDouble(p) <= JUNTAR_METROS) {
                    String t = linea(s, b, false);
                    if (t.isEmpty() && salon(b).isEmpty()) continue;
                    l.lineas.add(t);
                    l.salones.add(salon(b));
                }
            }
        }
        return lugares;
    }

    /** Lugares de una sola materia (para el mapa pequeño del detalle). */
    public static java.util.List<Lugar> lugares(Subject s) {
        java.util.List<Subject> una = new java.util.ArrayList<>();
        una.add(s);
        return lugares(una);
    }
}
