# Rushinga Provincial Disaster Monitoring and Management System (DPDMS)

DPDMS is a Spring Boot microservices implementation for recording, approving, mapping and reporting flood, drought, fire, zoonotic disease and mining-accident incidents.

## Stack choice

The backend is **Spring Boot 3**, with **MySQL** schemas administered through the MySQL command line. The UI is **Thymeleaf** (not React/Vue/Angular), selected because it is a lightweight server-rendered front end that meets the brief without requiring Node.js or a JavaScript build tool. The gateway is Spring Cloud Gateway and service discovery is Netflix Eureka. Services communicate over REST through the gateway. JWT bearer tokens are validated by each hazard service; a shared signing secret must be supplied as an environment variable in real deployment.

## Architecture

```text
Browser -> dashboard-service (8090) / API Gateway (8080) -> Eureka (8761)
                                                        -> flood-service (8081) -> dpdms_flood
                                                        -> drought-service (8082) -> dpdms_drought
                                                        -> fire-service (8083) -> dpdms_fire
                                                        -> zoonotic-service (8084) -> dpdms_zoonotic
                                                        -> mining-service (8085) -> dpdms_mining
```

Every hazard service is a separately packaged Spring Boot application. `hazard-core` deliberately contains only shared source code; it is not deployable and does not share a database. This preserves independent schemas while enforcing the same metadata and security rules everywhere.

`alert-service` is independently deployable on port 8087. Its `POST /api/alerts` endpoint persists every queued alert and sends it on a background thread so incident capture does not wait. In this classroom build the delivery outcome is recorded as `SIMULATED_DELIVERED`; production adapters must use `EMAIL_*` and `WHATSAPP_*` environment variables rather than committed credentials.

## Required local software

| Tool | Status on this computer | Action |
| --- | --- | --- |
| Java 21+ | Java 26 is installed | Ready |
| Maven | Maven 3.9.16 is installed | Ready |
| Git | Git is available through the bundled runtime | Configure GitHub account/repository |
| VS Code | Installed | Ready |
| MySQL Server + `mysql` command | Not detected | Install MySQL Community Server and add its `bin` folder to PATH |
| Node.js / npm | Node runtime is bundled for Codex, but `npm` is not detected in your normal PATH | Not needed for this Thymeleaf UI |
| Postman or Insomnia | Not detected from command line | Install either; import `postman/DPDMS.postman_collection.json` |

## MySQL command-line setup

1. Open Command Prompt or the MySQL shell and create the separate schemas:

   ```powershell
   mysql -u root -p < database/create-schemas.sql
   ```

2. Set credentials only in your terminal session, never in Git:

   ```powershell
   $env:MYSQL_USER="root"
   $env:MYSQL_PASSWORD="your-password"
   $env:DPDMS_JWT_SECRET="replace-with-a-long-random-at-least-32-character-secret"
   ```

3. Optionally add a demonstrator record after the flood service has started once (so tables exist):

   ```powershell
   mysql -u root -p < database/seed-example.sql
   ```

## Build and start

From this folder, build all service artifacts:

```powershell
mvn clean package
```

Start in this order, each in its own VS Code terminal:

```powershell
mvn -pl discovery-service spring-boot:run
mvn -pl flood-service spring-boot:run
mvn -pl drought-service spring-boot:run
mvn -pl fire-service spring-boot:run
mvn -pl zoonotic-disease-service spring-boot:run
mvn -pl mining-accident-service spring-boot:run
mvn -pl api-gateway spring-boot:run
mvn -pl dashboard-service spring-boot:run
mvn -pl alert-service spring-boot:run
```

Open `http://localhost:8090` for the dashboard, `http://localhost:8761` for Eureka and each service's `/swagger-ui/index.html` endpoint for its OpenAPI UI.

## Security and workflow

JWTs need `sub`, `role`, `hazard`, and, for recorders, `ward` claims. Each service independently verifies role and matching hazard claim before executing the endpoint; changing the gateway URL or UI does not bypass it.

### Demonstration accounts

All accounts below use the temporary password `ChangeMe123!` for local marking only. Change or remove them before deployment.

| Username | Role | Scope |
| --- | --- | --- |
| `national@dpdms.local` | NATIONAL | approved records across all hazards; read only |
| `flood.recorder.ward1` | RECORDER | FLOOD, Rushinga Ward 1 |
| `flood.supervisor` | SUPERVISOR | FLOOD approval workflow |
| `drought.recorder.ward1` | RECORDER | DROUGHT, Rushinga Ward 1 |
| `drought.supervisor` | SUPERVISOR | DROUGHT approval workflow |

- `RECORDER`: can create and edit only their own ward's records for exactly their assigned hazard. New records are `PENDING`.
- `SUPERVISOR`: can view and transition only their assigned hazard's records to `APPROVED`, `REJECTED`, or `CORRECTIONS_REQUESTED`.
- `NATIONAL`: read-only across hazards and sees only `APPROVED` records. Every write attempt returns `403 Forbidden`.
- Every create, edit and status transition adds an `incident_audit` row. Pending incidents are excluded from national list/dashboard queries.

The Postman collection includes a cross-hazard request intended to verify the required `403` response. For a complete submission, add an `auth-service` that issues these signed JWTs from password-hashed users (BCrypt), rather than manually minting test tokens.

## Hazard indicators

Each request includes the common metadata (`ward`, `district`, `province`, `occurredAt`, `reporter`, `severity`, `latitude`, `longitude`) plus an `indicators` JSON object. The backend rejects requests missing any mandatory keys.

| Service | Mandatory indicator keys |
| --- | --- |
| Flood | peakWaterLevelMetres, riverBasin, householdsDisplaced, areaFloodedHectares, inundationDurationDays |
| Drought | rainfallDeficitMm, consecutiveDryDays, cropFailurePercentage, peopleWaterShortage, livestockMortalityCount |
| Fire | areaBurnedHectares, suspectedCause, casualties, structuresDestroyed, fireStatus |
| Zoonotic | pathogenName, animalSpeciesAffected, confirmedHumanCases, confirmedAnimalCases, classification |
| Mining | mineNameAndType, accidentType, trappedOrInjuredMiners, fatalities, rescueOperationsOngoing |

## Remaining team work before submission

This clean foundation implements the five independently deployable CRUD/approval services, schemas, gateway, discovery, map page and security enforcement. The assignment brief also requires these production-grade modules, which should be completed by team members before submitting:

1. **auth-service** - MySQL users, BCrypt passwords, login and signed JWT issuing.
2. **report-service** - approved-only filtered CSV/XLSX/PDF/DOCX downloads.
3. **alert-service production adapters** - RabbitMQ/Kafka queue consumer plus real email/WhatsApp provider adapters using environment variables. The included service demonstrates non-blocking alert logging but does not send real messages.
4. Add end-to-end API tests, Docker Compose/HTTPS deployment configuration, and a checked-in architecture image for the presentation. See `docs/architecture.md` for the diagram and marker demonstration sequence.

Never commit `.env`, database passwords, JWT secrets, email keys or WhatsApp tokens.
