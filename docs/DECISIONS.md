# Odluke

Spisak odluka sa kratkim obrazloženjem. Veće odluke dobijaju poseban ADR u `docs/adr/` ([0001 korpa u browseru](adr/0001-client-side-cart.md), [0002 porudžbine bez javnog čitanja](adr/0002-orders-without-public-read.md), [0003 zaključavanje zalihe](adr/0003-pessimistic-locking-for-stock.md), [0004 prijava sa sesijom](adr/0004-employee-login-with-server-sessions.md), [0005 audit log](adr/0005-append-only-audit-log.md), [0006 red poslova u Postgresu](adr/0006-job-queue-in-postgres.md)). Poslednje ažuriranje: 2026-11-06.

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
| Prijava zaposlenih | Sesija u Postgresu (Spring Session JDBC), `HttpOnly` kolačić, BCrypt, CSRF i na prijavi, sve što nije dozvoljeno je zabranjeno ([ADR 0004](adr/0004-employee-login-with-server-sessions.md)) | odjava i otkaz deluju odmah, nema tajnog ključa, desetak zaposlenih |
| Admin frontend | `/admin` je u istom Next.js-u, sa svojim layoutom (shop rute su u grupi `(shop)`). `proxy.ts` samo preusmerava bez kolačića `SESSION`, a stvarnu proveru radi backend (`/api/auth/me`) na svakoj stranici. Meni i pristup po ulozi su u jednoj tabeli (`lib/admin-nav.ts`) | proxy nije mesto za autorizaciju (preporuka Next.js-a), backend je merodavan |
| Audit log | Tabela `audit_log` u istoj bazi, trigger odbija `UPDATE`, `DELETE` i `TRUNCATE`, upis u istoj transakciji kao izmena (`MANDATORY`), dozvoljene akcije su `CHECK` lista ([ADR 0005](adr/0005-append-only-audit-log.md)) | istorija ne može da se prepiše, a audit ne može da opiše izmenu koja se nije desila |
| Magacin u adminu | `/api/admin/stock` za vlasnika i magacionera (`@PreAuthorize`): lista sa pretragom (i neaktivne varijante), detalj, poslednjih 200 kretanja, prijem (1 do 100.000) i popis (uz obavezan razlog) | magacioner vidi sve što fizički postoji, ne samo ono što je u prodaji |
| Status porudžbine | `NEW → CONFIRMED → SHIPPED → DELIVERED`, otkazivanje samo iz `NEW` ili `CONFIRMED`; nedozvoljen prelaz je `409`. Red porudžbine se zaključava (`FOR UPDATE`) dok se status menja | dva prodavca ne mogu istovremeno da otkažu istu porudžbinu |
| Otkazivanje vraća zalihu | Vraća se onoliko koliko je porudžbina stvarno skinula (zbir njenih kretanja), kao `RETURN`; jedinstven indeks u bazi ne dozvoljava drugo vraćanje iste varijante | porudžbine iz Faze 1 nikad nisu skidale zalihu, pa ne smeju ni da je vrate |
| Ograničenje porudžbina | Najviše 20 porudžbina sa iste adrese za 10 minuta (`429` sa `Retry-After`), brojač u memoriji | zaštita od spama; po adresi, ne po email-u, da niko ne može da blokira tuđi email |
| Zaposleni | Samo vlasnik dodaje naloge, menja ulogu, isključuje i uključuje nalog i postavlja novu lozinku; svako menja svoju lozinku uz trenutnu. Vlasnik ne može da menja sopstveni nalog, a radnja uvek ima bar jednog aktivnog vlasnika (sve izmene prvo zaključaju aktivne vlasnike, redom po id-ju) | dva vlasnika koja istovremeno skidaju jedan drugog ne mogu da ostave radnju bez vlasnika |
| Popis magacina | Popis broji **policu**: server oduzima jedinice otvorenih porudžbina (`NEW`, `CONFIRMED`) koje su još na polici, pa slobodno za prodaju = izbrojano - rezervisano; manje od rezervisanog je `422`. Audit čuva izbrojano, rezervisano i razliku | inače bi popis ponovo prodao robu koja već ima kupca, a otkazivanje bi je vratilo dvaput |
| Gornja granica zalihe | 1.000.000 po varijanti: prijem preko toga je `422`, a baza ima `CHECK` | prijem nije imao gornju granicu, pa je ponavljan prijem mogao da prelije `int` u `500` |
| Tekst bez nevidljivih znakova | Polja sa slobodnim tekstom odbijaju kontrolne i format znakove (`\p{Cntrl}`, `\p{Cf}`: smer pisanja, zero-width), pa ime ne može da izgleda kao drugo ime u tabeli | lažiranje izgleda imena i razloga u admin pregledu i audit logu |
| Email zaposlenog | Samo običan ASCII, najviše 100 znakova | baza i Java različito spuštaju neka slova, pa bi isti nalog imao više "pisanja" |
| Gašenje sesija | Promena uloge, isključenje naloga i nova lozinka brišu sesije tog zaposlenog običnim SQL-om u istoj transakciji, a ne kroz Spring Session repozitorijum | repozitorijum briše u svojoj transakciji, pa bi poništena izmena ipak odjavila zaposlenog (uhvaćeno testom) |
| Lozinke zaposlenih | 12 do 64 znaka i najviše 72 bajta | BCrypt čita samo prva 72 bajta, pa bi duža lozinka bila tiho skraćena |
| Red poslova (Faza 4) | Tabela u Postgresu: posao se upisuje u istoj transakciji kao porudžbina (outbox), worker u istom procesu uzima poslove sa `FOR UPDATE SKIP LOCKED`, backoff i stanje `DEAD`. Bez RabbitMQ-a ([ADR 0006](adr/0006-job-queue-in-postgres.md)) | mali obim, atomičnost iz baze, nijedan novi servis za testove i deploy; izazvano agentom `boring-tech-advisor` |
| Neuspeh posla | Svaka greška obrađivača poništava celu transakciju posla, a neuspeh (`attempts`, backoff, `last_error`) se upisuje u novoj transakciji. Savepoint nije moguć: Hibernate-ov `JpaDialect` ga ne podržava | jedan put za sve greške, i za transakciju koja ne može da se commit-uje; obrađivač sme da koristi JPA. Cena: između poništavanja i upisa drugi worker može jednom da ponovi posao |
| Ponovni pokušaji | 14 pokušaja, 30 s duplo raste do 6 h, nasumični deo 50-100 % (ukupno 13 do 26 sati). `PermanentJobFailure` (npr. adresa koju mejl server ne prihvata) odmah daje `DEAD` | prekid SMTP-a od nekoliko sati ne sme da pretvori sve mejlove u `DEAD` (pregled `incident-thinker`); besmisleno je ponavljati ono što ne može da uspe |
| Mejl | `spring-boot-starter-mail`, običan tekst, stalan `Message-ID` (`order-confirmation-{id}@domen`). U razvoju Mailpit iz docker-compose (`localhost:8025`), u testovima Mailpit u Testcontainers-u. SMTP tajmauti 5 s za vezu i 30 s za odgovor. Gašenje čeka do 45 s da se započet posao završi | Mailpit ne šalje ništa napolje; duži tajmaut za odgovor smanjuje duplikate kad server prihvati mejl, a sporo odgovori |
| Račun (Faza 4) | Nastaje kad zaposleni potvrdi porudžbinu: posao `ORDER_INVOICE` se upisuje u istoj transakciji kao promena statusa. Porudžbina otkazana pre nego što posao krene ne dobija račun | račun za porudžbinu koja će možda biti otkazana nema smisla |
| Broj računa | `RN-{godina}-{000001}`, bez rupa: brojač po godini u `invoice_counters` (`INSERT ... ON CONFLICT DO UPDATE ... RETURNING`, red ostaje zaključan do kraja transakcije). Godina po vremenu u Beogradu | sekvenca zadržava brojeve poništenih transakcija, pa bi u numeraciji bilo rupa |
| Čuvanje računa | PDF kao `BYTEA` u `invoices`, jedan po porudžbini, trigger brani `UPDATE`, `DELETE` i `TRUNCATE` (kao audit log) | izdat račun se ne menja; kad bi se pravio iznova, izgledao bi drugačije posle promene koda |
| PDF | Apache PDFBox 3.0.8, ručan raspored, A4, više strana sa ponovljenim zaglavljem tabele. Fontovi Barlow i Barlow Condensed (OFL, `resources/fonts`). Zamene glifova (GSUB) su isključene. Znak koji font nema postaje `?` | PDFBox je Apache 2.0 i ista biblioteka u testovima čita tekst iz PDF-a. Ligatura "ij" se ne može kopirati ni pročitati kao slova. Emoji u imenu ne sme da obori posao |
| PDV na računu | Cene sadrže PDV. PDV = ukupno x stopa / (100 + stopa), zaokružen `HALF_UP` na 2 decimale, osnovica = ukupno - PDV. Stopa je u `shop.invoice.vat-rate` (20) | obračun na nivou računa, a ne stavke, pa je osnovica + PDV uvek tačno ukupno |
| Podaci prodavca | Iz env varijabli (`SHOP_SELLER_*`), u dev profilu izmišljeni. Na računu stoji "Ovo nije fiskalni račun" | repo je javan; fiskalizacija je van opsega |

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
| Gde se deploy-uje (Fly.io, Render, Railway, VPS) | pre prvog deploya | mora da ima javni link |
| Konačan naziv shopa | pre deploya | trenutno radni naslov |
| Plaćanje karticom | verovatno van opsega | portfolio ne mora da prima pravi novac |
| Više jezika (i18n) | samo ako zatreba | |

