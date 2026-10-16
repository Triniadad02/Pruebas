package com.cancha.cobros.web;

import com.cancha.cobros.model.FilaLista;
import com.cancha.cobros.model.Partido;
import com.cancha.cobros.model.Resumen;
import com.cancha.cobros.service.CobroService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Pantallas del partido.
 *   /e/{token}  -> el cobrador (puede marcar)       [token_editar]
 *   /v/{token}  -> los demás (solo mirar)           [token_ver]
 */
@Controller
public class PartidoController {

    private final CobroService servicio;

    public PartidoController(CobroService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/e/{token}")
    public String editar(@PathVariable String token, Model model) {
        Partido p = servicio.editable(token);
        cargarDatos(p, model);
        model.addAttribute("cuotaEstimada", servicio.cuotaEstimada(p.costoTotal(),
                ((Resumen) model.getAttribute("resumen")).presentes()));
        return "editar";
    }

    @GetMapping("/v/{token}")
    public String ver(@PathVariable String token, Model model) {
        Partido p = servicio.soloLectura(token);
        cargarDatos(p, model);
        return "ver";
    }

    /** Reúne lo que necesitan ambas pantallas: partido, resumen y listas separadas. */
    private void cargarDatos(Partido p, Model model) {
        List<FilaLista> filas = servicio.lista(p.id());
        List<FilaLista> presentes = filas.stream().filter(FilaLista::presente).toList();
        model.addAttribute("partido", p);
        model.addAttribute("resumen", servicio.resumen(p.id()));
        model.addAttribute("filas", filas);                                                  // todos (para marcar)
        model.addAttribute("pendientes", presentes.stream().filter(f -> !f.pagado()).toList());
        model.addAttribute("pagados", presentes.stream().filter(FilaLista::pagado).toList());
        model.addAttribute("presentesLista", presentes);
    }

    // ---- Acciones del cobrador (formularios normales: recargan la pantalla) -------------
    @PostMapping("/e/{token}/invitado")
    public String invitado(@PathVariable String token, @RequestParam String nombre, RedirectAttributes ra) {
        return ejecutar(token, ra, () -> servicio.agregarInvitado(token, nombre));
    }

    @PostMapping("/e/{token}/iniciar-cobro")
    public String iniciarCobro(@PathVariable String token, RedirectAttributes ra) {
        return ejecutar(token, ra, () -> servicio.iniciarCobro(token));
    }

    @PostMapping("/e/{token}/reabrir")
    public String reabrir(@PathVariable String token, RedirectAttributes ra) {
        return ejecutar(token, ra, () -> servicio.reabrirLista(token));
    }

    @PostMapping("/e/{token}/cerrar")
    public String cerrar(@PathVariable String token, RedirectAttributes ra) {
        return ejecutar(token, ra, () -> servicio.cerrar(token));
    }

    /**
     * Ejecuta una acción y vuelve a la pantalla. Si el servicio rechaza algo (IllegalStateException),
     * el mensaje se muestra como alerta. Un token inválido lanza 404 ANTES de llegar al redirect.
     */
    private String ejecutar(String token, RedirectAttributes ra, Runnable accion) {
        try {
            accion.run();
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/e/" + token;
    }
}
