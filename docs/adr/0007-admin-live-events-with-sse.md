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

## Proba (5.4)

Proba je urađena pre koda, sa probnim endpointom koji šalje događaj na svake 2 s, kroz `next dev` i kroz produkcioni build (`next build && next start`):

| Provera | Rezultat |
|---|---|
| Tok kroz `rewrites`, bez kompresije | događaji stižu odmah, i u dev i u produkciji |
| Browser šalje `Accept-Encoding: gzip` | **Next kompresuje odgovor i zadržava ceo tok**: za 9 s nije stigao nijedan događaj |
| Isto, uz `Cache-Control: no-cache, no-transform` | događaji ponovo stižu odmah; Next-ova kompresija preskače `no-transform` odgovore |
| Tok bez ikakvih podataka 40 s | **Next proxy prekida vezu posle 30 s** (`proxyTimeout`) |
| Događaj na svakih 20 s | veza drži, provereno 70 s |

Posledice za implementaciju: zaglavlje `Cache-Control: no-cache, no-transform` je obavezno, a heartbeat ide na svakih 15 s (`shop.events.heartbeat`). Pravi endpoint je zatim proveren istim putem: veza je kroz produkcioni Next trajala duže od 30 s uz heartbeat, a događaj za porudžbinu je stigao u istoj sekundi.

## Implementacija (5.4)

- Domenski događaji `OrderPlaced` i `VariantSoldOut` objavljuju se u transakciji porudžbine (prvo porudžbina, pa rasprodate varijante), a `AdminEventBroadcaster` ih šalje tek posle commit-a.
- Sadržaj događaja se čita iz baze (`AdminEventFeed`), isti upit služi i za nadoknadu.
- Id događaja je kursor `{poslednja porudžbina}-{poslednje kretanje zalihe}`; šalje se i uz heartbeat, pa browser uvek zna dokle je stigao, i kad su događaji za druge uloge preskočeni.
- Konekcija bez `Last-Event-ID` počinje od trenutnog stanja (bez istorije); sa njim dobija najviše 50 propuštenih porudžbina i 50 rasprodatih varijanti.
- Tok traje najviše 5 minuta, pa se browser ponovo poveže. Novi zahtev ponovo prolazi proveru sesije, pa isključen nalog ili promenjena uloga deluju najkasnije za 5 minuta. Najviše 200 otvorenih tokova (`503` preko toga), od 5.7 i najviše 10 po zaposlenom.
- Uloge: novu porudžbinu vide vlasnik i prodavac, poslednji komad vlasnik i magacioner.
- Pad jedne veze ne sme da utiče na ostale: svaka greška pri upisu ili zatvaranju veze se obrađuje unutar te veze (zatvoren tab je uobičajen, a `completeWithError` iz tuđe niti ume da baci izuzetak), pa se slanje drugima nastavlja.

### Dopuna posle pregleda koda (5.7)

- **Ponavljanje se prepoznaje po samom događaju, ne po redosledu id-jeva.** Porudžbine dobijaju id pri upisu, a commit-uju se drugim redom, pa porudžbina 100 može da stigne posle 101. Ranije je kursor (najveći viđeni id) takvu porudžbinu odbacivao i povezanim adminima; sada svaka veza pamti poslednjih 500 poslatih događaja, a kursor služi samo da browser zna odakle da nastavi.
- **Nova veza ne gubi događaj koji se commit-uje dok se otvara**: posle registracije se uvek pošalje i ono što je u bazi posle pročitanog kursora (obično ništa).
- **Slanje browserima ide u posebnoj niti** (`admin-events`), ne u niti kupčevog zahteva i ne u niti scheduler-a: spora ili poluotvorena admin veza ne usporava porudžbinu ni pozadinske poslove. Scheduler ima 2 niti (`spring.task.scheduling.pool.size`), pa heartbeat ne čeka na job worker kad SMTP sporo odgovara.
- **Najviše 10 tokova po zaposlenom** (`429`) i 200 ukupno (`503`), proveravano atomično.
- **Admin stranica sama otvara novi tok** posle odgovora koji nije `200` (backend se restartuje, server je zauzet): browser to ne radi sam. Ponovni pokušaj čeka 2 s, pa duplo, najviše 1 minut, a novi tok nastavlja od poslednjeg viđenog id-ja preko parametra `?lastEventId=` (zaglavlje `Last-Event-ID` browser šalje samo pri svom automatskom ponovnom povezivanju).

Poznata ograničenja koja ostaju: jedna nit za slanje znači da jedna zaglavljena veza usporava slanje ostalima dok upis ne istekne; nadoknada posle prekida ide po id-ju, pa porudžbina sa manjim id-jem koja se commit-uje dok je veza prekinuta ne stiže kao obaveštenje.

## Posledice

Dobro:

- Ugrađeno u Spring MVC i u browser (`EventSource`), sa ponovnim povezivanjem i `Last-Event-ID` bez dodatnog koda.
- Bezbednost ostaje ista kao za svaki drugi `GET` u adminu, bez posebne zaštite poruka.
- Propušteni događaji se nadoknađuju iz tabela koje već postoje.

Loše:

- Svako ponovno povezivanje je nov zahtev koji osvežava sesiju, pa otvoren tab ne ističe posle 30 minuta neaktivnosti, nego tek posle 8 sati (najduže trajanje sesije).
- Nadoknada ide po id-ju, a ne po vremenu commit-a: porudžbina sa manjim id-jem koja se commit-uje dok je veza prekinuta, a posle one sa većim, neće stići kao obaveštenje. Obaveštenje je pomoć, a lista porudžbina je izvor istine.
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
