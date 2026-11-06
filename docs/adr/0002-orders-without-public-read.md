# ADR 0002: Porudžbine bez javnog čitanja

Status: prihvaćeno (2026-11-06, Faza 1)

## Kontekst

Kupac je gost, a porudžbina sadrži lične podatke: ime, adresu e-pošte, telefon i adresu. Redni brojevi porudžbina su uzastopni. Kad bi postojao `GET /api/orders/{id}` bez prijave, svako bi mogao da prolazi kroz brojeve i čita tuđe podatke.

## Odluka

- Postoji samo `POST /api/orders`. Čitanja porudžbine nema dok ne postoji prijava i uloge (Faza 3).
- Odgovor na kreiranje sadrži broj, status, ukupnu cenu i stavke, ali ne i podatke o kupcu.
- Stranica potvrde prikazuje taj odgovor iz `sessionStorage`. U novoj kartici ili posle zatvaranja browsera potvrde nema, i to je namerno.
- Porudžbina je jedna transakcija: ako bilo koja stavka nije dostupna, server odgovara sa `422` i ništa se ne upisuje.
- Faza 1 ne proverava i ne skida zalihu. To se radi atomski u Fazi 2, pa se ne bi pisalo dvaput.

## Posledice

Dobro:

- Nema curenja ličnih podataka preko pogađanja brojeva.
- Nema lažne sigurnosti od provere zalihe koja bi bila podložna trci.

Loše:

- Kupac ne može kasnije da pogleda svoju porudžbinu. Potvrda mejlom dolazi u Fazi 4, a pregled "moje porudžbine" uz prijavu u Fazi 3.
- U Fazi 1 se može poručiti više nego što ima na stanju. Korpa blokira stavke bez zalihe, ali server to ne proverava.
- Redni brojevi porudžbina otkrivaju obim prodaje: ko napravi dve porudžbine u razmaku od nekoliko dana, oduzimanjem dobija broj porudžbina. Prihvatljivo za demo, a rešenje je javni nasumični token.
- Dvostruko slanje (dupli klik) pravi dve porudžbine. Frontend onemogućava dugme, a prava zaštita je idempotentni ključ (Faza 4). Ograničenje učestalosti dolazi uz prijavu (Faza 3).

## Razmotreno

- **Javno čitanje po nasumičnom tokenu** (UUID uz svaku porudžbinu): bezbedno za goste i dobro za praćenje porudžbine, ali traži novu kolonu i migraciju. Ostaje opcija za kasnije.
- **Odgovor sa podacima o kupcu**: nepotrebno, jer ih kupac upravo uneo.
