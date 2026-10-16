package com.cancha.cobros.repository;

import com.cancha.cobros.model.FilaLista;
import com.cancha.cobros.model.Jugador;
import com.cancha.cobros.model.Partido;
import com.cancha.cobros.model.Resumen;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Capa de acceso a datos. Aquí vive TODO el SQL.
 *
 * Usamos JdbcClient (Spring 6.1+): escribes la consulta, le pasas parámetros con nombre (:nombre)
 * y un "RowMapper" que convierte cada fila en un objeto Java. Es como pedirle algo al bodeguero
 * con un papel escrito: tú dices exactamente qué quieres y él te lo trae.
 *
 * Los parámetros con nombre (:p, :j) evitan la inyección SQL: nunca concatenes texto en las consultas.
 */
@Repository
public class CanchaRepository {

    private final JdbcClient jdbc;

    public CanchaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- Mapeadores: fila SQL -> objeto Java --------------------------------------------
    private static final RowMapper<Partido> PARTIDO = (rs, n) -> new Partido(
            rs.getInt("id"),
            rs.getObject("fecha", LocalDate.class),
            rs.getString("cancha"),
            rs.getBigDecimal("precio_hora"),
            rs.getBigDecimal("horas"),
            rs.getBigDecimal("costo_total"),
            rs.getBigDecimal("cuota"),
            rs.getString("estado"),
            rs.getString("token_ver"),      // uniqueidentifier -> texto
            rs.getString("token_editar"));

    private static final RowMapper<Jugador> JUGADOR = (rs, n) ->
            new Jugador(rs.getInt("id"), rs.getString("nombre"), rs.getBoolean("activo"));

    private static final RowMapper<FilaLista> FILA = (rs, n) -> new FilaLista(
            rs.getInt("jugador_id"),
            rs.getString("nombre"),
            rs.getInt("presente") == 1,
            rs.getBoolean("pagado"));

    private static final RowMapper<Resumen> RESUMEN = (rs, n) -> new Resumen(
            rs.getInt("partido_id"),
            rs.getInt("presentes"),
            rs.getInt("pagaron"),
            rs.getInt("pendientes"),
            rs.getBigDecimal("esperado"),
            rs.getBigDecimal("cobrado"),
            rs.getBigDecimal("sobrante_real"));

    // ---- Jugadores ----------------------------------------------------------------------
    public List<Jugador> todosLosJugadores() {
        return jdbc.sql("SELECT id, nombre, activo FROM dbo.Jugador ORDER BY activo DESC, nombre")
                .query(JUGADOR).list();
    }

    /** INSERT ... OUTPUT INSERTED.id: SQL Server te devuelve el id que acaba de generar. */
    public int crearJugador(String nombre) {
        return jdbc.sql("INSERT INTO dbo.Jugador (nombre) OUTPUT INSERTED.id VALUES (:nombre)")
                .param("nombre", nombre)
                .query(Integer.class).single();
    }

    public void alternarActivo(int jugadorId) {
        jdbc.sql("UPDATE dbo.Jugador SET activo = CASE WHEN activo = 1 THEN 0 ELSE 1 END WHERE id = :id")
                .param("id", jugadorId).update();
    }

    // ---- Partidos -----------------------------------------------------------------------
    /** Devuelve el token_editar del partido recién creado (para redirigir al cobrador a su pantalla). */
    public String crearPartido(LocalDate fecha, String cancha, BigDecimal precioHora, BigDecimal horas) {
        return jdbc.sql("""
                        INSERT INTO dbo.Partido (fecha, cancha, precio_hora, horas)
                        OUTPUT INSERTED.token_editar
                        VALUES (:fecha, :cancha, :precio, :horas)
                        """)
                .param("fecha", fecha)
                .param("cancha", cancha, Types.NVARCHAR)   // indicamos el tipo porque puede ser null
                .param("precio", precioHora)
                .param("horas", horas)
                .query(String.class).single();
    }

    public Optional<Partido> buscarPorTokenEditar(String token) {
        return jdbc.sql("SELECT * FROM dbo.Partido WHERE token_editar = :t")
                .param("t", token).query(PARTIDO).optional();
    }

    public Optional<Partido> buscarPorTokenVer(String token) {
        return jdbc.sql("SELECT * FROM dbo.Partido WHERE token_ver = :t")
                .param("t", token).query(PARTIDO).optional();
    }

    public List<Partido> ultimosPartidos() {
        return jdbc.sql("SELECT TOP 10 * FROM dbo.Partido ORDER BY fecha DESC, creado DESC")
                .query(PARTIDO).list();
    }