## Dug i otvorene stvari

Pregled na kraju Faze 1, dopunjen na kraju Faze 2. Nijedna stavka ne blokira Fazu 3.

| Stvar | Zašto je bitno | Kad |
|---|---|---|
| Storno račun kad se potvrđena porudžbina otkaže | sada račun ostaje, a porudžbina je otkazana; isto važi za otkazivanje u trenutku dok posao pravi račun (obrađivač namerno ne zaključava porudžbinu, da admin ne bi čekao na SMTP) | uz 4.3 ili posle Faze 4 |
| Fiskalizacija (ESIR) | prodaja fizičkim licima u Srbiji traži fiskalni račun; ovaj PDF to nije | van opsega portfolija |
| Validacija mejla u porudžbini (`@Email` sa labavim regexp-om) je blaža od `InternetAddress(..., true)` u slanju | adresa kao `a@b@c.rs` prođe API, a posao potvrde odmah ide u `DEAD` | uz 4.3 ili ranije: ista provera u API-ju |
| Nadzor reda: broj `DEAD` poslova, starost najstarijeg zakasnelog `PENDING`, vreme poslednjeg prolaza workera | danas se zaglavljen worker ili `DEAD` vidi samo u logu i SQL-u ([RUNBOOK](RUNBOOK.md)) | uz strukturirane logove i metrike |
| Brisanje starih `DONE` poslova (npr. starijih od 90 dana) | tabela samo raste, oko 100k redova godišnje pri sadašnjem obimu | kad zatreba |
| Ponovljen mejl potvrde koristi trenutno stanje porudžbine | posle više sati ponavljanja kupac otkazane porudžbine dobije "primili smo" | nizak prioritet |
| JDBC `socketTimeout` i tajmaut transakcije workera; transakcioni pgbouncer ili `idle_in_transaction_session_timeout` bi oslobodili zaključavanje usred slanja | bez toga zaglavljena veza zaustavlja worker; uz takav proxy dva workera mogu da pošalju isti mejl | pre deploya, uz izbor baze |
| Idempotentni ključ za porudžbinu | dupli klik ili ponovljen zahtev pravi dve porudžbine (frontend već onemogućava dugme) | Faza 4 |
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
| Poseban korisnik baze za aplikaciju: bez superkorisničkih prava, bez `ALTER`/`TRUNCATE`, samo `SELECT` i `INSERT` na `audit_log`; Flyway sa drugim nalogom | sada je `shop` superkorisnik (zvanična slika), pa može da isključi trigger audit loga, zaobiđe ga sa `session_replication_role`, pa i da izvrši komande na serveru baze | pre deploya |
| Ponovni unos lozinke pri pravljenju vlasnika, promeni uloge u vlasnika i postavljanju tuđe lozinke | ukradena vlasnikova sesija može da napravi nalog koji nadživi sesiju (recenzija 3.6) | uz 2FA |
| `__Host-` prefiks kolačića (`__Host-SESSION`, `__Host-XSRF-TOKEN`) | podsajt koji kontroliše neko drugi može da podmetne CSRF kolačić; važi samo ako produkcioni domen ima takve podsajtove | pre deploya |
| Vlasnik može da postavi lozinku drugom vlasniku i da radi kao on | po dizajnu, ali "ko je šta uradio" slabi; vlasnik koji je kompromitovan može da skine ostale vlasnike | uz 2FA; razmotriti obavezu promene lozinke pri sledećoj prijavi |
| Prijave, odjave i neuspešni pokušaji nisu u audit logu (samo izmene podataka); prvi vlasnik iz env varijabli je samo u logu aplikacije | audit odgovara na "ko je šta promenio", a ne na "ko se prijavio" | ako zatreba |
| `next` putanja se gubi kad sesija istekne (kolačić ostaje u browseru, pa `proxy.ts` ne dodaje `next`) | posle istekle sesije kraj prijave se otvara `/admin`, a ne ista stranica | nizak prioritet |
| Provera `SELECT quantity <> zbir kretanja` nije zakazana, a baza ne brani zbir | ručna izmena `inventory` tiho kvari pravilo "stanje je zbir istorije" | pre deploya (ili kao test/poslednja provera) |
| Promena sopstvene lozinke radi dva BCrypt-a dok drži red zaposlenog (`FOR UPDATE`) | kratko blokira audit upis istog naloga; nije deadlock | nizak prioritet |
| Porudžbine napravljene dok je radio kod iz 2.1 (pre `V4`) skinule su zalihu bez `OUT` reda, pa otkazivanje ništa ne vraća | samo lokalne baze koje su baš tada imale porudžbinu; ništa nije u produkciji | reset dev baze |
| Dev seed spaja proizvode po imenu (ime nije jedinstveno) | proizvod napravljen kasnije sa istim imenom može da pokvari seed; samo dev | nizak prioritet |
| Obavezan HTTPS (kolačić sesije sa `Secure`, preusmeravanje sa HTTP-a, HSTS) | bez njega se kolačić sesije zaposlenog šalje nešifrovano | pre deploya |
| Dvofaktorska prijava (2FA, npr. TOTP aplikacija) bar za vlasnika | ukradena lozinka vlasnika daje pristup svemu | posle Faze 3, pre deploya |
| Actuator na posebnom portu i backend dostupan samo Next serveru | `rewrites` nisu bezbednosna granica | pre deploya |
| Pun CSP sa nonce-om i pinovanje akcija po SHA uz Dependabot | dodatna odbrana | pre deploya |
| `X-Forwarded-For` je pouzdan samo dok backend vidi samo Next server (`forward-headers-strategy: native` je uključen u 3.1) | inače posetilac može da lažira adresu i zaobiđe ograničenje po adresi | pre deploya |
| Brojači za prijavu i porudžbine su u memoriji | sa više instanci svaka broji za sebe, restart ih briše | ako bude više instanci |
| Rezervacija zalihe u korpi (korpa koja "drži" artikal) | kupac može da vidi "na stanju" i dobije `422` pri porudžbini | uz prijavu, Faza 3 ili kasnije |
| Atomski `UPDATE ... RETURNING` umesto `SELECT ... FOR UPDATE` | brže ako jedna varijanta postane usko grlo (ADR 0003) | kad merenje pokaže potrebu |
| Količina u korpi može da bude veća od zalihe dok kupac ne pošalje porudžbinu (API vraća samo `inStock`, ne tačnu količinu) | kupac saznaje tek na checkout-u, ali dobija tačne stavke | uz rezervaciju ili "još samo N komada" |
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
- **3.1**: prijava zaposlenih ([ADR 0004](adr/0004-employee-login-with-server-sessions.md)). `V5` (`employees`, tabele Spring Session-a), `POST /api/auth/login` i `logout`, `GET /api/auth/me` i `csrf`, BCrypt 12, `429` posle 5 promašaja, sesija najviše 8 sati, prvi vlasnik iz env varijabli, tri dev naloga. Test pravog toka browsera na pravom HTTP serveru, jer MockMvc menja CSRF repozitorijum testnim. 92 backend testa.
- **3.2**: admin okvir. Shop rute su premeštene u grupu `(shop)`, pa admin nema korpu u zaglavlju. Stranice: `/admin/login` (CSRF iz kolačića, poruke za `401` i `429`, `next` dozvoljava samo `/admin` putanje), `/admin` sa karticama po ulozi, i stranice porudžbina, magacina, zaposlenih i audit loga sa proverom uloge i porukom "u izradi". Proxy preusmerava bez sesije. Provereno za sve tri uloge kroz Next proxy, a stranica prijave u browseru. 135 frontend testova.
- **3.3**: `V6` audit log (trigger protiv izmene, brisanja i `TRUNCATE`), `AuditService` u istoj transakciji, API magacina za vlasnika i magacionera, stranice `/admin/stock` (pretraga, stranice) i `/admin/stock/{id}` (prijem, popis, istorija). Rollback test kad audit ne može da se upiše, potvrđen mutacionim testom. Provereno kroz Next proxy na dev bazi: prijem, popis, audit redovi, prodavac dobija `403`. 105 backend i 146 frontend testova.
- **3.4**: porudžbine u adminu za vlasnika i prodavca: lista sa filterom po statusu i pretragom (broj, ime, email), detalj sa kupcem i istorijom, promena statusa po pravilima (`409` za nedozvoljen korak), otkazivanje vraća zalihu (`V7`: tip `RETURN`, jedinstven indeks, nove akcije u auditu, indeksi za listu). Ograničenje javnih porudžbina po adresi. Testovi: 10 istovremenih otkazivanja daju jedno vraćanje zalihe (mutacioni test bez zaključavanja pada), rollback kad audit ne može da se upiše, stara porudžbina bez skinute zalihe. Provereno kroz Next proxy na dev bazi. 117 backend i 152 frontend testa.
- **3.5**: zaposleni i audit log u adminu. `V8` (akcije za naloge, indeksi). `/api/admin/employees` i `/api/admin/audit` samo za vlasnika, `POST /api/auth/password` za svakog. Stranice: zaposleni (lista, dodavanje), detalj zaposlenog (uloga, pristup, nova lozinka, istorija), Moj nalog, Audit log sa filterom. Test je našao da Spring Session briše sesije van transakcije; ispravljeno. Mutacioni test bez zaključavanja vlasnika pada u 3 od 3 pokretanja. Provereno kroz Next proxy na dev bazi. 130 backend i 160 frontend testova.
- **3.6, sigurnosni pregled Faze 3** (4 nezavisna recenzenta na Opus-u, samo čitanje: prijava i sesije, autorizacija i API, baza i podaci, admin frontend; 36 nalaza, svaki proveren u kodu ili stvarnim zahtevom). **Dva P1, oba potvrđena i ispravljena:** (1) ograničenje prijave se zaobilazilo kodiranom putanjom (`/api/auth/%6Cogin` je posle zaključavanja dao `200` sa tačnom lozinkom; isto za ograničenje porudžbina), (2) prijava koja je u toku dok vlasnik isključuje ili menja nalog dobijala je sesiju koja je trajala do 8 sati (test sa zaustavljenom prijavom, bez filtera `200`). **Četiri P2, ispravljena:** različito spuštanje slova (`İ`) davalo je svakom zapisu emaila svoj budžet pokušaja, neograničen rast mape brojača (email do 2 MB), zaključavanje vlasnika sa tuđe adrese (sada par 5 / email 100 / adresa 50), i popis koji je brojao policu kao slobodnu zalihu. **Ostalo (P3):** ispravljeno ili upisano kao dug gore (`__Host-`, ponovni unos lozinke, vlasnik-vlasniku, nepokrivene prijave, superkorisnik baze). Odbačeno ili samo dokumentovano: nijedan nalog nije odbačen kao netačan, a tri su ostala kao poznati kompromis. Usput nađena moja greška iz 3.4: istorija zaliha nije imala naziv za tip `RETURN`. Migracija `V9` (verzija sesije, jedinstven `OUT` po porudžbini i varijanti, `CHECK` na zalihu, dva neiskorišćena indeksa uklonjena). Mutacioni testovi: bez filtera stanja zaposlenog svih 5 testova sesija pada.
- **4.0, odluka o redu poslova**: agent `boring-tech-advisor` (definicija iz plugina `backend-design`, pokrenut kao opšti agent jer plugin nije učitan) izazvao je RabbitMQ. Presuda: red u Postgresu. [ADR 0006](adr/0006-job-queue-in-postgres.md), stack u `CLAUDE.md`, README-u i ROADMAP-u više ne pominje RabbitMQ.
- **4.1, red poslova i mejl potvrde**: `V10` (`background_jobs`: outbox i red u jednom, `PENDING`/`DONE`/`DEAD`, jedan posao po porudžbini i tipu), upis posla u transakciji porudžbine, `JobWorker` (`FOR UPDATE SKIP LOCKED`, jedan posao po transakciji, backoff, `DEAD`), `@Scheduled` na 5 s (u testovima isključen, test sam pokreće worker), mejl potvrde i Mailpit. Testovi: atomičnost upisa, dva workera nad 20 poslova, worker kojem je ubijena veza (`pg_terminate_backend`), transakcija koja ne može da se commit-uje, SQL greška u obrađivaču, `0x00` u poruci greške, trajna greška, jedan mejl posle dva prolaza. Mutacije: bez `FOR UPDATE SKIP LOCKED` padaju testovi dva workera i mrtvog workera, a bez putanje za trajnu grešku i čišćenja znakova padaju sva 3 nova testa. Savepoint je odbačen jer ga Hibernate-ov `JpaDialect` ne podržava. Pregled `incident-thinker` (definicija iz plugina, opšti agent): duži prozor ponavljanja, trajna greška, čekanje pri gašenju, duži SMTP tajmaut i čišćenje kontrolnih znakova su urađeni; nadzor, brisanje starih poslova i tajmauti baze su upisani kao dug. [RUNBOOK](RUNBOOK.md) sa upitima za stanje reda i vraćanje `DEAD` poslova.
- **4.2, PDF račun**: `V11` (`invoice_counters`, `invoices` sa triggerom protiv izmene, tip posla `ORDER_INVOICE`), posao pri potvrdi porudžbine, `InvoiceService` (broj bez rupa, PDV, upis), `InvoicePdf` (PDFBox, Barlow), `OrderInvoiceHandler` (mejl sa PDF prilogom; ponovljen posao šalje već izdat račun), `ShopMail` (zajedničko slanje za oba mejla), `GET /api/admin/orders/{id}/invoice` za vlasnika i prodavca (`no-store`), stanje računa u detalju porudžbine (`NONE`/`PENDING`/`ISSUED`/`FAILED`) i odeljak "Račun" u adminu. Testovi: posao samo pri potvrdi, prvi broj u godini, nastavak od brojača, poništen račun ne ostavlja rupu, 20 istovremenih računa dobija 1..20, otkazana porudžbina bez računa, ponovljen posao bez drugog računa, emoji u imenu, trigger, preuzimanje po ulogama, PDV sa zaokruživanjem, Vitest za prikaz stanja. Usput: PDFBox 3 primenjuje ligaturu "ij", pa je izvučen tekst glasio "Ovo n e fiskalni"; zamene glifova su isključene. Savepoint nije bio potreban.
