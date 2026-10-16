package com.cancha.cobros.service;

import com.cancha.cobros.model.FilaLista;
import com.cancha.cobros.model.Jugador;
import com.cancha.cobros.model.Partido;
import com.cancha.cobros.model.RespuestaToggle;
import com.cancha.cobros.model.Resumen;
import com.cancha.cobros.repository.CanchaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Capa de reglas de negocio. El repositorio solo "habla SQL"; aquí se decide qué está permitido.
 * Ejemplo: no puedes marcar asistencia si la lista ya se cerró.
 * Las reglas de error se lanzan como IllegalStateException con un mensaje legible para el usuario.
 */
@Service
public class CobroService {

    private final CanchaRepository repo;

    /** Múltiplo al que se redondea la cuota HACIA ARRIBA (0.50 = de medio en medio sol). Ver application.properties. */
    private final BigDecimal pasoRedondeo;

    public CobroService(CanchaRepository repo,
                        @Value("${app.cuota.redondeo:0.50}") BigDecimal pasoRedondeo) {
        if (pasoRedondeo.signum() <= 0) {
            throw new IllegalArgumentException("app.cuota.redondeo debe ser mayor a 0");
        }
        this.repo = repo;
        this.pasoRedondeo = pasoRedondeo;
    }

    // ---- Acceso por token (reemplaza al login) ------------------------------------------
    public Partido editable(String token) {
        return repo.buscarPorTokenEditar(normalizar(token)).orElseThrow(this::noExiste);
    }

    public Partido soloLectura(String token) {
        return repo.buscarPorTokenVer(normalizar(token)).orElseThrow(this::noExiste);
    }

    /** Valida que el token sea un GUID real antes de tocar la base de datos. */
    private String normalizar(String token) {
        try {
            return UUID.fromString(token).toString();
        } catch (IllegalArgumentException e) {
            throw noExiste();
        }
    }

