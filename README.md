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

`alert-service` is independently deployable on port 8087. Its `POST /api/alerts` endpoint requires a signed bearer token from an authorized supervisor or provincial administrator, persists each queued email or WhatsApp alert, then attempts delivery asynchronously. WhatsApp provider message IDs are associated with alert records; signed Meta callbacks update WhatsApp statuses to `DELIVERED`, `READ`, or `DELIVERY_FAILED`. A `SENT` status means the provider accepted the request. Email remains `SENT` because SMTP delivery receipts are not available in this adapter. Approved incidents trigger alerts for floods above the configured danger threshold, active fires, zoonotic clusters/outbreaks, and mining incidents with trapped/injured miners or fatalities. Flood threshold defaults to 2.0 metres and can be changed with `DPDMS_FLOOD_DANGER_LEVEL_METRES`. Configure provider credentials and recipient addresses through environment variables; none are stored as defaults in the repository.

## Required local software

| Tool | Status on this computer | Action |
| --- | --- | --- |
| Java 21+ | Java 26 is installed | Ready for running services directly |
| Maven | Maven 3.9.16 is installed | Ready for building and running services directly |
| Git | Installed; repository has an `origin` remote | Commit and push reviewed changes before submission |
| Docker Desktop + Compose | Installed; all 11 images built and the stack started locally | Ready for the containerized workflow below |
| MySQL Server + `mysql` command | Needed only for running services directly on Windows | Not needed when using Compose; the stack starts MySQL |
| VS Code | Installed | Ready |
| Node.js / npm | Not required | The Thymeleaf UI does not use a JavaScript build tool |
| Postman or Insomnia | Optional | Import `postman/DPDMS.postman_collection.json` to exercise the APIs |

## Run locally with Docker Compose

This is the recommended way to run the complete system on Windows. Compose starts MySQL, all five hazard services, authentication, alerts, reports, the dashboard, gateway, discovery, Caddy, and the logging services.

1. Start Docker Desktop and enable **Host networking** under **Settings → Resources → Network** so image builds can download Maven dependencies.
2. From the repository root, copy `.env.example` to `.env`. Replace each password and secret placeholder with a unique value. This deployment disables demo users and creates the National account from `DPDMS_BOOTSTRAP_ADMIN_USERNAME` and `DPDMS_BOOTSTRAP_ADMIN_PASSWORD` in `.env`.
3. Build and start the stack:

   ```powershell
   docker compose --env-file .env -f deploy/compose.yml up -d --build
   docker compose --env-file .env -f deploy/compose.yml ps
   ```

4. Open `http://localhost` and sign in with the bootstrap National username/password you set in `.env`. Use that account to create other users. Do not paste passwords or provider tokens into source files or Git.
5. Follow service logs with `docker compose --env-file .env -f deploy/compose.yml logs -f`; press `Ctrl+C` to stop following logs without stopping services.

Grafana is bound to `127.0.0.1:3000`; sign in as `admin` with `GRAFANA_ADMIN_PASSWORD` from `.env`. Stop the stack with `docker compose --env-file .env -f deploy/compose.yml down`. This keeps database data in its named volume; do not add `-v` unless you intend to delete that data.

## MySQL command-line setup

1. Open Command Prompt or the MySQL shell and create the separate schemas:

   ```powershell
   mysql -u root -p -e "source database/create-schemas.sql"
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
   mysql -u root -p -e "source database/seed-example.sql"
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

For the manual Maven workflow, open `http://localhost:8090` for the dashboard and `http://localhost:8761` for Eureka. Swagger UI is at `/swagger-ui/index.html` on each hazard service (ports 8081-8085), auth-service (8086), alert-service (8087), and report-service (8088). Health and basic metrics are available through Actuator. These direct service ports are not published by Compose; use `http://localhost` for the containerized workflow.

## Security and workflow

JWTs need `sub`, `role`, and `hazard`; recorder tokens also include `ward`, and provincial administrator tokens include `province`. Each hazard API checks role, hazard, ward, and incident ownership on the server. National users can read approved records across hazards but cannot write. A National administrator may assign a recorder the `ALL` hazard scope; that recorder can submit and read their own incidents across hazard types only within their assigned ward, but cannot approve incidents. Supervisors may be scoped to one hazard or assigned `ALL` to review incidents across hazards. Provincial administrators have an `ALL` hazard scope, but incident reads are restricted server-side to approved records whose province matches their assigned province. They can view that scoped data on the map, and queue alerts for every hazard. Provincial administrators cannot create, edit, or administer user accounts; account creation remains National-only. They cannot review incident approvals.

### Local Maven demonstration accounts

When demo seeding is enabled for the Maven workflow, all seeded accounts use the temporary password `ChangeMe123!` for local marking only. Compose disables these accounts and instead uses the bootstrap National credentials in `.env`.

