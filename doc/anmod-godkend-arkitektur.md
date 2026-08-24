# Anmod/Godkend — arkitektur og invarianter

Udviklerdokumentation for rolle-anmodnings- og godkendelses-flowet i `dk.digitalidentity.rc.rolerequest`. Beskriver hvor reglerne lever, hvilke invarianter der skal holdes ved refaktoreringer, og hvor flowet historisk er gået i stykker.

## Overordnet flow

```
       Anmoder                       Modtager                   Godkender
   (logget ind bruger)            (rolle-modtager)         (kan være samme/forskel)
          │                              │                          │
          │   1. RolerequestRestController.create()                 │
          │   ──────────────────────────────────────────────►       │
          │       RequestService.canRequest(role, receiver, ou, …)  │
          │       └─ permissions via ApproverOptionService          │
          │       └─ rettigheder via determineRequestable           │
          │                                                         │
          │   2. RoleRequest persisteres med status REQUESTED       │
          │                                                         │
          │   3. RequestNotifierService scheduled task              │
          │       └─ getEmailsToSendTo(request)                     │
          │       └─ slår op via RequestApproverResolver.canApprove │
          │       └─ sender mail til alle gyldige godkendere ────►  │
          │                                                         │
          │                                  4. Godkender åbner liste
          │                                     RequestService.getPendingApprovableRequests()
          │                                     └─ filtrer via resolver.canApprove
          │                                                         │
          │                                  5. Godkender approve/reject
          │                                     RequestService.approve/reject
          │                                     └─ verificerer canApprove igen
```

## Enums

`RequestableBy` (hvem må *anmode*) og `ApprovableBy` (hvem må *godkende*) er to forskellige enums. De har overlappende værdier (begge har `AUTHRESPONSIBLE`, `MANAGERORSUBSTITUTE`, `AUTHORIZED`, `INHERIT`) men ikke identiske, og deres semantik er forskellig — anmod-siden tjekker rettigheder relativt til modtageren, godkend-siden tjekker rettigheder relativt til anmodningen (som indeholder modtageren).

| Værdi | RequestableBy | ApprovableBy | Betydning |
|---|---|---|---|
| `INHERIT` | ✓ | ✓ | Arv fra IT-system, derefter globale settings |
| `AUTOMATIC` | – | ✓ | Anmodningen auto-godkendes uden modtager |
| `EMPLOYEE` | ✓ | – | Modtageren selv må anmode |
| `MANAGERORSUBSTITUTE` | ✓ | ✓ | Leder eller stedfortræder for modtagerens OU |
| `AUTHRESPONSIBLE` | ✓ | ✓ | Autorisationsansvarlig **for modtagerens OU** |
| `AUTHORIZED` | ✓ | ✓ | Bemyndiget bruger inden for matchende IT-system + OU-scope |
| `SYSTEMRESPONSIBLE` | – | ✓ | `itSystem.getAttestationResponsible()` (ikke `systemOwner`) |
| `ADMIN` | ✓ | – | Direkte admin-rolle (admin bypasser også godkend-tjekket) |
| `NONE` | ✓ | – | Ingen må anmode |

## Hvor reglerne lever

### Anmod-siden

- `RequestService.canRequest(role, receiver, ou, globalSetting)` — autoritativ enkelt-anmodnings-check.
- `RequestService.getRequestableUserRoles(receiver)` — filtrerer hele rollekataloget til hvad brugeren må anmode om til en given modtager.
- `RequestService.determineRequestable` / `isPermissionMatchingRights` — den fælles tabel for `RequestableBy → boolean`.

### Godkend-siden

- `RequestApproverResolver.canApprove(request, approver)` — autoritativ check, bruges af **både** listing (`getPendingApprovableRequests`) **og** mail-notifier (`getEmailsToSendTo`). Dette er det eneste sted reglerne for godkendelse må findes.
- `RequestApproverResolver.resolveEffectiveOptions(request)` — løser `INHERIT` og returnerer den effektive liste af `ApprovableBy`.
- `RequestApproverResolver.isOuAccessAllowed` / `isItSystemAccessAllowed` — `AUTHORIZED`-specifikke OU/IT-system-tjek der også genbruges fra notifieren til at filtrere kandidatlister.

### INHERIT-resolution

`ApproverOptionService.getInheritedApproverOption` (og `getInheritedRequesterPermission`) er det eneste sted INHERIT må løses. Kæden er:

1. Rollens egen permission, hvis ikke `INHERIT`
2. Ellers IT-systemets permission, hvis ikke `INHERIT` (kun for UserRole — RoleGroup har ikke IT-system)
3. Ellers `settingsService.getRolerequestApprover()` / `getRolerequestRequester()`

Resultatet er en `List<ApprovableBy>`, ikke en enkelt værdi — flere godkendelseskanaler kan være aktive samtidigt.

## Invarianter

### AUTHRESPONSIBLE skal scopes til modtagerens OU

Autorisationsansvarlig er **per OrgUnit** uden arv og uden stedfortræder. En bruger der er autorisationsansvarlig for OU-A må ikke godkende anmodninger for modtagere i OU-B.

**Korrekt** (anmod- og godkend-siden bør begge se sådan ud):

