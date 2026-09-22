# DPDMS Architecture and Demonstration Guide

```mermaid
flowchart LR
  U[Ward recorder / supervisor / national user] --> UI[Thymeleaf dashboard :8090]
  U --> G[API Gateway :8080]
  UI --> G
  G --> A[Auth service :8086]
  G --> F[Flood :8081]
  G --> D[Drought :8082]
  G --> FI[Fire :8083]
  G --> Z[Zoonotic :8084]
  G --> M[Mining :8085]
  F --> FDB[(dpdms_flood)]
  D --> DDB[(dpdms_drought)]
  FI --> FIDB[(dpdms_fire)]
  Z --> ZDB[(dpdms_zoonotic)]
  M --> MDB[(dpdms_mining)]
  A --> ADB[(dpdms_auth)]
  E[Eureka :8761] -. discovery .-> G
  E -. discovery .-> F
  E -. discovery .-> D
  E -. discovery .-> FI
  E -. discovery .-> Z
  E -. discovery .-> M
  E -. discovery .-> A
```

## Marker demonstration sequence

1. Open Eureka and show all services `UP`.
2. Sign in to the dashboard as `national@dpdms.local`; show only approved flood data and the map marker.
3. In Postman, login as `national@dpdms.local`, call `GET /flood/api/incidents`, and show `200 OK`.
4. Use the same token on `POST /flood/api/incidents`, and show `403 Forbidden`.
5. Login as `flood.recorder.ward1`, create a valid incident, and show its `PENDING` state.
6. Login as `flood.supervisor`, call the transition endpoint with `APPROVED`, then refresh the national dashboard.
