# ADR 0005: Audit log kao tabela u koju se samo dopisuje

Status: prihvaćeno (2026-11-06, Faza 3)

## Kontekst

Zaposleni menjaju podatke: zalihu (3.3), status porudžbine (3.4), naloge i uloge (3.5). Vlasnik mora da vidi ko je šta promenio, kada, i kakva je vrednost bila pre i posle. Pravilo iz plana: audit se piše **u istoj transakciji** kao izmena i **ne sme da se menja ni briše**.

## Odluka

Tabela `audit_log` u istoj bazi (`V6`), sa triggerima koji odbijaju `UPDATE`, `DELETE` i `TRUNCATE`.

- Red ima `actor_id` i `actor_email` (email je kopiran, pa red ostaje čitljiv i kad se zaposlenom promeni nalog), `action`, `entity_type`, `entity_id`, i `old_value` i `new_value` kao JSONB objekte.
- Dozvoljene akcije i tipovi entiteta su `CHECK` lista u bazi. Svaka celina koja doda novu akciju (3.4, 3.5) dodaje je novom migracijom.
- `AuditService.record` ima `Propagation.MANDATORY`: mora da bude pozvan unutar transakcije izmene, inače baca grešku. Upis se odmah šalje bazi (`saveAndFlush`), pa greška u auditu obara i samu izmenu.
- Audit se piše samo kad se podatak zaista promeni. Popis koji se poklapa sa stanjem ne ostavlja red.
- Zaposleni se nikad ne brišu (samo deaktiviraju), pa `actor_id` ima običan strani ključ bez `ON DELETE`.

## Posledice

Dobro:

- Izmena i audit red uspevaju ili padaju zajedno. Test bez test transakcije to dokazuje: kad audit red ne može da se upiše, zaliha i kretanje se ne menjaju. Mutacioni test (privremeno uklonjen `@Transactional`) pokazuje da bi test pao.
- Ni aplikacija ni ručni SQL (`UPDATE`, `DELETE`, `TRUNCATE`) ne mogu da prepišu istoriju.
- JSONB dozvoljava da svaka akcija čuva svoja polja, a da šema ostane ista.

Loše:

- Zaštita važi protiv grešaka i nepažnje, ne protiv onoga ko ima lozinku baze. Aplikacija se povezuje kao `shop`, a taj nalog u zvaničnoj slici `postgres` je **superkorisnik**: može da isključi trigger (`ALTER TABLE ... DISABLE TRIGGER`), da ga zaobiđe sa `SET session_replication_role = replica` ili da zameni funkciju. Pravo rešenje je poseban nalog za aplikaciju, bez superkorisničkih prava i bez `ALTER`, sa `SELECT` i `INSERT` na `audit_log`, i drugi nalog za Flyway (dug pred deploy). Testovi baš tako čiste tabelu između testova.
- JSONB nije tipiziran: neispravan sadržaj bi se video tek pri čitanju. Baza proverava samo da je vrednost objekat.
- Tabela samo raste. Arhiviranje starih redova nije u planu.

## Razmotreno

- **Samo aplikaciona zaštita** (nema koda koji briše): `DELETE FROM audit_log` bi i dalje radio, pa uslov "ne sme da se briše" ne bi bio ispunjen.
- **Poseban korisnik baze bez `UPDATE`/`DELETE` prava**: najjača zaštita, ali traži drugi nalog za Flyway i za aplikaciju. Ide uz deploy, a trigger ostaje i tada.
- **Audit u posebnoj bazi ili kroz red poruka**: ne može da bude u istoj transakciji, pa bi audit mogao da opiše izmenu koja se nije desila ili da propusti onu koja jeste.
- **Hibernate Envers**: automatska istorija entiteta, ali beleži verzije redova, a ne poslovne akcije ("prijem 7 komada"), i dodaje zavisnost.