    private ResponseStatusException noExiste() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Partido no encontrado");
    }

    // ---- Consultas ----------------------------------------------------------------------
    public List<FilaLista> lista(int partidoId) {
        return repo.lista(partidoId);
    }

    public Resumen resumen(int partidoId) {
        return repo.resumen(partidoId);
    }

    public List<Partido> ultimosPartidos() {
        return repo.ultimosPartidos();
    }

    public List<Jugador> jugadores() {
        return repo.todosLosJugadores();
    }

    // ---- Panel del cobrador -------------------------------------------------------------
    public String crearPartido(LocalDate fecha, String cancha, BigDecimal precioHora, BigDecimal horas) {
        if (fecha == null) throw new IllegalStateException("Indica la fecha.");
        if (precioHora == null || precioHora.signum() <= 0) throw new IllegalStateException("El precio por hora debe ser mayor a 0.");
        if (horas == null || horas.signum() <= 0) throw new IllegalStateException("Las horas deben ser mayores a 0.");
        String canchaLimpia = (cancha == null || cancha.isBlank()) ? null : cancha.trim();
        return repo.crearPartido(fecha, canchaLimpia, precioHora, horas);
    }

    public void crearJugador(String nombre) {
        repo.crearJugador(validarNombre(nombre));
    }

    public void alternarActivo(int jugadorId) {
        repo.alternarActivo(jugadorId);
    }

    private String validarNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty() || limpio.length() > 100) {
            throw new IllegalStateException("Escribe un nombre válido (máximo 100 caracteres).");
        }
        return limpio;
    }

    // ---- Marcar asistencia (estado LISTA) -----------------------------------------------
    /*
     * OJO: aquí NO usamos @Transactional a propósito. Cada operación (un UPDATE/INSERT/DELETE) ya es
     * atómica por sí sola y se confirma al terminar. Si lo envolvemos en una transacción, la fila
     * modificada queda BLOQUEADA mientras se lee el resumen de todas las filas; con dos personas
     * tocando a la vez, cada una espera la fila de la otra y SQL Server cancela una (deadlock).
     * Analogía: dos personas con la lista del colegio, cada una con su dedo sobre un nombre,
     * y ambas intentando leer la lista completa: se estorban mutuamente.
     */
    public RespuestaToggle alternarPresente(String token, int jugadorId) {
        Partido p = editable(token);
        exigirEstado(p, "LISTA", "La lista ya está cerrada. Reábrela para cambiar a los presentes.");
        try {
            repo.alternarPresente(p.id(), jugadorId);
        } catch (DataAccessException e) {
            throw new IllegalStateException("No se pudo marcar a ese jugador.");
        }
        return respuesta(p, repo.estaPresente(p.id(), jugadorId));
    }

    /** Agrega a alguien que no está en la lista fija (queda registrado y presente en este partido). */
    @Transactional
    public void agregarInvitado(String token, String nombre) {
        Partido p = editable(token);
        exigirEstado(p, "LISTA", "La lista ya está cerrada. Reábrela para agregar invitados.");
        int id = repo.crearJugador(validarNombre(nombre));
        repo.alternarPresente(p.id(), id);
    }

    // ---- Cobrar (estado COBRANDO) -------------------------------------------------------
    // Sin @Transactional por la misma razón explicada arriba (evita deadlocks al cobrar rápido).
    public RespuestaToggle alternarPago(String token, int jugadorId) {
        Partido p = editable(token);
        exigirEstado(p, "COBRANDO", "El cobro no está activo para este partido.");
        if (repo.alternarPago(p.id(), jugadorId) == 0) {
            throw new IllegalStateException("Ese jugador no está en la lista de presentes.");
        }
        return respuesta(p, repo.estaPagado(p.id(), jugadorId));
    }

    // ---- Cambios de estado --------------------------------------------------------------
    /**
     * "Cierra la lista" y FIJA la cuota. Desde aquí la cuota ya no cambia aunque llegue alguien:
     * así nadie paga distinto. La cuota se calcula en Java (ver cuotaEstimada) y se guarda.
     */
    public void iniciarCobro(String token) {
        Partido p = editable(token);
        exigirEstado(p, "LISTA", "El cobro ya fue iniciado para este partido.");
        int presentes = repo.resumen(p.id()).presentes();
        if (presentes == 0) {
            throw new IllegalStateException("No hay jugadores marcados como presentes.");
        }
        BigDecimal cuota = cuotaEstimada(p.costoTotal(), presentes);
        if (repo.iniciarCobro(p.id(), cuota) == 0) {
            throw new IllegalStateException("No se pudo iniciar el cobro. Recarga la pantalla e inténtalo de nuevo.");
        }
    }

    /** Vuelve a la lista (por si llegó alguien tarde). Solo si todavía nadie ha pagado. */
    public void reabrirLista(String token) {
        Partido p = editable(token);
        if (repo.reabrirSinPagos(p.id()) == 0) {
            throw new IllegalStateException("No se puede reabrir: ya hay pagos registrados.");
        }
    }

    public void cerrar(String token) {
        Partido p = editable(token);
        exigirEstado(p, "COBRANDO", "Solo se puede cerrar un partido que está en cobro.");
        repo.cerrar(p.id());
    }

    // ---- Auxiliares ---------------------------------------------------------------------
    private void exigirEstado(Partido p, String esperado, String mensaje) {
        if (!esperado.equals(p.estado())) throw new IllegalStateException(mensaje);
    }

    private RespuestaToggle respuesta(Partido p, boolean activo) {
        Resumen r = repo.resumen(p.id());
        BigDecimal cuota = p.cuota() != null ? p.cuota() : cuotaEstimada(p.costoTotal(), r.presentes());
        return new RespuestaToggle(activo, r.presentes(), r.pagaron(), r.pendientes(),
                cuota, r.esperado(), r.cobrado(), r.sobranteReal(), r.porcentaje());
    }

    /**
     * Cuota por persona = (costo total / presentes) redondeada HACIA ARRIBA al múltiplo configurado.
     * Siempre sube, así que lo recaudado nunca queda por debajo del costo de la cancha.
     *
     * Ejemplos con paso 0.50:   40 / 16 = 2.50  ->  2.50   (cuadra exacto)
     *                           40 / 18 = 2.22  ->  2.50   (sobran 5.00 para el fondo)
     * Con paso 1.00 el mismo 40 / 18 daría 3.00.
     */
    public BigDecimal cuotaEstimada(BigDecimal costo, int presentes) {
        if (presentes <= 0) return null;
        BigDecimal porPersona = costo.divide(BigDecimal.valueOf(presentes), 10, RoundingMode.HALF_UP);
        BigDecimal pasos = porPersona.divide(pasoRedondeo, 0, RoundingMode.CEILING);   // cuántos "escalones" hacen falta
        return pasos.multiply(pasoRedondeo).setScale(2, RoundingMode.HALF_UP);
    }
}