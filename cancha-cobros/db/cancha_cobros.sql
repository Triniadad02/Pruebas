/* =====================================================================
   SISTEMA DE COBROS DE CANCHA  -  Script para SQL Server
   ---------------------------------------------------------------------
   Idea central (analogía): es la "lista de asistencia del colegio" con
   una segunda columna: "pagó". El sistema solo calcula la cuota y te
   muestra quién falta, para que nadie se te pase.

   Ejecutar COMPLETO en SSMS (compatible con SQL Server 2012 o superior).
   Si algo falla, revisa la pestaña 'Messages': los errores salen en rojo.
   ===================================================================== */

-- ---------------------------------------------------------------------
-- 0. Base de datos
-- ---------------------------------------------------------------------
IF DB_ID('cancha_cobros') IS NULL
    CREATE DATABASE cancha_cobros;
GO
USE cancha_cobros;
GO

-- ---------------------------------------------------------------------
-- 1. Reinicio de desarrollo (borra todo para poder re-ejecutar el script)
--    ¡Quítalo o coméntalo cuando ya tengas datos reales!
--    El orden importa: primero lo que depende de otras cosas.
-- ---------------------------------------------------------------------
IF OBJECT_ID('dbo.vw_resumen_partido') IS NOT NULL DROP VIEW      dbo.vw_resumen_partido;
IF OBJECT_ID('dbo.fn_lista_partido')   IS NOT NULL DROP FUNCTION  dbo.fn_lista_partido;
IF OBJECT_ID('dbo.sp_iniciar_cobro')   IS NOT NULL DROP PROCEDURE dbo.sp_iniciar_cobro;
IF OBJECT_ID('dbo.sp_reabrir_lista')   IS NOT NULL DROP PROCEDURE dbo.sp_reabrir_lista;
IF OBJECT_ID('dbo.Asistencia') IS NOT NULL DROP TABLE dbo.Asistencia;   -- depende de Partido y Jugador
IF OBJECT_ID('dbo.Partido')    IS NOT NULL DROP TABLE dbo.Partido;
IF OBJECT_ID('dbo.Jugador')    IS NOT NULL DROP TABLE dbo.Jugador;
GO

-- ---------------------------------------------------------------------
-- 2. TABLA Jugador  (la "libreta de contactos" fija del grupo, ~25)
--    'activo' = 0 oculta a quien ya no juega SIN borrar su historial.
-- ---------------------------------------------------------------------
CREATE TABLE dbo.Jugador (
    id              INT IDENTITY(1,1) PRIMARY KEY,      -- se autonumera solo
    nombre          NVARCHAR(100) NOT NULL,
    telefono        NVARCHAR(20)  NULL,                 -- útil para Yape/WhatsApp luego
    activo          BIT           NOT NULL DEFAULT 1,
    fecha_registro  DATETIME2     NOT NULL DEFAULT SYSDATETIME()
);
GO

-- ---------------------------------------------------------------------
-- 3. TABLA Partido  (un "día de juego")
--
--    costo_total: columna CALCULADA (precio_hora * horas). Así nunca se
--    desincroniza: SQL Server la recalcula sola si cambias precio u horas.
--
--    cuota: queda NULL hasta que inicies el cobro. Se GUARDA ya redondeada
--    para que, si mañana cambias la regla de redondeo, los partidos viejos
--    no cambien (el historial es histórico).
--
--    estado (el ciclo de vida del partido):
--      LISTA    -> estás marcando quiénes vinieron
--      COBRANDO -> cuota fija, estás marcando quién pagó
--      CERRADO  -> terminó todo
--
--    token_ver / token_editar: reemplazan al login (por ahora).
--      - token_ver:    el link que compartes por WhatsApp (solo lectura)
--      - token_editar: el link SOLO del cobrador (puede marcar)
--    Son GUIDs, imposibles de adivinar (a diferencia de /partido/1).
-- ---------------------------------------------------------------------
CREATE TABLE dbo.Partido (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    fecha         DATE           NOT NULL DEFAULT CAST(SYSDATETIME() AS DATE),
    cancha        NVARCHAR(100)  NULL,
    precio_hora   DECIMAL(10,2)  NOT NULL CHECK (precio_hora > 0),
    horas         DECIMAL(4,2)   NOT NULL CHECK (horas > 0),
    costo_total   AS CAST(precio_hora * horas AS DECIMAL(10,2)) PERSISTED,
    cuota         DECIMAL(10,2)  NULL,
    estado        VARCHAR(10)    NOT NULL DEFAULT 'LISTA'
                  CONSTRAINT CK_Partido_estado
                  CHECK (estado IN ('LISTA','COBRANDO','CERRADO')),
    token_ver     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    token_editar  UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
    creado        DATETIME2      NOT NULL DEFAULT SYSDATETIME()
);
GO