```java
orgUnitService.isAuthorizationManagerFor(approver, receiver)
```

**Forkert** (any-OU — har givet bagslag to gange):

```java
!orgUnitService.getByAuthorizationManagerMatchingUser(approver).isEmpty()
```

`getByAuthorizationManagerMatchingUser` returnerer alle OU'er hvor brugeren er autorisationsansvarlig — uden at vide noget om modtageren. Den må bruges til UI-formål (fx "vis hvilke OU'er jeg er ansvarlig for") men aldrig som permission-gate.

### Anmod- og godkend-siden skal være enige

`RequestService.getPendingApprovableRequests` (listen) og `RequestNotifierService.getEmailsToSendTo` (mailen) skal returnere konsistente sæt for den samme anmodning. Inden refaktoreringen i 2026r1 MR !700 havde de hver sin inline kopi af logikken, hvilket ledte til at folk fik mails om anmodninger de ikke kunne godkende. De skal nu begge gå gennem `RequestApproverResolver.canApprove` — ikke duplikere logik.

### Modtageren godkender ikke sig selv

`canApprove` returnerer altid `false` hvis `approver == request.getRequester()`. `getPendingApprovableRequests` filtrerer derudover også på `req.getReceiver() != user` for samme effekt på listen.

### AUTHORIZED-afgrænsninger flettes — postponed må ikke skygge VALUE

I `accessibleItsSystems`/`accessibleOrgUnits` skal *postponed*-afgrænsninger og konkrete *VALUE*-afgrænsninger **flettes** (union). Et tidligt `return` af postponed-mængden (fordi den er ikke-tom) skygger for VALUE-afgrænsninger fra brugerens øvrige bemyndiget-roller, så vedkommende mister adgang de retmæssigt har. Samtidig gælder: en **tom** afgrænsning (ingen IT-system/OU valgt) betyder bevidst **ALLE** — den semantik skal bevares; det var kun skygningen der var fejlen.

### SYSTEMRESPONSIBLE bruger `attestationResponsible`

Ikke `systemOwner`. `systemOwner` er attestations-flowet (en anden brugssituation). I anmod-godkend-flowet skal `itSystem.getAttestationResponsible()` bruges konsekvent — både til at afgøre om brugeren må godkende og til at finde modtageren af notifikations-mailen.

### RoleGroup ≠ UserRole

`canApproveForRoleGroup` itererer `roleGroup.getUserRoleAssignments()` og kræver typisk at constraint-tjekket holder for **alle** assignments (`allMatch`). Ny logik der kun håndterer `UserRole` skal også have en RoleGroup-pendant — det er en gentagen kilde til "manglede en gren"-bugs.

## Historiske bugs at lære af

| Dato | Bug | Hvor | Hvorfor det skete |
|---|---|---|---|
| pre-2026 | `canApprove` AUTHRESPONSIBLE-check var any-OU | `RequestService.determineApprovable` | Listens inline filter scopede korrekt, så bug'et var skjult i UI |
| 2026-04 | Anmod-siden AUTHRESPONSIBLE var any-OU | `RequestService.getPermittedSettings` | Samme any-OU-mønster — fixet ved at scope til modtagerens OU |
| 2026-05 | Konsolidering af listing + notifier mistede listens scopede filter | `RequestApproverResolver.determineApprovable` (MR !700) | Refaktoreringen valgte den eksisterende unscoped variant som "kanonisk" og lagde dermed bug'et frit i listen |
| 2026-05 | Leder-anmoder-blokerer-flowet | `RequestApproverResolver.determineApprovable` + `OrgUnitService.getEffectiveApprover` | `getEffectiveApprover` sprang ikke OU'er over hvor lederen selv var anmoder, så ingen højere leder blev fundet |
| 2026-06 | Bemyndiget fik forkerte IT-systemer (nogle så intet, andre alt) | `RequestAuthorizedRoleService.accessibleItsSystems` (+ `accessibleOrgUnits`) | En ikke-tom *postponed*-afgrænsning fik metoden til at returnere med det samme og dermed **skygge** for de konkrete (VALUE-)afgrænsninger fra andre roller. Fixet ved at **flette** postponed- og VALUE-afgrænsninger i stedet for short-circuit. NB: tom afgrænsning ⇒ ALLE er bevidst og skal bevares — kun skygningen var fejlen |

Fællesnævner: når den fælles regel-tabel ændres, skal **begge** sider opdateres samtidigt, og scoping-invarianten på AUTHRESPONSIBLE skal eksplicit verificeres med en test der ville fejle ved en regression til any-OU-varianten.

## Tests

`ui/src/test/java/dk/digitalidentity/rc/rolerequest/service/RequestServiceTest.java` rummer både anmod- og godkend-suiterne. Test-fabrik er `MockFactory` i `dk.digitalidentity.rc.mockfactory.rolerequest`. Nye permission-grene skal have positive + negative tests for både `UserRole` og `RoleGroup`, og AUTHRESPONSIBLE-tests skal eksplicit verificere scoping ved at stubbe `getByAuthorizationManagerMatchingUser` med en ikke-tom liste mens `isAuthorizationManagerFor` returnerer false — testen fanger derved en regression til any-OU-varianten.
