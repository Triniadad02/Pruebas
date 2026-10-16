package com.cancha.cobros.web;

import com.cancha.cobros.service.CobroService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/** Panel del cobrador: crear partidos y administrar la lista fija de jugadores. */
@Controller
public class PanelController {

    // Fijamos la zona horaria de Lima: si el servidor está en otro país, "hoy" seguiría siendo correcto.
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final CobroService servicio;

    public PanelController(CobroService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/")
    public String panel(Model model) {
        model.addAttribute("hoy", LocalDate.now(LIMA));
        model.addAttribute("partidos", servicio.ultimosPartidos());
        return "panel";
    }

    @PostMapping("/partidos")
    public String crearPartido(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                               @RequestParam(required = false) String cancha,
                               @RequestParam BigDecimal precioHora,
                               @RequestParam BigDecimal horas,
                               RedirectAttributes ra) {
        try {
            String token = servicio.crearPartido(fecha, cancha, precioHora, horas);
            return "redirect:/e/" + token;
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    // ---- Jugadores ----------------------------------------------------------------------
    @GetMapping("/jugadores")
    public String jugadores(Model model) {
        model.addAttribute("jugadores", servicio.jugadores());
        return "jugadores";
    }

    @PostMapping("/jugadores")
    public String nuevoJugador(@RequestParam String nombre, RedirectAttributes ra) {
        try {
            servicio.crearJugador(nombre);
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/jugadores";
    }

    @PostMapping("/jugadores/{id}/activo")
    public String alternarActivo(@PathVariable int id) {
        servicio.alternarActivo(id);
        return "redirect:/jugadores";
    }
}
