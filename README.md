# SIGEU Backend

SIGEU Backend es la API REST principal del Sistema Inteligente de Gestion de Emergencias Urbanas. Su responsabilidad es administrar autenticacion, usuarios, reportes ciudadanos, entidades operativas, recursos disponibles y automatizacion del flujo de atencion.

El backend se integra con el frontend web de SIGEU y con una base de datos PostgreSQL desplegada en Railway.

## Objetivo

El objetivo del backend es centralizar la logica de negocio del sistema, garantizando persistencia de datos, validaciones, seguridad, control de recursos y trazabilidad del estado de cada emergencia reportada.

## Arquitectura

La aplicacion sigue una arquitectura por capas:

- Controladores REST: exponen los endpoints consumidos por el frontend.
- Servicios: contienen la logica operativa y reglas del sistema.
- Repositorios: gestionan el acceso a datos mediante Spring Data JPA.
- Modelos: representan usuarios, emergencias e inventarios de recursos.
- Seguridad: maneja autenticacion, tokens, hashing de contrasenas, cabeceras y limites de peticiones.

## Tecnologias

- Java 17
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- PostgreSQL
- H2 para pruebas automatizadas
- BCrypt para proteccion de contrasenas
- JWT para autenticacion
- Maven
- Railway para despliegue

## Modulos principales

- Autenticacion: inicio de sesion, registro y recuperacion basica de acceso.
- Emergencias: creacion, consulta, seguimiento, actualizacion de estado y eliminacion.
- Recursos operativos: control de unidades disponibles, ocupadas y en espera.
- Automatizacion: asignacion de recursos, transicion de estados y liberacion de unidades.
- Validaciones: control de longitud, campos obligatorios, entidades validas y estados permitidos.
- Seguridad: JWT, BCrypt, rate limit y cabeceras HTTP.

## Endpoints principales

### Autenticacion

- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/auth/recover`

### Emergencias

- `GET /api/emergencies`
- `GET /api/emergencies?target=POLICIA`
- `GET /api/emergencies/mine`
- `POST /api/emergencies`
- `PUT /api/emergencies/{id}/status`
- `DELETE /api/emergencies/{id}`

### Recursos operativos

- `GET /api/emergencies/resources?target=POLICIA`
- `POST /api/emergencies/resources/add`
- `POST /api/emergencies/resources/remove`

## Base de datos

El sistema utiliza PostgreSQL como base de datos principal. Las entidades persistentes incluyen:

- `User`: usuarios ciudadanos y entidades operativas.
- `Emergency`: reportes de emergencia y su trazabilidad operativa.
- `ResourceInventory`: inventario de recursos por entidad.

La configuracion de Hibernate utiliza actualizacion incremental del esquema para conservar los datos existentes durante el despliegue.

## Seguridad

- Las contrasenas se almacenan con hashing BCrypt.
- El login y el registro validan formato y longitud.
- El sistema emite tokens JWT para usuarios autenticados.
- Los endpoints pueden exigir token mediante configuracion gradual.
- El rate limit reduce abuso en autenticacion, reportes y llamadas generales a la API.
- Los reportes validan titulo, descripcion, ubicacion, tipo, imagen y entidad destino.
- El inicializador de datos conserva usuarios existentes y solo crea datos base si faltan.

## Automatizacion operativa

El servicio `EmergencyWorkflowService` coordina el ciclo de vida de los reportes. Su logica permite:

- Asignar recursos disponibles segun la entidad.
- Dejar reportes en espera cuando no existe cupo operativo.
- Estimar tiempos de resolucion segun el tipo y gravedad del incidente.
- Marcar reportes como resueltos al cumplir el tiempo operativo.
- Liberar recursos ocupados al finalizar la atencion.
- Mantener resumenes de disponibilidad para cada entidad.

## Variables de entorno

Configuracion principal del entorno:

```env
PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://host:port/database
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=change-me
SIGEU_JWT_SECRET=change-this-long-random-secret
SIGEU_JWT_EXPIRATION_SECONDS=86400
SIGEU_AUTH_REQUIRE_TOKEN=false
SIGEU_RATE_LIMIT_ENABLED=true
SIGEU_WORKFLOW_ENABLED=true
SIGEU_RESOURCES_POLICIA=30
SIGEU_RESOURCES_HOSPITAL=15
SIGEU_RESOURCES_BOMBEROS=8
```

`SIGEU_AUTH_REQUIRE_TOKEN` permite activar o desactivar la exigencia obligatoria del token sin modificar codigo fuente.

## Pruebas y verificacion

Comandos de referencia:

```powershell
cmd.exe /c mvnw.cmd test
cmd.exe /c mvnw.cmd package -DskipTests
```

Las pruebas automatizadas utilizan H2 en memoria mediante `src/test/resources/application.properties`, por lo que no modifican la base de datos productiva.

## Despliegue

El backend esta preparado para ejecutarse en Railway. El servicio toma el puerto desde la variable `PORT` y la conexion a PostgreSQL desde las variables de entorno de la plataforma.

La aplicacion mantiene separada la configuracion sensible del codigo fuente mediante variables de entorno, especialmente credenciales de base de datos y secreto JWT.
