# ADR 0008: Pravi deploy je odložen, Faza 6 priprema sistem za deploy

Status: prihvaćeno (2026-11-07, Faza 6)

## Kontekst

Plan je od početka imao javni deploy kao kraj projekta ("README mora da ima link koji radi"). Na početku Faze 6 `boring-tech-advisor` je izazvao izbor platforme, baze i nadzora. Autor je zatim odlučio da je projekat demo za portfolio i da se pravi deploy planira tek ako se softver bude prodavao.

Činjenice:

- Pravi deploy traži stvari van repoa: hosting, domen, mejl provajdera i naloge koji se plaćaju.
- Domen je preduslov za HTTPS, HSTS i `__Host-` kolačiće.
- Većina bezbednosnog i operativnog duga se može zatvoriti i bez hostinga, i dokazati testom ili `compose` okruženjem na lokalnoj mašini.

## Odluka

Faza 6 priprema sistem za deploy, ali ga ne pušta u rad. Ulazi sve što se radi i proverava lokalno: Dockerfile za backend i frontend i ceo sistem jednom komandom, poseban korisnik baze sa tajmautima, Actuator na posebnom portu i backend dostupan samo Next serveru, pun CSP, proxy koji čita adresu backenda u runtime-u, idempotentni ključ za porudžbinu, nasumični javni token porudžbine, storno račun, Dependabot, revizija korisničkog interfejsa i end-to-end test cele kupovine.

Odlaže se do dana kad se projekat bude prodavao: izbor platforme, VPS, domen, mejl provajder, javni link, HTTPS, HSTS i `__Host-` kolačići, 2FA, nadzor sa uzbunama, backup sa restore testom i `claude-security` sken. Dug sa tim stavkama dobija rok "pre prvog pravog deploya".

## Sačuvan plan za taj dan

Presuda `boring-tech-advisor` (Faza 6, 2026-11-07), da se ne izvodi ponovo:

| Pitanje | Presuda | Zašto |
|---|---|---|
| Platforma | Jedan VPS sa 2 GB (npr. Hetzner, oko 12 € mesečno), Docker Compose i Caddy | Fly nema besplatan nivo za nove naloge, a tri mašine i volumen izlaze oko 21 $ mesečno. Render free se uspavljuje posle 15 min i nema trajni disk, pa worker i SSE ne mogu da rade. Na VPS-u nema lock-ina. |
| Baza | Kontejner `postgres:16` sa `shm_size`, bez PgBouncera | Upravljani Postgres na Flyju košta 38 $ mesečno. Role `shop_migrator` (vlasnik, Flyway) i `shop_app` (bez superkorisnika, bez `UPDATE`, `DELETE` i `TRUNCATE` na `audit_log`), tajmauti na roli i u JDBC URL-u. Dnevni `pg_dump -Fc`, enkriptovan, u spoljni storage, uz stvaran restore test. |
| Nadzor | Spoljni uptime ping, cron skripta nad Actuator metrikama reda i Healthchecks.io | Bez Prometheusa i Grafane dok nema skupljača koji koristi istoriju. `worker.last.poll` je `NaN` dok worker nije prošao, pa skripta to posle zagrevanja računa kao grešku. |

Cene Flya, Rendera i Hetznera su proverene na stranicama provajdera u novembru 2026. i treba ih proveriti ponovo. Iz pamćenja i neproverene ostaju Railway, Oracle Always Free, uslovi UptimeRobota i Healthchecks.io, ponašanje PgBouncera sa `SKIP LOCKED` i Spring Session i tačan raspon podrške za PG 16.

Spike lista pre nego što kombinacija postane odluka:

1. RSS memorija sva tri procesa na 2 GB i na 1 GB pod k6 scenarijima.
2. Potvrda tačnog plana, cene, PDV-a i regiona.
3. `shop_app` ne može da isključi trigger audit loga ni da postavi `session_replication_role`, a Flyway kao `shop_migrator` prolazi `V1` do poslednje migracije.
4. Pun restore dumpa u čist kontejner, aplikacija se diže, meri se trajanje.
5. Actuator na posebnom portu: metrike dostupne iz Docker mreže bez kolačića, nedostupne spolja.
6. SSE kroz Caddy (heartbeat 15 s, prekid tihe veze posle 30 s, bez baferovanja, preživljava `docker compose up -d`).
7. `idle_in_transaction_session_timeout` oslobađa zaključavanje zaglavljenog workera, a `socketTimeout` ne seče izveštaje.
8. Mrtvi-čovek lanac: namerno srušen worker i backup cron moraju da pošalju uzbunu u roku od 10 min.

## Posledice

Dobro:

- Faza 6 se završava bez troška, naloga i tajni van repoa, a sistem se i dalje pokreće jednom komandom.
- Odluke o platformi se ne donose bez stvarnog opterećenja i cene, pa se ne troše na hosting koji niko ne koristi.
- Ništa od onoga što je agent proverio ne mora da se istražuje iznova.

Loše:

- README nema javni link, pa poslodavac projekat vidi samo kroz kod, dokumentaciju i lokalno pokretanje.
- HTTPS, HSTS, `__Host-` kolačići i 2FA nisu dokazani na pravom okruženju. Kolačić sesije je i dalje `Secure` van `dev` profila, ali bez HTTPS-a ne radi, pa se pravi sistem ne može pustiti u rad dok se ti koraci ne urade.
- Nadzor i backup postoje samo kao plan i postupak u [RUNBOOK](../RUNBOOK.md)-u.

## Kada ponovo razmotriti

- Pojavi se kupac ili plan prodaje softvera: ovaj ADR postaje ulaz u planiranje deploy faze.
- Poslodavac ili autor traži javnu demo adresu: tada je dovoljno besplatno ili jeftino okruženje bez 2FA, ali pre toga treba zatvoriti HTTPS i `__Host-` i isključiti `dev` profil.

## Razmotreno

- **Pun deploy u Fazi 6**: javni link je jači za portfolio, ali traži domen, mejl provajdera i trajan trošak za projekat koji niko ne koristi. Odbačeno na zahtev autora.
- **Besplatan nivo (Render, Fly probni kredit)**: spava ili ističe, pa ne drži worker i SSE. Ne ostavlja pravi trag u README-u.
- **Ništa nije pripremljeno, deploy kad zatreba**: Docker, uloge u bazi i CSP bi se tada radili pod pritiskom roka. Jeftinije je da sistem već bude spreman.
