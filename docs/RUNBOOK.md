# Runbook

Šta raditi kad nešto u radu shopa krene naopako. Svaka stavka ima simptom, proveru i oporavak.

## Pozadinski poslovi (Faza 4)

Poslovi su redovi u tabeli `background_jobs` ([ADR 0006](adr/0006-job-queue-in-postgres.md)). Worker radi u backend procesu i na svakih 5 sekundi uzima poslove kojima je došlo vreme. Isporuka je "bar jednom": ako backend padne posle slanja mejla, a pre upisa `DONE`, kupac dobije isti mejl dvaput.

### Nadzor bez admina (od 5.1)

Vlasnik (sa kolačićem sesije) ili sistem za nadzor čita stanje direktno sa backenda; `/actuator` ne ide kroz Next proxy.

- `GET /actuator/health`: za vlasnika komponenta `jobs` ima `pending`, `dead`, `done`, `resolved`, `oldestOverdueSeconds` i `workerLastPollSecondsAgo`. Uvek je `UP`, pa se uzbuna ne zasniva na njoj.
- `GET /actuator/metrics/shop.jobs?tag=status:DEAD`: broj odustalih poslova. Uzbuna kad je veći od 0.
- `GET /actuator/metrics/shop.jobs.worker.last.poll`: sekunde od poslednjeg uspešnog prolaza workera (`NaN` dok worker nije prošao nijednom). Više od nekoliko minuta znači da worker ne radi ili da baza ne odgovara.
- `GET /actuator/metrics/shop.jobs.oldest.overdue`: koliko čeka najstariji posao kojem je došlo vreme. Duže od 10 minuta znači da worker ne stiže.

Logovi su van profila `dev` JSON (ECS): `log.logger` je npr. `rs.fishingshop.jobs.JobWorker`, a `log.level` je `WARN` za neuspeo pokušaj i `ERROR` za `DEAD`.

### Stanje reda

```sql
SELECT status, count(*), min(next_attempt_at) AS next_due, max(attempts) AS max_attempts
FROM background_jobs
GROUP BY status;
```

- `PENDING` sa `next_due` daleko u prošlosti: worker ne radi ili ne stiže. Proveri log (`rs.fishingshop.jobs.JobWorker`) i restartuj backend. Zaključani poslovi se oslobađaju sami kad se veza prekine.
- Mnogo `PENDING` sa `max_attempts > 0`: spoljašnji servis (SMTP) ne radi. Log ima `WARN ... failed, attempt N of 14` sa razlogom.
- `DEAD > 0`: posao je odustao. Vlasnik to vidi i kao upozorenje na admin Pregledu. Razlog je na **Admin → Poslovi** ili:

```sql
SELECT id, type, order_id, attempts, last_error, finished_at
FROM background_jobs WHERE status = 'DEAD' ORDER BY finished_at DESC;
```

### Mejl server je bio nedostupan

Pokušaji traju oko 13 do 26 sati (14 pokušaja, razmak od 30 s duplo raste do 6 h, sa nasumičnim delom). Duži prekid ostavlja poslove u stanju `DEAD`. Kad server ponovo radi, vlasnik ih vraća u red na **Admin → Poslovi → Odustali → Ponovi** (svaki pokušaj ostaje u audit logu). Za mnogo poslova odjednom, ili kad admin ne radi:

```sql
UPDATE background_jobs
SET status = 'PENDING', attempts = 0, finished_at = NULL, next_attempt_at = now()
WHERE status = 'DEAD' AND type = 'ORDER_CONFIRMATION_EMAIL';
```

Ručni `UPDATE` ne ostavlja trag u audit logu, pa ga zapiši u incident.

### Adresa koju mejl server ne prihvata

Posao odmah ide u `DEAD`, bez ponavljanja (`PermanentJobFailure` u `last_error`). Od 4.3 API takvu adresu odbija već pri porudžbini, pa ovo važi za stare porudžbine. Ispravi adresu u porudžbini, pa klikni "Ponovi".

### Kupac kaže da je dobio dva ista mejla

Očekivano posle pada ili restarta backenda tokom slanja. Proveri da li je tada bio restart. Ako nije, a dupliranja ima više, to je greška u kodu: tabela dozvoljava samo jedan posao po porudžbini i tipu (`uq_background_jobs_order_type`), pa uzrok traži u slanju.

