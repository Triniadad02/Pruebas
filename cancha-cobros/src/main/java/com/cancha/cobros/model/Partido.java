package com.cancha.cobros.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un día de juego (fila de la tabla Partido).
 * tokenVer = link de solo lectura (se comparte). tokenEditar = link del cobrador.
 */
public record Partido(
        int id,
        LocalDate fecha,
        String cancha,
        BigDecimal precioHora,
        BigDecimal horas,
        BigDecimal costoTotal,
        BigDecimal cuota,        // null hasta que se inicia el cobro
        String estado,           // LISTA | COBRANDO | CERRADO
        String tokenVer,
        String tokenEditar) {
}