| Username | Role | Scope |
| --- | --- | --- |
| `national@dpdms.local` | NATIONAL | Approved records across all hazards; read only |
| `recorder.ward1` / `supervisor` | RECORDER / SUPERVISOR | ALL, Rushinga Ward 1 / ALL hazards approval |
| `provincial.admin` | PROVINCIAL_ADMIN | ALL hazards; approved records and map limited to Mashonaland Central; can queue all-hazard alerts |
| `flood.recorder.ward1` / `flood.supervisor` | RECORDER / SUPERVISOR | FLOOD, Rushinga Ward 1 / FLOOD approval |
| `drought.recorder.ward1` / `drought.supervisor` | RECORDER / SUPERVISOR | DROUGHT, Rushinga Ward 1 / DROUGHT approval |
| `fire.recorder.ward1` / `fire.supervisor` | RECORDER / SUPERVISOR | FIRE, Rushinga Ward 1 / FIRE approval |
| `zoonotic.recorder.ward1` / `zoonotic.supervisor` | RECORDER / SUPERVISOR | ZOONOTIC, Rushinga Ward 1 / ZOONOTIC approval |
| `mining.recorder.ward1` / `mining.supervisor` | RECORDER / SUPERVISOR | MINING, Rushinga Ward 1 / MINING approval |

- `RECORDER`: creates incidents only for their assigned ward. The normal scope is one hazard; a National-created `ALL` scope permits submissions across all hazards while keeping the ward restriction. Reporter identity comes from the signed token. They can read their own records and edit pending/correction-requested records; corrected records return to `PENDING`.
- `SUPERVISOR`: reads and reviews incidents for their assigned hazard, or all hazards when assigned `ALL`. Only `PENDING` incidents can be reviewed; rejection requires a reason.
- `PROVINCIAL_ADMIN`: read-only access to approved incidents in the province claim across all hazards, plus map view and all-hazard alert management. Cannot approve incidents or manage user accounts.
- `NATIONAL`: read-only for incident data across hazards and sees only approved incidents. Incident create/update/delete/approval attempts return `403 Forbidden`. The National account is also permitted to provision scoped user accounts through the separate account-administration endpoint.
- **Assignment scope note:** the brief requires ward recorders and provincial hazard supervisors to be limited to one hazard, and reserves cross-hazard visibility for NATIONAL. At your request, this build also provides cross-hazard recorder/supervisor demo accounts and an all-hazard Provincial Admin account with province-scoped approved-only reads. These role-scope choices are enforced by the backend, but they intentionally differ from the assignment's strict single-hazard/single-cross-hazard-role rules. For strict rubric alignment, use the hazard-specific recorder/supervisor accounts and reserve cross-hazard visibility for NATIONAL. Account provisioning is a separate National-only administration feature; it does not grant incident write or approval access.
- Create, edit, delete, and transition actions add audit records. Pending incidents are excluded from national lists, dashboards, and maps.
- National administrators can create scoped users with `POST /auth/users`; the endpoint hashes passwords with BCrypt and accepts the four supported roles. In the local demo, seeded accounts share the documented temporary password. The VPS Compose deployment disables those demo accounts and creates only the unique bootstrap National account set in `.env`.

Example National-only user provisioning request (use the admin bearer token):

```http
POST /auth/users
Authorization: Bearer <national-admin-token>
Content-Type: application/json

{"username":"provincial@example.org","password":"a-unique-password-of-12-or-more-characters","role":"PROVINCIAL_ADMIN","hazard":"ALL","province":"Mashonaland Central"}
```

The Postman collection includes a cross-hazard request intended to verify the required `403` response for hazard-scoped accounts. The auth-service issues signed JWTs from BCrypt password hashes; the hazard API integration tests use signed test tokens and an isolated H2 database.

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

Set these variables before starting `alert-service`. Configure real recipient addresses locally; the project does not supply recipient defaults:

```powershell
$env:DPDMS_ALERT_EMAIL="recipient@example.org"
$env:DPDMS_ALERT_WHATSAPP_TO="+263XXXXXXXXX"
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

To create a manual alert, first sign in through `POST http://localhost:8080/auth/login` as a supervisor or provincial administrator and use the returned bearer token. Then call `POST http://localhost:8080/alerts/api/alerts` with JSON fields `hazard` (`FLOOD`, `DROUGHT`, `FIRE`, `ZOONOTIC`, or `MINING`), `channel` (`EMAIL` or `WHATSAPP`), and `message`; `recipient` is optional and defaults to the recipient configured in the environment. Supervisors may queue alerts within their hazard scope; Provincial Admin can queue for any hazard. The service records each attempt. Provider credentials and recipient details are intentionally omitted from the repository; actual delivery requires valid provider settings and a recipient.

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

The full Compose stack has been built and started locally. Public HTTPS and the WhatsApp webhook still require a public domain and VPS; those cannot be verified on `localhost`. MySQL initialization scripts run automatically only when Compose creates a fresh database volume; back up existing data before replacing or removing any production volume.

Never commit `.env`, database passwords, JWT secrets, email keys or WhatsApp tokens.
