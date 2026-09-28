# DPDMS project defense script

## 1. Introduction

Good morning. Our project is the Rushinga Provincial Disaster Monitoring and Management System. It replaces paper, phone, and spreadsheet reporting with a role-aware web system for capturing, reviewing, visualizing, and reporting disaster incidents. It covers floods, droughts, fires, zoonotic diseases, and mining accidents.

## 2. Architecture and technology

The backend uses Java 21 and Spring Boot. Each of the five hazard types has an independent service and database schema. Supporting services handle authentication, service discovery, API routing, alerts, reporting, and the dashboard. Services communicate through REST and service discovery. MySQL stores the incident data. The user interface uses Thymeleaf, which keeps the dashboard in the Java application and avoids a separate frontend build. Docker Compose starts the application and its supporting observability services.

## 3. Demonstration flow

I sign in as a ward recorder and submit an incident with its shared location and severity details plus the required indicators for that hazard. The record begins in `PENDING`. I sign in as the supervisor for that same hazard and approve it. I then open the dashboard as a National user and show that the approved incident appears in the counts, recent records, map, and reports. Pending records are kept out of National views, the dashboard, and reports.

## 4. Hazard-specific data

Every incident uses consistent ward, district, province, occurrence time, reporter, severity, status, and GPS coordinates. Each hazard service also validates its five required indicators. For example, a flood records peak water level, catchment, displaced households, flooded area, and inundation duration. This structure lets services keep hazard-specific data while supporting common dashboards and reports.

## 5. Security and approval

Authentication issues signed JWTs and passwords are stored as BCrypt hashes. Authorization is enforced by the backend APIs, not only by hiding interface controls. In assignment-aligned mode, a recorder is limited to one ward and one hazard, and a supervisor can review only that hazard. National users can read approved records across hazards but cannot change them. Provincial Admin reads are limited to approved records in the assigned province and do not include user administration or approval actions. Every state transition is recorded in an audit trail.

The previous all-hazard Recorder/Supervisor demo mode is optional and off by default. A configuration flag enables it only when demonstrating that behavior; the same flag is checked by authentication and the hazard services. Provincial Admin has an additional province-wide, approved-only view across hazards, as requested for this project; the brief itself reserves the nationwide all-hazard view for National users.

## 6. Alerts, reports, and monitoring

The alert service records each delivery attempt with its channel, recipient, timestamp, status, and failure detail. Email and WhatsApp delivery depend on valid external provider credentials and connectivity, which are configured through environment variables rather than committed to GitHub. The report service generates PDF, DOCX, XLSX, and CSV files with hazard and location filters. Actuator health endpoints and centralized logs support operational checks.

## 7. Testing and close

The project includes unit tests for hazard scoping and approval behavior, plus API integration tests. We also built the Docker images and started the Compose services. The project demonstrates microservice decomposition, role-based controls, hazard workflows, aggregation, reporting, and external alert integration. Thank you; I’m ready for questions.

## Likely questions

- **Why use separate hazard services?** Each hazard has its own indicators and can be built and deployed independently while keeping shared metadata consistent.
- **How do you prevent an unapproved report from appearing?** The API applies approval-state filters on the server, and the dashboard, map, and report queries consume those approved-only results.
- **How is cross-hazard access controlled?** National has read-only cross-hazard access. Recorder and Supervisor accounts are hazard-scoped by default, and the APIs enforce those scopes.
- **What happens if an email or WhatsApp provider is unavailable?** The incident workflow remains separate from delivery; the alert attempt and failure details are logged so delivery issues do not block incident capture.
- **What is needed for public deployment?** A public domain, valid HTTPS DNS routing, production secrets, and a publicly reachable WhatsApp webhook endpoint.
