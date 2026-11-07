# Merenje: najprodavaniji i niske zalihe (5.3)

Ista baza kao za [izveštaj prodaje](sales-report.md): kontejner `fishing-shop-perf`, 547.500 porudžbina i 1.046.700 stavki za 5 godina, uz demo katalog od 33 varijante. Upit za najprodavanije je u [`backend/perf/explain-best-sellers.sql`](../../backend/perf/explain-best-sellers.sql).

## Rezultati

| Najprodavaniji | Prvo rešenje (`count(DISTINCT)`) | `count(*)` + `V14` | Kroz API pre `force_custom_plan` | Kroz API posle |
|---|---|---|---|---|
| 30 dana | 34 ms | 27 ms | 72 ms | 73 ms |
| godina | 450 ms | 125 ms | 315 ms | 135 ms |
| 5 godina | 2.050 ms | 410 ms | 1.300 ms | 425 ms |

SQL kolone su merene u `psql` sa `\timing` i `jit = off` (medijana od pet pokretanja), a API kolone su ceo HTTP zahtev sa prijavljenim vlasnikom (medijana od deset).

Niske zalihe: 12 ms kroz API. Upit čita samo katalog (varijante, proizvode, zalihu), pa ne raste sa brojem porudžbina.

## Šta je merenje pokazalo

1. **`count(DISTINCT order_id)` je bio najskuplji deo.** Da bi brojao različite porudžbine po varijanti, Postgres sortira milion stavki po `(variant_id, order_id)` kroz disk (`Sort Method: external merge Disk: 41424kB`), pa tek onda sabira. Sa `count(*)` sabiranje ide kroz heš tabelu u memoriji, i 5 godina pada sa 2 s na 0,4 s.
2. **`count(*)` je tačan samo ako je varijanta najviše jednom po porudžbini.** `OrderService` to radi od 1.4 (spaja duplikate), ali baza to nije branila. Migracija `V14` dodaje jedinstven indeks `(order_id, variant_id)` i briše stari `idx_order_items_order`, jer ga novi indeks pokriva prvom kolonom. Indeks se nad 1M stavki gradi za ~0,44 s, a perf baza nije imala nijedan duplikat.
3. **Generički plan pripremljene naredbe.** Kroz API je isti upit bio 3 puta sporiji nego u `psql`-u. JDBC drajver posle nekoliko izvršavanja prelazi na pripremljenu naredbu, a Postgres tada može da izabere generički plan, koji ne zna koliki je period: procena je 2.738 porudžbina umesto 525.873, pa bira petlju kroz indeks za svaku porudžbinu (`PREPARE` + `force_generic_plan`: 1,28 s, `force_custom_plan`: 0,41 s). Rešenje je `SET LOCAL plan_cache_mode = force_custom_plan` u transakciji izveštaja (`ReportSession`), zajedno sa `jit = off`. Usput je i izveštaj prodaje za 5 godina pao sa 530 na 355 ms kroz API, jer ga je pogađao isti problem.
4. **JIT ovde nije bio uzrok.** Isključen je kao kod prodaje, ali kroz API se vreme nije promenilo, što je i navelo na pripremljenu naredbu.

## Odluka

Bez keša i bez zbirne tabele: najgori slučaj (5 godina) je oko 0,4 s, ispod praga od 500 ms. Jedina izmena šeme je `V14`, koja je istovremeno pravilo podataka i preduslov za brz upit.
