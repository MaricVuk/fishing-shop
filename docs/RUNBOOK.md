# Runbook

Šta raditi kad nešto u radu shopa krene naopako. Svaka stavka ima simptom, proveru i oporavak.

## Pozadinski poslovi (Faza 4)

Poslovi su redovi u tabeli `background_jobs` ([ADR 0006](adr/0006-job-queue-in-postgres.md)). Worker radi u backend procesu i na svakih 5 sekundi uzima poslove kojima je došlo vreme. Isporuka je "bar jednom": ako backend padne posle slanja mejla, a pre upisa `DONE`, kupac dobije isti mejl dvaput.

### Stanje reda

```sql
SELECT status, count(*), min(next_attempt_at) AS next_due, max(attempts) AS max_attempts
FROM background_jobs
GROUP BY status;
```

- `PENDING` sa `next_due` daleko u prošlosti: worker ne radi ili ne stiže. Proveri log (`rs.fishingshop.jobs.JobWorker`) i restartuj backend. Zaključani poslovi se oslobađaju sami kad se veza prekine.
- Mnogo `PENDING` sa `max_attempts > 0`: spoljašnji servis (SMTP) ne radi. Log ima `WARN ... failed, attempt N of 14` sa razlogom.
- `DEAD > 0`: posao je odustao. Pogledaj razlog:

```sql
SELECT id, type, order_id, attempts, last_error, finished_at
FROM background_jobs WHERE status = 'DEAD' ORDER BY finished_at DESC;
```

### Mejl server je bio nedostupan

Pokušaji traju oko 13 do 26 sati (14 pokušaja, razmak od 30 s duplo raste do 6 h, sa nasumičnim delom). Duži prekid ostavlja poslove u stanju `DEAD`. Kad server ponovo radi, vrati ih u red:

```sql
UPDATE background_jobs
SET status = 'PENDING', attempts = 0, finished_at = NULL, next_attempt_at = now()
WHERE status = 'DEAD' AND type = 'ORDER_CONFIRMATION_EMAIL';
```

Do celine 4.3 (admin stranica sa dugmetom "ponovi") ovo je jedini način.

### Adresa koju mejl server ne prihvata

Posao odmah ide u `DEAD`, bez ponavljanja (`PermanentJobFailure` u `last_error`). Ispravi adresu u porudžbini, pa vrati taj jedan posao u red istim `UPDATE` upitom sa `WHERE id = ...`.

### Kupac kaže da je dobio dva ista mejla

Očekivano posle pada ili restarta backenda tokom slanja. Proveri da li je tada bio restart. Ako nije, a dupliranja ima više, to je greška u kodu: tabela dozvoljava samo jedan posao po porudžbini i tipu (`uq_background_jobs_order_type`), pa uzrok traži u slanju.
