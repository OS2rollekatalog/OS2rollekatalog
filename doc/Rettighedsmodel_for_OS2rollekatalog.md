# Datamodel for rettigheder
OS2rollekatalog understøtter en række forskellige regler for rettighedsstyring, der alle påvirker hvilke rettigheder brugerne har, det kan fx være

* Brugeren er tildelt en rettighed direkte (den simpleste regel)
* Alle medarbejdere i enheden "Borgerservice" er tildelt en rettighed
* Alle ledere i Natur og Miljø forvaltningen (dvs inkl underliggende enheder) er tildelt en rettighed
* ... og mange andre typer af regler

En beregningsmotor i OS2rollekatalog (CurrentAssignmentCalculator) udregner de faktiske gældende rettighedstildelinger, og gemmer disse i current_assignment tabellen i databasen. På runtime laves der altid opslag i denne database, da den er simpel at lave opslag i, og altid afspejler de aktuelt gældende tildelinger, baseret på de regler der er opsat.

## Interceptor-model til at skabe current_assignment tabellen
For at sikre at current_assignments tabellen ER retvisende, skal ændringer i regelgrundlaget trigge løbende opdateringer - dvs at hvis man fx laver en ny regel på en enhed, så skal alle relevante brugeres rettigheder genberegnes, og current_assignments tabellen afspejle denne ændring. Dette håndteres via interceptors, der fanger alle relevante metoder i koden.

![Interceptor illustration](/doc/img/RoleChangeInterceptor.png)

I klassen RoleChangeInterceptor.java er disse events opsat som cutpoints, der fanger specifikke metoder i koden

![Interceptor cutpoints](/doc/img/RoleChangeInterceptor-Cutpoint.png)

Tilsammen dækker disse cutpoints alle metoder i koden som kan ændre på en rettighedsregel (og det er vigtigt, at man ved introduktionen af nye typer af regler, også tilføjer cutpoints til disse i interceptoren, da rettigheder ellers ikke opdateres).

## Dannelse af historik til attestering, rapportering m.m.
Tabellen current_assignments indeholder kun aktuelt gældende rettigheder, så for at sikre at der er et datagrundlag til rapportering, attestering, og andre processer der har brug for at kende til ændringer i rettigheder, dannes der også historik-tabeller over tildelinger. Disse gemmes i tabellen historic_assignment.

Denne tabel indeholder mere eller mindre samme oplysninger som current_assignment tabellen, men er udvidet med yderligere metadata, der bl.a. angiver hvornår rettigheden var gyldig, og indeholder dermed også historiske tildelinger.

Historiktabellen vedligeholdes samtidig med current_assignment tabellen, og i klassen CurrentAssignmentService, hvor man gemmer alle aktuelle rettigheder for en given bruger, laver den også opdatering i historic_assignment som en side-effekt af at opdatere de faktiske gældende rettigheder - dvs historikken skabes i samme flow som aktuelle rettigheder, og vil derfor altid afspejle hvad der på et tidspunkt har været gældende jf current_assignment tabellen.

![historik kode](/doc/img/saveAllForUserCode.png)

## Replikering af rettighedsændringer til eksterne systemer
OS2rollekatalog sender rettighedsændringer til en række eksterne systemer, herunder Active Directory, Miljøportalen, MitID Erhverv og KSP/CICS. OS2rollekatlaog har også en række andre processer som trigges af rettighedsændringer, fx håndteringen af manuelle it-systemer (også kaldet simple it-systemer).

Da current_assignment tabellen alene indeholder aktuelle rettigheder, er den mangelfuld til at afgøre alle rettighedsændringer - fx kan den ikke nemt fortælle hvilke rettigheder der er frataget.

Derfor baserer disse processer sig på historiktabellen historic_assignment, da den indeholder alle rettigheder, inkl dem der er frataget, samt oplysninger om hvornår disse er frataget. Ud fra den oplysning, kan der dannes en egentlig "delta" på hvilke rettighedsændringer der er foretaget, som så kan sendes til de eksterne systemer til behandling.

