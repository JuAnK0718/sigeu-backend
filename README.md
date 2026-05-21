# SIGEU Backend

Backend Spring Boot para el sistema SIGEU. Expone endpoints de autenticacion y gestion de emergencias usados por el frontend.

## Endpoints principales

- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/auth/recover`
- `GET /api/emergencies?target=POLICIA`
- `POST /api/emergencies`
- `PUT /api/emergencies/{id}/status`
- `DELETE /api/emergencies/{id}`

## Configuracion

El proyecto puede usar variables de entorno. Si no existen, conserva los valores por defecto del archivo `application.properties`.

```env
PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://host:port/database
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=change-me
SIGEU_SEED_DEMO_USERS=true
```

## Verificacion local

```powershell
cmd.exe /c mvnw.cmd test
cmd.exe /c mvnw.cmd package -DskipTests
```

Los tests usan H2 en memoria desde `src/test/resources/application.properties`, asi que no tocan la base de datos de Railway.

## Nota importante

El inicializador de datos no borra usuarios al arrancar. Solo crea usuarios demo si faltan, para evitar perdida de datos en Railway.
