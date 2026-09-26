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

`alert-service` is independently deployable on port 8087. Its `POST /api/alerts` endpoint requires a signed bearer token from the matching hazard supervisor (or provincial administrator), persists each queued email or WhatsApp alert, then attempts delivery asynchronously. WhatsApp provider message IDs are associated with alert records; signed Meta callbacks update WhatsApp statuses to `DELIVERED`, `READ`, or `DELIVERY_FAILED`. A `SENT` status means the provider accepted the request. Email remains `SENT` because SMTP delivery receipts are not available in this adapter. Approved incidents trigger alerts for floods above the configured danger threshold, active fires, zoonotic clusters/outbreaks, and mining incidents with trapped/injured miners or fatalities. Flood threshold defaults to 2.0 metres and can be changed with `DPDMS_FLOOD_DANGER_LEVEL_METRES`. The default recipients are `groupof5pple@yahoo.com` and `+263781330055`. Configure provider credentials through environment variables; never commit them.

## Required local software

| Tool | Status on this computer | Action |
| --- | --- | --- |
| Java 21+ | Java 26 is installed | Ready |
| Maven | Maven 3.9.16 is installed | Ready |
| Git | Available; `origin` is configured | Commit and push reviewed changes before submission |
| VS Code | Installed | Ready |
| MySQL Server + `mysql` command | Required | Install MySQL Community Server; add its `bin` folder to PATH |
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
PowerShell process variables do not carry into other VS Code terminals. Set MYSQL_USER, MYSQL_PASSWORD, and the same DPDMS_JWT_SECRET in every service terminal before starting that service. Keep SMTP and WhatsApp secrets in the alert-service terminal only.

3. After all five hazard services have started once (so all tables exist), add one sample incident per hazard. The script uses fixed IDs and can be rerun safely:

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
mvn -pl auth-service spring-boot:run
mvn -pl flood-service spring-boot:run
mvn -pl drought-service spring-boot:run
mvn -pl fire-service spring-boot:run
mvn -pl zoonotic-disease-service spring-boot:run
mvn -pl mining-accident-service spring-boot:run
mvn -pl api-gateway spring-boot:run
mvn -pl dashboard-service spring-boot:run
mvn -pl alert-service spring-boot:run
mvn -pl report-service spring-boot:run
```

Open `http://localhost:8090` for the dashboard and `http://localhost:8761` for Eureka. Swagger UI is at `/swagger-ui/index.html` on each hazard service (ports 8081-8085), auth-service (8086), alert-service (8087), and report-service (8088). Health and basic metrics are available through Actuator.

## Security and workflow

JWTs need `sub`, `role`, `hazard`, and, for recorders, `ward` claims. Each hazard API checks role, hazard, ward, and incident ownership on the server. National users can read approved records across hazards but cannot write. Provincial administrators are scoped to one hazard; National is the only role with cross-hazard access. Alert creation requires the matching hazard supervisor or a provincial administrator scoped to that hazard.

### Demonstration accounts

All seeded accounts use the temporary password `ChangeMe123!` for local marking only. Change or remove them before deployment.

| Username | Role | Scope |
| --- | --- | --- |
| `national@dpdms.local` | NATIONAL | Approved records across all hazards; read only |
| `flood.recorder.ward1` / `flood.supervisor` / `flood.provincial.admin` | RECORDER / SUPERVISOR / PROVINCIAL_ADMIN | FLOOD, Rushinga Ward 1 / FLOOD approval / FLOOD administration |
| `drought.recorder.ward1` / `drought.supervisor` / `drought.provincial.admin` | RECORDER / SUPERVISOR / PROVINCIAL_ADMIN | DROUGHT, Rushinga Ward 1 / DROUGHT approval / DROUGHT administration |
| `fire.recorder.ward1` / `fire.supervisor` / `fire.provincial.admin` | RECORDER / SUPERVISOR / PROVINCIAL_ADMIN | FIRE, Rushinga Ward 1 / FIRE approval / FIRE administration |
| `zoonotic.recorder.ward1` / `zoonotic.supervisor` / `zoonotic.provincial.admin` | RECORDER / SUPERVISOR / PROVINCIAL_ADMIN | ZOONOTIC, Rushinga Ward 1 / ZOONOTIC approval / ZOONOTIC administration |
| `mining.recorder.ward1` / `mining.supervisor` / `mining.provincial.admin` | RECORDER / SUPERVISOR / PROVINCIAL_ADMIN | MINING, Rushinga Ward 1 / MINING approval / MINING administration |

