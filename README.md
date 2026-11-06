# Ribolovački shop

[![CI](https://github.com/MaricVuk/fishing-shop/actions/workflows/ci.yml/badge.svg)](https://github.com/MaricVuk/fishing-shop/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791)
[![Licenca: MIT](https://img.shields.io/badge/Licenca-MIT-yellow.svg)](LICENSE)

Sistem za malu firmu koja prodaje ribolovačku opremu: web shop, magacin, zaposleni i uloge, pozadinski poslovi i izveštaji.

## O projektu

Portfolio projekat, rađen solo, fazu po fazu. Svaka faza se završava tako da posle nje nešto radi, a svaka veća odluka je zapisana u [dokumentaciji](#dokumentacija).

Težište projekta:

- **tačne zalihe** i pod istovremenim porudžbinama ("dva kupca, poslednji komad")
- **pravila u samoj bazi**: ograničenja i verzionisane Flyway migracije
- **testovi sa pravim PostgreSQL-om** (Testcontainers), a ne sa H2

## Stack

| Sloj | Tehnologija |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA |
| Baza | PostgreSQL 16, Flyway migracije |
| Frontend | TypeScript, Next.js 16, Tailwind CSS 4, shadcn/ui |
| Red poslova | RabbitMQ (Faza 4) |
| Testovi | JUnit 6, Testcontainers, Vitest |
| Infrastruktura | Docker, GitHub Actions |

## Arhitektura

```
[Shop (Next.js)]  ──►  [Spring Boot API]  ──►  [PostgreSQL]
[Admin panel]     ──►        │
                             ▼
                       [RabbitMQ red]  ──►  [Worker: mejl, PDF račun, magacin]
```

Spring Boot je jedini backend i drži celu poslovnu logiku (zalihe, porudžbine, cene). Frontend samo prikazuje podatke. Trenutno postoje Next.js shop, Spring Boot API i PostgreSQL, a admin panel i RabbitMQ dolaze po fazama.

## Status

| Faza | Opis | Stanje |
|---|---|---|
| 0 | Priprema: skelet, baza, migracije, CI | Gotova |
| 1 | Katalog i porudžbina | Gotova |
| 2 | Magacin i konkurentnost | Gotova |
| 3 | Zaposleni, uloge, audit log | Sledeća |
| 4 | Pozadinski poslovi | Planirana |
| 5 | Izveštaji, dashboard, uživo obaveštenja | Planirana |

Trenutno radi: katalog sa pretragom, filterom po kategoriji, sortiranjem i paginacijom, stranica proizvoda sa varijantama i dostupnošću, korpa i slanje porudžbine (bez plaćanja). Zaliha se proverava i skida pri porudžbini pod zaključavanjem reda, pa ni 50 istovremenih kupaca ne mogu da kupe više nego što ima, a svaka promena zalihe ostaje u istoriji ([ADR 0003](docs/adr/0003-pessimistic-locking-for-stock.md)).

Detalji svake faze su u [planu razvoja](docs/ROADMAP.md).

## Pokretanje

Potrebno: JDK 21 i Docker. Maven nije potreban, projekat ima Maven wrapper (`mvnw`).

```bash
# 1. baza
docker compose up -d

# 2. backend (profil dev za lokalni rad, vidi ispod)
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# 3. frontend (u drugom terminalu, potreban je Node.js 22.12 ili noviji)
cd frontend
npm install
npm run dev

# provera
curl http://localhost:8080/actuator/health
curl "http://localhost:8080/api/products?size=3&sort=price_desc"
```

Shop je na http://localhost:3000, a dokumentacija API-ja (Swagger UI) na http://localhost:8080/swagger-ui.html.

Profil `dev` je potreban za lokalni rad: daje razvojne vrednosti za bazu (iste kao u `docker-compose.yml`), učitava demo proizvode i uključuje Swagger UI. Bez njega backend traži `DB_URL`, `DB_USER` i `DB_PASSWORD` iz okruženja i ne izlaže Swagger, što je ponašanje za produkciju.

Fajl `.env` čita samo `docker compose`. Backend čita promenljive okruženja (npr. `set -a; source .env; set +a` pre pokretanja), a frontend čita `frontend/.env.local` (`cp frontend/.env.example frontend/.env.local`). Adresa backenda `BACKEND_URL` za `/api/*` proxy se ugrađuje pri build-u, pa se za produkciju zadaje pri `npm run build`.

Testovi (zahtevaju Docker, koriste Testcontainers sa pravim PostgreSQL-om):

```bash
(cd backend && ./mvnw verify)
(cd frontend && npm test)
```

## Struktura repoa

```
fishing-shop/
├── backend/            Spring Boot aplikacija (paket rs.fishingshop)
│   └── src/main/resources/db/migration/   Flyway migracije
├── frontend/           Next.js aplikacija
├── docs/               plan razvoja i zapisi odluka
├── .github/workflows/  CI
├── docker-compose.yml  PostgreSQL za razvoj
├── .env.example        razvojne vrednosti env varijabli
└── LICENSE             MIT licenca
```

## Dokumentacija

- [Plan razvoja](docs/ROADMAP.md): faze, ciljevi i tehničke teme svake faze
- [Odluke](docs/DECISIONS.md): šta je izabrano i zašto

## Licenca

[MIT](LICENSE)
