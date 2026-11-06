# Ribolovački shop

Repo: `fishing-shop`. Naziv shopa još nije odlučen, pa je "Ribolovački shop" radni naslov.

Kompletan sistem za malu/srednju firmu koja prodaje ribolovačku opremu: web shop, magacin, zaposleni i uloge, pozadinski poslovi, izveštaji. Portfolio projekat, rađen solo, fazu po fazu.

## Stack

| Sloj | Tehnologija |
|---|---|
| Backend | Java 21, Spring Boot, Spring Data JPA |
| Baza | PostgreSQL 16, Flyway migracije |
| Frontend | TypeScript, React (Next.js), dolazi u Fazi 1 |
| Red poslova | RabbitMQ, dolazi u Fazi 4 |
| Infrastruktura | Docker, GitHub Actions |

## Arhitektura

```
[React/TS shop]  ──►  [Spring Boot API]  ──►  [PostgreSQL]
[Admin panel]    ──►        │
                            ▼
                      [RabbitMQ red]  ──►  [Worker: mejl, PDF račun, magacin]
```

## Pokretanje

Potrebno: JDK 21 i Docker. Maven nije potreban, projekat ima Maven wrapper (`mvnw`).

```bash
# 1. baza
docker compose up -d

# 2. backend
cd backend
./mvnw spring-boot:run

# provera
curl http://localhost:8080/actuator/health
```

Razvojne vrednosti su već podrazumevane. Za sopstvene: `cp .env.example .env` (fajl `.env` je u `.gitignore`).

Testovi (zahtevaju Docker, koriste Testcontainers sa pravim Postgresom):

```bash
cd backend && ./mvnw verify
```

## Struktura repoa

```
fishing-shop/
├── backend/            Spring Boot aplikacija (paket rs.fishingshop)
│   └── src/main/resources/db/migration/   Flyway migracije (V1, V2, ...)
├── frontend/           (Faza 1)
├── docs/               odluke (DECISIONS.md), ADR, dnevnik učenja
├── .github/workflows/  CI
├── docker-compose.yml  Postgres za razvoj
└── .env.example        razvojne vrednosti env varijabli
```

---

## Faze

Svaka faza se završi tako da posle nje nešto radi. Redosled je bitan, rokova nema.

### Faza 1: Katalog i porudžbina
- [ ] Model: kategorija, proizvod, **varijante** (štap 2.4m / 2.7m / 3.0m, svaka sa svojim SKU, cenom i zalihom)
- [ ] REST API: lista proizvoda, detalj, pretraga, filter po kategoriji, paginacija
- [ ] Korpa i kreiranje porudžbine (bez plaćanja)
- [ ] React frontend: lista, detalj, korpa, checkout

**Tehničke stvari na koje treba obratiti pažnju**
- DTO naspram entiteta: entiteti nikad ne idu direktno u JSON
- Validacija ulaza (`@Valid`, Bean Validation) i jedinstven format grešaka (`@ControllerAdvice`)
- N+1 upiti u JPA (`@EntityGraph`, `JOIN FETCH`), proveri brojem SQL upita
- Novac je `NUMERIC` / `BigDecimal`, nikad `double`
- Cena u porudžbini se **kopira** u `order_items` (cena proizvoda se kasnije menja)
- Paginacija i sortiranje na serveru, ne na klijentu
- OpenAPI/Swagger dokumentacija API-ja

### Faza 2: Magacin i konkurentnost
- [ ] Zalihe po varijanti, smanjenje zalihe pri porudžbini u jednoj transakciji
- [ ] Rešen problem "dva kupca, poslednji komad" (optimistic ili pessimistic locking)
- [ ] Test sa 50 istovremenih kupaca koji dokazuje da se ne proda više nego što ima
- [ ] Istorija kretanja zaliha (ulaz, izlaz, korekcija)

**Tehničke stvari na koje treba obratiti pažnju**
- Granice transakcije (`@Transactional`), nivoi izolacije, šta tačno zaključava `SELECT ... FOR UPDATE`
- Optimistic (`@Version`) naspram pessimistic locking-a: kad koji i kakav je retry
- `CHECK (quantity >= 0)` u bazi kao poslednja linija odbrane
- Deadlock: uvek zaključavaj redove istim redosledom
- Rezervacija zalihe naspram direktnog skidanja (korpa koja "drži" artikal)
- Integracioni testovi sa pravim Postgresom (Testcontainers), ne H2

### Faza 3: Zaposleni, uloge, audit log
- [ ] Login (JWT ili sesija), hashovanje lozinki (BCrypt/Argon2)
- [ ] Uloge: vlasnik, prodavac, magacioner (RBAC)
- [ ] Audit log: ko, šta, kada, stara i nova vrednost

**Tehničke stvari na koje treba obratiti pažnju**
- Autentikacija naspram autorizacije; metodska zaštita (`@PreAuthorize`)
- Refresh tokeni, istek, odjava; gde se token čuva na frontendu (httpOnly cookie)
- CORS, CSRF, rate limiting na login
- Audit log se piše u istoj transakciji kao izmena, ne sme da se menja ni briše
- Tajne (lozinke, ključevi) samo kroz env varijable, nikad u repou

### Faza 4: Pozadinski poslovi
- [ ] RabbitMQ red, worker servis
- [ ] Mejl potvrde, generisanje PDF računa
- [ ] Retry sa exponential backoff-om, dead-letter red

**Tehničke stvari na koje treba obratiti pažnju**
- **Idempotentnost**: isti posao izvršen dvaput ne sme da pošalje dva mejla
- Outbox obrazac: porudžbina i događaj se upisuju atomski, tek onda idu u red
- Potvrde poruka (ack/nack), šta se desi kad worker padne usred posla
- Poison poruke i dead-letter red

### Faza 5: Izveštaji, dashboard, uživo obaveštenja
- [ ] Izveštaji: prodaja po periodu, najprodavaniji, niske zalihe
- [ ] Admin dashboard sa grafikonima
- [ ] WebSocket obaveštenja (nova porudžbina, poslednji komad)

**Tehničke stvari na koje treba obratiti pažnju**
- Agregacioni SQL upiti, indeksi, `EXPLAIN ANALYZE`
- Keširanje (Redis ili Caffeine) i invalidacija
- Opterećenje: bar jedan load test (k6 ili Gatling) sa brojevima u README-u

---

## Kroz sve faze (ono što razdvaja student-projekat od profesionalnog)

- [ ] Testovi: unit, integracioni (Testcontainers), bar jedan end-to-end
- [ ] CI na svaki push (build + testovi), već postavljen
- [ ] Dockerfile za backend, ceo sistem podiže jedna komanda
- [ ] Flyway: šema se menja samo novim migracijama, nikad izmenom stare
- [ ] Strukturirani logovi, health check, osnovne metrike (Actuator)
- [ ] Deploy na javni link (README mora da ima link koji radi)
- [ ] ADR beleške: kratke odluke "zašto X a ne Y" u `docs/adr/`
