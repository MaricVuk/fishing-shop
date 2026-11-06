# Odluke

Spisak odluka sa kratkim obrazloženjem. Veće odluke dobijaju poseban ADR u `docs/adr/`. Poslednje ažuriranje: 2026-11-06.

## Odlučeno

| Tema | Odluka | Obrazloženje |
|---|---|---|
| Jezik koda | Engleski (kod, komentari, tabele, API). UI i README na srpskom | nazivi se kasnije teško menjaju, a kod čitaju i poslodavci |
| Backend | Java 21 + Spring Boot + Maven | najtraženije u Srbiji i EU, Spring Security/JPA/transakcije pogađaju shop |
| Baza | PostgreSQL 16 + Flyway | dobre transakcije za zalihe, šema kroz verzionisane migracije |
| Frontend | Next.js (App Router) + TypeScript | najrasprostranjeniji React okvir, SSR za katalog |
| Odnos frontenda i backenda | Spring je jedini backend, Next.js samo prikazuje | poslovna logika ostaje na Java strani, to je portfolio priča |
| Admin deo | Isti Next.js, zaštićene `/admin` rute | jedan deploy, jednostavnije. Uloge se proveravaju i na backendu |
| Valuta i jezik shopa | RSD, srpski | |
| Repo | Javan GitHub repo, licenca MIT | portfolio mora da se vidi. Tajne samo kroz env, `.env.example` u repou |
| Okruženje | Windows 11 + WSL2, projekat u Linux fajl sistemu | brži Maven i `node_modules`, Docker koristi WSL2 |
| Line endings | `.gitattributes`: `* text=auto eol=lf` | Windows CRLF kvari shell skripte i Docker |
| CI | GitHub Actions (build + testovi) | već u skeletu |
| Modeli | Sonnet podrazumevano. Opus za planiranje faze, arhitekturu, težak debug, sigurnost i pregled pred merge. Haiku/Sonnet za mehaničke poslove | Opus troši limite brže, koristi se tamo gde greška košta |
| Sub-agenti | Ne na početku. Videti plan ispod | svaki počinje bez konteksta i troši tokene |
| Skill-ovi | Jedan po poslu, bez duplikata. Izbor u `CLAUDE.md` | duplikati zbunjuju Claude-a |
| Imena | Repo `fishing-shop`, Java paket `rs.fishingshop`, Maven artifactId `fishing-shop` | doslednost sa pravilom "engleski kod" |
| Git | Samo autor commit-uje i push-uje. Claude Code predlaže plan, poruku i trenutak | istorija repoa je deo portfolija i mora da bude autorova |
| Redosled rada u fazi | Tanke vertikalne celine (API, pa odmah stranica). Jedna grana i jedan PR po celini, lokalni commit-ovi češće, push kad je celina gotova | rezultat se vidi odmah, a ugovor API-ja se proverava rano |
| Korpa i kupci (Faza 1) | Korpa u browseru (localStorage), server prima gotovu porudžbinu. Kupac je gost, bez naloga | Spring ostaje bez sesija |
| Greške API-ja | Spring `ProblemDetail` | ugrađeno, bez nove zavisnosti |
| Pretraga (Faza 1) | `ILIKE`, indeks tek ako zatreba | mali katalog |
| Seed podaci | Kategorije ostaju u `V1`, demo proizvodi idu u dev-only lokaciju | produkcija ne dobija demo proizvode |
| Slike proizvoda | Kolona `image_url` u `V3`, bez uploada u Fazi 1 | shop bez slika izgleda prazno, a upload nije potreban |
| Komentari u kodu | Samo kad su tehnički neophodni | čitljiv kod bez šuma |
| UI biblioteka | Tailwind CSS 4 + shadcn/ui (komponente se kopiraju u repo) | podrazumevani izbor iz plana, bez zaključavanja u biblioteku |
| Dizajn smernice | Izvedene iz repoa `pbakaus/impeccable` kao referenca, plugin nije instaliran: jedan akcenat, bez ugnežđenih kartica i dekorativnih efekata, sva stanja (učitavanje, prazno, greška, fokus), kontrast najmanje 4.5:1 | instalater tog plugina povlači binarni fajl i dodaje hook, pa ga ne uključujemo bez pregleda |
| Fontovi | Hanken Grotesk (tekst) i Barlow Condensed (naslovi), oba sa `latin-ext` | srpski dijakritici |
| Frontend dohvat podataka | Server komponente sa `cache: "no-store"`, stanje filtera u URL-u, forma bez klijentskog JavaScript-a | SSR za katalog, linkovi koji se dele |
| Next.js keširanje | `cacheComponents` i `partialPrefetching` isključeni (skelet ih uključuje) | katalog je dinamičan, prvo dosadno rešenje |
| Frontend testovi | Vitest za čiste funkcije (`lib/`) | najlakši test runner za TypeScript |
| Naziv shopa | Radni naslov "Ribolovački shop" u jednoj konstanti (`frontend/src/lib/config.ts`) | konačan naziv se odlučuje kasnije |
| Dostupnost u API-ju | Samo "na stanju / nema na stanju" po varijanti, bez tačnih količina | tačna zaliha nije javan podatak |
| Vidljivost proizvoda | Proizvod je vidljiv (lista i detalj) samo ako je aktivan i ima bar jednu aktivnu varijantu | isto pravilo na oba mesta, neaktivan proizvod daje 404 |
| Rute frontenda | Skeleton liste je u grupi rute `products/(list)`, a detalj nema `loading.tsx` | `loading.tsx` bi omotao detalj u Suspense, pa bi nepostojeći proizvod vraćao HTTP 200 umesto 404 |
| Atributi varijanti | U bazi engleski ključevi (`length`, `color`), vrednosti na jeziku shopa; prevod ključeva je u frontendu | ključevi su deo modela, vrednosti su sadržaj shopa |
| Čitanje porudžbina | Nema `GET /api/orders/{id}` dok ne postoji prijava (Faza 3). Odgovor na kreiranje ne sadrži podatke o kupcu | gost + redni broj u URL-u bi otkrivao tuđa imena i adrese |
| Zaliha u Fazi 1 | Porudžbina ne proverava i ne skida zalihu (to je Faza 2). Frontend ne dozvoljava dodavanje varijante koja nema na stanju | zaliha mora da se rešava atomski, ne dvaput |
| Greške validacije | `400` sa listom `errors: [{field, code}]` (šifre Bean Validation-a), frontend ih prevodi na srpski | poruke na jeziku shopa žive u UI-ju, ne u API-ju |
| Nedostupne stavke | `422` sa `unavailableVariantIds`, a porudžbina se ne upisuje ni delimično | cela porudžbina je jedna transakcija |
| Korpa | `localStorage` čuva samo `variantId` i količinu, a trenutne podatke daje `GET /api/variants?ids=` | nikad zastarele cene u korpi |
| Mejl u porudžbini | Server ne "popravlja" razmake oko mejla, odbija ga sa `400`. Frontend ga čisti pre slanja | tihe ispravke kriju greške |
| Spajanje stavki | Duplirane varijante u porudžbini se spajaju, najviše 99 komada po stavci | klijent ne mora da vodi računa o duplikatima |
| Pristup API-ju iz browsera | `rewrites` u Next.js šalju samo `/api/*` na Spring (ne i `/actuator` ni Swagger) | browser zove isti domen, pa nema CORS konfiguracije |
| Stanje korpe u frontendu | `useSyncExternalStore` nad `localStorage`, bez biblioteke za stanje | sinhronizacija između kartica i bez nepoklapanja pri hidrataciji |
| Potvrda porudžbine | Odgovor servera (bez podataka o kupcu) ide u `sessionStorage` i prikazuje se na `/checkout/success` | nema javnog čitanja porudžbina |
| Validacija forme | Ista pravila na klijentu (poruke na srpskom) i na serveru (šifre grešaka). Server je merodavan | brza povratna informacija, a bezbednost je na serveru |
| Blokiranje u korpi | Korpa sa nedostupnom stavkom ili stavkom bez zalihe ne može na završetak porudžbine | server u Fazi 1 ne proverava zalihu |

