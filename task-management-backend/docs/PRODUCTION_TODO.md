# Production Readiness TODO

> Datum: 23 juli 2026  
> Status: Work in Progress

---

## 🔴 Kritiek (Blokkerend voor productie)

- [ ] **1. Persistente database activeren**
  - MSSQL configuratie is al voorbereid
  - Volg instructies in `task-management-backend/docs/how-to-switch-to-mssql.md`
  - Geschatte inspanning: 1-2 uur

- [ ] **2. Authenticatie/Autorisatie implementeren**
  - OAuth2/OIDC integratie (bijv. Quarkus OIDC extension)
  - Role-based access control (RBAC)
  - JWT token validatie
  - User identity valideren bij task operaties
  - Geschatte inspanning: 8-12 uur

---

## 🟠 Hoge Prioriteit (Voor go-live)

- [ ] **3. Error logging verbeteren**
  - Bestand: `ThrowableExceptionMapper.java`
  - Stack traces toevoegen aan error logging
  - Geschatte inspanning: 15 min

- [ ] **4. Idempotency tokens implementeren**
  - `X-Idempotency-Key` header support toevoegen
  - Retry-veilige API calls mogelijk maken
  - Geschatte inspanning: 2-4 uur

- [ ] **5. Graceful shutdown implementeren**
  - Quartz scheduler shutdown coördineren
  - Axon Framework shutdown orchestreren
  - `ShutdownEvent` handler implementeren
  - Geschatte inspanning: 1-2 uur

- [ ] **6. CORS productie-configuratie**
  - Environment-specifieke CORS origins
  - Verwijder hardcoded localhost waarden voor productie
  - Geschatte inspanning: 30 min

---

## 🟡 Medium Prioriteit (Nice to have)

- [ ] **7. Custom health checks toevoegen**
  - Axon Framework status check
  - Saga status check
  - Database connectivity check
  - Endpoint: `/q/health`
  - Geschatte inspanning: 30 min

- [ ] **8. Swagger UI activeren**
  - `quarkus-smallrye-openapi` dependency toevoegen
  - Swagger UI beschikbaar maken op `/q/swagger-ui`
  - API documentatie verbeteren
  - Geschatte inspanning: 2-3 uur

- [ ] **9. Rate limiting implementeren**
  - Bescherming tegen overbelasting
  - Per-client rate limits
  - Geschatte inspanning: 2-4 uur

- [ ] **10. Backup/restore procedures documenteren**
  - MSSQL backup strategie
  - Event store recovery procedures
  - Disaster recovery plan
  - Geschatte inspanning: 2-4 uur

---

## ✅ Reeds Voltooid

- [x] OpenAPI specificatie (`task-management-api.yaml`)
- [x] Bean validation op request models
- [x] HTTP body size limit (1M)
- [x] Flyway database migraties
- [x] Prometheus metrics (`/q/metrics`)
- [x] OpenTelemetry basis configuratie
- [x] Docker multi-stage build met non-root user
- [x] Unit en integratie tests
- [x] MSSQL Flyway migraties voorbereid

---

## Tijdsinschatting

| Categorie | Items | Geschatte tijd |
|-----------|-------|----------------|
| Kritiek | 2 | 9-14 uur |
| Hoge prioriteit | 4 | 4-7 uur |
| Medium prioriteit | 4 | 5-11 uur |
| **Totaal** | **10** | **18-32 uur** |

---

## Referenties

- [Production Readiness Review](PRODUCTION_READINESS_REVIEW.md)
- [How to Switch to MSSQL](how-to-switch-to-mssql.md)
- [Architecture Documentation](architecture.md)

