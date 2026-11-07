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

## Faza 2: Magacin i konkurentnost (gotova)

- [x] Zalihe po varijanti, smanjenje zalihe pri porudžbini u jednoj transakciji
- [x] Rešen problem "dva kupca, poslednji komad" (pessimistic locking, [ADR 0003](adr/0003-pessimistic-locking-for-stock.md))
- [x] Test sa 50 istovremenih kupaca koji dokazuje da se ne proda više nego što ima
- [x] Istorija kretanja zaliha (ulaz, izlaz, korekcija)

**Tehničke stvari na koje treba obratiti pažnju**
- Granice transakcije (`@Transactional`), nivoi izolacije, šta tačno zaključava `SELECT ... FOR UPDATE`
- Optimistic (`@Version`) naspram pessimistic locking-a: kad koji i kakav je retry
- `CHECK (quantity >= 0)` u bazi kao poslednja linija odbrane
- Deadlock: uvek zaključavaj redove istim redosledom
- Rezervacija zalihe naspram direktnog skidanja (korpa koja "drži" artikal)
- Integracioni testovi sa pravim Postgresom (Testcontainers), ne H2

## Faza 3: Zaposleni, uloge, audit log (gotova)

- [x] Login (sesija u bazi, ne JWT), hashovanje lozinki (BCrypt)
- [x] Uloge: vlasnik, prodavac, magacioner (RBAC)
- [x] Audit log: ko, šta, kada, stara i nova vrednost

**Tehničke stvari na koje treba obratiti pažnju**
- Autentikacija naspram autorizacije; metodska zaštita (`@PreAuthorize`)
- Refresh tokeni, istek, odjava; gde se token čuva na frontendu (httpOnly cookie)
- CORS, CSRF, rate limiting na login
- Audit log se piše u istoj transakciji kao izmena, ne sme da se menja ni briše
- Tajne (lozinke, ključevi) samo kroz env varijable, nikad u repou

**Šta su odluke donele**
- [ADR 0004](adr/0004-employee-login-with-server-sessions.md): sesija u Postgresu umesto JWT-a, verzija sesije, ograničenje pokušaja prijave
- [ADR 0005](adr/0005-append-only-audit-log.md): audit log kojem trigger u bazi ne dozvoljava izmenu ni brisanje
- Sigurnosni pregled sa 4 nezavisna recenzenta (36 nalaza); ostatak je zapisan kao dug u [DECISIONS.md](DECISIONS.md)

## Faza 4: Pozadinski poslovi (gotova)

- [x] Red poslova u Postgresu (outbox), worker ([ADR 0006](adr/0006-job-queue-in-postgres.md))
- [x] Mejl potvrde, generisanje PDF računa
- [x] Retry sa exponential backoff-om, dead-letter red (stanje `DEAD`, admin pregled u 4.3)

**Tehničke stvari na koje treba obratiti pažnju**
- **Idempotentnost**: isti posao izvršen dvaput ne sme da pošalje dva mejla
- Outbox obrazac: porudžbina i događaj se upisuju atomski, tek onda idu u red
- Potvrde poruka (ack/nack), šta se desi kad worker padne usred posla
- Poison poruke i dead-letter red

**Šta su odluke donele**
- [ADR 0006](adr/0006-job-queue-in-postgres.md): red poslova je tabela u Postgresu (outbox i red u jednom, `FOR UPDATE SKIP LOCKED`), bez RabbitMQ-a; izazvano agentom `boring-tech-advisor`
- Umesto ack/nack: posao "u radu" je zaključan red, pa se posao workera koji padne oslobađa sam; neuspeh daje backoff, trajna greška ili 14 pokušaja daju `DEAD`, a vlasnik posao ponavlja ili označava kao rešen u adminu
- Isporuka je "bar jednom": jedinstven posao po porudžbini i tipu, a mejl može da se ponovi samo ako backend padne između slanja i upisa
- PDF račun (PDFBox) sa brojem bez rupa, nastaje pri potvrdi porudžbine i ne može da se menja; mejl sa računom je poseban posao
- Pregled `incident-thinker` pre puštanja workera i pregled koda cele faze (10 nalaza, svi ispravljeni); postupci su u [RUNBOOK.md](RUNBOOK.md)
- Idempotentni ključ za porudžbinu je pomeren u dug

## Faza 5: Izveštaji, dashboard, uživo obaveštenja (gotova)

- [x] Izveštaji: prodaja po periodu, najprodavaniji, niske zalihe (5.2, 5.3, [merenja](perf/sales-report.md))
- [x] Admin dashboard sa grafikonima (5.5, po ulozi, osvežava se na događaj iz 5.4)
- [x] Uživo obaveštenja u adminu (nova porudžbina, poslednji komad) kroz Server-Sent Events ([ADR 0007](adr/0007-admin-live-events-with-sse.md)) (5.4)

