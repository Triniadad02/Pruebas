# ⚽ Cobros de cancha

Control de asistencia y cobro para partidos del grupo. Spring Boot 3 + Thymeleaf + SQL Server (JDBC puro).

## Cómo ejecutarlo

1. **Base de datos:** abre `db/cancha_cobros.sql` en SSMS y ejecútalo completo (crea la BD `cancha_cobros`,
   tablas, procedimientos, vista y datos de prueba).
2. **Conexión:** edita `src/main/resources/application.properties` (usuario/clave de SQL Server) o define las
   variables de entorno `DB_USER`, `DB_PASSWORD` y `ADMIN_PIN`.
   - Si no conecta: en *SQL Server Configuration Manager* activa **TCP/IP** y verifica el puerto 1433;
     el login `sa` debe estar habilitado (modo de autenticación mixta).
3. **Arrancar:** `mvn spring-boot:run` (o ejecuta `CanchaCobrosApplication` desde tu IDE).
4. Abre <http://localhost:8080> → ingresa el PIN (por defecto `1234`, cámbialo).

## Flujo de uso

1. En el panel crea el **partido** (precio por hora y horas).
2. Marca a los **presentes** con un toque (puedes agregar invitados).
3. **Iniciar cobro:** se fija la cuota (redondeo hacia arriba a 0.50).
4. Marca **Pagó** a cada uno; el nombre pasa a la lista de pagados.
5. **Cerrar partido.** El balance muestra si sobra para el fondo del grupo.

Comparte con el botón de WhatsApp el link **solo lectura** (`/v/{token}`). El link del cobrador (`/e/{token}`) no se comparte.

## Estructura

```
db/cancha_cobros.sql            Esquema, procedimientos, vista y datos de prueba
src/main/java/com/cancha/cobros
  model/        Records: Jugador, Partido, FilaLista, Resumen, RespuestaToggle
  repository/   CanchaRepository  -> todo el SQL (JdbcClient)
  service/      CobroService      -> reglas de negocio
  web/          PanelController, PartidoController, ApiController, AdminController
  config/       WebConfig + AdminInterceptor (PIN), Formato (moneda en plantillas)
src/main/resources/templates    panel, editar, ver, jugadores, admin
src/main/resources/static       css/app.css, js/app.js
```

## Rutas

| Ruta | Quién | Qué hace |
|---|---|---|
| `/`, `/partidos`, `/jugadores/**` | Cobrador (PIN) | Panel, crear partido, jugadores |
| `/e/{token}` | Cobrador | Marcar presentes y pagos |
| `/v/{token}` | Todos | Solo lectura (se refresca cada 20 s) |
| `/api/e/{token}/presente/{id}`, `/pago/{id}` | JS | Alternar presente / pagado (JSON) |

## Para que tus amigos abran el link

`localhost` solo funciona en tu PC. Opciones para probar: misma red WiFi usando la IP de tu PC
(`http://192.168.x.x:8080`), o un túnel temporal (ngrok / cloudflared). Para uso permanente hay que desplegarlo.

## Pendiente para después (fuera de alcance por ahora)

Yape integrado, notificaciones, fondo del grupo con gastos, login real con usuarios, historial por jugador.
