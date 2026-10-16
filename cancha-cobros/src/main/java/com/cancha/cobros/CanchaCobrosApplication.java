package com.cancha.cobros;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto de entrada: Spring Boot arranca el servidor web y escanea los paquetes de abajo. */
@SpringBootApplication
public class CanchaCobrosApplication {
    public static void main(String[] args) {
        SpringApplication.run(CanchaCobrosApplication.class, args);
    }
}
