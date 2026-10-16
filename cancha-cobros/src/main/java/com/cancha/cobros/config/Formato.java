package com.cancha.cobros.config;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ayudante para las plantillas: en HTML se usa así -> ${@fmt.soles(valor)} */
@Component("fmt")
public class Formato {

    public String soles(BigDecimal valor) {
        if (valor == null) return "—";
        return "S/ " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
