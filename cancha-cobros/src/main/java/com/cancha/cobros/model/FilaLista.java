package com.cancha.cobros.model;

/** Una fila de la "hoja de asistencia": jugador + si vino + si pagó (viene de fn_lista_partido). */
public record FilaLista(int jugadorId, String nombre, boolean presente, boolean pagado) {
}
