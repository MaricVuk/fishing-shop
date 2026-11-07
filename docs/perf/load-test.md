# Opterećenje: k6 nad bazom sa podacima za 5 godina (5.6)

Cilj: videti kako backend radi kad više korisnika radi istovremeno, i dokazati da zaliha ne ide ispod nule ni pod opterećenjem. Skripte su u [`backend/perf/k6/`](../../backend/perf/k6/), a pokreće ih `run.sh`.

## Postavka

| | |
|---|---|
| Podaci | [perf baza](sales-report.md): 547.500 porudžbina i 1.046.465 stavki (5 godina), demo katalog sa 33 varijante |
| Backend | Spring Boot sa `dev` profilom, jedna instanca, Tomcat 200 niti, Hikari pool 10 (podrazumevano), `SHOP_ORDERS_MAX_PER_ADDRESS=1000000` (ograničenje po adresi bi odgovaralo `429`), `SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS=-1` (vidi niže) |
| Baza | Postgres 16 u Dockeru, `--shm-size=512m` (vidi niže) |
| Alat | k6 2.3.0 kroz Docker sliku `grafana/k6` |
| Mašina | laptop sa 12 logičkih procesora i 32 GB, WSL2, Docker Desktop. **Generator opterećenja, backend i baza rade na istoj mašini**, pa su brojevi relativni, a ne tvrdnja o kapacitetu servera |

Svaki scenario se pokreće dva puta, a **računa se drugi**: prvi zagreva JVM i keš baze (u scenariju kataloga je hladan prvi pokušaj bio oko 30 % sporiji).

## Rezultati

| Scenario | Opterećenje | Zahteva | Zahteva/s | p50 | p95 | max | Greške |
|---|---|---|---|---|---|---|---|
| **Katalog** (lista, pretraga, detalj, kategorije) | 50 virtuelnih korisnika, 40 s, bez pauze | 120.465 | **3.000** | 15,0 ms | **35,5 ms** | 123 ms | 0 % |
| **Porudžbine, jedna varijanta** (zaliha 100) | 50 korisnika, 30 s | 30.662 | 1.019 | 48,0 ms | **85,6 ms** | 393 ms | 0 % |
| **Porudžbine, rasute** (1 do 3 stavke, 33 varijante) | 30 korisnika, 30 s | 19.006 | 630 | 45,9 ms | **72,2 ms** | 139 ms | 0 % |
| **Izveštaji** (pregled, prodaja 30 dana, najprodavaniji 30 dana, niske zalihe; zatim 5 korisnika sa izveštajima za 5 godina) | 20 korisnika vlasnika bez pauze 30 s, pa 5 korisnika 30 s | 2.730 | 41 | 238 ms | 630 ms | 1.620 ms | 0 % |

Izveštaji po zahtevu (isti run):

| Zahtev | p50 | p95 | max |
|---|---|---|---|
| pregled (`/api/admin/dashboard`) | 236 ms | 368 ms | 459 ms |
| prodaja, 30 dana | 201 ms | 319 ms | 430 ms |
| najprodavaniji, 30 dana | 385 ms | 612 ms | 865 ms |
| niske zalihe | 115 ms | 253 ms | 354 ms |
| prodaja, 5 godina po mesecima (5 korisnika, posle tipičnog) | 609 ms | 652 ms | 671 ms |
| najprodavaniji, 5 godina (5 korisnika) | 646 ms | 724 ms | 1.620 ms |

Katalog po zahtevu: lista 36 ms, pretraga 35 ms, detalj 36 ms, kategorije 35 ms (p95).

## Zaliha pod opterećenjem

Scenario "jedna varijanta": zaliha varijante je podešena na **100**, a 50 korisnika bez pauze 30 s šalje porudžbine od po 1 komad. Ishod, proveren upitima u bazi posle svakog pokretanja:

| | Ishod |
|---|---|
| Uspešne porudžbine (`201`) | **100** |
| Odbijene zbog zalihe (`422`) | 30.562 |
| `503` (isteklo čekanje na red) | 0 |
| Ostali odgovori | 0 |
| Zaliha posle | **0** |
| Prodato komada (`order_items`) | **100** |
| Kretanja `OUT` | **100** |
| Redova zalihe ispod nule | 0 |

Pod 50 istovremenih kupaca za isti red, tačno onoliko porudžbina koliko ima komada prolazi, ostale dobijaju `422`, i niko ne čeka dovoljno dugo za `503`. To je ista garancija kao test sa 50 kupaca iz Faze 2 ([ADR 0003](../adr/0003-pessimistic-locking-for-stock.md)), ali na pravom HTTP serveru, sa 30.000 pokušaja i bazom sa milion stavki.