## Plan za sub-agente po fazama

| Faza | Sub-agenti |
|---|---|
| 0 / pre Faze 1 | `schema-reviewer` jednom nad `V1__init_schema.sql` |
| 1 | nijedan |
| 2 | recenzent koda, pisac testova za konkurentni test |
| 3 | `security-reviewer` |
| 4 | `incident-thinker` pre puštanja workera, `boring-tech-advisor` za pitanje RabbitMQ naspram reda u Postgresu |
| Pred deploy | `claude-security` sken, jednom (skup po tokenima) |

## Odlučujemo kasnije

| Tema | Kad | Napomena |
|---|---|---|
| Autentikacija (JWT ili sesija) | Faza 3 | ADR |
| Red poslova (RabbitMQ ili jednostavnije) | Faza 4 | prvo ga izazovi `boring-tech-advisor` agentom |
| Gde se deploy-uje (Fly.io, Render, Railway, VPS) | pre prvog deploya | mora da ima javni link |
| Konačan naziv shopa | pre deploya | trenutno radni naslov |
| Plaćanje karticom | verovatno van opsega | portfolio ne mora da prima pravi novac |
| Više jezika (i18n) | samo ako zatreba | |

## Za proveru

- Obrisati folder `reference/` (autor). Sadržaj je preuzet u skelet.
- Pregled `V1` je urađen ručno (agent `schema-reviewer` nije bio dostupan u toj sesiji). Nalazi 1, 3 i 5 su u `V2`. Otvoreno: indeks za pretragu po nazivu, jedinstveno ime kategorije po roditelju, SKU bez razlike velikih i malih slova. Ako se plugin `backend-design` učita, može se pokrenuti i agent.
- Odlučiti da li CI koristi `./mvnw` umesto `mvn`, da lokalna i CI verzija Mavena budu iste.
- Frontend izgled nije vizuelno proveren u svetloj temi i na telefonu (Chrome ne dozvoljava prozor uži od oko 500 px). Pogledati na pravom telefonu ili kroz DevTools.
- Slike proizvoda: dok `image_url` nije popunjen, kartice imaju rezervnu sliku. Izgled kataloga zavisi od pravih fotografija.
- `npm audit` javlja 13 upozorenja niskog i srednjeg nivoa (bez visokih), uglavnom preko razvojnih alata. Pratiti ih kroz Dependabot.
- OpenAPI (springdoc 3.2.0) radi sa Spring Boot 4.1.2. Pre deploya isključiti `/v3/api-docs` i Swagger UI u produkciji (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`).
- Greške validacije upita (`/api/products?size=0`) i dalje imaju opštu poruku bez detalja po polju. Greške tela porudžbine već imaju listu po polju.
- Dvostruko slanje porudžbine (dupli klik): frontend onemogućava dugme, a prava zaštita je idempotentni ključ (Faza 4).
- Zaštita od spama na `POST /api/orders` (ograničenje učestalosti): uz prijavu u Fazi 3.

## Urađeno

- 2026-11-06: skelet generisan preko Spring Initializr-a (Spring Boot **4.1.2**, najnovija stabilna, umesto ranije planiranog 3.5.7). Prvi `./mvnw verify` prošao (Testcontainers 2.x, Flyway V1, Hibernate `validate`), a `/actuator/health` je `UP` protiv compose baze.
- JDK 21 (Temurin 21.0.13) instaliran ručno u `~/.local/jdk`, jer SDKMAN traži `zip`, a `sudo` nije bio dostupan.
- 2026-11-06: celina 1.1 spojena (PR #4): `V3` (`image_url`), dev seed kroz repeatable migraciju, API kategorija i liste proizvoda, springdoc 3.2.0.
- 2026-11-06: celina 1.5 (frontend porudžbine): dugme "Dodaj u korpu", korpa u `localStorage`, stranice `/cart`, `/checkout` i `/checkout/success`, broj komada u zaglavlju, greške po polju na srpskom. 106 frontend testova. Proverena cela kupovina u browseru.
- 2026-11-06: celina 1.4 (backend porudžbine): `POST /api/orders` (kopira naziv, SKU i cenu u stavke, ukupno u `BigDecimal`, jedna transakcija), `GET /api/variants?ids=` za korpu, greške po polju i `422` za nedostupne stavke. 40 testova.
- 2026-11-06: celina 1.3: `GET /api/products/{id}` (proizvod, kategorija, varijante sa dostupnošću, 2 SQL upita), stranica detalja sa putanjom, kartica je link, prava 404 stranica. Demo vrednosti atributa prevedene na srpski.
- 2026-11-06: celina 1.2: Next.js frontend sa listom proizvoda (pretraga, kategorije, sortiranje, paginacija), 32 testa, frontend posao u CI-ju. GitHub akcije podignute na `checkout@v7`, `setup-java@v6`, `setup-node@v7`.
- 2026-11-06: Node.js v26.13.0 (LTS) instaliran u `~/.local/node`, jer je WSL do tada video samo Windows `npm` preko `/mnt/c`.
- 2026-11-06: `V2` (ograničenja i indeksi) sa testovima, README kao izložba projekta, plan faza u `docs/ROADMAP.md`, MIT `LICENSE`. Faza 0 zatvorena.
