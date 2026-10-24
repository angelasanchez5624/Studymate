package com.example.studymate;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/** Crea una materia, o la edita si se abre con el extra "subjectId". */
public class AddSubjectActivity extends AppCompatActivity {

    private static final int[] IDS_DIAS = {R.id.dia0, R.id.dia1, R.id.dia2, R.id.dia3, R.id.dia4, R.id.dia5, R.id.dia6};

    private Subject editando = null;
    private LinearLayout listBloques;

    /** Lo que guarda cada horario en pantalla (uno por cada item_bloque.xml). */
    private static class VistaBloque {
        View raiz;
        boolean[] dias = new boolean[7];
        EditText etInicio, etFin, etSalon;
        double lat = 0, lon = 0; // ubicación de este salón (0, 0 = sin ubicación)
    }

    private final List<VistaBloque> vistas = new ArrayList<>();

    // Ubicación: cada horario marca la suya. "pidiendo" es el horario que abrió el mapa.
    private VistaBloque pidiendo = null;
    private final androidx.activity.result.ActivityResultLauncher<android.content.Intent> elegirUbicacion =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), r -> {
                if (pidiendo == null || r.getResultCode() != RESULT_OK || r.getData() == null) return;
                pidiendo.lat = r.getData().getDoubleExtra("lat", 0);
                pidiendo.lon = r.getData().getDoubleExtra("lon", 0);
                mostrarUbicacion(pidiendo);
                pidiendo = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_subject);
        Ui.insets(this);

        EditText etNombre = findViewById(R.id.etNombreMateria);
        EditText etProfesor = findViewById(R.id.etProfesor);
        listBloques = findViewById(R.id.listBloques);

        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelarMateria).setOnClickListener(v -> finish());
        findViewById(R.id.btnAgregarBloque).setOnClickListener(v -> agregarBloque(null));

        // ¿Modo edición?
        int subjectId = getIntent().getIntExtra("subjectId", -1);
        if (subjectId != -1) {
            for (Subject s : Datos.SUBJECTS) if (s.id == subjectId) editando = s;
            if (editando == null) {
                finish();
                return;
            }
            ((TextView) findViewById(R.id.tvTituloMateria)).setText(R.string.materia_editar);
            ((TextView) findViewById(R.id.btnGuardarMateria)).setText(R.string.guardar_cambios);
            etNombre.setText(editando.name);
            etProfesor.setText(editando.professor);
            for (Bloque b : Horario.bloques(editando)) {
                VistaBloque vb = agregarBloque(b);
                // Materias de antes: la ubicación era de la materia; se pasa a cada horario
                if (!b.tieneUbicacion() && (editando.lat != 0 || editando.lon != 0)) {
                    vb.lat = editando.lat;
                    vb.lon = editando.lon;
                    mostrarUbicacion(vb);
                }
            }

        } else {
            agregarBloque(null); // una materia nueva empieza con un horario vacío
        }

        findViewById(R.id.btnGuardarMateria).setOnClickListener(v -> {
            String nombre = etNombre.getText().toString().trim();
            if (nombre.isEmpty()) {
                etNombre.setError(getString(R.string.materia_nombre_error));
                return;
            }

            // Lee cada horario de la pantalla
            List<Bloque> bloques = new ArrayList<>();
            for (VistaBloque vb : vistas) {
                String ini = leerHora(vb.etInicio);
                String fin = leerHora(vb.etFin);
                String salon = vb.etSalon.getText().toString().trim();
                String dias = Horario.diasComoTexto(vb.dias);

                boolean conUbicacion = vb.lat != 0 || vb.lon != 0;
                // Un horario totalmente vacío se ignora
                if (dias.isEmpty() && ini.isEmpty() && fin.isEmpty() && salon.isEmpty() && !conUbicacion) continue;

                if (!ini.isEmpty() && !fin.isEmpty() && Horario.minutos(fin) <= Horario.minutos(ini)) {
                    vb.etFin.setError(getString(R.string.hora_fin_error));
                    return;
                }
                Bloque b = new Bloque();
                b.days = dias;
                b.startTime = ini;
                b.endTime = fin;
                b.room = salon;
                b.lat = vb.lat;
                b.lon = vb.lon;
                bloques.add(b);
            }

            int id = editando != null ? editando.id : Datos.nextSubjectId();
            Subject s = new Subject(id, nombre, etProfesor.getText().toString().trim(), "", "");
            s.blocks = bloques;
            // La materia guarda la ubicación del primer horario que tenga una
            // (la usan las versiones anteriores de la app y sirve de respaldo)
            for (Bloque b : bloques) {
                if (b.tieneUbicacion()) {
                    s.lat = b.lat;
                    s.lon = b.lon;
                    break;
                }
            }
            // Texto de resumen (lo usan la lista de materias y las materias viejas)
            s.schedule = Horario.resumen(s);
            s.room = Horario.salones(s);
            Repo.guardarMateria(s);
            Toast.makeText(this, editando != null ? R.string.cambios_guardados : R.string.materia_guardada, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    /** "📍 Elegir en el mapa" o "📍 Ubicación marcada · Cambiar" (+ botón Quitar) de un horario. */
    private void mostrarUbicacion(VistaBloque vb) {
        boolean hay = vb.lat != 0 || vb.lon != 0;
        ((TextView) vb.raiz.findViewById(R.id.btnUbicacionBloque))
                .setText(hay ? R.string.ubicacion_marcada : R.string.elegir_mapa);
        vb.raiz.findViewById(R.id.btnQuitarUbicacionBloque).setVisibility(hay ? View.VISIBLE : View.GONE);
    }

    /**
     * Abre el mapa para marcar el salón de este horario. Si aún no tiene ubicación, el mapa
     * empieza donde está el horario anterior (casi siempre es la misma sede).
     */
    private void pedirUbicacion(VistaBloque vb) {
        pidiendo = vb;
        android.content.Intent i = new android.content.Intent(this, MapPickerActivity.class)
                .putExtra("lat", vb.lat).putExtra("lon", vb.lon);
        if (vb.lat == 0 && vb.lon == 0) {
            for (VistaBloque otro : vistas) {
                if (otro != vb && (otro.lat != 0 || otro.lon != 0)) {
                    i.putExtra("centroLat", otro.lat).putExtra("centroLon", otro.lon);
                    break;
                }
            }
        }
        elegirUbicacion.launch(i);
    }

    /** Agrega un horario a la pantalla; si "b" no es null, lo llena con sus datos. */
    private VistaBloque agregarBloque(Bloque b) {
        VistaBloque vb = new VistaBloque();
        vb.raiz = LayoutInflater.from(this).inflate(R.layout.item_bloque, listBloques, false);
        vb.etInicio = vb.raiz.findViewById(R.id.etHoraInicio);
        vb.etFin = vb.raiz.findViewById(R.id.etHoraFin);
        vb.etSalon = vb.raiz.findViewById(R.id.etSalon);
        // Todos los horarios usan los mismos ids: que Android no intente restaurarlos solo
        vb.etInicio.setSaveEnabled(false);
        vb.etFin.setSaveEnabled(false);
        vb.etSalon.setSaveEnabled(false);

        if (b != null) {
            vb.dias = Horario.dias(b);
            ponerHora(vb.etInicio, b.startTime);
            ponerHora(vb.etFin, b.endTime);
            vb.etSalon.setText(b.room);
            vb.lat = b.lat;
            vb.lon = b.lon;
        }

        vb.raiz.findViewById(R.id.btnUbicacionBloque).setOnClickListener(v -> pedirUbicacion(vb));
        vb.raiz.findViewById(R.id.btnQuitarUbicacionBloque).setOnClickListener(v -> {
            vb.lat = 0;
            vb.lon = 0;
            mostrarUbicacion(vb);
        });

        for (int i = 0; i < 7; i++) {
            final int k = i;
            vb.raiz.findViewById(IDS_DIAS[i]).setOnClickListener(v -> {
                vb.dias[k] = !vb.dias[k];
                pintarDias(vb);
            });
        }
        vb.etInicio.setOnClickListener(v -> elegirHora(vb.etInicio, 8));
        vb.etFin.setOnClickListener(v -> elegirHora(vb.etFin, 10));
        vb.raiz.findViewById(R.id.btnQuitarBloque).setOnClickListener(v -> {
            vistas.remove(vb);
            listBloques.removeView(vb.raiz);
            actualizarTitulos();
        });

        vistas.add(vb);
        listBloques.addView(vb.raiz);
        pintarDias(vb);
        mostrarUbicacion(vb);
        actualizarTitulos();
        return vb;
    }

    /** "Horario 1", "Horario 2"... y solo deja quitar si hay más de uno. */
    private void actualizarTitulos() {
        for (int i = 0; i < vistas.size(); i++) {
            View r = vistas.get(i).raiz;
            ((TextView) r.findViewById(R.id.tvTituloBloque)).setText(getString(R.string.horario_n, i + 1));
            r.findViewById(R.id.separadorBloque).setVisibility(i == 0 ? View.GONE : View.VISIBLE);
            r.findViewById(R.id.btnQuitarBloque).setVisibility(vistas.size() > 1 ? View.VISIBLE : View.GONE);
        }
    }

    private void pintarDias(VistaBloque vb) {
        for (int i = 0; i < 7; i++) {
            TextView tv = vb.raiz.findViewById(IDS_DIAS[i]);
            tv.setBackgroundResource(vb.dias[i] ? R.drawable.bg_filter_on : R.drawable.bg_filter_off);
            tv.setTextColor(ContextCompat.getColor(this, vb.dias[i] ? R.color.white : R.color.text_muted));
        }
    }

    /**
     * Abre el reloj en formato 12 h (con a. m. / p. m.).
     * La hora se GUARDA en 24 h ("14:00") en el tag del campo y se MUESTRA como "2:00 p. m.".
     */
    private void elegirHora(EditText destino, int horaPorDefecto) {
        int min = Horario.minutos(leerHora(destino));
        int h = min >= 0 ? min / 60 : horaPorDefecto;
        int m = min >= 0 ? min % 60 : 0;
        new TimePickerDialog(this, (view, hh, mm) ->
                ponerHora(destino, String.format(java.util.Locale.US, "%02d:%02d", hh, mm)),
                h, m, Ajustes.hora24).show(); // reloj de 12 o 24 h según Configuración
        destino.setError(null);
    }

    /** Guarda la hora en 24 h (tag) y muestra el texto con a. m. / p. m. */
    private void ponerHora(EditText campo, String hhmm) {
        String valor = hhmm == null ? "" : hhmm.trim();
        campo.setTag(valor);
        campo.setText(Horario.ampm(valor));
    }

    /** Devuelve la hora en 24 h ("14:00"), o "" si no se eligió. */
    private String leerHora(EditText campo) {
        Object t = campo.getTag();
        return t == null ? "" : t.toString();
    }
}