## Šta je opterećenje otkrilo

1. **Deljena memorija Postgresa.** Prvi pokušaj izveštaja sa više korisnika je dao 76 od 151 zahteva `500` ("najprodavaniji, 5 godina"): `could not resize shared memory segment ... No space left on device`. Paralelni upiti Postgresa koriste `/dev/shm`, a Docker ga podrazumevano ograničava na 64 MB. Jedan korisnik to ne vidi, pet istovremenih velikih izveštaja vidi. Ispravka: `--shm-size=512m` za perf bazu i `shm_size: 256mb` u `docker-compose.yml`. **Važi i za deploy**: svaka Postgres instanca u kontejneru treba dovoljno `/dev/shm`, ili `max_parallel_workers_per_gather = 0`.
2. **Mreža Docker Desktopa, ne backend.** Scenario kataloga je u više od 5 uzastopnih pokušaja imao zastoj: oko 25 do 40 s u testu sve virtuelne korisnike je zaustavio `dial: i/o timeout`, a test je trajao 70 s umesto 40. Backend je za to vreme odgovarao u 2 ms (direktan `curl`), a brojači `ListenDrops` i `ListenOverflows` su bili 0. Najverovatniji uzrok (nije dokazan direktno): Tomcat zatvara vezu posle 100 zahteva, pa je pri 1.500 zahteva u sekundi stalno nastajalo novo povezivanje, a ponovno korišćenje izvornog porta iza NAT-a Docker Desktopa se sudara sa vezama u `TIME_WAIT` na serveru (novi `SYN` se ignoriše do ponovnog slanja). Potvrda je da sa `SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS=-1` u dva pokretanja nije bilo nijednog pada, a test je trajao tačno 40 s. To je podešavanje test okruženja, ne izmena produkcije. U produkciji Next server drži mali skup veza ka backendu.
3. **Hikari pool je usko grlo kataloga.** Pri 3.000 zahteva/s Hikari je imao svih 10 veza zauzetih i 23 do 32 niti koje čekaju, a odziv je i dalje bio 35 ms (p95). Ako bi katalog ikad trebalo da radi pri većem opterećenju, `spring.datasource.hikari.maximum-pool-size` je prvo podešavanje; ovde nije menjano.
4. **Izveštaji pod konkurencijom skaliraju slabo, ali predvidivo.** Najprodavaniji za 30 dana traje 73 ms kad je sam, a 385 ms (p50) kad 20 korisnika bez pauze traži izveštaje, jer se 12 procesora deli sa Postgresom i generatorom opterećenja. To je test izdržljivosti (20 vlasnika koji bez pauze osvežavaju), ne realno opterećenje: radnja ima jednog do tri vlasnika. Prag od 500 ms za pojedinačan zahtev iz [DECISIONS](../DECISIONS.md) važi za izveštaje bez konkurencije i tamo je ispunjen ([sales-report](sales-report.md), [product-reports](product-reports.md)).
5. **Hladan prvi pokušaj.** U scenariju kataloga prvo pokretanje 2.148 zahteva/s (p95 54 ms), a drugo 3.000 (p95 35 ms). U porudžbinama 838 naspram 1.019 zahteva/s. Zato se računa drugo pokretanje.

## Kako ponoviti

Perf bazu napravi prema [sales-report](sales-report.md) (sa `--shm-size`), pokreni backend nad njom sa dve dodatne promenljive:

```bash
cd backend && ./mvnw -q -DskipTests package && DB_URL=jdbc:postgresql://localhost:5433/shop_perf DB_USER=shop DB_PASSWORD=perf_only SHOP_JOBS_WORKER_ENABLED=false SHOP_ORDERS_MAX_PER_ADDRESS=1000000 SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS=-1 SERVER_PORT=8091 java -jar target/fishing-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Pa u drugom terminalu, jedan po jedan (`run.sh` pokreće scenario dva puta, vraća zalihu pre svakog pokretanja i posle "jedne varijante" proverava bazu):

```bash
backend/perf/k6/run.sh catalog
```

```bash
backend/perf/k6/run.sh orders-hot
```

```bash
backend/perf/k6/run.sh orders-spread
```

```bash
backend/perf/k6/run.sh reports
```

Kontejner `grafana/k6` dolazi do backenda preko IP adrese WSL-a (`hostname -I`), jer `localhost` iz kontejnera ne vodi do WSL-a. Sirovi rezultati ostaju u `backend/perf/k6/results/` (nije u gitu). Perf baza posle testova ima oko 33.000 dodatnih porudžbina; za čist ponovljen test napravi je iznova.
