# Odluke

Spisak odluka sa kratkim obrazloženjem. Veće odluke dobijaju poseban ADR u `docs/adr/` ([0001 korpa u browseru](adr/0001-client-side-cart.md), [0002 porudžbine bez javnog čitanja](adr/0002-orders-without-public-read.md)). Poslednje ažuriranje: 2026-11-06.

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
| Odbijene porudžbine | `422` sa `unavailableVariantIds` (varijanta ne postoji ili je neaktivna), sa `variantId` (zbir iznad 99 komada) ili samo sa opštom porukom (ukupna cena ne staje u kolonu). Porudžbina se ne upisuje ni delimično | cela porudžbina je jedna transakcija |
| Korpa | `localStorage` čuva samo `variantId` i količinu, a trenutne podatke daje `GET /api/variants?ids=` | nikad zastarele cene u korpi |
| Mejl u porudžbini | Server ne "popravlja" razmake oko mejla, odbija ga sa `400`. Frontend ga čisti pre slanja | tihe ispravke kriju greške |
| Spajanje stavki | Duplirane varijante u porudžbini se spajaju, najviše 99 komada po stavci | klijent ne mora da vodi računa o duplikatima |
| Pristup API-ju iz browsera | `rewrites` u Next.js šalju samo `/api/*` na Spring (ne i `/actuator` ni Swagger) | browser zove isti domen, pa nema CORS konfiguracije |
| Stanje korpe u frontendu | `useSyncExternalStore` nad `localStorage`, bez biblioteke za stanje | sinhronizacija između kartica i bez nepoklapanja pri hidrataciji |
| Potvrda porudžbine | Odgovor servera (bez podataka o kupcu) ide u `sessionStorage` i prikazuje se na `/checkout/success` | nema javnog čitanja porudžbina |
| Validacija forme | Ista pravila na klijentu (poruke na srpskom) i na serveru (šifre grešaka). Server je merodavan | brza povratna informacija, a bezbednost je na serveru |
| Blokiranje u korpi | Korpa sa nedostupnom stavkom ili stavkom bez zalihe ne može na završetak porudžbine | server u Fazi 1 ne proverava zalihu |
| Konfiguracija bez podrazumevanih vrednosti | Bez profila `dev` backend traži `DB_URL`, `DB_USER` i `DB_PASSWORD`, a Swagger je isključen. Razvojne vrednosti i Swagger su u `application-dev.yml` | zaboravljena promenljiva ne sme tiho da koristi slabu lozinku iz javnog repoa |
| Ograničenje ulaza | Telo zahteva do 32 KB (`413`), `page` do 9999, bez kontrolnih znakova u poljima kupca, razlomljeni brojevi se odbijaju, ukupna cena mora da stane u kolonu (`422`) | server ne prima neispravne podatke i ne ispravlja ih tiho |
| Neočekivane greške | `500` u `ProblemDetail` formatu sa opštom porukom, detalji samo u logu | jedan format grešaka i bez curenja unutrašnjosti |
| Bezbednosna zaglavlja frontenda | `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy` i CSP samo sa direktivama bez skripti (`frame-ancestors`, `base-uri`, `object-src`, `form-action`), bez `X-Powered-By` | pun CSP traži nonce za Next.js skripte, pa je za kasnije |
| Dev seed | Samo ubacuje podatke (`ON CONFLICT DO NOTHING`), a Flyway ignoriše nedostajuću repeatable migraciju | isključivanje `dev` profila ne sme da sruši start na istoj bazi |

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

## Dug i otvorene stvari

Pregled na kraju Faze 1, dopunjen na kraju Faze 2. Nijedna stavka ne blokira Fazu 3.

