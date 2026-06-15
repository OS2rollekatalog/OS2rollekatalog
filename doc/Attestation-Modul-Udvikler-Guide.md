# Attestationsmodulet — udvikler-guide

Denne guide er til nye udviklere på Rollekataloget der skal sætte sig ind i
attestationsmodulet. Den forklarer de fire attestationstyper, hvordan moduleten
hænger sammen, hvor i koden tingene ligger, og hvordan man får data ind så man
kan teste lokalt.

For sluttest af attesteringer i et eksisterende driftsmiljø — se
`Rollekataloget - Test Guide Attestering.docx`.

## 1. Overblik

Attestationsmodulet er den del af Rollekataloget der periodisk beder ledere,
systemansvarlige og admins om at gå ind og bekræfte at deres medarbejderes /
it-systems rolletildelinger stadig er korrekte. Modulet er stort set
selvstændigt og ligger under pakken
`dk.digitalidentity.rc.attestation` i `ui/`-modulet.

Tre principper er værd at forstå før koden:

1. **Attesteringer dannes natligt af et batch-job**, ikke i realtid. Hvis du
   slår attestation til og venter på at noget skal ske med det samme, sker der
   intet. Kør jobbet manuelt, eller vent til næste morgen.
2. **Attesteringer er et frosset snapshot**. Når en attestation er oprettet, er
   tildelingerne på den tidspunktet "frosset" via historiske/temporal-tabeller.
   Selv hvis brugerens tildelinger ændres efter, attesterer lederen det der
   stod på `createdAt`-datoen.
3. **Alt grupperes på et `AttestationRun`**. Et run er én "runde" af
   attesteringer med samme deadline. Når jobbet kører og det er ~30 dage før
   deadline, dannes runet og dets `Attestation`-rækker.

```
AttestationRun (deadline, sensitive, finished)
   └── Attestation (type, ouUuid eller itSystemId, responsibleUserUuid)
         ├── AttestationUser  (én pr. bruger der skal attesteres)
         ├── OrganisationUserAttestationEntry / ItSystemUserAttestationEntry / ...
         │     (én pr. handling den ansvarlige har udført)
         └── AttestationMail  (notifikationer der er sendt ud)
```

## 2. De fire attestationstyper

Typerne defineres i `Attestation.AttestationType`
(`ui/src/main/java/dk/digitalidentity/rc/attestation/model/entity/Attestation.java`).

| Type | Hvem attesterer? | Hvad attesteres? | Hvor ses den i UI? |
|------|------------------|------------------|--------------------|
| `ORGANISATION_ATTESTATION` | Leder af enheden (eller stedfortræder) | Alle medarbejdere i lederens enhed + roller tildelt enheden direkte | Forsiden af attestation, "Enheder" |
| `IT_SYSTEM_ATTESTATION` | It-systemansvarlig | Brugere som er tildelt roller på et it-system gennem en *systemansvarlig*-tildeling | Forsiden under "It-systemer (tildelinger)" |
| `IT_SYSTEM_ROLES_ATTESTATION` | It-systemansvarlig | Selve rolleopbygningen — userroles og deres systemroller | Forsiden under "It-systemer (roller)" |
| `MANAGER_DELEGATED_ATTESTATION` | Delegate udpeget af en leder | **Den delegerende leders egne rolletildelinger** (ikke lederens medarbejdere) — plus evt. OU-niveau-tildelinger på den enhed lederen er ansvarlig for | Forsiden under "Delegeret leder" |

### 2.1 ORGANISATION_ATTESTATION (lederattestering)

Den klassiske leder-attestering. For hver enhed dannes én attestation pr. run.
Lederen bedes om at tage stilling til to ting:

* **Brugerne** i enheden — for hver bruger godkendes / afvises de
  rolletildelinger personen har (direkte eller gennem rollebuketter).
* **Enhedens egne rolletildelinger** — roller tildelt enheden som helhed
  (med titler/funktioner).

Når begge dele er gennemgået, sættes `verifiedAt` på attestationen.

Relevante services:

* `OrganisationAttestationService` — opbygger DTO'er, håndterer verify/reject.
* `UserAttestationTrackerService.updateOrganisationUserAttestations()` —
  natlig oprettelse af attestationer for hver enhed med relevante tildelinger.

Specielt:

* Hvis AD-attestation er slået til (`isADAttestationEnabled`), dannes der
  også organisations­attesteringer for enheder der bare *har medarbejdere*,
  selvom der ikke er nogen rolletildelinger. Dette er for at sørge for at
  AD-konti også gennemgås.
* Sensitive runs (se afsnit 3) springer denne mekanisme over.
* Hvis lederen for en enhed selv er medarbejder i samme enhed, søger
  `findTargetOuForAttestation()` opefter for at finde en parent-OU med en
  *anden* leder, så man ikke attesterer sig selv.
* Stedfortrædere (substitutes) for en leder kan udføre attesteringen på
  lederens vegne, jf. `ManagerSubstituteService.isSubstituteforOrgUnit`.

### 2.2 IT_SYSTEM_ATTESTATION (systemansvarliges brugerliste)

