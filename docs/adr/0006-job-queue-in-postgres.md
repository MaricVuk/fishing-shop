# ADR 0006: Red poslova u Postgresu umesto RabbitMQ-a

Status: prihvaćeno (2026-11-06, Faza 4)

## Kontekst

Faza 4 uvodi pozadinske poslove: mejl potvrde porudžbine i PDF račun, sa ponovnim pokušajima (exponential backoff) i mestom za poslove koji stalno padaju (dead-letter). Plan je od početka imao RabbitMQ, ali je u `DECISIONS.md` pitanje ostalo otvoreno ("RabbitMQ ili jednostavnije"), uz pravilo da se prvo izazove agentom `boring-tech-advisor`.

Činjenice:

- Obim je mali: portfolio shop, i u najboljem slučaju nekoliko stotina porudžbina dnevno, po dva posla na porudžbinu.
- Posao mora da nastane **atomski** sa porudžbinom (outbox obrazac). Ako porudžbina ne uspe, posla nema. Ako uspe, posao ne sme da se izgubi.
- Isti posao izvršen dvaput ne sme da pošalje dva mejla.
- Deploy još nije izabran, a mora da ima javni link. Svaki dodatni servis košta ili ima ograničen besplatan nivo.

## Odluka

Red poslova je tabela u postojećoj bazi. Nema brokera.

- Poslovi se upisuju u tabelu u **istoj transakciji** kao porudžbina. Tabela je istovremeno outbox i red, pa nema releja koji šalje dalje.
- Worker radi u istom Spring Boot procesu (`@Scheduled`) i uzima poslove sa `SELECT ... FOR UPDATE SKIP LOCKED`, pa dva workera ne uzimaju isti posao, a posao workera koji padne usred rada se oslobađa sa transakcijom.
- Neuspeh pomera `next_attempt_at` po formuli `base * 2^attempts`. Posle zadatog broja pokušaja posao prelazi u stanje `DEAD` i vidi se u adminu, gde može ručno da se ponovi.
- Isporuka je "bar jednom", pa obrađivač mora da bude idempotentan: jedinstveni ključ u bazi sprečava drugi mejl za isti posao.
- Kod koji pravi posao ne zna kako se red izvršava (mali interfejs), pa zamena izvršioca ne dira `OrderService`.

Tačna šema ide u migraciju celine 4.1.

## Posledice

Dobro:

- Atomičnost porudžbine i posla daje baza, bez distribuirane transakcije i bez drugog skoka u kojem mogu da nastanu duplikati.
- Nijedan novi servis: isti `pg_dump` čuva i porudžbine i poslove, testovi koriste postojeći Testcontainers Postgres, a deploy ostaje jedan backend i jedna baza.
- Stanje reda je običan SQL upit (`count(*) ... GROUP BY status`), a mrtvi poslovi su redovi koje admin može da pregleda.

Loše:

- Worker proverava tabelu u intervalu, pa posao ne kreće istog trena (kašnjenje od nekoliko sekundi je prihvatljivo za mejl i račun).
- Česti `UPDATE` redova pravi mrtve torke. Na ovom obimu autovacuum to rešava, ali završene poslove treba povremeno čistiti ili arhivirati.
- Nema fan-out-a ni potrošača van Jave. Ako zatreba, dodaje se relej iz iste tabele ka brokeru.
- RabbitMQ ne ulazi u CV kroz ovaj projekat. Koncepti koje faza pokazuje (outbox, idempotentnost, backoff, dead-letter, oporavak posle pada) važe i za broker.

## Kada ponovo razmotriti

- Merenje pokaže da proveravanje tabele ili mrtve torke opterećuju bazu (ne očekuje se ispod desetina hiljada poslova na sat).
- Isti događaj treba da čita više nezavisnih potrošača ili servis u drugom jeziku.
- Izabrani deploy daje broker besplatno, a cilj postane "rad sa brokerom". Tada se dodaje opcioni RabbitMQ izvršilac iza istog interfejsa, samo u compose profilu, i javni deploy ne zavisi od njega.

## Razmotreno

- **RabbitMQ i poseban worker servis**: push isporuka, ugrađeni DLX i TTL, nezavisno skaliranje. Ali outbox tabela i dalje mora da postoji, pa se dodaje i relej (drugi skok "bar jednom"), drugi stateful sistem za backup i nadogradnje, drugi kontejner u testovima i plaćen ili ograničen broker na deployu. Ništa od toga ovaj obim ne traži.
- **Spring `@Async` ili `ApplicationEvent` posle commit-a**: najjednostavnije, ali posao živi samo u memoriji. Restart između commit-a i slanja gubi mejl, a nema ponovnog pokušaja ni dead-letter stanja.
- **Biblioteka za poslove (JobRunr, db-scheduler)**: rade nad Postgresom, ali sakrivaju baš ono što faza treba da pokaže i dodaju zavisnost. Tabela i jedan upit su dovoljni.
