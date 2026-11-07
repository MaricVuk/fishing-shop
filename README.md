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
| Red poslova | tabela u PostgreSQL-u, worker u Spring Boot-u (Faza 4) |
| Testovi | JUnit 6, Testcontainers, Vitest |
| Infrastruktura | Docker, GitHub Actions |

## Arhitektura

```
[Shop (Next.js)] ─┐
                  ├──►  [Spring Boot: API + worker (mejl, PDF)]  ──►  [PostgreSQL: podaci + tabela poslova]
[Admin panel]    ─┘
```

Spring Boot je jedini backend i drži celu poslovnu logiku (zalihe, porudžbine, cene). Frontend samo prikazuje podatke. Postoje Next.js shop sa admin panelom, Spring Boot API sa workerom za pozadinske poslove ([ADR 0006](docs/adr/0006-job-queue-in-postgres.md)) i PostgreSQL. Admin dobija uživo obaveštenja (nova porudžbina, poslednji komad) kroz Server-Sent Events ([ADR 0007](docs/adr/0007-admin-live-events-with-sse.md)).

## Status

| Faza | Opis | Stanje |
|---|---|---|
| 0 | Priprema: skelet, baza, migracije, CI | Gotova |
| 1 | Katalog i porudžbina | Gotova |
| 2 | Magacin i konkurentnost | Gotova |
| 3 | Zaposleni, uloge, audit log | Gotova |
| 4 | Pozadinski poslovi | Gotova |
| 5 | Izveštaji, dashboard, uživo obaveštenja | Gotova |
| 6 | Priprema za deploy (pravi deploy je odložen, [ADR 0008](docs/adr/0008-deploy-postponed.md)) | U toku |

Trenutno radi: katalog sa pretragom, filterom po kategoriji, sortiranjem i paginacijom, stranica proizvoda sa varijantama i dostupnošću, korpa i slanje porudžbine (bez plaćanja). Zaliha se proverava i skida pri porudžbini pod zaključavanjem reda, pa ni 50 istovremenih kupaca ne mogu da kupe više nego što ima, a svaka promena zalihe ostaje u istoriji ([ADR 0003](docs/adr/0003-pessimistic-locking-for-stock.md)). Zaposleni se prijavljuju na `/admin` (sesija u bazi, uloge vlasnik, prodavac i magacioner), vode porudžbine i magacin, a svaka izmena ostaje u audit logu koji se ne može menjati ni brisati ([ADR 0004](docs/adr/0004-employee-login-with-server-sessions.md), [ADR 0005](docs/adr/0005-append-only-audit-log.md)). Kupac dobija mejl potvrde, a kad prodavac potvrdi porudžbinu nastaje PDF račun sa brojem bez rupa i stiže mejlom. Mejlovi i računi idu kroz red poslova u Postgresu sa ponovnim pokušajima, a vlasnik u adminu vidi poslove koji su odustali i ponovo ih pokreće ([ADR 0006](docs/adr/0006-job-queue-in-postgres.md)). Vlasnik u adminu vidi prodaju po danima, nedeljama i mesecima sa grafikonom i najprodavanije artikle, a vlasnik i magacioner vide varijante kojima ponestaje zalihe. Nova porudžbina i prodat poslednji komad pojavljuju se u adminu odmah, bez osvežavanja stranice. Pregled u adminu je dashboard po ulozi: vlasnik vidi prihod i porudžbine koje čekaju, prodavac porudžbine, a magacioner niske zalihe. Izveštaji za 5 godina (547k porudžbina, 1M stavki) traju ispod pola sekunde bez keša ([prodaja](docs/perf/sales-report.md), [najprodavaniji](docs/perf/product-reports.md)).

## Opterećenje

k6 (kroz Docker) nad bazom sa 547.500 porudžbina i 1M stavki, jedna instanca backenda, sve na jednoj mašini ([detalji i zamke](docs/perf/load-test.md)):

| Scenario | Opterećenje | Zahteva/s | p95 | Greške |
|---|---|---|---|---|
| Katalog | 50 korisnika | 3.000 | 35,5 ms | 0 % |
| Porudžbine, jedna varijanta sa 100 komada | 50 korisnika | 1.019 | 85,6 ms | 0 % |
| Porudžbine, rasute po katalogu | 30 korisnika | 630 | 72,2 ms | 0 % |
| Izveštaji (pregled, prodaja, najprodavaniji, niske zalihe) | 20 korisnika bez pauze | 41 | 630 ms | 0 % |

Pod 50 istovremenih kupaca za varijantu sa 100 komada prošlo je tačno **100** porudžbina (30.562 je dobilo `422`), zaliha je ostala 0, a baza nema nijedan red ispod nule.

Detalji svake faze su u [planu razvoja](docs/ROADMAP.md).

## Pokretanje

Potrebno: JDK 21 i Docker. Maven nije potreban, projekat ima Maven wrapper (`mvnw`).