Bruges når et it-system har en konfigureret *systemansvarlig*, og roller på
systemet er markeret med "systemansvarlig attesterer tildelinger". Den
systemansvarlige får én attestation pr. it-system, hvor han/hun ser hver
bruger med systemets roller og godkender / afviser tildelingerne.

Relevante services:

* `ItSystemUsersAttestationService`
* `UserAttestationTrackerService.updateSystemUserAttestations()` — opretter
  attesteringer baseret på `findValidGroupByResponsibleUserUuid…()`.

### 2.3 IT_SYSTEM_ROLES_ATTESTATION (rolleopbygning)

Den systemansvarlige attesterer ikke brugere, men selve **rolleopbygningen**:
hvilke userroles/systemroles findes på systemet, hvilken kobling har de? Dette
er typisk hvad en udviklingsansvarlig på et fagsystem bedes attestere.

Relevante services:

* `ItSystemUserRolesAttestationService`
* `ItSystemAttestationTrackerService.updateItSystemRolesAttestations()`.

Sensitive og extraSensitive runs **skipper** denne type — det er kun normale
runs der opretter rolleopbygnings­attesteringer.

### 2.4 MANAGER_DELEGATED_ATTESTATION (delegeret lederattestering)

**Vigtigt — typen er snævrere end den lyder.** En leder *M* kan via
`ManagerDelegate` udpege en delegate *D* som skal attestere **M's egne
rolletildelinger**. Det er altså personen *M*'s rettigheder der attesteres,
ikke alle medarbejderne under M.

