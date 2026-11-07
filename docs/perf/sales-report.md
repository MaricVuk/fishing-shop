# Merenje: izveštaj prodaje (5.2)

Cilj: proveriti odluku "živi upiti, bez keša" ([DECISIONS](../DECISIONS.md)) na obimu za 5 godina, pre nego što se doda indeks ili keš.

## Podaci

Poseban Postgres 16 kontejner (`fishing-shop-perf`, port 5433). Šema je napravljena pokretanjem backenda sa profilom `dev` (Flyway i demo katalog sa 33 varijante), a porudžbine su ubačene skriptom [`backend/perf/seed-orders.sql`](../../backend/perf/seed-orders.sql):

| Tabela | Redova | Veličina |
|---|---|---|
| `orders` | 547.500 (5 godina × 300 dnevno, 4 % otkazano) | 63 MB |
| `order_items` | 1.046.700 | |

Mašina: laptop, WSL2, Docker Desktop, podrazumevana Postgres podešavanja (`work_mem` 4 MB, `jit` uključen). Svaki broj je medijana od pet uzastopnih pokretanja, uz topao keš.

## Rezultati

Upit je isti kao u `SalesReportService`, a za `EXPLAIN` je u [`backend/perf/explain-sales.sql`](../../backend/perf/explain-sales.sql).

| Period | Prvo rešenje (`EXPLAIN ANALYZE`) | Prvo saberi, pa spoji | + `jit = off` | Kroz API (`curl`) |
|---|---|---|---|---|
| 30 dana po danima (8.453 porudžbine) | 31 ms | 17 ms | 17 ms | 40 ms |
| godina po nedeljama (105.522) | 300 ms | 139 ms | 128 ms | 150 ms |
| 5 godina po mesecima (525.873) | 1.230 ms | 650 ms | 480 ms | 530 ms |

Prvo rešenje je mereno samo kroz `EXPLAIN ANALYZE`, koji meri svaki red i sam dodaje vreme. Ostale SQL kolone su merene bez njega (`\timing` u `psql`), a poslednja kolona je ceo HTTP zahtev sa prijavljenim vlasnikom.

## Šta je plan pokazao

1. **Spajanje pre sabiranja.** Prvo rešenje je svaku porudžbinu spajalo sa nizom perioda (`generate_series`), pa tek onda grupisalo. Za 5 godina to znači sortiranje 525k redova kroz disk (`Sort Method: external merge Disk: 20856kB`, `Merge Left Join`). Kad se porudžbine prvo saberu po periodu (`HashAggregate`, 61 red), spajanje sa nizom je trivijalno. Indeks to ne bi rešio.
2. **JIT koji košta više nego što donosi.** Posle prve ispravke planer i dalje procenjuje 525k grupa umesto 61 (ne zna koliko meseci ima u 5 godina), pa cena prelazi `jit_above_cost` i Postgres kompajlira upit: `JIT: Timing: ... Total 300.791 ms` na upitu od 780 ms. Rešenje je `SET LOCAL jit = off` samo u transakciji izveštaja, bez izmene podešavanja baze.
3. **Indeks nije potreban.** Kraći periodi već koriste `idx_orders_created` (`Bitmap Heap Scan`). Period od 5 godina čita 96 % tabele, pa je `Seq Scan` ispravan izbor, a ostatak vremena odlazi na pretvaranje vremena u beogradsko i grupisanje po mesecu za svaki red. Pokrivajući indeks `(created_at) INCLUDE (status, total)` ne smanjuje taj rad, pa nije dodat.

## Dopuna iz 5.3

Merenje najprodavanijih ([product-reports.md](product-reports.md)) je otkrilo da i ovaj upit kroz API ponekad dobija generički plan pripremljene naredbe. Od 5.3 oba izveštaja koriste `SET LOCAL plan_cache_mode = force_custom_plan` (`ReportSession`), pa kroz API sada traju: 30 dana 25 ms, godina po nedeljama 105 ms, 5 godina po mesecima 355 ms.

## Odluka

Bez indeksa i bez keša. Najgori slučaj, ceo period od 5 godina, kroz API traje oko 0,5 s, što je na granici praga iz DECISIONS (500 ms). Taj izveštaj otvara jedan vlasnik povremeno, a uobičajeni periodi traju 40 do 150 ms. Ako obim poraste, sledeći korak je noćna zbirna tabela po danu, samo za zatvorene dane.

## Kako ponoviti

```bash
docker run -d --name fishing-shop-perf --shm-size=512m -e POSTGRES_DB=shop_perf -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=perf_only -p 127.0.0.1:5433:5432 postgres:16
```

```bash
cd backend && ./mvnw -q -DskipTests package && DB_URL=jdbc:postgresql://localhost:5433/shop_perf DB_USER=shop DB_PASSWORD=perf_only SHOP_JOBS_WORKER_ENABLED=false SERVER_PORT=8091 java -jar target/fishing-shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Kad backend krene (Flyway i demo katalog), zaustavi ga, pa ubaci porudžbine i pokreni `EXPLAIN`:

```bash
docker cp backend/perf/seed-orders.sql fishing-shop-perf:/tmp/ && docker exec fishing-shop-perf psql -U shop -d shop_perf -v years=5 -v per_day=300 -f /tmp/seed-orders.sql
```

```bash
docker cp backend/perf/explain-sales.sql fishing-shop-perf:/tmp/ && docker exec fishing-shop-perf psql -U shop -d shop_perf -v from=2021-11-01 -v to=2026-11-07 -v unit=month -f /tmp/explain-sales.sql
```

`--shm-size` je obavezan za paralelne izveštaje ([load test](load-test.md)), a port je samo na `127.0.0.1` zbog slabe lozinke. Kontejner se briše sa `docker rm -f fishing-shop-perf`, a njegov anonimni volumen sa `docker volume rm <ime iz docker inspect>`. Dev baza se ne dira.