```bash
# 1. baza i Mailpit (hvata mejlove u razvoju)
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

Shop je na http://localhost:3000, dokumentacija API-ja (Swagger UI) na http://localhost:8080/swagger-ui.html, a svi mejlovi iz razvoja (potvrde i računi) u Mailpit-u na http://localhost:8025. Mailpit ništa ne šalje napolje.

Profil `dev` je potreban za lokalni rad: daje razvojne vrednosti za bazu (iste kao u `docker-compose.yml`), učitava demo proizvode i uključuje Swagger UI. Bez njega backend traži `DB_URL`, `DB_USER` i `DB_PASSWORD`, mejl server (`MAIL_HOST`, `SHOP_MAIL_FROM` i ostalo) i podatke prodavca za račun (`SHOP_SELLER_*`) iz okruženja (spisak je u `.env.example`) i ne izlaže Swagger, što je ponašanje za produkciju.

Profil `dev` pravi i po jedan nalog zaposlenog za svaku ulogu. Ovi nalozi postoje samo u razvojnoj bazi:

| Uloga | Email | Lozinka |
|---|---|---|
| Vlasnik | `vlasnik@ribolov.test` | `dev-vlasnik-lozinka` |
| Prodavac | `prodavac@ribolov.test` | `dev-prodavac-lozinka` |
| Magacioner | `magacioner@ribolov.test` | `dev-magacioner-lozinka` |

Bez profila `dev` prvi vlasnik se pravi iz `SHOP_OWNER_EMAIL` i `SHOP_OWNER_PASSWORD` (najmanje 12 znakova), i to samo dok u bazi nema nijednog zaposlenog. Prijava radi preko sesije u bazi ([ADR 0004](docs/adr/0004-employee-login-with-server-sessions.md)).

Fajl `.env` čita samo `docker compose`. Backend čita promenljive okruženja (npr. `set -a; source .env; set +a` pre pokretanja), a frontend čita `frontend/.env.local` (`cp frontend/.env.example frontend/.env.local`). Adresa backenda `BACKEND_URL` za `/api/*` proxy (`frontend/src/proxy.ts`) se čita pri pokretanju, pa jedna slika radi u svakom okruženju.

Testovi (zahtevaju Docker, koriste Testcontainers sa pravim PostgreSQL-om):

```bash
(cd backend && ./mvnw verify)
(cd frontend && npm test)
```

### Ceo sistem jednom komandom (demo)

Potreban je samo Docker. Komanda gradi obe slike, podiže bazu, backend, frontend i Mailpit, i puni katalog demo proizvodima (profil `dev` ostaje isključen):

```bash
docker compose --env-file demo.env -f compose.prod.yml -f compose.demo.yml up --build -d --wait
```

Shop je na `http://localhost:3100`, admin na `/admin` (isti demo nalozi kao gore), a mejlovi koje shop šalje u Mailpitu na `http://localhost:8026`. Provera da sve radi:

```bash
scripts/smoke.sh
```

Zaustavljanje (podaci ostaju u volumenu `fishing-shop-prod_pgdata`):

```bash
docker compose --env-file demo.env -f compose.prod.yml -f compose.demo.yml down
```

Baza u tom steku ima tri naloga: superkorisnika samo za prvo pravljenje, `shop_migrator` za Flyway i `shop_app` za backend, koji ne može da menja audit log ni račune ni šemu ([RUNBOOK](docs/RUNBOOK.md)). Ako si pokretao stek pre ove izmene, ukloni stari volumen sa `docker volume rm fishing-shop-prod_pgdata`.

`demo.env` ima javne demo vrednosti i nalozi sa poznatim lozinkama, pa se ovaj način pokreće samo na lokalnoj mašini (portovi su vezani za `127.0.0.1`). `compose.prod.yml` sam traži prave vrednosti za bazu, mejl server i podatke prodavca (`.env.example`) i bez njih ne startuje. Pravi deploy je odložen ([ADR 0008](docs/adr/0008-deploy-postponed.md)).

## Struktura repoa

```
fishing-shop/
├── backend/            Spring Boot aplikacija (paket rs.fishingshop)
│   └── src/main/resources/db/migration/   Flyway migracije
├── frontend/           Next.js aplikacija
├── docs/               plan razvoja i zapisi odluka
├── .github/workflows/  CI
├── docker-compose.yml  PostgreSQL i Mailpit za razvoj
├── compose.prod.yml    baza, backend i frontend iz slika; compose.demo.yml i demo.env dodaju demo podatke i Mailpit
├── scripts/            smoke.sh: provera da pokrenut sistem radi
├── .env.example        razvojne vrednosti env varijabli
└── LICENSE             MIT licenca
```

## Dokumentacija

- [Plan razvoja](docs/ROADMAP.md): faze, ciljevi i tehničke teme svake faze
- [Odluke](docs/DECISIONS.md): šta je izabrano i zašto
- [Runbook](docs/RUNBOOK.md): šta raditi kad pozadinski poslovi zapnu

## Licenca

[MIT](LICENSE)