### Račun nije napravljen ili nije poslat

Račun i njegov mejl su dva posla. **"Račun nije napravljen"** u adminu znači da je `ORDER_INVOICE` u stanju `DEAD`: račun ne postoji. Razlog je na **Admin → Poslovi**, a posle ispravke klikni "Ponovi". Ručno:

```sql
UPDATE background_jobs
SET status = 'PENDING', attempts = 0, finished_at = NULL, next_attempt_at = now()
WHERE type = 'ORDER_INVOICE' AND order_id = ...;
```

Kad račun postoji, a kupac ga nije dobio, `DEAD` je posao **"Mejl sa računom"** (`ORDER_INVOICE_EMAIL`). Račun se može preuzeti sa stranice porudžbine i poslati ručno, pa se posao označi kao rešen ("Označi kao rešeno", uz razlog). Ponovljen posao računa ne pravi drugi račun (`uq_invoices_order`) i ne šalje mejl ponovo, a izdat račun ne može da se izmeni ni obriše (trigger). Pogrešan račun se ispravlja storno računom, koji još ne postoji (dug u [DECISIONS](DECISIONS.md)).

### Posao koji ponavljanje ne može da popravi

Na primer, adresa koju mejl server ne prihvata, ili stara porudžbina. Vlasnik na **Admin → Poslovi** klikne "Označi kao rešeno" i upiše šta je urađeno umesto toga. Posao prelazi u `RESOLVED`, ostaje u listi pod "Rešeni" i u audit logu, a upozorenje na Pregledu nestaje.

## Baza i nalozi (Faza 6)

U `compose.prod.yml` baza ima tri naloga ([ADR 0005](adr/0005-append-only-audit-log.md), `docker/postgres/init-roles.sh`):

| Nalog | Šta sme | Gde se koristi |
|---|---|---|
| `POSTGRES_USER` | sve | samo pri prvom pravljenju baze i za ručni popravak |
| `shop_migrator` | vlasnik tabela, pravi šemu | Flyway pri startu backenda (`SPRING_FLYWAY_USER`) |
| `shop_app` | `SELECT`, `INSERT`, a `UPDATE`/`DELETE` samo na spisku u `R__app_grants.sql` | backend u radu (`DB_USER`) |

Razvojna baza (`docker-compose.yml`) i testovi koriste jedan nalog.

### Posle migracije backend javlja `permission denied for table ...`

Nova tabela je po podrazumevanju samo za dopisivanje. Ako je backend treba i da menja, dodaj je u `backend/src/main/resources/db/migration/R__app_grants.sql` i u spisak u `DatabaseRolesTests.appCanUpdateOnlyTheTablesTheApplicationChanges`. Repeatable migracija se izvrši sama pri sledećem startu. Hitno, ručno:

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "GRANT UPDATE ON nova_tabela TO shop_app"
```

Pa isto upiši u `R__app_grants.sql`, inače se izgubi pri sledećoj promeni tog fajla.

### Promena lozinke naloga baze

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "ALTER ROLE shop_app PASSWORD 'nova-lozinka'"
```

Pa promeni `SHOP_DB_APP_PASSWORD` (ili `SHOP_DB_MIGRATOR_PASSWORD`) u okruženju i restartuj backend. Init skripta ne radi ponovo na postojećoj bazi.

### Baza napravljena pre 6.3

Init skripta se izvršava samo pri prvom pravljenju baze. Demo volumen se uklanja (`docker volume rm fishing-shop-prod_pgdata`). Baza sa pravim podacima traži ručni prelaz: izvrši naredbe iz `init-roles.sh` kao superkorisnik, pa `REASSIGN OWNED BY shop TO shop_migrator`.

### Tajmauti u logu

- `canceling statement due to statement timeout`: upit je trajao duže od 30 s (`statement_timeout` na roli `shop_app`). Izveštaji traju ispod 2 s, pa je to problem upita ili zaključanog reda.
- `terminating connection due to idle-in-transaction timeout`: transakcija je mirovala duže od 120 s, najčešće worker koji čeka SMTP. Baza je oslobodila zaključan red, a posao se vraća u red i ponavlja (vidi "Pozadinski poslovi").