**Tehničke stvari na koje treba obratiti pažnju**
- Agregacioni SQL upiti, indeksi, `EXPLAIN ANALYZE`
- Keširanje i invalidacija: bez keša dok merenje ne pokaže potrebu, zatim Caffeine sa TTL-om (odluka u [DECISIONS.md](DECISIONS.md))
- Opterećenje: bar jedan load test (k6 ili Gatling) sa brojevima u README-u (urađeno u 5.6, [merenja](perf/load-test.md))

**Šta su odluke donele**
- Odluke pre koda izazvane agentom `boring-tech-advisor`: bez keša, živi SQL upiti umesto zbirnih tabela, SSE umesto WebSocket-a ([ADR 0007](adr/0007-admin-live-events-with-sse.md))
- Izveštaji su mereni na bazi sa 547k porudžbina i 1M stavki: `EXPLAIN ANALYZE` je našao spajanje pre sabiranja, JIT, `count(DISTINCT)` i generički plan pripremljene naredbe, ne nedostatak indeksa; jedina migracija je `V14` (varijanta jednom po porudžbini) ([merenja](perf/sales-report.md), [najprodavaniji](perf/product-reports.md))
- Proba pre koda: Next gzip zadržava SSE tok bez `no-transform`, proxy prekida tihu vezu posle 30 s
- Load test (k6): katalog 3.000 zahteva/s, zaliha pod 50 istovremenih kupaca tačna; otkrio `/dev/shm` u Postgres kontejneru ([load test](perf/load-test.md))
- Logovi kao JSON, metrike reda i health bez obaranja shopa; CI je prvi put pao i našao pravu grešku u slanju obaveštenja
- Pregled koda cele faze: 10 nalaza, 9 ispravljeno (2 potvrđena testom koji je pao), 1 svesno ostavljen kao dug

## Faza 6: Priprema za deploy (u toku)

Plan potvrđen 2026-11-07. Pravi deploy je odložen do dana kad se softver bude prodavao ([ADR 0008](adr/0008-deploy-postponed.md)); faza priprema sistem tako da se pokreće jednom komandom i da je bezbednosni dug koji se može zatvoriti bez hostinga zatvoren. Jedna grana i jedan PR po celini.

- [x] 6.0 Odluke: ADR 0008, ROADMAP i DECISIONS usklađeni (`boring-tech-advisor` za platformu, bazu i nadzor)
- [x] 6.1 Dependabot, pinovanje akcija po SHA, pregled `npm audit`
- [x] 6.2 Dockerfile za backend i frontend, ceo sistem jednom komandom, proxy koji čita adresu backenda u runtime-u, profil `dev` se ne uključuje
- [x] 6.3 Poseban korisnik baze (`shop_migrator`, `shop_app`) i tajmauti baze
- [x] 6.4 Actuator na posebnom portu, backend dostupan samo Next serveru, `X-Forwarded-For`
- [x] 6.5 Pun CSP sa nonce-om
- [ ] 6.6 Idempotentni ključ za porudžbinu
- [ ] 6.7 Nasumični javni token porudžbine
- [ ] 6.8 Storno račun
- [ ] 6.9 UI revizija (`impeccable`, `emil-design-eng`), vizuelnu potvrdu radi autor
- [ ] 6.10 End-to-end test cele kupovine (Playwright) u CI-ju
- [ ] 6.11 Pregled koda cele faze i zatvaranje

**Odloženo do prvog pravog deploya** (vidi [ADR 0008](adr/0008-deploy-postponed.md)): izbor platforme, VPS, domen, mejl provajder i javni link; HTTPS, HSTS i `__Host-` kolačići; 2FA za vlasnika; nadzor sa uzbunama i backup sa restore testom; `claude-security` sken.

## Kroz sve faze: kvalitet i inženjerske prakse

- [ ] Testovi: unit, integracioni (Testcontainers), bar jedan end-to-end (end-to-end je 6.10)
- [x] CI na svaki pull request i push na `main` (build + testovi)
- [x] Dockerfile za backend, ceo sistem podiže jedna komanda (6.2)
- [x] Flyway: šema se menja samo novim migracijama, nikad izmenom stare
- [x] Strukturirani logovi, health check, osnovne metrike (Actuator, 5.1)
- [ ] Deploy na javni link (odloženo do prodaje softvera, [ADR 0008](adr/0008-deploy-postponed.md))
- [x] ADR beleške: kratke odluke "zašto X a ne Y" u `docs/adr/` (do sada [korpa u browseru](adr/0001-client-side-cart.md), [porudžbine bez javnog čitanja](adr/0002-orders-without-public-read.md), [zaključavanje zalihe](adr/0003-pessimistic-locking-for-stock.md), [prijava sa sesijom](adr/0004-employee-login-with-server-sessions.md), [audit log](adr/0005-append-only-audit-log.md), [red poslova u Postgresu](adr/0006-job-queue-in-postgres.md), [uživo obaveštenja kroz SSE](adr/0007-admin-live-events-with-sse.md) i [deploy odložen](adr/0008-deploy-postponed.md))

## Otvorene odluke

Šta još nije odlučeno (naziv shopa, plaćanje, pravi deploy) vodi se u tabeli "Odlučujemo kasnije" u [DECISIONS.md](DECISIONS.md).