| Stvar | Zašto je bitno | Kad |
|---|---|---|
| Idempotentni ključ za porudžbinu | dupli klik ili ponovljen zahtev pravi dve porudžbine (frontend već onemogućava dugme) | Faza 4 |
| Ograničenje učestalosti na `POST /api/orders` | spam porudžbine | uz prijavu, Faza 3 |
| Svetla tema i telefon nisu vizuelno provereni (Chrome ne dozvoljava prozor uži od oko 500 px) | izgled je deo portfolija | pre deploya, na pravom telefonu ili kroz DevTools |
| Slike proizvoda (`image_url` je prazan, kartice imaju rezervnu sliku) | izgled kataloga zavisi od fotografija | pre deploya |
| Pri deployu ne uključivati profil `dev` (u njemu su Swagger UI i razvojna lozinka) | bez profila je Swagger isključen, a lozinka obavezna, pa je dovoljno da `dev` ostane isključen | pre deploya |
| Dockerfile za backend i frontend, ceo sistem jednom komandom | ROADMAP to obećava | pre deploya |
| End-to-end test cele kupovine (npr. Playwright) | ručna provera nije regresioni test | pre deploya |
| Strukturirani logovi i metrike (Actuator izlaže samo `health` i `info`) | lakši rad u produkciji | Faza 4 ili 5 |
| Dependabot (`.github/dependabot.yml`). `npm audit` javlja 13 upozorenja niskog i srednjeg nivoa, bez visokih | zavisnosti zastarevaju | uskoro |
| CI koristi `mvn`, lokalno `./mvnw`. `ubuntu-latest` prelazi na Ubuntu 26 | ista verzija Mavena i stabilan runner | kad bude zgodno |
| Indeks za pretragu po nazivu (`pg_trgm`) | `LIKE '%x%'` ne koristi običan indeks, pa je sporo na velikom katalogu | kad katalog naraste |
| Jedinstveno ime kategorije po roditelju, SKU bez razlike velikih i malih slova | otvoreno iz pregleda šeme | kad krenu potkategorije |
| Greške validacije upita (`/api/products?size=0`) imaju opštu poruku bez detalja po polju | tela porudžbine već imaju listu po polju | nizak prioritet |
| Agent `schema-reviewer` nije bio dostupan, pregled `V1` je urađen ručno | drugo mišljenje | ako se plugin `backend-design` učita |
| Ime vlasnika u `LICENSE` je GitHub nalog | javno je vidljivo | po želji |
| Proxy `/api/*` koji čita adresu backenda u runtime-u (route handler umesto `rewrites`, samo `GET /api/variants` i `POST /api/orders`) | adresa se ugrađuje pri build-u, pa jedna slika ne radi u više okruženja | pre deploya |
| Actuator na posebnom portu i backend dostupan samo Next serveru | `rewrites` nisu bezbednosna granica | pre deploya |
| Pun CSP sa nonce-om i pinovanje akcija po SHA uz Dependabot | dodatna odbrana | pre deploya |
| `server.forward-headers-strategy` i `X-Forwarded-For` za ograničenje učestalosti | iza proxy-ja svi korisnici dolaze sa IP adrese Next servera | Faza 3 |
| Rezervacija zalihe u korpi (korpa koja "drži" artikal) | kupac može da vidi "na stanju" i dobije `422` pri porudžbini | uz prijavu, Faza 3 ili kasnije |
| Atomski `UPDATE ... RETURNING` umesto `SELECT ... FOR UPDATE` | brže ako jedna varijanta postane usko grlo (ADR 0003) | kad merenje pokaže potrebu |
| Količina u korpi može da bude veća od zalihe dok kupac ne pošalje porudžbinu (API vraća samo `inStock`, ne tačnu količinu) | kupac saznaje tek na checkout-u, ali dobija tačne stavke | uz rezervaciju ili "još samo N komada" |
| `next dev` sam pravi `frontend/AGENTS.md` | fajl se pojavljuje kao nepraćen posle svakog pokretanja | odlučiti: `.gitignore` ili commit |
| Kopija projekta u `Documents` koristi istu Docker bazu i port 3000/8080 | upisala je svoje `V4` i `V5` u lokalnu dev bazu, pa naš backend ne startuje | odmah, pre sledećeg lokalnog pokretanja |
| Uzastopni brojevi porudžbina otkrivaju obim prodaje | konkurent iz dve porudžbine računa koliko ih ima | Faza 3, javni nasumični token |

## Urađeno