- `RECORDER`: creates incidents only for their hazard and ward. Reporter identity comes from the signed token. They can read their own records and edit pending/correction-requested records; corrected records return to `PENDING`.
- `SUPERVISOR`: reads and reviews only their hazard's incidents. Only `PENDING` incidents can be reviewed; rejection requires a reason.
- `PROVINCIAL_ADMIN`: read access and alert-log access are limited to the single hazard in the signed token. Incident approvals remain supervisor actions.
- `NATIONAL`: read-only across hazards and sees only approved incidents. Every write attempt returns `403 Forbidden`.
- Create, edit, delete, and transition actions add audit records. Pending incidents are excluded from national lists, dashboards, and maps.
- National administrators can create scoped users with `POST /auth/users`; the endpoint hashes passwords with BCrypt and accepts the four supported roles. In the local demo, seeded accounts share the documented temporary password. The VPS Compose deployment disables those demo accounts and creates only the unique bootstrap National account set in `.env`.

Example National-only user provisioning request (use the admin bearer token):

```http
POST /auth/users
Authorization: Bearer <national-admin-token>
Content-Type: application/json

{"username":"flood.recorder@example.org","password":"a-unique-password-of-12-or-more-characters","role":"RECORDER","hazard":"FLOOD","ward":"Rushinga Ward 2"}
```

The Postman collection includes a cross-hazard request intended to verify the required `403` response. The auth-service issues signed JWTs from BCrypt password hashes; the hazard API integration tests use signed test tokens and an isolated H2 database.

## Hazard indicators

Each request includes the common metadata (`ward`, `district`, `province`, `occurredAt`, `severity`, `latitude`, `longitude`) plus an `indicators` JSON object. The server derives `reporter` from the authenticated user. The backend validates required indicators, numeric ranges/counts, and defined categorical values.

| Service | Mandatory indicator keys |
| --- | --- |
| Flood | peakWaterLevelMetres, riverBasin, householdsDisplaced, areaFloodedHectares, inundationDurationDays |
| Drought | rainfallDeficitMm, consecutiveDryDays, cropFailurePercentage, peopleWaterShortage, livestockMortalityCount |
| Fire | areaBurnedHectares, suspectedCause, casualties, structuresDestroyed, fireStatus |
| Zoonotic | pathogenName, animalSpeciesAffected, confirmedHumanCases, confirmedAnimalCases, classification |
| Mining | mineNameAndType, accidentType, trappedOrInjuredMiners, fatalities, rescueOperationsOngoing |

## Submission status and remaining work

The project includes MySQL-backed users with BCrypt password hashes and signed JWT login, role-scoped CSV/XLSX/DOCX/PDF report downloads with approval-status filters, the five independently deployable hazard services, gateway, discovery, auditing, and a role-based dashboard. Recorders can submit incidents, supervisors can approve/reject/request corrections, and national users can view approved incidents and download reports.

Remaining work for production deployment or group submission:

1. **Public deployment setup** - provide a Linux VPS and domain, point DNS to the VPS, and configure the secrets in `.env` before bringing up `deploy/compose.yml`.
2. **Full-stack acceptance check** - run the Compose stack on the VPS and verify MySQL-backed workflows, HTTPS, logs, and the WhatsApp webhook end to end. Docker is not installed on the development computer, so that deployment check has not run here.
3. **Human deliverables** - complete the group presentation and peer evaluation form; export the Mermaid diagram in `docs/architecture.md` if the marker cannot render Mermaid.

## Report downloads

Start `report-service`, sign in through `auth-service`, and use the returned bearer token with one of these approved-only report endpoints:

```text
GET http://localhost:8080/reports/api/reports/csv?fromDate=2026-09-01&toDate=2026-09-30&status=APPROVED
GET http://localhost:8080/reports/api/reports/xlsx?hazard=flood&status=APPROVED
GET http://localhost:8080/reports/api/reports/docx?district=Rushinga&status=APPROVED
GET http://localhost:8080/reports/api/reports/pdf?severity=HIGH&status=APPROVED
```

Filters are `hazard`, `ward`, `district`, `severity`, `fromDate`, `toDate`, and `status` (`PENDING`, `APPROVED`, `REJECTED`, or `CORRECTIONS_REQUESTED`). Dates are inclusive UTC calendar days. Status defaults to `APPROVED`. The service forwards the caller token to hazard services, so each service enforces the caller's access scope. Reports include the hazard-specific indicators for each incident.

