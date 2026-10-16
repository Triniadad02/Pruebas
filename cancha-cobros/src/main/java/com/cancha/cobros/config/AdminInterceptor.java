package com.cancha.cobros.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Un "guardia de puerta": antes de entrar al panel revisa si la sesión ya ingresó el PIN.
 * Si no hay PIN configurado (vacío), deja pasar a todos (útil en desarrollo).
 */
public class AdminInterceptor implements HandlerInterceptor {

    public static final String SESION_ADMIN = "admin";
    private final String pin;

    public AdminInterceptor(String pin) {
        this.pin = pin;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (pin == null || pin.isBlank()) return true;
        if (Boolean.TRUE.equals(req.getSession().getAttribute(SESION_ADMIN))) return true;
        res.sendRedirect(req.getContextPath() + "/admin");
        return false;
    }
}
