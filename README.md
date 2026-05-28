# SIGEU Backend

Backend Spring Boot para SIGEU. Expone autenticacion, gestion de emergencias, seguimiento ciudadano, recursos operativos y automatizacion de casos.

## Tecnologias

- Java 17
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- PostgreSQL en Railway
- H2 para tests
- BCrypt para contrasenas
- JWT propio con activacion gradual

## Endpoints principales

- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/auth/recover`
- `GET /api/emergencies?target=POLICIA`
- `GET /api/emergencies/mine`
- `POST /api/emergencies`
- `PUT /api/emergencies/{id}/status`
- `DELETE /api/emergencies/{id}`
- `GET /api/emergencies/resources?target=POLICIA`
- `POST /api/emergencies/resources/add`
- `POST /api/emergencies/resources/remove`

## Variables de entorno

Usa `.env.example` como referencia. Las mas importantes para Railway son:

```env
PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://host:port/database
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=change-me
SIGEU_JWT_SECRET=change-this-long-random-secret
SIGEU_AUTH_REQUIRE_TOKEN=false
SIGEU_RATE_LIMIT_ENABLED=true
SIGEU_WORKFLOW_ENABLED=true
```

`SIGEU_AUTH_REQUIRE_TOKEN=false` permite activar JWT de forma gradual. Cuando el frontend ya este confirmado con token, se puede cambiar a `true` para exigir autenticacion.

## Verificacion local

```powershell
cmd.exe /c mvnw.cmd test
cmd.exe /c mvnw.cmd package -DskipTests
```

Los tests usan H2 en memoria desde `src/test/resources/application.properties`; no tocan la base de datos de Railway.

## Automatizacion operativa

El servicio `EmergencyWorkflowService` asigna recursos, deja reportes en espera si no hay cupo, mueve casos a resueltos segun el tiempo estimado y libera recursos al resolver. Los tiempos cambian segun el tipo de emergencia detectado en el texto del reporte.

## Seguridad

- Las contrasenas se guardan con BCrypt.
- Login y registro validan longitud y formato.
- Los reportes validan titulo, descripcion, ubicacion, tipo e imagen.
- JWT se emite en login/registro y se valida si llega como `Bearer token`.
- Rate limit reduce abuso en autenticacion, reportes y API general.
- El inicializador de datos no borra usuarios existentes.

## Nota de despliegue

No cambies `ddl-auto=update` a `create` en produccion porque eso podria borrar datos. Para Railway, confirma que las variables de base de datos y `SIGEU_JWT_SECRET` esten configuradas antes de activar autenticacion obligatoria.
