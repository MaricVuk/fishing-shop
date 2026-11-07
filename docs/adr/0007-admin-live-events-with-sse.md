# ADR 0007: Uživo obaveštenja u adminu kroz Server-Sent Events umesto WebSocket-a

Status: prihvaćeno (2026-11-07, Faza 5)

## Kontekst

Faza 5 traži da zaposleni u adminu vide događaje bez osvežavanja stranice: nova porudžbina i prodat poslednji komad varijante. ROADMAP je od početka pisao "WebSocket obaveštenja", a pitanje je izazvano agentom `boring-tech-advisor` (WebSocket, Server-Sent Events ili osvežavanje stranice).

Činjenice:

- Poruke idu samo u jednom smeru, od servera ka adminu. Admin ništa ne šalje kroz isti kanal.
- Desetak zaposlenih, jedna instanca backenda.
- Prijava je sesija u Postgresu sa `HttpOnly` kolačićem i CSRF zaštitom ([ADR 0004](0004-employee-login-with-server-sessions.md)). Browser zove backend samo kroz `/api/*` na istom domenu (Next.js `rewrites`).
- Obaveštenje je pomoć, a ne izvor istine: porudžbina i zaliha se uvek čitaju iz baze.

## Odluka

`GET /api/admin/events` vraća `text/event-stream` kroz Spring MVC `SseEmitter`, bez nove zavisnosti.

- Pristup proverava isto pravilo uloga kao ostatak admina. To je `GET` sa istim kolačićem sesije, pa CSRF ne učestvuje.
- Događaj nastaje tek **posle commit-a** (`@TransactionalEventListener(phase = AFTER_COMMIT)`), pa poništena porudžbina ne šalje ništa.
- Id događaja je id reda koji ga opisuje: `orders.id` za novu porudžbinu, `stock_movements.id` za kretanje `OUT` posle kojeg je `quantity_after = 0`. Kad se veza prekine, browser pri ponovnom povezivanju šalje `Last-Event-ID`, a server iz tih tabela šalje ono što je propušteno (ograničen broj redova). Nema nove tabele ni događaja u memoriji koji bi se izgubili pri restartu.
- Server šalje heartbeat komentar na svakih 15 do 20 sekundi, sa zaglavljima `Cache-Control: no-cache, no-transform` i `X-Accel-Buffering: no`.
- Ako veza ne radi, admin radi kao i do sada: stranica je sveža koliko i poslednje učitavanje.

Uslov pre koda: proba na produkcionom build-u frontenda (`next build && next start`) koja pokazuje da događaji kroz `rewrites` stižu bez baferovanja, i koliko dugo proxy drži otvorenu vezu. Ako proba ne uspe, rešenje je osvežavanje (upit na 15 s sa istim "posle id-ja" pravilom), a ovaj ADR se dopunjuje.

## Posledice

Dobro:

- Ugrađeno u Spring MVC i u browser (`EventSource`), sa ponovnim povezivanjem i `Last-Event-ID` bez dodatnog koda.
- Bezbednost ostaje ista kao za svaki drugi `GET` u adminu, bez posebne zaštite poruka.
- Propušteni događaji se nadoknađuju iz tabela koje već postoje.

Loše:

- Svako ponovno povezivanje je nov zahtev koji osvežava sesiju, pa otvoren tab ne ističe posle 30 minuta neaktivnosti, nego tek posle 8 sati (najduže trajanje sesije).
- Posle odjave ili isteka sesije `EventSource` sam ponavlja zahteve i dobija `401`, pa frontend mora da proveri `/api/auth/me` i da zatvori vezu.
- Svaki otvoren tab drži jedan asinhroni zahtev na serveru. Na ovom obimu to nije bitno.
- Sa više instanci backenda događaj nastaje samo na instanci koja je primila porudžbinu. Tada se dodaje `pg_notify` u transakciji i jedna `LISTEN` veza po instanci, a replay ostaje isti.

## Kada ponovo razmotriti

- Admin treba da šalje poruke kroz isti kanal (dopisivanje, zajedničko uređivanje): tada WebSocket.
- Izabrani deploy ili Next proxy ne mogu da drže otvoren tok: tada osvežavanje.
- Više instanci backenda: `LISTEN/NOTIFY`, kao gore.

## Razmotreno

- **WebSocket (Spring STOMP)**: dvosmeran kanal koji ovde nije potreban. Traži `spring-boot-starter-websocket`, posebnu zaštitu poruka (podrazumevano i CSRF token u STOMP `CONNECT` okviru), `Upgrade` zahtev koji mora da prođe kroz Next proxy, a nadoknadu propuštenih događaja bi i dalje trebalo napisati.
- **Osvežavanje stranice ili upit na 15 s**: najjednostavnije i radi svuda, ali kasni i šalje zahteve i kad se ništa ne dešava. Ostaje kao rezervni put.
- **Događaji kroz red poslova (ADR 0006)**: kašnjenje od 5 s zbog intervala workera i ponovni pokušaji "bar jednom" za nešto što je samo obaveštenje. Pogrešan alat.
