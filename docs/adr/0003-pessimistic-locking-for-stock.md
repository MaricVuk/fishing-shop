# ADR 0003: Pesimističko zaključavanje zalihe pri porudžbini

Status: prihvaćeno (2026-11-06, Faza 2)

## Kontekst

U Fazi 1 porudžbina nije gledala zalihu. Test sa 50 istovremenih kupaca poslednjeg komada je to potvrdio: prošlo je svih 50 porudžbina, a zaliha je ostala 1.

Zaliha je u tabeli `inventory`, jedan red po varijanti, sa `CHECK (quantity >= 0)` i kolonom `version` iz `V1`. Treba odlučiti kako se taj red čita i menja kad više kupaca kupuje u isto vreme.

## Odluka

- Pri porudžbini se redovi zalihe zaključavaju sa `SELECT ... FOR UPDATE` (`InventoryRepository.findForUpdate`, `PESSIMISTIC_WRITE`).
- Redovi se zaključavaju **jedan po jedan, u rastućem redosledu `variant_id`**. Svaka transakcija tako čeka istim redom, pa dve porudžbine ne mogu da drže jedna drugoj redove (deadlock).
- Provera i skidanje su u istoj transakciji kao upis porudžbine (`Propagation.MANDATORY` na `StockService.take`). Ako bilo koja stavka nema dovoljno, odgovor je `422` sa `insufficientStockVariantIds` i ne upisuje se ništa.
- Varijanta bez reda u `inventory` računa se kao da ima 0 komada.
- Čekanje na zaključan red je ograničeno na 3 sekunde (`SET LOCAL lock_timeout`). Ako istekne, odgovor je `503` sa `Retry-After: 1`, a ne opšti `500`. Isto važi za deadlock koji prijavi baza.
- `@Version` ostaje na entitetu kao druga linija odbrane, a `CHECK (quantity >= 0)` kao poslednja.

## Posledice

Dobro:

- Ishod je uvek isti: uspe tačno onoliko porudžbina koliko ima komada. Nema petlje za ponovni pokušaj.
- Kod je pravolinijski: zaključaj, proveri, skini.
- Testovi to dokazuju na pravom Postgresu: 50 kupaca na 1 i na 3 komada, dve porudžbine sa istim varijantama u suprotnom redosledu (20 rundi), rollback bez test transakcije i `503` kad je red predugo zaključan.

Loše:

- Kupci iste varijante čekaju jedan na drugog dok traje transakcija. Za mali shop je to zanemarljivo, ali pod velikim opterećenjem jedna popularna varijanta postaje usko grlo.
- Porudžbina sa 50 stavki šalje 50 malih upita.

## Razmotreno

- **Optimističko zaključavanje (`@Version`)**: kolona već postoji. Pod velikom konkurencijom većina pokušaja pada sa `OptimisticLockException`, pa treba petlja za ponovni pokušaj izvan transakcije, a ishod zavisi od broja pokušaja. Odbačeno zbog složenosti, ne zbog ispravnosti.
- **Atomski `UPDATE inventory SET quantity = quantity - :q WHERE variant_id = :id AND quantity >= :q RETURNING quantity`**: jedan upit po stavci, zaključava red implicitno i vraća novo stanje. Podjednako ispravno i brže. Odbačeno samo zato što ROADMAP traži da se razume šta tačno zaključava `SELECT ... FOR UPDATE`, a ova varijanta krije zaključavanje. Ako zaliha postane usko grlo, ovo je prvi korak.
- **Jedan upit `WHERE variant_id IN (...) ORDER BY variant_id FOR UPDATE`**: Postgres ne garantuje da zaključava redove redom iz `ORDER BY`, pa se na to ne može osloniti protiv deadlock-a.
- **`FOR UPDATE SKIP LOCKED` ili `NOWAIT`**: kupac bi dobio odbijanje iako bi se red oslobodio za nekoliko milisekundi.
- **Rezervacija u korpi**: traži identitet kupca i vreme isteka. Odloženo dok ne postoji prijava.