-- Índices únicos: buscar por token debe ser rápido y no repetirse.
CREATE UNIQUE INDEX UX_Partido_token_ver    ON dbo.Partido(token_ver);
CREATE UNIQUE INDEX UX_Partido_token_editar ON dbo.Partido(token_editar);
GO

-- ---------------------------------------------------------------------
-- 4. TABLA Asistencia  (la "tabla puente": qué jugador fue a qué partido)
--    Una fila = "Luis estuvo en el partido del martes".
--
--    UNIQUE(partido_id, jugador_id): evita marcar dos veces al mismo
--    jugador en el mismo partido (el error clásico de dedo).
--
--    CHECK de pago: si pagado = 1, DEBE haber fecha_pago; si no, no.
--    Esto evita datos contradictorios a nivel de base de datos.
-- ---------------------------------------------------------------------
CREATE TABLE dbo.Asistencia (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    partido_id  INT NOT NULL
                CONSTRAINT FK_Asistencia_Partido REFERENCES dbo.Partido(id)
                ON DELETE CASCADE,                      -- si borras el partido, caen sus asistencias
    jugador_id  INT NOT NULL
                CONSTRAINT FK_Asistencia_Jugador REFERENCES dbo.Jugador(id),
    pagado      BIT NOT NULL DEFAULT 0,
    fecha_pago  DATETIME2 NULL,
    metodo      VARCHAR(15) NULL                        -- 'EFECTIVO' / 'YAPE' (opcional, para después)
                CONSTRAINT CK_Asistencia_metodo
                CHECK (metodo IS NULL OR metodo IN ('EFECTIVO','YAPE','PLIN')),
    CONSTRAINT UQ_Asistencia_partido_jugador UNIQUE (partido_id, jugador_id),
    CONSTRAINT CK_Asistencia_pago
        CHECK ( (pagado = 0 AND fecha_pago IS NULL)
             OR (pagado = 1 AND fecha_pago IS NOT NULL) )
);
GO

-- Para que "¿quiénes fueron a este partido?" sea rápido.
CREATE INDEX IX_Asistencia_partido ON dbo.Asistencia(partido_id);
GO

-- ---------------------------------------------------------------------
-- 5. PROCEDIMIENTO sp_iniciar_cobro
--    "Cierra la lista" y fija la cuota. Es el botón: ¡Iniciar cobro!
--
--    Regla de redondeo: hacia ARRIBA a múltiplos de 0.50
--       40 / 18 = 2.22  ->  x2 = 4.44 -> CEILING = 5 -> /2 = 2.50
--    Como siempre sube, nunca quedas corto con el dueño de la cancha.
--
--    Por qué se fija aquí: si la cuota cambiara después de que alguien
--    pagó, tendrías gente que pagó 2.50 y otros 3.00. Con estado COBRANDO
--    la cuota queda congelada.
-- ---------------------------------------------------------------------
CREATE PROCEDURE dbo.sp_iniciar_cobro
    @partido_id INT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @estado    VARCHAR(10),
            @costo     DECIMAL(10,2),
            @presentes INT,
            @cuota     DECIMAL(10,2);

    SELECT @estado = estado, @costo = costo_total
    FROM dbo.Partido
    WHERE id = @partido_id;

    IF @estado IS NULL
        THROW 50001, 'El partido no existe.', 1;
    IF @estado <> 'LISTA'
        THROW 50002, 'El cobro ya fue iniciado para este partido.', 1;

    SELECT @presentes = COUNT(*)
    FROM dbo.Asistencia
    WHERE partido_id = @partido_id;

    IF @presentes = 0
        THROW 50003, 'No hay jugadores marcados como presentes.', 1;

    -- Redondeo hacia arriba a 0.50
    SET @cuota = CEILING((@costo / @presentes) * 2.0) / 2.0;

    UPDATE dbo.Partido
    SET cuota = @cuota,
        estado = 'COBRANDO'
    WHERE id = @partido_id;
END
GO

-- ---------------------------------------------------------------------
-- 6. PROCEDIMIENTO sp_reabrir_lista
--    Por si llegó alguien tarde o marcaste mal. SOLO se permite si nadie
--    ha pagado todavía (si no, la cuota ya está "comprometida").
-- ---------------------------------------------------------------------
CREATE PROCEDURE dbo.sp_reabrir_lista
    @partido_id INT
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1 FROM dbo.Asistencia
               WHERE partido_id = @partido_id AND pagado = 1)
        THROW 50004, 'No se puede reabrir: ya hay pagos registrados.', 1;

    UPDATE dbo.Partido
    SET cuota = NULL,
        estado = 'LISTA'
    WHERE id = @partido_id AND estado = 'COBRANDO';
END
GO

