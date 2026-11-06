# Odluke

Spisak odluka sa kratkim obrazloženjem. Veće odluke dobijaju poseban ADR u `docs/adr/`. Poslednje ažuriranje: 2026-11-06.

## Odlučeno

| Tema | Odluka | Obrazloženje |
|---|---|---|
| Jezik koda | Engleski (kod, komentari, tabele, API). UI i README na srpskom | nazivi se kasnije teško menjaju, a kod čitaju i poslodavci |
| Backend | Java 21 + Spring Boot + Maven | najtraženije u Srbiji i EU, Spring Security/JPA/transakcije pogađaju shop |
| Baza | PostgreSQL 16 + Flyway | dobre transakcije za zalihe, šema kroz verzionisane migracije |
| Frontend | Next.js (App Router) + TypeScript | najrasprostranjeniji React okvir, SSR za katalog |
| Odnos frontenda i backenda | Spring je jedini backend, Next.js samo prikazuje | poslovna logika ostaje na Java strani, to je portfolio priča |
| Admin deo | Isti Next.js, zaštićene `/admin` rute | jedan deploy, jednostavnije. Uloge se proveravaju i na backendu |
| Valuta i jezik shopa | RSD, srpski | |
| Repo | Javan GitHub repo, licenca MIT | portfolio mora da se vidi. Tajne samo kroz env, `.env.example` u repou |
| Okruženje | Windows 11 + WSL2, projekat u Linux fajl sistemu | brži Maven i `node_modules`, Docker koristi WSL2 |
| Line endings | `.gitattributes`: `* text=auto eol=lf` | Windows CRLF kvari shell skripte i Docker |
| CI | GitHub Actions (build + testovi) | već u skeletu |
| Modeli | Sonnet podrazumevano. Opus za planiranje faze, arhitekturu, težak debug, sigurnost i pregled pred merge. Haiku/Sonnet za mehaničke poslove | Opus troši limite brže, koristi se tamo gde greška košta |
| Sub-agenti | Ne na početku. Videti plan ispod | svaki počinje bez konteksta i troši tokene |
| Skill-ovi | Jedan po poslu, bez duplikata. Izbor u `CLAUDE.md` | duplikati zbunjuju Claude-a |
| Imena | Repo `fishing-shop`, Java paket `rs.fishingshop`, Maven artifactId `fishing-shop` | doslednost sa pravilom "engleski kod" |
| Git | Samo autor commit-uje i push-uje. Claude Code predlaže plan, poruku i trenutak | istorija repoa je deo portfolija i mora da bude autorova |

## Plan za sub-agente po fazama

| Faza | Sub-agenti |
|---|---|
| 0 / pre Faze 1 | `schema-reviewer` jednom nad `V1__init_schema.sql` |
| 1 | nijedan |
| 2 | recenzent koda, pisac testova za konkurentni test |
| 3 | `security-reviewer` |
| 4 | `incident-thinker` pre puštanja workera, `boring-tech-advisor` za pitanje RabbitMQ naspram reda u Postgresu |
| Pred deploy | `claude-security` sken, jednom (skup po tokenima) |

## Odlučujemo kasnije

| Tema | Kad | Napomena |
|---|---|---|
| Autentikacija (JWT ili sesija) | Faza 3 | ADR |
| Red poslova (RabbitMQ ili jednostavnije) | Faza 4 | prvo ga izazovi `boring-tech-advisor` agentom |
| UI biblioteka (Tailwind + shadcn/ui ili druga) | početak Faze 1 | |
| Gde se deploy-uje (Fly.io, Render, Railway, VPS) | pre prvog deploya | mora da ima javni link |
| Naziv i izgled shopa | početak Faze 1 | |
| Plaćanje karticom | verovatno van opsega | portfolio ne mora da prima pravi novac |
| Više jezika (i18n) | samo ako zatreba | |

## Za proveru

- Obrisati folder `reference/` (autor). Sadržaj je preuzet u skelet.
- Pokrenuti `schema-reviewer` nad `V1__init_schema.sql` pre Faze 1. Posebno pitanje: seed kategorije u `V1` se izvršavaju i u produkciji, pa možda treba da pređu u zasebnu dev-only migraciju ili seed skriptu.
- Odlučiti da li CI koristi `./mvnw` umesto `mvn`, da lokalna i CI verzija Mavena budu iste.

## Urađeno

- 2026-11-06: skelet generisan preko Spring Initializr-a (Spring Boot **4.1.2**, najnovija stabilna, umesto ranije planiranog 3.5.7). Prvi `./mvnw verify` prošao (Testcontainers 2.x, Flyway V1, Hibernate `validate`), a `/actuator/health` je `UP` protiv compose baze.
- JDK 21 (Temurin 21.0.13) instaliran ručno u `~/.local/jdk`, jer SDKMAN traži `zip`, a `sudo` nije bio dostupan.
