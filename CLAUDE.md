# CLAUDE.md

Pravila projekta. Učitavaju se na početku svake sesije, zato su kratka. Detalji i obrazloženja su u `docs/DECISIONS.md`.

## Projekat

Ribolovački shop: web shop, magacin, zaposleni i uloge, pozadinski poslovi, izveštaji. Portfolio projekat, jedan autor, radi se **fazu po fazu** (faze su u `README.md`).

**Imena:** repo `fishing-shop`, Java paket `rs.fishingshop`, Maven artifactId `fishing-shop`.

**Trenutna faza:** 0 (priprema). Ažuriraj ovu liniju kad krene nova faza.

## Stack

- Backend: Java 21, Spring Boot, Spring Data JPA, Maven (`backend/`)
- Baza: PostgreSQL 16, Flyway migracije
- Frontend: Next.js (App Router) + TypeScript (`frontend/`)
- Red poslova: RabbitMQ (Faza 4)
- Infrastruktura: Docker, GitHub Actions

## Arhitektura

- Spring je **jedini backend**. Next.js samo prikazuje podatke i ne sadrži poslovnu logiku (zalihe, porudžbine, cene).
- Admin deo je isti Next.js sa zaštićenim `/admin` rutama (uloge se proveravaju i na backendu, ne samo na frontendu).
- Entiteti nikad ne idu direktno u JSON. Koristi DTO.
- Ne uvodi novu tehnologiju ili zavisnost bez jasnog razloga. Prvo razmotri dosadno rešenje.

## Kod

- **Kod, komentari, nazivi tabela, kolona i API ruta: engleski.** UI tekstovi i README: srpski (latinica).
- Novac je `BigDecimal` / `NUMERIC`, nikad `double`.
- Cena i naziv proizvoda se **kopiraju** u stavku porudžbine.
- Validacija ulaza (`@Valid`) i jedinstven format grešaka.
- Tajne samo kroz env varijable. U repo ide samo `.env.example`. Repo je javan.

## Baza

- Šemu menjaju **samo nove Flyway migracije** (`V{n}__opis.sql`). Postojeću migraciju nikad ne menjaj.
- `ddl-auto: validate`, Hibernate ne pravi šemu.
- Ograničenja (`CHECK`, `NOT NULL`, strani ključevi) idu u bazu, ne samo u kod.
- Pre svake nove migracije pokreni `backend-design:review-migration`.

## Testovi

- Integracioni testovi koriste **pravi Postgres kroz Testcontainers**, ne H2.
- Za konkurentnost (Faza 2) prvo napiši test koji pada, pa tek onda rešenje.
- Posao nije gotov dok `mvn verify` ne prođe.

## Git

- **Samo autor commit-uje i push-uje. Ti to ne radiš, nikad.** Ne pokrećeš `git add`, `git commit`, `git push`, `git merge`, `git rebase`, `git reset`, `git stash`, `git tag`, ne menjaš grane, ne otvaraš ni ne spajaš PR (`gh pr create/merge`). Čak ni kad te autor posredno zamoli da "završiš posao", ni kad ti se čini da je očigledno.
- Dozvoljene su samo komande za čitanje: `git status`, `git diff`, `git log`, `git branch`.
- Umesto toga **predloži**: kad je dobar trenutak za commit (npr. logična celina, testovi prolaze), naziv grane, tačnu poruku commita i listu fajlova koji idu u njega. Autor to izvršava sam.
- Nikad direktno na `main`. Grana po funkcionalnosti: `feature/...`, `fix/...`. PR čak i solo.
- Format commit poruke: `feat:`, `fix:`, `test:`, `docs:`, `chore:`, `refactor:`.
- `.gitattributes` sa `* text=auto eol=lf` (autor radi na Windowsu).

## Okruženje

- Windows 11 + **WSL2**. Projekat živi u Linux fajl sistemu (`~/projects/...`), ne na `C:\`.
- Docker Desktop. **Nikad** ne pokreći `docker volume prune`, `docker system prune` ni `docker compose down -v` bez izričitog traženja i bez da prvo kažeš šta će se izgubiti.

## Kako radiš sa autorom

- Autor uči dok gradi. Objašnjavaj odluke kratko i korak po korak, sa malim vizuelnim primerom (dijagram, tabela) kad pomaže.
- Razgovor je na srpskom, neformalno. Kod i komentari na engleskom.
- Za sve što dira više fajlova predloži plan pa čekaj odobrenje.
- Ako je zahtev dvosmislen, pitaj jedno kratko pitanje umesto da pogađaš.
- **Modeli:** Sonnet je podrazumevani. Ako zadatak liči na planiranje faze, arhitektonsku odluku, težak debug konkurentnosti, sigurnosni pregled ili pregled pred merge, jednom na početku predloži prelazak na Opus.
- **Sub-agente** ne pokreći osim kad autor to traži ili je predviđeno za tekuću fazu u `docs/DECISIONS.md`.
- **Skill-ovi, jedan po poslu** (bez mešanja duplikata): PR pregled `code-review:code-review`, backend pregled `backend-design:audit`, TDD `superpowers:test-driven-development`, debug `superpowers:systematic-debugging` + `backend-design:debugging-discipline`.

## Faza je gotova kad

- [ ] testovi prolaze (`mvn verify`, a za frontend i njegovi testovi)
- [ ] README checklista faze je štiklirana
- [ ] za svaku veću odluku postoji zapis u `docs/DECISIONS.md` ili ADR u `docs/adr/`
- [ ] `docs/learning-log.md` ima unos za fazu
- [ ] CLAUDE.md ima ažuriranu liniju "Trenutna faza"
