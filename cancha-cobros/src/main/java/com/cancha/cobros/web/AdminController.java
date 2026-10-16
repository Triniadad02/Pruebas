package com.cancha.cobros.web;

import com.cancha.cobros.config.AdminInterceptor;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Pantalla del PIN del cobrador. */
@Controller
public class AdminController {

    @Value("${app.admin-pin:}")
    private String adminPin;

    @GetMapping("/admin")
    public String formulario() {
        return "admin";
    }

    @PostMapping("/admin")
    public String entrar(@RequestParam String pin, HttpSession session, RedirectAttributes ra) {
        // MessageDigest.isEqual compara en tiempo constante (buena práctica para secretos).
        boolean ok = !adminPin.isBlank() && MessageDigest.isEqual(
                pin.getBytes(StandardCharsets.UTF_8), adminPin.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            ra.addFlashAttribute("error", "PIN incorrecto.");
            return "redirect:/admin";
        }
        session.setAttribute(AdminInterceptor.SESION_ADMIN, true);
        return "redirect:/";
    }
}
