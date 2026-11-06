# ADR 0004: Prijava zaposlenih sa sesijom u bazi

Status: prihvaćeno (2026-11-06, Faza 3)

## Kontekst

Faza 3 uvodi zaposlene (vlasnik, prodavac, magacioner) i zaštićen `/admin` deo. Zaposlenih je desetak, kupci ostaju gosti bez naloga. Frontend i backend su na istom domenu, jer browser zove samo `/api/*` kroz Next.js.

Šta mora da važi:

- Odjava, otkaz ili promena uloge deluju odmah, a ne kad istekne neki token.
- JavaScript ne može da pročita ništa čime se predstavlja prijava, pa XSS ne može da ukrade sesiju.
- Javni shop ostaje bez prijave.

## Odluka

**Sesija na serveru, sačuvana u Postgresu (Spring Session JDBC), sa `HttpOnly` kolačićem.**

- Prijava je `POST /api/auth/login` (forma: `email`, `password`) kroz Spring Security `formLogin`. Spring tako sam menja id sesije posle prijave (session fixation) i rotira CSRF token.
- Lozinke su BCrypt hash sa cenom 12. Nalog koji nije aktivan proverava se tek posle lozinke, pa pogrešna lozinka, nepostojeći email i deaktiviran nalog daju isti odgovor (`401`) i traju isto.
- Kolačić `SESSION` je `HttpOnly`, `Secure` (osim u profilu `dev`) i `SameSite=Lax`. Sesija ističe posle 30 minuta neaktivnosti, a najkasnije 8 sati posle prijave.
- CSRF zaštitu ima svaki zahtev koji menja stanje, uključujući prijavu i odjavu: kolačić `XSRF-TOKEN` koji frontend vraća u zaglavlju `X-XSRF-TOKEN` (`csrf.spa()`). Izuzetak je samo javni `POST /api/orders`, jer tamo nema sesije koja bi se zloupotrebila.
- Ograničenje pokušaja ima tri ključa, svaki na 15 minuta (`429` sa `Retry-After`): **5** neuspeha za par email i adresa, **100** za sam email (usporava napad sa mnogo adresa) i **50** za adresu. Par je strog, a email sam širok, da niko ne može jeftino da zaključa vlasnika iz tuđe adrese. Email koji nije običan ASCII (ili je duži od 100 znakova) deli jednu kantu po adresi, jer baza i Java ne spuštaju neka slova (npr. `İ`) isto, pa bi svaki zapis dobio svoj budžet. Takav email nikad ne nalazi nalog. Ograničenje se proverava istim matcherom putanje koji koristi sama prijava (kodirana putanja `/api/auth/%6Cogin` je ranije prolazila pored ograničenja), a prijava sa parametrima u URL-u se odbija.
- **Verzija sesije:** `employees.session_version` raste pri promeni uloge, isključenju i svakoj promeni lozinke. Sesija pamti verziju, ulogu i aktivnost naloga iz trenutka prijave, a filter na svakom zahtevu (jedno čitanje po ključu) poredi to sa bazom i prekida sesiju koja se ne slaže. Brisanje sesija u istoj transakciji ostaje brz put, ali ne hvata prijavu koja je u toku dok vlasnik menja nalog: takva sesija se upiše tek posle brisanja. To je pokazao test sa zaustavljenom prijavom (bez filtera dobija `200` posle isključenja naloga).
- Promena sopstvene lozinke gasi **sve** sesije, i trenutnu, pa se zaposleni prijavljuje novom lozinkom.
- Sve što nije izričito dozvoljeno je zabranjeno (`denyAll`). Nepoznata ruta za anonimnog posetioca daje `401`, pa se iz odgovora ne vidi koje rute postoje.
- Prvi vlasnik se pravi iz `SHOP_OWNER_EMAIL` i `SHOP_OWNER_PASSWORD` (najmanje 12 znakova), i to samo dok nema nijednog zaposlenog. Lozinka nikad nije u repou. Profil `dev` ima po jedan test nalog za svaku ulogu.

## Posledice

Dobro:

- Odjava, isključenje naloga, promena uloge i nova lozinka brišu sesije iz baze, a verzija sesije hvata i one koje se upišu naknadno.
- Nema tajnog ključa za potpis, pa nema ni ključa koji može da procuri.
- Sesije preživljavaju restart backenda i rade sa više instanci, bez Redisa.
- Sve teške stvari (fixation, CSRF, poređenje lozinke) radi Spring Security, a ne naš kod.

Loše:

- Svaki zahtev zaposlenog čita sesiju iz baze. Za desetak ljudi to je zanemarljivo.
- Brojači neuspelih prijava su u memoriji, pa sa više instanci svaki broji za sebe, a restart ih briše. Mapa je ograničena na 10.000 ključeva (najstariji se izbacuju), a ključ je kratak.
- Svaki zahtev zaposlenog čita jedan red iz `employees` (provera verzije sesije).
- Adresa posetioca dolazi iz `X-Forwarded-For` koji šalje Next. To je pouzdano tek kad je backend dostupan samo Next serveru (dug pred deploy).
- Uloga je zapisana u sesiji, ali je svaki zahtev poredi sa bazom, pa promena uloge deluje odmah.

## Razmotreno

- **JWT access token + refresh token**: odjava i otkaz ne deluju odmah bez crne liste, a crna lista je opet tabela u bazi. Uz to dolaze tajni ključ, rotacija i otkrivanje krađe refresh tokena. Prednosti JWT-a (više nezavisnih servisa, mobilna aplikacija) ovde ne postoje.
- **Sesija samo u memoriji (Tomcat)**: nula novih zavisnosti, ali restart odjavljuje sve, ne radi sa više instanci, i ne može da se nađu sve sesije jednog zaposlenog.
- **Argon2 umesto BCrypt-a**: noviji algoritam, ali traži BouncyCastle. BCrypt je u Spring Security-ju i dovoljan je.
- **Token u `localStorage`**: čitljiv iz JavaScript-a, pa jedan XSS znači krađu sesije.
- **Dvofaktorska prijava**: korisna bar za vlasnika, zapisana kao dug pred deploy, zajedno sa obaveznim HTTPS-om, `__Host-` prefiksom kolačića, i ponovnim unosom lozinke pri pravljenju vlasnika i postavljanju tuđe lozinke (ukradena vlasnikova sesija sad može da napravi nalog koji nadživi sesiju).
- **Provera aktivnosti samo brisanjem sesija (prva verzija ove odluke)**: nedovoljno, vidi "Verzija sesije".
