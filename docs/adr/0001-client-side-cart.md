# ADR 0001: Korpa u browseru

Status: prihvaćeno (2026-11-06, Faza 1)

## Kontekst

Spring Boot je jedini backend i nema sesija. Kupci su gosti, jer prijava dolazi tek u Fazi 3. Korpa mora da radi već u Fazi 1 i da prikazuje tačne cene.

## Odluka

- Korpa živi u `localStorage` i čuva samo `variantId` i količinu (najviše 50 stavki i 99 komada po stavci).
- Naziv, atributi, cena i dostupnost se uvek čitaju sa servera preko `GET /api/variants?ids=`.
- Porudžbina se šalje jednim zahtevom (`POST /api/orders`), a server ponovo učitava varijante i računa ukupnu cenu. Cena iz browsera se nikad ne koristi.
- Stanje u Reactu je `useSyncExternalStore` nad `localStorage`, bez dodatne biblioteke. To daje sinhronizaciju između kartica i izbegava nepoklapanje pri hidrataciji.

## Posledice

Dobro:

- Backend ostaje bez sesija i bez stanja korpe.
- U korpi nikad nema zastarele cene, a nedostupna stavka se odmah označi.
- Nema CORS-a, jer browser zove isti domen kroz `rewrites` u Next.js.

Loše:

- Korpa se ne deli između uređaja i nije vezana za nalog. Kad se uvede prijava (Faza 3), treba je spojiti sa nalogom pri logovanju.
- Korpa ne "drži" zalihu. Rezervacija je posebno pitanje Faze 2.
- Korisnik može da izmeni `localStorage`, pa server proverava sve: postojanje varijante, količinu i cenu.
- Svaki prikaz korpe pravi jedan dodatni zahtev.

## Razmotreno

- **Korpa na serveru** (sesija ili tabela u bazi): traži sesiju ili token pre nego što postoji prijava, pa komplikuje Fazu 1 bez koristi.
- **Cena kopirana u `localStorage`**: najjednostavnije, ali korpa može da prikazuje cenu koja više ne važi.
