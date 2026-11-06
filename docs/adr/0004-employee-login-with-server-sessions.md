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
- Posle 5 neuspešnih pokušaja za isti email, ili 50 sa iste adrese, prijava je blokirana 15 minuta (`429` sa `Retry-After`).
- Sve što nije izričito dozvoljeno je zabranjeno (`denyAll`). Nepoznata ruta za anonimnog posetioca daje `401`, pa se iz odgovora ne vidi koje rute postoje.
- Prvi vlasnik se pravi iz `SHOP_OWNER_EMAIL` i `SHOP_OWNER_PASSWORD` (najmanje 12 znakova), i to samo dok nema nijednog zaposlenog. Lozinka nikad nije u repou. Profil `dev` ima po jedan test nalog za svaku ulogu.

## Posledice

Dobro:

- Odjava briše sesiju iz baze. Kad u 3.5 dođe deaktivacija, ona će brisati sve sesije tog zaposlenog.
- Nema tajnog ključa za potpis, pa nema ni ključa koji može da procuri.
- Sesije preživljavaju restart backenda i rade sa više instanci, bez Redisa.
- Sve teške stvari (fixation, CSRF, poređenje lozinke) radi Spring Security, a ne naš kod.

Loše:

- Svaki zahtev zaposlenog čita sesiju iz baze. Za desetak ljudi to je zanemarljivo.
- Brojač neuspelih prijava je u memoriji, pa sa više instanci svaka broji za sebe, a restart ga briše.
- Adresa posetioca dolazi iz `X-Forwarded-For` koji šalje Next. To je pouzdano tek kad je backend dostupan samo Next serveru (dug pred deploy).
- Uloga je zapisana u sesiji. Promena uloge mora da obriše sesije tog zaposlenog (3.5).

## Razmotreno

- **JWT access token + refresh token**: odjava i otkaz ne deluju odmah bez crne liste, a crna lista je opet tabela u bazi. Uz to dolaze tajni ključ, rotacija i otkrivanje krađe refresh tokena. Prednosti JWT-a (više nezavisnih servisa, mobilna aplikacija) ovde ne postoje.
- **Sesija samo u memoriji (Tomcat)**: nula novih zavisnosti, ali restart odjavljuje sve, ne radi sa više instanci, i ne može da se nađu sve sesije jednog zaposlenog.
- **Argon2 umesto BCrypt-a**: noviji algoritam, ali traži BouncyCastle. BCrypt je u Spring Security-ju i dovoljan je.
- **Token u `localStorage`**: čitljiv iz JavaScript-a, pa jedan XSS znači krađu sesije.
- **Dvofaktorska prijava**: korisna bar za vlasnika, zapisana kao dug pred deploy, zajedno sa obaveznim HTTPS-om.
