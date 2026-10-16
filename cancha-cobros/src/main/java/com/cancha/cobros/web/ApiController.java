package com.cancha.cobros.web;

import com.cancha.cobros.model.RespuestaToggle;
import com.cancha.cobros.service.CobroService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints que llama el JavaScript (fetch) al tocar un nombre. Devuelven JSON, no HTML,
 * para actualizar la pantalla sin recargarla.
 */
@RestController
public class ApiController {

    private final CobroService servicio;

    public ApiController(CobroService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/api/e/{token}/presente/{jugadorId}")
    public RespuestaToggle presente(@PathVariable String token, @PathVariable int jugadorId) {
        return servicio.alternarPresente(token, jugadorId);
    }

    @PostMapping("/api/e/{token}/pago/{jugadorId}")
    public RespuestaToggle pago(@PathVariable String token, @PathVariable int jugadorId) {
        return servicio.alternarPago(token, jugadorId);
    }

    /** Reglas de negocio rotas -> 409 con el mensaje que el JS muestra en un alert. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> regla(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}
