package com.cancha.cobros.model;

/** Un integrante del grupo (fila de la tabla Jugador). Un "record" es una clase inmutable de solo datos. */
public record Jugador(int id, String nombre, boolean activo) {
}