### Alert provider configuration

Set these variables before starting `alert-service` (the recipient defaults above can be overridden):

```powershell
$env:DPDMS_ALERT_EMAIL="groupof5pple@yahoo.com"
$env:DPDMS_ALERT_WHATSAPP_TO="+263781330055"
$env:SMTP_HOST="smtp.example.com"
$env:SMTP_PORT="587"
$env:SMTP_USERNAME="your-sender@example.com"
$env:SMTP_PASSWORD="your-smtp-app-password"
$env:SMTP_AUTH="true"
$env:SMTP_STARTTLS="true"
$env:WHATSAPP_ACCESS_TOKEN="your-meta-cloud-api-token"
$env:WHATSAPP_PHONE_NUMBER_ID="your-meta-phone-number-id"
$env:WHATSAPP_APP_SECRET="your-meta-app-secret"
$env:WHATSAPP_WEBHOOK_VERIFY_TOKEN="a-random-value-you-choose"
$env:DPDMS_FLOOD_DANGER_LEVEL_METRES="2.0"
```

To create a manual alert, first sign in through `POST http://localhost:8080/auth/login` as a supervisor and use the returned bearer token. Then call `POST http://localhost:8080/alerts/api/alerts` with JSON fields `hazard` (the supervisor's hazard), `channel` (`EMAIL` or `WHATSAPP`), and `message`; `recipient` is optional and defaults to the configured recipient. Only the matching supervisor or provincial administrator can queue an alert. The service records each attempt. Provider credentials are not included in the repository, so actual delivery requires valid provider settings.

For WhatsApp delivery receipts, configure a public Meta webhook callback at `https://<your-domain>/alerts/api/webhooks/whatsapp`, subscribe to the `messages` webhook field, set `WHATSAPP_APP_SECRET` to the Meta app secret, and use the same locally chosen value for `WHATSAPP_WEBHOOK_VERIFY_TOKEN` in both this service and Meta's verification form. The receiver verifies `X-Hub-Signature-256` against the raw request body before changing alert status. Do not put either secret in Git or send it in chat.

## VPS deployment, HTTPS, and central logs

The production stack definition is in `deploy/compose.yml`. It builds each Spring Boot module, creates the seven MySQL schemas, uses a restricted `dpdms_app` database account, sends service logs to Fluent Bit and Loki, provisions Loki as Grafana's log source, and exposes the dashboard/API only through Caddy. Grafana is bound to the VPS loopback address on port 3000 so it can be reached through an SSH tunnel.

1. Obtain a Linux VPS and a domain. Point the domain's DNS A record to the VPS public IP; allow inbound TCP ports 80 and 443.
2. Install Docker Engine and the Docker Compose plugin. Clone this repository on the VPS.
3. Copy `.env.example` to `.env`. Replace every placeholder with unique random secrets, set `DPDMS_DOMAIN` to the real domain, choose the bootstrap National username/password, and add the SMTP and Meta values required for delivery. Restrict `.env` to the deploy user (`chmod 600 .env`). Never commit `.env`.
4. Start the stack from the repository root:

   ```sh
   docker compose --env-file .env -f deploy/compose.yml up -d --build
   docker compose --env-file .env -f deploy/compose.yml ps
   ```

5. Open `https://<your-domain>` and sign in with the bootstrap National account. Caddy obtains and renews a public TLS certificate when DNS points to the VPS and the HTTP/HTTPS ports are reachable. Use the National account to create individual recorders, supervisors, and provincial administrators through `POST /auth/users`; then remove the bootstrap password from `.env`. Update Meta's webhook callback URL to `https://<your-domain>/alerts/api/webhooks/whatsapp` and verify it with the matching token in `.env`.
6. To inspect central logs, create an SSH tunnel with `ssh -L 3000:127.0.0.1:3000 <user>@<vps-address>`, then open `http://localhost:3000` and sign in as `admin` with `GRAFANA_ADMIN_PASSWORD` from `.env`.

The current development computer has no Docker CLI, and no public domain/VPS has been supplied. The deployment files are prepared, but the Compose stack and public certificate cannot be launched or verified on the VPS until those are available. MySQL initialization scripts run automatically only when Compose creates a fresh database volume; back up existing data before replacing or removing any production volume.

Never commit `.env`, database passwords, JWT secrets, email keys or WhatsApp tokens.