    // ---- Asistencia y pagos -------------------------------------------------------------
    /**
     * Hoja de asistencia completa del partido: TODOS los jugadores activos del grupo y, para cada uno,
     * si vino (presente) y si ya pagó. Es como la lista del colegio: aparecen todos, y unos tienen tilde.
     * LEFT JOIN = "dame todos los jugadores, aunque no tengan asistencia en este partido".
     * El OR incluye a un jugador desactivado si ya estaba registrado en este partido.
     */
    public List<FilaLista> lista(int partidoId) {
        return jdbc.sql("""
                        SELECT j.id AS jugador_id,
                               j.nombre,
                               CASE WHEN a.id IS NULL THEN 0 ELSE 1 END AS presente,
                               ISNULL(a.pagado, 0)                      AS pagado
                        FROM dbo.Jugador j
                        LEFT JOIN dbo.Asistencia a
                               ON a.jugador_id = j.id
                              AND a.partido_id = :id
                        WHERE j.activo = 1 OR a.id IS NOT NULL
                        ORDER BY j.nombre
                        """)
                .param("id", partidoId).query(FILA).list();
    }

    public Resumen resumen(int partidoId) {
        return jdbc.sql("SELECT * FROM dbo.vw_resumen_partido WHERE partido_id = :id")
                .param("id", partidoId).query(RESUMEN).single();
    }

    /** Si ya estaba marcado lo desmarca; si no, lo marca. Un solo botón para las dos acciones. */
    public void alternarPresente(int partidoId, int jugadorId) {
        jdbc.sql("""
                        IF EXISTS (SELECT 1 FROM dbo.Asistencia WHERE partido_id = :p AND jugador_id = :j)
                            DELETE FROM dbo.Asistencia WHERE partido_id = :p AND jugador_id = :j
                        ELSE
                            INSERT INTO dbo.Asistencia (partido_id, jugador_id) VALUES (:p, :j)
                        """)
                .param("p", partidoId).param("j", jugadorId).update();
    }

    public boolean estaPresente(int partidoId, int jugadorId) {
        return jdbc.sql("SELECT COUNT(*) FROM dbo.Asistencia WHERE partido_id = :p AND jugador_id = :j")
                .param("p", partidoId).param("j", jugadorId)
                .query(Integer.class).single() > 0;
    }

    /**
     * Alterna pagado/no pagado. En un UPDATE de SQL Server, todas las expresiones del SET leen los
     * valores ANTERIORES a la actualización, por eso los tres CASE usan "pagado" sin enredarse.
     * Devuelve cuántas filas cambió (0 = ese jugador no está en la lista).
     */
    public int alternarPago(int partidoId, int jugadorId) {
        return jdbc.sql("""
                        UPDATE dbo.Asistencia
                        SET pagado     = CASE WHEN pagado = 1 THEN 0 ELSE 1 END,
                            fecha_pago = CASE WHEN pagado = 1 THEN NULL ELSE SYSDATETIME() END,
                            metodo     = CASE WHEN pagado = 1 THEN NULL ELSE 'EFECTIVO' END
                        WHERE partido_id = :p AND jugador_id = :j
                        """)
                .param("p", partidoId).param("j", jugadorId).update();
    }

    public boolean estaPagado(int partidoId, int jugadorId) {
        return jdbc.sql("SELECT pagado FROM dbo.Asistencia WHERE partido_id = :p AND jugador_id = :j")
                .param("p", partidoId).param("j", jugadorId)
                .query(Boolean.class).single();
    }

    // ---- Ciclo de vida del partido ------------------------------------------------------
    /** Guarda la cuota y pasa a COBRANDO. Devuelve 0 si el partido ya no estaba en LISTA. */
    public int iniciarCobro(int partidoId, BigDecimal cuota) {
        return jdbc.sql("UPDATE dbo.Partido SET cuota = :cuota, estado = 'COBRANDO' WHERE id = :id AND estado = 'LISTA'")
                .param("cuota", cuota).param("id", partidoId).update();
    }

    /** Vuelve a LISTA solo si está en COBRANDO y nadie ha pagado (NOT EXISTS). Devuelve 0 si no se pudo. */
    public int reabrirSinPagos(int partidoId) {
        return jdbc.sql("""
                        UPDATE dbo.Partido SET cuota = NULL, estado = 'LISTA'
                        WHERE id = :id AND estado = 'COBRANDO'
                          AND NOT EXISTS (SELECT 1 FROM dbo.Asistencia WHERE partido_id = :id AND pagado = 1)
                        """)
                .param("id", partidoId).update();
    }

    public void cerrar(int partidoId) {
        jdbc.sql("UPDATE dbo.Partido SET estado = 'CERRADO' WHERE id = :id AND estado = 'COBRANDO'")
                .param("id", partidoId).update();
    }
}