OS2rollekatalog har klassen PublishAssignmentService der håndterer dette flow. Dens primære afvikling kører som et skeduleret job, der afvikles en gang i minuttet, og finder alle rettighedsændringer (via historic_assignment) der er udført det sidste minut (i praksis finder den alle ændringer udført siden sidste afvikling, men det vil typisk være det seneste minut).

For hver rettighedsændring fundet, vil PublishAssignmentService sende et HookEvent til alle klasser der implementerer interfacet AssignmentHookHandler. Dette HookEvent indeholder alle relevante oplysninger om rettighedsændringen, og det er så den individuelle AssignmentHookHandler's ansvar at udføre den nødvendige opdatering.

![event publisering](/doc/img/publishEvent.png)

Hvert ekstern system (eller hver process der har brug for at kende til rettighedsændringer), har sin egen AssignmentHookHandler, her et eksempel på den der sender rettighedsændringer til Active Directory

![event publisering til AD](/doc/img/ActiveDirectoryHookHandler.png)

En AssignmentHookHandler bør ikke udføre handlinger der tager langt tid (fx kalde webservices) i sin handleEvent() implementation, da dette blokerer for de efterfølgende events, herunder også events til andre AssignmentHookHandler klasser. I stedet skal de enten afvikle disse asynkront, smide den i en lokal kø til afvikling, og/eller finde en anden robust måde at afvikle rettighedsændringen. Da et HookEvent kun afleveres én gang til en AssignmentHookHandler, er det i alle tilfælde bedst at placere dem i en intern kø, så man kan implementre relevant retry-logik.

## Hjørnetilfælde håndteret af specialkode
Nogle ændringer af rettigheder falder udenfor det nuværende flow, som primært baserer sig på Jobfunktionsroller. Af eksisterende "workarounds" findes der i dag disse i kodebasen

### håndtering af vægte på systemroller (til AD grupper)
Systemroller kan have en vægt, hvor en person med flere systemroller tildelt indenfor samme it-system, så kun effektivt skal have den/de roller med højeste vægt tildelt. Dette er en opmærkning lavet til håndteringen af AD grupper, og ændringen på vægte er isoleret til en hjælper-metode, som så også sikrer at AD grupper opdateres når vægte ændres

![ændring på vægte](/doc/img/ChangeWeight.png)

### håndtering af ændrer i systemroller
Historiktabellen historic_assignment er en historiktabel over tildelte Jobfunktionsroller. Hvis indholdet af en Jobfunktionsrolle ændrer sig, fx hvis der tilføjes eller fjernes en systemrolle, så ændrer det ikke ved historikken.

Dvs at overvågning af historiktabellen, udført af PublishAssignmentService, ikke vil kunne fange disse ændringer. Der er it-systemer (fx Active Directory), hvor ændringer til systemrollerne i en jobfunktionsrolle, vil kræve at der skal opdateres gruppe-medlemsskaber i AD. For at understøtte dette (samt andre replikeringer der har samme behov), er der tilføjet en direkte overførsel af information fra RoleChangeInterceptor til PublishAssignmentService

![direkte kommunikation](/doc/img/DirectCommunication.png)

Her sendes en besked til PublishAssignmentService, der fortæller at en given jobfunktionsrolle har fået ændret indhold. Dette trigger så et EventHook (for hver bruger der er tildelt denne Jobfunktionsrolle) til alle AssignmentHookHandlers.

### håndtering af fremtidige tildelinger
Historiktabellen historic_assignment indeholder også oplysninger om fremtidige tildelinger - logikken i PublishAssignmentService kigger alene på UpdatedAt timestamp'et, og vil derfor modtage fremtidige ændringer når de oprettes, og kan ikke på det tidspunkt sende dem som event. For at understøtte dette scenarie, placeres disse hændelser i en kø-tabel (future_assignments), som der så afvikles hver morgen kort efter midnat (for de tildelinger som skal være aktiv netop den dag)

![fremtidige tildelinger](/doc/img/FutureAssignments.png)

## Overblik over flow
Nedenstående tegning illustrerer flowet i sine hovedtræk

![flow](/doc/img/Flow.png)