Det giver mening fordi en almindelig `ORGANISATION_ATTESTATION` ellers ville
lade lederen attestere sig selv (eller skubbe det til parent-OU'en). Når M
har sensitive eller særlige roller bruges en delegate i stedet, så kontrollen
forbliver "anden persons øjne".

Konkret hvad delegaten ser når attestationen åbnes:

* Den delegerende leder *M* listet som "user attestation" — D godkender /
  afviser M's rolletildelinger, præcis som en almindelig leder ville gøre med
  sine medarbejdere.
* Eventuelle OU-niveau-tildelinger på den `responsibleOuUuid` der hører til M.
* Andre medarbejdere i samme OU vises *ikke* — de attesteres af M selv via
  en parallel `ORGANISATION_ATTESTATION`.

Hvis flere ledere har valgt samme delegate, ser delegaten flere
`MANAGER_DELEGATED_ATTESTATION`-rækker (én pr. delegerende leder).

Hvordan trackeren beslutter at det er denne type:

* `UserAttestationTrackerService.updateOrganisationUserAttestations()`
  henter alle aktive `ManagerDelegate` på datoen.
* For brugerassignments: hvis `assignment.getUserUuid()` matcher en leder der
  har delegeret, går assignmentet ind i en `MANAGER_DELEGATED_ATTESTATION`.
* For OU-niveau-assignments: hvis OU'en har en leder der har delegeret, går
  assignmentet ind i en `MANAGER_DELEGATED_ATTESTATION` (på samme OU).

Relevante services:

* `ManagerDelegateAttestationService`.
* `OrganisationAttestationService.buildUserAttestations()` filtrerer ekstra
  for `MANAGER_DELEGATED_ATTESTATION` så kun delegerende ledere kommer med.
* Trackeren bruger `historyAttestationManagerDelegateDao` til at finde aktive
  delegeringer på datoen.

## 3. Sensitive og extraSensitive runs

Et `AttestationRun` har to flag der ændrer hvordan det opfører sig:

* `sensitive = true` — kun brugere med mindst én rolle markeret som
  `sensitiveRole` (eller `extraSensitiveRole`) tages med. Bruges fx midt
  imellem to almindelige attesteringer for at have et hyppigere check af de
  sensitive roller.
* `extraSensitive = true` — endnu strammere; kun rolletildelinger markeret som
  `extraSensitiveRole`.

Intervallerne for hver type defineres i `AttestationRunTrackerService`:

| Indstilling (`CheckupIntervalEnum`) | Normal | Sensitive | ExtraSensitive |
|-------------------------------------|--------|-----------|----------------|
| `YEARLY`                            | 12 mdr | 6 mdr     | 3 mdr          |
| `EVERY_HALF_YEAR`                   | 6 mdr  | 3 mdr     | 3 mdr          |

ExtraSensitive runs dannes kun når intervallet er `YEARLY` (jf. linje 40 i
`AttestationRunTrackerService.updateRuns()`).

Markeringen `sensitiveRole` / `extraSensitiveRole` sættes på den enkelte
`UserRole`-entitet.

## 4. Temporal-tabellerne — hvad er frosset, hvad er live?

Det her er det sværeste at gennemskue ved modulet, men også det vigtigste at
forstå. En attestation må aldrig vise "andre rolletildelinger end dem der var
gældende da den blev oprettet" — for ellers ville en leder kunne attestere
roller han aldrig har set, eller en bruger kunne nå at slette en sensitive
rolle lige inden lederen klikker på den. Modulet løser det ved at føre tre
egne *temporale* tabeller, og derefter bruge attestationens `createdAt` som
"se kataloget som det så ud denne dag"-pegepind.

### 4.1 De tre temporale tabeller

Alle tre arver `TemporalAssignmentBase`
(`attestation/model/entity/temporal/`) og deler de samme tidsfelter:

| Felt | Type | Betydning |
|------|------|-----------|
| `id` | bigint PK | Surrogat-id. |
| `validFrom` | date | Datoen denne version af rækken først var gældende. |
| `validTo` | date | Datoen rækken holdt op med at være gældende. `NULL` = stadig gyldig. |
| `updatedAt` | date | Sidste dag det natlige job så denne version. Bruges til at finde forsvundne rækker. |
| `recordHash` | varchar | MD5 af alle `@PartOfNaturalKey`-felter. Bruges til at genfinde "samme logiske assignment". |

| Tabel | Entity | Indhold |
|-------|--------|---------|
| `attestation_user_role_assignments` | `AttestationUserRoleAssignment` | Én række pr. (bruger × rolle/rollebuket × it-system × ansvarlig × hvordan tildelt). |
| `attestation_ou_role_assignments` | `AttestationOuRoleAssignment` | Én række pr. (OU × rolle/rollebuket × titler × undtagne brugere × ansvarlig × …). |
| `attestation_system_role_assignments` | `AttestationSystemRoleAssignment` (+`…Constraint`) | Én række pr. (userrole × systemrole × it-system × ansvarlig). Selve rolleopbygningen. |

`@PartOfNaturalKey` markerer hvilke felter der definerer den "logiske
identitet" af en assignment — dvs. de felter der indgår i `recordHash`. Felter
som `userName`, `userRoleDescription`, `responsibleOuName` er **ikke** med:
hvis brugeren skifter navn ændres rækken in-place, ikke en ny version.

### 4.2 Hvordan opdateres tabellerne (idé bag updaterne)

`UserAssignmentsUpdaterJdbc.updateUserRoleAssignments(today)` viser mønstret
for alle tre updatere:

1. Læs hele dagens autoritative billede ud fra Rollekatalogets globale
   historik-tabeller (`HistoricAssignment` m.fl.) og oversæt hver post til
   en `AttestationUserRoleAssignment` med beregnet `recordHash`.
2. For hver beregnede række:
   * **Findes der en gyldig række (`validFrom ≤ today AND (validTo > today OR validTo IS NULL)`) med samme hash?**
     * Ja → opdatér ikke-natural-key-felter (navne, beskrivelser) hvis de er
       ændret, og sæt `updatedAt = today`. **`validFrom` rør vi ikke.**
     * Nej → indsæt ny række med `validFrom = today`, `updatedAt = today`,
       `validTo = NULL`.
3. Til sidst: alle rækker hvor `updatedAt < today` men som stadig var
   gældende, sættes `validTo = today` ("invalidated"). Dette er hvordan
   forsvundne tildelinger lukkes.

Konsekvenser man bør forstå:

* Et navnet bliver opdateret in-place og vil derfor være "live" — selv på
  gamle attestationer. Det er bevidst, så lederen ser brugerens nuværende
  navn frem for "Bo Hansen (gift Pedersen)".
* En rolle der får ændret om den er sensitiv eller extra-sensitiv vil få
  **ny række** (de er natural-key-felter). Den gamle række får `validTo`
  sat til opdateringsdagen.
* En tildeling der bliver slettet og genoprettet samme dag fanges som "same
  hash" → ingen ny række, kun `updatedAt`-bump. Det er en bevidst
  forsimpling.

### 4.3 Sådan virker frysningen

Hver `Attestation` har et `createdAt` (en `LocalDate`). Alle services bruger
det som pegepind når de skal slå tildelinger op for at vise attestationen:

```java
// fra OrganisationAttestationService.getAttestation
List<AttestationUserRoleAssignment> userAssignments =
    userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(
        attestation.getCreatedAt(), orgUnitUuid);
```

Den underliggende JPQL er:

```jpql
WHERE s.validFrom <= :validAt
  AND (s.validTo > :validAt OR s.validTo IS NULL)
```

Forestil dig en attestation oprettet 2026-04-15:

* Tildeling A var aktiv den dag → `validFrom = 2026-04-10`, `validTo = NULL`.
  Næste morgen sletter en admin tildelingen. Updateren sætter
  `validTo = 2026-04-16`. Attestationen ser stadig A, fordi
  `validTo > 2026-04-15` ✓.
* Tildeling B oprettes 2026-04-20. Updateren laver ny række med
  `validFrom = 2026-04-20`. Attestationen ser **ikke** B, fordi
  `validFrom <= 2026-04-15` ✗.
* Tildeling C er aktiv hele perioden, men brugerens navn rettes 2026-04-22.
  Rækken opdateres in-place → attestationen viser nu det nye navn (bevidst).

Det er det grundprincip der gør det *sikkert* at lade en leder gå ind og
attestere flere uger efter deadlinen blev sat: snapshot er stabilt.

### 4.4 Hvad er **frosset** og hvad er **live** ved en attestation

| Feltet | Frosset på `createdAt`? | Hvor kommer det fra? |
|--------|-------------------------|----------------------|
| Hvilke tildelinger lederen ser | ✅ Ja | `attestation_user_role_assignments` / `_ou_*` / `_system_*` slået op via `validAt = createdAt`. |
| `assignedFrom` på tildelingen | ✅ Ja | Gemmes på den temporale række ved persist (kommer fra historikkens `validFrom`). |
| Sensitive- / extraSensitive-status | ✅ Ja (på den synlige række) | Natural-key-felt; en ændring laver en ny række så snapshottet før ændringen forbliver. |
| Inheritance, assignedThroughType, responsibleOu/User | ✅ Ja | Natural-key-felter. |
| Bruger- og rolle-**navne**, beskrivelser | ⚠️ Live (de opdateres in-place) | Ikke natural-key — opdateres på samme række. |
| `Attestation.deadline` / `verifiedAt` / `responsibleUserUuid` | Live (ændres direkte på `Attestation`) | Hvis it-system skifter ansvarlig flyttes også `createdAt` frem i `ItSystemAttestationTrackerService.ensureWeHaveAttestationFor` så det nye snapshot starter fra dagens billede. |
| Hvem aktuelt er stedfortræder for lederen | Live | `ManagerSubstituteService` slår op i live tabeller. |
| Aktive `ManagerDelegate` ved oprettelsen | ✅ Ja (brugt i tracker) | `historyAttestationManagerDelegateDao.findAllByDate(when)`. |
| Sensitive-flag på attestation/run | ✅ Ja | Skrives på `Attestation`/`AttestationRun` ved oprettelse. |
| Listen "tildelinger siden sidste attestering" | ⚠️ Semi-live | Slås op fra `previousAttestation.verifiedAt` til `createdAt` — ikke længere frem end snapshottet, men begynder ved sidste *færdige* attestation. |
| Indstillinger (AD-attestation, opt-in-OU'er, sensitive-interval) | Live | Læses fra `SettingsService` hver gang. |
| It-system-/role-/user-objekter brugt i fx ændrings-mails | Live | `userRoleService.getById(...)` mv. — bemærk at en slettet rolle kan resultere i `null` i mail-bygning. |
| `AttestationUser` (selve "brugeren skal med på listen") | ✅ Ja | Skrevet ved tracker-tid. Hvis brugeren slettes bagefter, fanger UI'en det og falder tilbage til `null` på navn. |

Tommelfingerregel: **alt som handler om "hvad er rolle-billedet?" er frosset
på createdAt; alt som handler om "hvad gør personen lige nu?" (verifyAt,
ressourceopslag i mailskabeloner, indstillinger) er live.**

### 4.5 Et praktisk eksempel

Du tester lokalt: leder M er ansvarlig for OU=Drift. Du opretter attestation
i går (`createdAt = i går`). I dag laver du to ændringer:

1. Du fjerner Userrole "DBA" fra Bo. → Updateren markerer rækken `validTo = i dag`.
   Lederens åbne attestation viser **stadig** "DBA" på Bo.
2. Du tilføjer ny Userrole "Reader" til Eva. → Ny række `validFrom = i dag`.
   Lederens attestation viser **ikke** Reader på Eva.
3. Du retter Bo's navn fra "Bo H." til "Bo Hansen". → Samme række opdateres
   in-place. Lederens attestation viser nu "Bo Hansen".
4. Lederen klikker afvis på Bo's "DBA" rolle med en bemærkning. → Skrives på
   `OrganisationUserAttestationEntry` med `createdAt = nu`. Live.
5. I mailen til Bo med ændringsønsket bruges `userRoleService.getById(dbaId)`
   til at vise rollens *nuværende* navn. Hvis du i mellemtiden har omdøbt
   "DBA" til "Database Administrator", er det det navn der står i mailen,
   selv om snapshottet stadig hed "DBA".

Det er den slags "halv-live" detalje man skal være forberedt på når man
debugger en mærkelig observation.

### 4.6 Hvorfor er det så indviklet — og er det nødvendigt?

Første gang man læser modulet er den umiddelbare reaktion: *"Tre temporale
tabeller, en hash-baseret diff, hand-rolled JDBC, en updater pr. tabel —
hvorfor ikke bare slå op i de live tabeller?"*. Her er rationalet bag de
beslutninger der gør modulet komplekst, hvad alternativerne ville koste, og
hvor jeg vil indrømme at koden er mere besværlig end den behøver at være.

#### Hvorfor overhovedet en separat snapshot-model?

Det grundlæggende problem er at en attestation **skal** være et stabilt
billede over uger eller måneder. Tre alternativer er værd at overveje:

1. **Slå op i live tabeller hver gang attestationen vises.**
   Det virker kun hvis ingen ændrer noget i mellemtiden. Det gør de.
2. **Kopiér alle relevante assignment-rækker ind i selve `Attestation`-aggregatet.**
   Triviel frysning, men datamængden eksploderer. En kommune med 5.000
   medarbejdere og to attestation-runs i året kan let lande på millioner af
   duplikerede rækker pr. år, og du betaler både lager og write-amplification.
3. **Brug systemversionerede tabeller (MariaDB temporal tables).**
   Kunne i princippet gøres, men hvert eneste live skrive-operation (rolle
   tildeles/fjernes — sker hele dagen) ville få temporal-overhead. Modulet
   har bevidst valgt at flytte den omkostning over i ét natligt batch.

Den valgte model er en **CQRS-agtig løsning**: live-datamodellen blander sig
ikke med attestation, og attestationsverdenen har sin egen optimerede
read-model med præcis de felter den har brug for, præ-aggregeret med
inheritance, exceptions, ansvarlig-resolution osv. allerede løst.

#### Hvorfor 3 tabeller frem for 1?

Brugertildelinger, OU-tildelinger og system-rolle-opbygning har **væsensforskellige
naturlige nøgler** (en bruger har ikke titler, en OU har ikke `userUuid`, en
systemrolle har constraints). En fælles tabel ville enten være en
sparse mega-tabel eller kræve dårligt struktureret JSON. Tre tabeller med
hver deres `@PartOfNaturalKey`-felter giver normalt indekserede joins.

#### Hvorfor hash i stedet for at slå op på natural key direkte?

`AttestationUserRoleAssignment`'s naturlige nøgle er ~10 kolonner inkl. en
collection. At lave et indeks på det er muligt men dyrt og uoverskueligt.
`recordHash` er én MD5-streng der dækker hele nøglen, og indekset
`(record_hash, valid_from, valid_to)` (jf. `V1_144__add_attestation_index.sql`)
gør lookuppet i `findValidUserRoleAssignmentWithHash` til ét punkt-opslag.

Det er også hvad der gør "har noget ændret sig?"-tjekket billigt:
*samme hash → eksisterende række kan beholdes; ny hash → ny række.*
Uden hash skulle man enten sammenligne hver kolonne eller acceptere blind
duplicering.

#### Hvorfor JDBC og ikke Hibernate i updaterne?

Klassens egen kommentar siger det rent ud:

> *JDBC Notice!*
> *These classes are made to save memory, hibernate will consume around 1gb*
> *whereas these use much less.*
> — `UserAssignmentsUpdaterJdbc.java`

Et natligt opdateringsbatch kan let røre ved hundredtusinder af rækker. Med
Hibernate's first-level cache der pinner hver entitet, brænder du heap.
JDBC + manuel batching har ingen session-state, så hukommelsesforbruget er
fladt og forudsigeligt.

Det er ikke gratis: dao-laget bliver håndskrevet SQL/JDBC, JPA-forholdene er
ikke tilgængelige (fanget i kommentaren *"all relations are not populated, so
be aware!"*), og man har dobbelt-implementering (JPA-DAO til *læsning* fra
service-laget, JDBC-DAO til *batch-opdatering*). Men alternativet — Hibernate
der OOM'er kl. 03:00 — er værre.

Bemærk også performance-tricks i samme klasse:

* `READ_UNCOMMITTED` isolation under updateren. Tracker-jobbet er
  idempotent på dagens snapshot, så dirty reads er acceptable.
* `setTimeout(600)` (10 min pr. transaction) for at undgå at en enkelt
  transaktion hænger på en lås.
* **Per-it-system iteration** (`for (Long itSystemId : itSystemIds)`) —
  begrænser hver transaktions arbejdsmængde og lader log fremgang pænt pr.
  system. Også her: bounded memory.
* **Batches af 500** ved invalidering — undgår at sende
  `WHERE id IN (...)` med titusinder af parametre.

#### Hvorfor "ingen update hvis intet er ændret"?

> *If content is the same there is no reason to update (as it fills up the*
> *binlog of mariadb etc…)* — `UserAssignmentsUpdaterJdbc.update()`

Selv ved no-op writes skriver MariaDB binlog-events hvis replikering eller
PITR er slået til. Med en kommune-størrelse datasæt giver det **gigabyte
binlog pr. nat** uden ændringer. `contentEquals()` springer hele write'et
over når intet er ændret — billigste skrivning er ingen skrivning.

Det er også derfor `updatedAt` opdateres i en separat batch-update bagefter:
det er *ét* felt, det skal *altid* skrives (selv ved no-op), og det fortjener
sin egen 500-id batch i stedet for at blokere den indre løkke.

#### Er det jeg er enig i at det er nødvendigt?

Ja, langt hen ad vejen. Den valgte arkitektur løser tre reelle problemer:

1. **Stabilt snapshot** uden at duplikere data pr. attestation.
2. **Skala** — Hibernate på et stort datasæt om natten = tikkende OOM-bombe.
3. **Live-trafik forbliver hurtig** — alt det tunge arbejde sker kl. 05:00.

Men der er steder hvor kompleksiteten er **tilfældig** snarere end essentiel
— ting jeg ville rette hvis jeg gik ind for at oprydde:

* De tre updatere (`User`/`Ou`/`SystemRole`) har næsten identisk skelet
  (læs, hash, find-or-create, invalider) men er hand-roll'et hver for sig.
  En fælles `AbstractTemporalUpdater<T>` ville koste 100 linjer og tjene
  300 ind.
* Snapshot-bindingen er implicit: man passer `attestation.getCreatedAt()`
  *manuelt* som `validAt` i hvert eneste service-kald. Glemmer man det ét
  sted, blander man frosset og live data uden compiler-advarsel. En lille
  `AttestationView`-wrapper der eksponerer DAO'erne med `validAt` allerede
  bundet, ville fjerne hele kategorien af fejl.
* `MANAGER_DELEGATED_ATTESTATION`-logikken sidder dels i trackeren, dels
  som efter-filter i `OrganisationAttestationService.buildUserAttestations`.
  Det er to steder at huske — det burde leve i én tjeneste.
* Trackeren læser fra `HistoricAssignment`-tabellerne *og* skriver til
  `attestation_*_role_assignments`, og service-laget læser så fra de
  sidste. Dvs. der er reelt **to** snapshot-lag (rc-historik + attestation
  temporal) der opdateres hver nat. Der er gode grunde (forskellige felter,
  inheritance allerede løst i attestation-laget), men det er værd at vide
  at det er der.

Bundlinjen: arkitekturen er ikke overengineered i sit fundament; den er
dimensioneret til en stor kommune. Det er detaljerne der trænger til en
fælles kam.

## 5. Det natlige tracker-job — fra ingenting til en deadline

Den vigtigste indgang er `AttestationTask`, hvor to scheduled metoder kører:

* `updateAttestation()` — kører om natten (default 05:00). Først opdateres
  alle "temporale" / historiske assignment-tabeller for dagen i dag, så
  trackerne kan lave et frosset snapshot. Derefter kalder den
  `AttestationRunTrackerService` og de tre tracker-services.
* `finishOutstandingAttestations()` — kører lidt senere (default 06:00). Går
  igennem unfinished attestationer og sætter `verifiedAt` hvis alle entries
  rent faktisk er udført.

Flow inde i `updateAttestation()`:

1. Tjek at `rc.scheduled.enabled = true` *og* at attestering er slået til i
   indstillingerne (`SettingsService.isScheduledAttestationEnabled()`).
2. Tjek at historikken er kørt for i dag (`HistoryService.hasHistoryBeenGenerated`)
   — uden den er attesteringer baseret på et utilstrækkeligt snapshot.
3. Hvis Flyway har kørt nye migrationer siden sidst, force-recompute af alle
   OU-hashes (`ouAssignmentsUpdaterJdbc.updateAllOuHashOnly`) — ellers kunne
   gamle hashes maskere reelle ændringer.
4. Opdatér de temporale assignment-tabeller (OU, user, system).
5. Kald `AttestationRunTrackerService.updateRuns()`. Den finder næste deadline
   ud fra `getFirstAttestationDate()` og intervallet, og opretter et
   `AttestationRun` *hvis* vi er ≤ `daysForAttestation` (default 30) før
   deadline.
6. Kald `UserAttestationTrackerService` (organisation + system users) og
   `ItSystemAttestationTrackerService` (rolleopbygning). De opretter
   `Attestation`-rækker og knytter `AttestationUser`-entries til runet.
7. Notér `lastRun` så jobbet ikke kører to gange samme dag.

Den centrale "skal jeg lave en attestation nu?"-test står i hver tracker:

```java
if (run.getDeadline().minusDays(daysForAttestation).isAfter(when)) {
    // Deadlinen er længere end 30 dage ude — ikke endnu
    return;
}
// Lav den
```

E-mail-notifikationer kører i et separat job (`AttestationEmailNotificationTask`
default 09:00) som kigger på `notifyDaysBeforeDeadline`, `reminder1/2/3`,
`escalationReminder` (alle i `AttestationConfig`).

## 6. Konfiguration

### 6.1 `application.properties`

```properties
# Hvornår kører jobbene (Spring cron, sek/min/time/...)
rc.attestation.attestation_cron               = 0 0 5 * * *
rc.attestation.finish_outstanding_cron        = 0 0 6 * * *
rc.attestation.attestation_cache_ttl_cron     = 0 0 1 * * *
rc.attestation.attestation_notifications_cron = 0 0 9 * * *

# Forskellige tærskler (defaults i AttestationConfig)
rc.attestation.daysForAttestation             = 30   # vindue før deadline
rc.attestation.notifyDaysBeforeDeadline       = 20
rc.attestation.reminder1DaysBeforeDeadline    = 10
rc.attestation.reminder2DaysBeforeDeadline    = 3
rc.attestation.reminder3DaysAfterDeadline     = 5
rc.attestation.escalationReminderDaysAfterDeadline = 5
```

### 6.2 Indstillinger i UI / database (`SettingsService`)

| Setting key | Hvad styrer den? |
|-------------|------------------|
| `SETTING_SCHEDULED_ATTESTATION_ENABLED` | Master-knap. Hvis false gør jobbet ingenting. |
| `SETTING_SCHEDULED_ATTESTATION_INTERVAL` | `YEARLY` eller `EVERY_HALF_YEAR`. |
| `SETTING_FIRST_ATTESTATION_DATE` | Ankerdato — alle deadlines udregnes som denne plus N intervaller. |
| `SETTING_SCHEDULED_ATTESTATION_EXCEPTED_ORG_UNITS` | Kommasepareret OU-uuid-liste der skal undtages (med nedarvning). |
| `SETTING_SCHEDULED_ATTESTATION_OPTED_IN_ORG_UNITS` | I "opt-in"-mode er kun disse OU'er med. |
| `SETTING_AD_ATTESTATION_ENABLED` | Tving organisationsattesteringer for alle OU'er med medarbejdere — også uden roller. |
| `SETTING_ATTESTATION_ORG_UNIT_SELECTION_OPT_IN` | Vælg om OU-listen er "exclude" eller "include". |

### 6.3 Roller med adgang til modulet

`@RequireAnyAttestationEligibleRole` slipper igennem:

* `ROLE_ADMINISTRATOR`
* `ROLE_ATTESTATION_ADMINISTRATOR`
* `ROLE_MANAGER`
* `ROLE_SUBSTITUTE`
* `ROLE_IT_SYSTEM_RESPONSIBLE`
* `ROLE_MANAGER_SUBSTITUDE` (delegate)

## 7. Kodelandkort

```
ui/src/main/java/dk/digitalidentity/rc/attestation/
├── annotation/         egne Spring/JPA-annotationer
├── config/             AttestationConfig (binder rc.attestation.*)
├── controller/
│   ├── mvc/            Thymeleaf-controllere (UI)
│   └── rest/           REST-endpoints til UI'ens AJAX-kald
├── dao/                Spring-Data-repositories
├── exception/          Domæneundtagelser
├── model/
│   ├── dto/            DTO'er til UI/API + enums
│   └── entity/         JPA-entities (Attestation, AttestationRun, …)
│       └── temporal/   "Frosne" historiske tabeller (AttestationUserRoleAssignment, …)
├── service/
│   ├── tracker/        ATTESTATION-DANNELSE — natligt batch
│   │   ├── AttestationRunTrackerService     opretter runs
│   │   ├── UserAttestationTrackerService    organisation + system-bruger
│   │   └── ItSystemAttestationTrackerService rolleopbygning
│   ├── temporal/       opdaterer de temporale tabeller (JDBC)
│   ├── report/         eksport-rapporter
│   ├── util/           validering + helpers (AttestationUtil m.fl.)
│   ├── OrganisationAttestationService       leder-flow
│   ├── ItSystemUsersAttestationService      systemansvarlig — brugere
│   ├── ItSystemUserRolesAttestationService  systemansvarlig — roller
│   ├── ManagerDelegateAttestationService    delegate-flow
│   ├── AttestationEmailNotificationService  e-mailing
│   ├── AttestationLockService               sambruger-låse på en attestation
│   └── ...
└── task/               Scheduled-klasser (AttestationTask m.fl.)
```

UI:

```
ui/src/main/resources/templates/attestationmodule/
├── index.html                 dashboard for ledere/systemansvarlige
├── orgunits/attestate.html    leder-flow + delegate-flow (samme template)
├── itsystems/attestate.html   IT_SYSTEM_ATTESTATION-flow
├── itsystems/roleAssignment*  IT_SYSTEM_ROLES_ATTESTATION-flow
├── admin/                     attestation admin (oversigt over runs)
└── reports/                   rapport-views
```

## 8. Sådan får du data ind når du udvikler lokalt

Modulet kræver tre ting før du overhovedet ser noget:

1. En `User` med en attestation-relevant rolle (typisk `ROLE_MANAGER` ved at
   være leder af en `OrgUnit`, eller `ROLE_IT_SYSTEM_RESPONSIBLE` ved at være
   *attestationResponsible* på et it-system).
2. Mindst ét rolletilskud (UserRole eller RoleGroup) som peger på den
   bruger / enhed du vil teste med.
3. Indstillinger som beskrevet i 6.2 sat så et run dannes nu eller om få dage.

Anbefalet lokal opsætning:

### 8.1 Start databasen

```bash
docker compose -f docker/mariadb/docker-compose.yml up -d
```

(Brug evt. egen lokal database — tjek `application.properties` i `ui/`).

### 8.2 Start applikationen og log ind

* Konfigurér en lokal user via SAML-stub eller test-profil.
* Tildel brugeren `ROLE_ADMINISTRATOR` så du kan ændre indstillinger.

### 8.3 Konfigurér modulet

Under **Administration → Indstillinger → Attestering**:

1. Slå "Skemalagt attestering" til.
2. Vælg interval (`Hvert halve år` for hyppige test).
3. Sæt **første attestationsdato** til 2-3 uger ude i fremtiden — modulet
   opretter run når det er ≤ 30 dage til deadline.
4. Brug enten "Opt in" eller "Opt out" til at vælge en OU eller et system du
   vil teste på.

### 8.4 Triggér jobbet

Det natlige job kører normalt 05:00. Til udvikling har du to valg:

* **Vent til næste morgen.**
* **Kør jobbet manuelt.** I `AttestationTask.updateAttestation()` er der
  allerede en udkommenteret `@Scheduled(fixedDelay = 10000000L)` som du kan
  bruge under lokal kørsel — ellers kan du midlertidigt tilføje et
  REST-endpoint eller en debug-metode der kalder
  `attestationRunTrackerService.updateRuns(LocalDate.now())` plus de tre
  tracker-services. Husk også `historicAssignment*Updater*` jobbene først,
  ellers er snapshottet tomt.

  Kort sagt — den manuelle rækkefølge svarer til:

  ```java
  // 1. byg/refresh historikken
  historyService.generateHistory();
  // 2. opdatér attestation-temporal-tabellerne
  ouAssignmentsUpdaterJdbc.updateOuAssignments(today);
  userAssignmentsUpdaterJdbc.updateUserRoleAssignments(today);
  systemRoleAssignmentsUpdaterJdbc.updateItSystemAssignments(today);
  // 3. dann run + attestationer
  attestationRunTrackerService.updateRuns(today);
  userAttestationTracker.updateOrganisationUserAttestations(today);
  userAttestationTracker.updateSystemUserAttestations(today);
  systemAttestationTracker.updateItSystemRolesAttestations(today);
  ```

### 8.5 Test-scenarie til hver type

| Type | Sådan provokerer du den |
|------|--------------------------|
| ORGANISATION_ATTESTATION | Lav en OrgUnit med en leder (`User`), opret en `UserRole`, tildel den til en medarbejder i enheden. |
| IT_SYSTEM_ATTESTATION | Sæt en `attestationResponsible` på et `ItSystem`, sæt "systemansvarlig attesterer tildelinger" på en `UserRole`, tildel rollen til en bruger. |
| IT_SYSTEM_ROLES_ATTESTATION | Sæt blot en `attestationResponsible` på et `ItSystem` (uden flueben i tildelings-checkbox). Trackeren danner alligevel en attestation pr. system med systemansvarlig. |
| MANAGER_DELEGATED_ATTESTATION | Lav en leder M med en eller flere rolletildelinger på sig selv (M skal være `User` med roller direkte tildelt). Opret derefter en `ManagerDelegate` hvor M peger på delegate D. Når trackeren kører, dannes en delegeret attestation der lader D attestere *M's egne* roller — ikke M's medarbejdere. |

### 8.6 Brug `MockFactory` i unit tests

`ui/src/test/java/dk/digitalidentity/rc/mockfactory/attestation/MockFactory.java`
har genvejskonstruktører til de typiske entities, fx:

```java
Attestation a = MockFactory.createOrganisationAttestation(
        1L, "att-uuid", ouUuid, "MinEnhed");
AttestationRun run = MockFactory.createAttestationRun(1L, LocalDate.now().plusDays(14));
a.setAttestationRun(run);
```

For service-test mock'er vi typisk DAO'erne (se eksempler i
`OrganisationAttestationServiceTest`, `ItSystemUsersAttestationServiceTest`,
`ItSystemUserRolesAttestationServiceTest`, `ManagerDelegateAttestationServiceTest`).

## 9. Statusenums og hvad de betyder

`AttestationStatus` (UI/DTO):

| Værdi | Betyder |
|-------|---------|
| `APPROVED` | Lederen har bekræftet at brugerens roller er korrekte. |
| `REMARKS` | Lederen har afvist mindst én rolle og evt. skrevet en bemærkning — der dannes ændringsønske til admin. |
| `DELETE` | Lederen har bedt om at brugerens AD-konto fjernes. |
| `NOT_VERIFIED` | Endnu ikke gennemgået. |

`AdminAttestationStatus` (admin-overblikket):

| Værdi | Betyder |
|-------|---------|
| `NOT_STARTED` | Ingen handling endnu. |
| `ON_GOING` | Mindst én handling, men ikke alle. |
| `FINISHED` | `verifiedAt` er sat. |

## 10. Fælles faldgruber

* **Du tilføjer en rolle og forventer at den dukker op i en attestation
  *samme dag*.** Det gør den ikke — attestationer er frosset på `createdAt`.
  En ny rolle dukker først op i næste run efter `createdAt`-datoen, eller
  vises i sektionen "tildelinger siden sidste attestering" når den har været
  aktiv i mellemrummet.
* **Et nyt run er oprettet, men attestationerne mangler.** Tjek (a) om
  deadlinen er mere end `daysForAttestation` ude (default 30 — ingen
  attestationer dannes endnu), (b) om historikken faktisk er kørt for i dag,
  (c) om OU'erne er undtaget, (d) om it-system-en har ekskluderet attestation.
* **`isOrganisationAttestationDone` returnerer false selvom alle brugere er
  godkendt.** Husk at `OrganisationRoleAttestationEntry` *også* skal være sat
  hvis enheden har egne rolletildelinger. UI'en gør det i et andet skærmbillede
  end brugerlisten.
* **Sensitive run inkluderer "tomme" brugere uden sensitive roller.**
  `shouldDisregardAssignment()` sorterer sensitive bort på assignment-niveau,
  men en bruger kan ende på listen via en *anden* sensitive rolle og så også
  se sine ikke-sensitive roller. Det er forventet.
* **Stedfortrædere kan ikke verificere sig selv.** `verifyUser` kaster
  HTTP 400 hvis `performedByUserId == userUuid`.

## 11. Hvor finder jeg mere?

* Sluttest af attestering (kommune-perspektiv):
  `doc/Rollekataloget - Test Guide Attestering.docx`.
* End-user-vejledning: `doc/Rollekataloget - Brugermanual.docx`, kapitel 12.
* Indstillinger og driftsforhold:
  `doc/Rollekataloget - Implementeringsvejledning.docx` og
  `doc/Rollekataloget - Drift.docx`.
* Eksisterende unit-test for hvert flow:
  `ui/src/test/java/dk/digitalidentity/rc/attestation/service/`.
* Migrations: søg på `*attestation*` under
  `ui/src/main/resources/db/migration/mysql/`.

