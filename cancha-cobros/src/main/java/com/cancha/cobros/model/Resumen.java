package com.cancha.cobros.model;

import java.math.BigDecimal;

/** Los números clave del partido (viene de la vista vw_resumen_partido). */
public record Resumen(
        int partidoId,
        int presentes,
        int pagaron,
        int pendientes,
        BigDecimal esperado,
        BigDecimal cobrado,
        BigDecimal sobranteReal) {

    /** Porcentaje cobrado (0-100) para la barra de progreso. */
    public int porcentaje() {
        return presentes == 0 ? 0 : pagaron * 100 / presentes;
    }
}