-- ---------------------------------------------------------------------
-- 7. FUNCIÓN fn_lista_partido  (alimenta la pantalla de marcado)
--    Devuelve TODOS los jugadores activos del grupo y, para cada uno, si
--    está presente en ese partido y si ya pagó. Es la "hoja de asistencia"
--    completa: los 25, con un tilde en los que vinieron.
--    LEFT JOIN = "dame todos los jugadores, aunque no tengan asistencia".
-- ---------------------------------------------------------------------
CREATE FUNCTION dbo.fn_lista_partido (@partido_id INT)
RETURNS TABLE
AS
RETURN
(
    SELECT
        j.id                                  AS jugador_id,
        j.nombre,
        CASE WHEN a.id IS NULL THEN 0 ELSE 1 END AS presente,
        ISNULL(a.pagado, 0)                   AS pagado,
        a.fecha_pago
    FROM dbo.Jugador j
    LEFT JOIN dbo.Asistencia a
           ON a.jugador_id = j.id
          AND a.partido_id = @partido_id
    WHERE j.activo = 1
       OR a.id IS NOT NULL      -- incluye a un jugador desactivado si ya estaba en el partido
);
GO

-- ---------------------------------------------------------------------
-- 8. VISTA vw_resumen_partido  (los 3 números clave + pendientes)
--
--    esperado      = cuota x presentes   (lo que DEBERÍAS juntar)
--    cobrado       = cuota x los que pagaron (lo que REALMENTE tienes)
--    sobrante_real = cobrado - costo_total
--
--    OJO: el sobrante se calcula sobre lo COBRADO, no sobre lo esperado.
--    Mientras falte gente por pagar, sobrante_real sale negativo (es lo
--    que aún falta para cubrir la cancha). Cuando todos pagan, pasa a ser
--    el dinero que va al fondo del grupo.
-- ---------------------------------------------------------------------
CREATE VIEW dbo.vw_resumen_partido
AS
SELECT
    p.id            AS partido_id,
    p.fecha,
    p.cancha,
    p.estado,
    p.costo_total,
    p.cuota,
    COUNT(a.id)                                              AS presentes,
    ISNULL(SUM(CASE WHEN a.pagado = 1 THEN 1 ELSE 0 END), 0) AS pagaron,
    COUNT(a.id)
      - ISNULL(SUM(CASE WHEN a.pagado = 1 THEN 1 ELSE 0 END), 0) AS pendientes,
    ISNULL(p.cuota, 0) * COUNT(a.id)                         AS esperado,
    ISNULL(p.cuota, 0)
      * ISNULL(SUM(CASE WHEN a.pagado = 1 THEN 1 ELSE 0 END), 0) AS cobrado,
    ISNULL(p.cuota, 0)
      * ISNULL(SUM(CASE WHEN a.pagado = 1 THEN 1 ELSE 0 END), 0)
      - p.costo_total                                        AS sobrante_real
FROM dbo.Partido p
LEFT JOIN dbo.Asistencia a ON a.partido_id = p.id
GROUP BY p.id, p.fecha, p.cancha, p.estado, p.costo_total, p.cuota;
GO

-- ---------------------------------------------------------------------
-- 9. DATOS DE PRUEBA  (bórralos cuando uses datos reales)
-- ---------------------------------------------------------------------
INSERT INTO dbo.Jugador (nombre) VALUES
 (N'Carlos'), (N'Luis'), (N'Miguel'), (N'Jorge'), (N'Pedro'),
 (N'Andrés'), (N'Diego'), (N'Raúl'),  (N'Fabián'), (N'Kevin');

-- Partido: 20 soles/hora x 2 horas = 40
INSERT INTO dbo.Partido (cancha, precio_hora, horas)
VALUES (N'Cancha Los Pinos', 20.00, 2);

-- Marcamos 8 presentes (jugadores 1 a 8) en el partido 1
INSERT INTO dbo.Asistencia (partido_id, jugador_id)
SELECT 1, id FROM dbo.Jugador WHERE id BETWEEN 1 AND 8;
GO

-- ---------------------------------------------------------------------
-- 10. PRUEBA RÁPIDA  (ejecuta de a un bloque y mira los resultados)
-- ---------------------------------------------------------------------
/*
-- a) Iniciar el cobro: 40 / 8 = 5.00 de cuota
EXEC dbo.sp_iniciar_cobro @partido_id = 1;
SELECT * FROM dbo.vw_resumen_partido;

-- b) Marcar que Carlos (jugador 1) pagó
UPDATE dbo.Asistencia
SET pagado = 1, fecha_pago = SYSDATETIME(), metodo = 'EFECTIVO'
WHERE partido_id = 1 AND jugador_id = 1;

-- c) Ver quién falta (pantalla de cobro)
SELECT nombre, pagado FROM dbo.fn_lista_partido(1) WHERE presente = 1;

-- d) Resumen actualizado (verás cobrado = 5 y sobrante_real negativo)
SELECT * FROM dbo.vw_resumen_partido;

-- e) Probar que NO se puede reabrir con pagos
EXEC dbo.sp_reabrir_lista @partido_id = 1;   -- debe dar error 50004
*/