- **Faza 0** (2026-11-06): skelet preko Spring Initializr-a (Spring Boot 4.1.2, najnovija stabilna umesto planiranog 3.5.7), `V1` iz `reference/`, `V2` (ograničenja i indeksi) sa testovima, README, plan u `docs/ROADMAP.md`, MIT `LICENSE`. JDK 21.0.13 je instaliran ručno u `~/.local/jdk` (SDKMAN traži `zip`, a `sudo` nije bio dostupan), a Node.js 26.13.0 u `~/.local/node` (WSL je video samo Windows `npm`).
- **1.1**: `V3` (`image_url`), dev seed kroz repeatable migraciju, `GET /api/categories` i `GET /api/products` (pretraga, filter, sortiranje, paginacija, 2 SQL upita), springdoc 3.2.0.
- **1.2**: Next.js frontend sa listom proizvoda, frontend posao u CI-ju, GitHub akcije na `checkout@v7`, `setup-java@v6`, `setup-node@v7`.
- **1.3**: `GET /api/products/{id}`, stranica detalja sa putanjom, kartica je link, prava 404 stranica.
- **1.4**: `POST /api/orders` (kopira naziv, SKU i cenu u stavke, ukupno u `BigDecimal`, jedna transakcija), `GET /api/variants?ids=`, greške po polju i `422`.
- **1.5**: korpa u `localStorage`, `/cart`, `/checkout`, `/checkout/success`, broj komada u zaglavlju. Cela kupovina proverena u browseru.
- **1.6, Faza 1 zatvorena**: ROADMAP checklista, status u README-u, ADR 0001 i 0002, pregled duga, `CLAUDE.md` na Fazu 2.
- **Revizija Faze 1** (4 paralelna recenzenta: backend, bezbednost, frontend, testovi i dokumenta, svaki nalaz proveren u kodu ili stvarnim zahtevom): nije bilo P0. Ispravljeno: stranica greške nije ponovo učitavala podatke, `500` bez `ProblemDetail` formata, neograničen `page` i telo zahteva, kontrolni znakovi i razlomljeni brojevi u porudžbini, podrazumevana lozinka i Swagger u svim profilima, fokus i pristupačnost korpe i forme, kontrast, bezbednosna zaglavlja, slabi testovi i netačnosti u dokumentima. Odbačeno: izlazak iz `/api/*` kroz proxy (5 varijanti, sve 404 ili 400) i NUL u pretrazi (vraća 200). 53 backend i 110 frontend testova.
- **2.1**: zaliha se proverava i skida pri porudžbini pod pesimističkim zaključavanjem, redovi u rastućem redosledu `variant_id` ([ADR 0003](adr/0003-pessimistic-locking-for-stock.md)). `422` sa `insufficientStockVariantIds`, `503` sa `Retry-After` kad čekanje na red istekne. Test sa 50 kupaca je prvo pao (50 porudžbina na 1 komad), pa tek onda rešenje. Pravi rollback test bez test transakcije. 62 backend testa.
- **2.2**: `V4` sa tabelom `stock_movements` (`IN`, `OUT`, `ADJUSTMENT`). Baza proverava predznak promene prema tipu, da samo `OUT` ima porudžbinu, da korekcija ima razlog i da stanje posle promene nije negativno. Postojeća zaliha dobija početni red (`Opening balance`), pa je zbir kretanja uvek jednak stanju. `StockService.receive` i `adjust` postoje samo kao servis, HTTP rute dolaze uz uloge u Fazi 3. Porudžbina se sad upisuje pre skidanja zalihe (kretanje pokazuje na nju), pa rollback test zaista poništava upisan red. Plugin `backend-design` nije instaliran, pa je `V4` pregledan ručno. 72 backend testa.
- **2.3, Faza 2 zatvorena**: checkout prikazuje koje stavke nemaju dovoljno na stanju (`insufficientStockVariantIds`, sa nazivom i varijantom) i osvežava dostupnost, a za `503` kaže da je prodavnica zauzeta. Provereno u browseru na privremenoj bazi. README status. 72 backend i 116 frontend testova.
