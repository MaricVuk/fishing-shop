# Plan razvoja

Detaljan plan po fazama. Kratak pregled statusa je u [README.md](../README.md), a obrazloženja odluka u [DECISIONS.md](DECISIONS.md).

Svaka faza se završi tako da posle nje nešto radi. Redosled je bitan, rokova nema.

## Faza 0: Priprema (gotova)

- [x] Spring Boot skelet (Java 21, Maven wrapper)
- [x] PostgreSQL 16 kroz Docker Compose
- [x] Flyway migracije (V1 osnovna šema, V2 dodatna ograničenja i indeksi)
- [x] Integracioni test sa pravim Postgresom (Testcontainers)
- [x] CI na svaki pull request i push na `main`

## Faza 1: Katalog i porudžbina (gotova)

- [x] Model: kategorija, proizvod, **varijante** (štap 2.4m / 2.7m / 3.0m, svaka sa svojim SKU, cenom i zalihom)
- [x] REST API: lista proizvoda, detalj, pretraga, filter po kategoriji, paginacija
- [x] Korpa i kreiranje porudžbine (bez plaćanja)
- [x] React frontend: lista, detalj, korpa, checkout

**Tehničke stvari na koje treba obratiti pažnju**
- DTO naspram entiteta: entiteti nikad ne idu direktno u JSON
- Validacija ulaza (`@Valid`, Bean Validation) i jedinstven format grešaka (`@ControllerAdvice`)
- N+1 upiti u JPA (`@EntityGraph`, `JOIN FETCH`), proveri brojem SQL upita
- Novac je `NUMERIC` / `BigDecimal`, nikad `double`
- Cena u porudžbini se **kopira** u `order_items` (cena proizvoda se kasnije menja)
- Paginacija i sortiranje na serveru, ne na klijentu
- OpenAPI/Swagger dokumentacija API-ja

## Faza 2: Magacin i konkurentnost (sledeća)

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

## Faza 3: Zaposleni, uloge, audit log

- [ ] Login (JWT ili sesija), hashovanje lozinki (BCrypt/Argon2)
- [ ] Uloge: vlasnik, prodavac, magacioner (RBAC)
- [ ] Audit log: ko, šta, kada, stara i nova vrednost

**Tehničke stvari na koje treba obratiti pažnju**
- Autentikacija naspram autorizacije; metodska zaštita (`@PreAuthorize`)
- Refresh tokeni, istek, odjava; gde se token čuva na frontendu (httpOnly cookie)
- CORS, CSRF, rate limiting na login
- Audit log se piše u istoj transakciji kao izmena, ne sme da se menja ni briše
- Tajne (lozinke, ključevi) samo kroz env varijable, nikad u repou

## Faza 4: Pozadinski poslovi

- [ ] RabbitMQ red, worker servis
- [ ] Mejl potvrde, generisanje PDF računa
- [ ] Retry sa exponential backoff-om, dead-letter red

**Tehničke stvari na koje treba obratiti pažnju**
- **Idempotentnost**: isti posao izvršen dvaput ne sme da pošalje dva mejla
- Outbox obrazac: porudžbina i događaj se upisuju atomski, tek onda idu u red
- Potvrde poruka (ack/nack), šta se desi kad worker padne usred posla
- Poison poruke i dead-letter red

## Faza 5: Izveštaji, dashboard, uživo obaveštenja

- [ ] Izveštaji: prodaja po periodu, najprodavaniji, niske zalihe
- [ ] Admin dashboard sa grafikonima
- [ ] WebSocket obaveštenja (nova porudžbina, poslednji komad)

**Tehničke stvari na koje treba obratiti pažnju**
- Agregacioni SQL upiti, indeksi, `EXPLAIN ANALYZE`
- Keširanje (Redis ili Caffeine) i invalidacija
- Opterećenje: bar jedan load test (k6 ili Gatling) sa brojevima u README-u

## Kroz sve faze: kvalitet i inženjerske prakse

- [ ] Testovi: unit, integracioni (Testcontainers), bar jedan end-to-end
- [x] CI na svaki pull request i push na `main` (build + testovi)
- [ ] Dockerfile za backend, ceo sistem podiže jedna komanda
- [x] Flyway: šema se menja samo novim migracijama, nikad izmenom stare
- [ ] Strukturirani logovi, health check, osnovne metrike (Actuator)
- [ ] Deploy na javni link (README mora da ima link koji radi)
- [x] ADR beleške: kratke odluke "zašto X a ne Y" u `docs/adr/` (do sada [korpa u browseru](adr/0001-client-side-cart.md) i [porudžbine bez javnog čitanja](adr/0002-orders-without-public-read.md))

## Otvorene odluke

Šta još nije odlučeno (autentikacija, red poslova, UI biblioteka, deploy, naziv shopa) vodi se u tabeli "Odlučujemo kasnije" u [DECISIONS.md](DECISIONS.md).
