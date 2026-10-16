package com.cancha.cobros.model;

import java.math.BigDecimal;

/**
 * Lo que el servidor le responde al navegador cuando tocas un nombre.
 * El JavaScript usa esto para actualizar la pantalla SIN recargarla (más rápido en la cancha).
 */
public record RespuestaToggle(
        boolean activo,           // true = quedó presente / pagado; false = se desmarcó
        int presentes,
        int pagaron,
        int pendientes,
        BigDecimal cuotaEstimada, // null si aún no hay presentes
        BigDecimal esperado,
        BigDecimal cobrado,
        BigDecimal sobranteReal,
        int porcentaje) {
}
