Unreleased
- Rettelse: Kontaktmailen til udfører og advis om ændringer i it-systemer angav den, der oprindeligt tildelte rollen, som den, der havde fjernet den. Hvem der fjerner en rolle, blev aldrig registreret, fordi fjernelsen først opdages, når tildelingerne genberegnes i baggrunden. Den, der laver ændringen, følger nu med til genberegningen, så mailen nævner den person, der fjernede rollen, også når den forsvinder via en enhed, en stilling eller en rollebuket. Forsvinder rollen uden at en person står bag, fx ved fratrædelse, står der "Systembruger (system)" (#124)
- Rettelse: Et attesteringsrul, der var over deadline men endnu ikke afløst af det næste, fik hver nat nye enheder, medarbejdere og it-systemer med, som var kommet til efter deadline. Rullet kunne derfor aldrig blive færdigt. Et rul over deadline får nu ikke længere nye attesteringer; de kommer med i næste rul. Skift af ansvarlig på eksisterende attesteringer slår fortsat igennem (#209)
- Rettelse: Den natlige opdatering af attesteringens tildelinger kunne løbe tør for hukommelse og stoppe hele applikationen, hvis et it-system havde mange tildelinger. Alle tildelinger på it-systemet blev hentet og holdt i hukommelsen på én gang. Tildelingerne hentes nu i bidder (#196)
- Rettelse: Kolonnen "Enhed" i attesteringsmodulets rapporter var tom for direkte tildelte roller og rollebuketter siden 2026r2, mens ophørte tildelinger fra før opgraderingen havde enheden udfyldt. Kolonnen viser nu den ansvarlige enhed, når rollen ikke er tildelt via en enhed (#197)
- Rettelse: Revisionsrapporten viste tildelinger som "Ikke attesteret" efter et gennemført rul, selvom ingen var blevet spurgt om dem. I et følsomt rul gjaldt det brugere uden følsomme roller i en enhed, der kun var med i rullet på grund af andre brugere, og for roller tildelt via enhed var det nok, at en it-systemansvarlig havde attesteret samme it-system for en anden samling. Begge vises nu som "Uden for attestering" (#201)
- Rettelse: EntraID-synkroniseringen fejlede ved hver kørsel, hvis en gruppe i EntraID havde skiftet navn eller beskrivelse, og alle it-systemer efter den berørte rolle blev sprunget over - også deres medlemskaber. Auditloggen kunne kun aflæse rollens tidligere værdier, når rollen blev gemt fra brugerfladen, og ikke fra et baggrundsjob. De tidligere værdier hentes nu fra databasen, når rollen gemmes af et baggrundsjob (#200)
- Rettelse: Revisionsrapporten for et attestationsrul viste attesteringsstatus, der ikke stammede fra det valgte rul. Tildelinger i enheder, der slet ikke var med i rullet - fx efter et følsomt rul, hvor kun enheder med følsomme roller attesteres - stod som "Ikke attesteret" i stedet for "Uden for attestering", og markeringen "attestering i gang" kom fra det efterfølgende rul, der stadig kørte. Rapporten bruger nu kun attesteringer fra det rul, den er trukket for, og en attestering, der ikke har taget stilling til tildelingen, kan ikke længere overskrive statussen fra den attestering, der havde ansvaret. Er ingen af de attesteringer, der dækkede tildelingen, nået frem til en afgørelse, oplyses den nyeste af dem frem for den ældste (#162)
- Rettelse: En rollebuket tildelt til en enhed med afgrænsning til bestemte stillinger blev vist som tildelt via enheden, uden stillingen. Afgrænsningen virkede — kun medarbejdere med de valgte stillinger fik buketten — men stillingen blev ikke båret med over på de beregnede tildelinger, så hverken brugerens rolleoversigt, rollebukettens liste over tildelte brugere eller `GET /api/v2/rolegroup/{id}/assignments` kunne vise, at tildelingen kom fra et stillingskryds. Stillingen sættes nu på de beregnede rækker. De berørte tildelinger genberegnes ved opgraderingen og får derved ny startdato i historikken, så de fremgår som nye i den førstkommende attestering (#111)
- Rettelse: Pladsholderen {afgrænsninger} stod ikke på listen over pladsholdere til den gentagne del af mailskabelonen "Kontakt udfører - jobfunktionsrolle", kun på advis-varianten. Pladsholderen blev tabt, da udfører- og advis-skabelonen fik hver sin liste for at give plads til {begrundelse}. Den virkede, hvis man selv skrev den ind, og står nu på listen igen (#164)
- Rettelse: En bruger kunne markere roller under "Alle jobfunktionsroller" i anmodningsguiden og blev så afvist med en fejl ved tryk på Anmod — på 2026r3 vist som "Din session er udløbet", selvom sessionen var i orden. Rollerne var sat op til både at arve og at have deres egen indstilling for, hvem der må anmode. De to ting udelukker hinanden, men rollelisten og selve anmodningen fortolkede kombinationen forskelligt, så listen viste roller frem, som anmodningen afviste. Begge steder følger nu samme regel: arver en rolle, gælder det arvede alene. Roller med begge dele forsvinder derfor fra listen, indtil opsætningen skrives ud eksplicit. Det samme gjaldt indstillingen for, hvem der må godkende, hvor listen kunne vise en anden godkender end den, anmodningen rent faktisk fik — og roller, hvis godkender stod tom efter fjernelsen af "Administrator", arver nu igen godkender fra it-systemet eller den globale indstilling. Brugerfladen kan desuden ikke længere gemme kombinationen (#159)
- Rettelse: Med "Jobfunktionsroller og rollebuketter listes samlet" slået til faldt anmodningsguiden tilbage til fire adskilte faner, så snart fanen "Anbefalede" eller "Alle" blev fravalgt. Den samlede visning krævede, at begge faner var slået til. Den vises nu, så længe mindst én af dem er slået til (#154)
- Rettelse: Attesteringen viste navn og beskrivelse på jobfunktionsroller, rollebuketter og systemroller, som de så ud dengang tildelingen blev oprettet. Rettede man beskrivelsen — fx for at give lederne mere at forholde sig til, inden en kontrol blev sendt ud — slog ændringen aldrig igennem i den igangværende attestering. Historiktabellerne gemmer en kopi af navn og beskrivelse, når rækken skrives, og kopien blev aldrig opdateret. Attesteringen henter dem nu fra rollen selv og bruger kun kopien, hvis rollen er slettet
- Rettelse: Godkendelse af en anmodning om fjernelse af en rettighed udløste ikke længere mail til leder og autorisationsansvarlige. Fjern-anmodninger arver ansættelsesstedet fra den tildeling, der skal fjernes, men opslaget blev lagt om til de beregnede tildelinger, hvor feltet kun er udfyldt for nedarvede tildelinger - og kun direkte tildelinger kan anmodes fjernet. Anmodningen stod derfor uden enhed, og mailen blev droppet uden spor i loggen. Ansættelsesstedet hentes nu fra tildelingen igen, og en anmodning uden enhed skriver en advarsel i loggen
- Rettelse: Listen over afventende anmodninger kunne fejle for alle uden administratorrettigheder, hvis blot én anmodning stod uden ansættelsessted. Opslaget af den godkendende leder tjekkede ikke for manglende enhed. En anmodning uden enhed kan nu blot ikke godkendes
- Rettelse: I Excel-rapportens ark "Enhedsroller" stod fravalgte stillinger i kolonnen "Udvalgte stillinger", så en tildeling så ud til at gælde præcis de stillinger, den netop ikke gjaldt. Udvalgte og fravalgte stillinger blev lagt i samme liste, fordi arket kun havde én kolonne til dem. Fravalgte stillinger har nu deres egen kolonne, hvorved "Udvalgte tillidsfunktioner" rykker én kolonne til højre (#139)
- Rettelse: Kun én systemansvarlig og én systemejer pr. it-system blev hentet fra Kitos, selvom kataloget understøtter flere. Var der registreret to i Kitos, kom kun den ene med, og kunne den første ikke genkendes som bruger i kataloget, kom ingen med. Alle systemansvarlige og systemejere fra Kitos overføres nu, og en person, der ikke kan genkendes, springes over uden at koste de øvrige deres plads
- Rettelse: I Excel-rapporten "Brugerroller" stod en rettighed, der var tildelt en enhed med "Fravælg stillinger", som "Tildelt via stilling" efterfulgt af medarbejderens egen stillingsbetegnelse. Den beregnede tildeling får stemplet modtagerens stilling, så rapporten kan skelne medarbejderens ansættelser fra hinanden, men stemplet kunne ikke fortælle, om stillingen var årsagen til tildelingen eller blot den, medarbejderen tilfældigvis har - så én enhedstildeling lignede en række forskellige stillingstildelinger. Tildelingen husker nu hvilken af de to det er, og en tildeling med fravalgte stillinger vises som "Tildelt via enhed" i rapporten, i attesteringen og på medarbejderens rolleoversigt, uden medarbejderens egen stilling under sig. Stillingen bliver stående på selve tildelingen, så rapporten fortsat kun medtager de ansættelser, tildelingen faktisk nåede. Åbne tildelinger rettes ved opgraderingen; rapporter trukket på en dato før opgraderingen viser fortsat den gamle tekst. Tildelingerne attesteres nu som en del af rolleopbygningen i enheden frem for pr. stilling, hvilket kan ændre den viste attesteringsstatus. API v2 angiver tilsvarende ORG_UNIT i assignedThrough og udelader assignedThroughTitle for disse tildelinger
- Rettelse: Attestering af rolleopbygning blev sendt til personer, der ikke længere er systemansvarlige for it-systemet. Modtagerlisten blev slået op i et forældet øjebliksbillede, der ved opgraderingen blev fyldt med alle, der nogensinde havde stået som ansvarlige, mens adgangen til selve attesteringen blev styret af den aktuelle liste - modtagerne fik altså en mail, de ikke kunne handle på. Øjebliksbilledet bringes nu i sync med den aktuelle liste ved opstart
- Rettelse: Tildelinger lavet med "Fravælg enheder nedarvet" nåede aldrig ud til brugerne. Ingen af de fire metoder bag fanen var dækket af de hooks, der udløser genberegning, provisionering (AD/KSP-CICS/DMP) og historik, så tildelingen lå kun på enheden i databasen og ramte først brugerne, hvis de af andre grunde blev genberegnet. Både oprettelse og redigering udløser nu genberegning, provisionering og historik som alle andre enhedstildelinger
- Rettelse: En tildeling med undtagne enheder blev vist som "-13 stillinger" i fanen "Enheder" på jobfunktionsroller og rollebuketter. Den vises nu som "Nedarvet (med undtagne enheder)"
- Rettelse: Ændringer af dataafgrænsninger på en systemrolle nåede ikke ud til brugerne. Afgrænsningerne indgår i den beregnede tildeling, men hverken tilføjelse, ændring, fjernelse eller bulk-erstatning udløste en genberegning, så brugerne beholdt de gamle værdier, indtil en anden ændring tilfældigvis rørte dem
- Rettelse: En nedarvet rollebuket-tildeling med fremtidig startdato genberegnede kun brugerne i selve enheden, ikke i underenhederne, den dag den blev aktiv
- Rettelse: `rc.customer.apikey` giver ikke længere adgang til API'erne, efter at den automatiske oprettelse af en "Administrator"-klient ved første opstart blev fjernet, men Docker-dokumentationen beskrev fortsat nøglen som adgangen til API'erne. En frisk installation, der fulgte dokumentationen, fik derfor 401 på alle API-kald. Docker.md beskriver nu, at den første klient skal oprettes direkte i databasen, og at efterfølgende klienter oprettes i brugergrænsefladen under Klienter
- Rettelse: Opslag af API-klienter cachede også de opslag, der ikke fandt en klient. En nyoprettet klient fik derfor 401 i op til 10 minutter, og ukendte nøgler kunne fylde cachen ubegrænset. Opslag uden hit caches ikke længere
- Rettelse: I Excel-rapporten "Brugerroller" gentog kolonnerne "Enhedsnavn" og "Enheds UUID" den enhed, rollen var tildelt på - altså det samme som kolonnen "Tildelt via" - i stedet for medarbejderens ansættelsesenhed. Kolonnerne viser nu igen ansættelsesenheden, og en rolle nedarvet fra en overliggende enhed vises på den enhed, medarbejderen faktisk er ansat i. Ledere og stedfortrædere uden ansættelse i enheden vises fortsat på den enhed, rollen er tildelt på
- Rettelse: POST /api/v2/function/sync afviste en tom liste med 400, så kildesystemet ikke kunne deaktivere sin sidste tillidsfunktion. En tom liste deaktiverer nu alle tillidsfunktioner, som den altid har gjort på POST /api/function og POST /api/title
- Rettelse: Applikationen kunne ikke starte, medmindre `spring.main.lazy-initialization` var slået til. Et internt application event var ved en fejl markeret som Spring-bean, og dens konstruktørparameter kan ikke autowires, så opstarten fejlede med "required a bean of type 'long' that could not be found"
- Reparation af beregnede felter, der kun kan udregnes i Java, kører nu som et baggrundsjob i stedet for en databasemigrering. Jobbet gennemgår rækkerne i små bidder, så driften ikke belastes, og gentages automatisk når beregningen ændres. Første job genopretter record_hash på åbne historikposter for enhedstildelinger, så en fjernet eller ændret tildeling igen kan lukke sin historikpost
- Rettelse: Oprydningen "Fjern direkte tildelinger efter deaktivering" slettede de direkte tildelinger på deaktiverede og slettede brugere uden at udløse en genberegning. Rettighederne blev derfor liggende i de beregnede tildelinger og fremgik fortsat af brugerens side, rapporter, attestering og AD/KSP-CICS — og da kilderækkerne var væk, kunne ingen senere ændring udløse genberegningen. Tildelingerne fjernes nu ad samme vej som en manuel fjernelse, så de bliver auditlogget og genberegnet, og hver bruger ryddes op i sin egen transaktion, så én fejlende bruger ikke afbryder hele kørslen (#130)
- Rettelse: Sletning eller ændring af en systemrolle fejlede med en databasefejl, hvis rollens navn var længere end 128 tegn. Navnekolonnen i den log, der ligger bag notifikationsmailen om ændringer i it-systemer, blev ikke udvidet sammen med systemrollernes eget navnefelt. Kolonnen er nu lige så bred som kilden
- Rettelse: Enheder blev udtrukket til attestering og lukket igen af sig selv natten efter, uden at lederen havde taget stilling til noget. Det ramte enheder, hvis medarbejdere kun har rettigheder arvet fra en overliggende enhed: udtrækket talte de arvede rettigheder med, mens afslutningen kun ser på det, lederen selv skal godkende - de direkte tildelinger og enhedens egne rettigheder. De to sider bruger nu samme regel, så en enhed kun kommer med i attesteringen, når der faktisk er noget at tage stilling til. Arvede rettigheder attesteres fortsat på den enhed, de er tildelt på
- Rettelse: En stedfortræder kunne fortsat se og hente attesteringsrapporter for en enhed, der havde fået en anden leder. En stedfortrædelse gælder en bestemt leder på en bestemt enhed, men rækken bliver liggende, når enheden skifter leder - typisk når en enhed, der har arvet sin leder fra den overliggende enhed, får sin egen leder. Attesteringsmodulet kontrollerer nu, at stedfortrædelsens leder stadig er leder for enheden
- Rettelse: Den første mail i en attestering nåede kun frem til en håndfuld ledere, mens rykkerne bagefter kom ud til alle. Udsendelsen løber alle modtagere igennem i én kørsel, og gik det galt for en enkelt modtager, stoppede resten af kørslen med det samme. Da hver mailtype kun kunne sendes på én bestemt dato, blev de manglende mails aldrig sendt senere. En modtager, der fejler, springes nu over og logges med navn, så resten af kørslen fortsætter, og en mail, der ikke nåede ud på sin dato, sendes ved næste kørsel — indtil den efterfølgende rykker er på tur, så ingen får to mails samme dag

2026.08.24 - Release 2026r4
- [ROL-433](https://os2web.atlassian.net/browse/ROL-433) Flyt javascript til selvstændige filer
- [ROL-458](https://os2web.atlassian.net/browse/ROL-458) Understøttelse af fuldført oprettelse/tildeling i Simple It-systemer
- [ROL-474](https://os2web.atlassian.net/browse/ROL-474) Tildeling af rolle til flere brugere på samme tid
- [ROL-540](https://os2web.atlassian.net/browse/ROL-540) Anmod/Godkend Log
- [ROL-541](https://os2web.atlassian.net/browse/ROL-541) Udvidelse af CreateDelete-funktionalitet: Benyt It-systemets opsatte BackSync OU'er
- [ROL-543](https://os2web.atlassian.net/browse/ROL-543) Tekstredigering i beskrivelsesfelt knyttet til jobfunktionsroller og rollebuketter
- [ROL-544](https://os2web.atlassian.net/browse/ROL-544) Muligheden for, ved udskudte dataafgrænsninger, at gruppere valget af udskudt dataafgrænsning
- [ROL-549](https://os2web.atlassian.net/browse/ROL-549) ADSyncService - generelle indstillinger - AD Noter
- [ROL-552](https://os2web.atlassian.net/browse/ROL-552) Ny valgmulighed for visning af roller ved anmodning til medarbejder
- [ROL-553](https://os2web.atlassian.net/browse/ROL-553) Anmod / Godkend modulet – udskudt dataafgrænsning
- [ROL-559](https://os2web.atlassian.net/browse/ROL-559) Tilføjelse til notifikationsmail til tildeler
- [ROL-564](https://os2web.atlassian.net/browse/ROL-564) Anmod/ godkend - Begrundelsesfelt medtages i mail information
- [ROL-572](https://os2web.atlassian.net/browse/ROL-572) Opdatering af brugermanualen


Øvrige fejlrettelser og mindre forbedringer:
- Rettelse: Stavefejl ("adgant" i stedet for "adgang") i beskrivelsen på systemrollerne "Rolletildeler - Enheder" og "Rolletildeler - Brugere". Teksten rettes automatisk på eksisterende installationer
- Rettelse: En jobfunktionsrolle eller rollebuket, der var tildelt en enhed på udvalgte titler OG samtidig havde undtagne brugere, blev givet til alle i enheden, som ikke stod på undtagelseslisten - uanset titel. Undtagelseslisten fungerede altså som en selvstændig tildeling frem for kun som et fravalg. Titelfilteret respekteres nu, også når der er undtagne brugere på tildelingen. Undtagne brugere og undtagne enheder kan nu kun fravælge, aldrig tildele, og nedarvning afgøres af tildelingens eget nedarvnings-flag. Eksisterende tildelinger, hvor flaget ikke svarede til den hidtidige adfærd (nedarvning sat sammen med undtagne brugere, eller undtagne enheder uden nedarvning), normaliseres af en migrering, så ingen bruger mister eller får rettigheder ved opgraderingen
- Rettelse: Rapporten "Rolleopbygning: Alle systemer" kunne ikke hentes (browseren viste ERR_INVALID_RESPONSE), hvis blot ét it-system ikke havde en attestering af rolleopbygning i rapportperioden. Sådanne it-systemer medtages nu korrekt med status "Ikke attesteret". Samtidig viser kolonnen "Ansvarlig bruger" nu navne i stedet for UUID'er
- Rettelse: Excel-rapporter med dataafgrænsninger gav en fejl (NullPointerException) i stedet for at blive dannet, når en dataafgrænsning af typen VALUE manglede en værdi (fx en udskudt afgrænsning). Tomme værdier håndteres nu uden at vælte rapporten
- Rettelse: Rettigheder fjernet i kataloget blev ikke altid fjernet i AD/KSP-CICS, fordi dirty-markeringen var hensigts-baseret og afkoblet fra den faktiske medlemskabsberegning. Markeringen drives nu af et RoleMembershipChanged-event udledt af CurrentAssignment-deltaet, så også fjernelser via arv/rollebuket fanges (#83)
- Rettelse: Når en rollebuket blev fjernet fra en enhed med nedarvning, blev kun enhedens egne brugere sat til genberegning — ikke brugere i de nedarvede underenheder. De beholdt derfor rollerne (også i fx OS2faktor) indtil en anden ændring eller en fuld genberegning udløste opdateringen. Var tildelingen nedarvet, genberegnes brugerne i underenhederne nu straks
- Rettelse: Funktions-API'et (POST /api/v2/function og /api/v2/function/sync) kasserede et medsendt UUID ved oprettelse og genererede sit eget. Kildesystemet skulle derfor vedligeholde en mapping mellem egne og Rollekatalogets UUID'er, og en omdøbning blev til en deaktivering + nyoprettelse, hvor de jobfunktionsroller, kommunen havde sat op på funktionen, mistede deres tilknytning. Et medsendt UUID gemmes nu som funktionens identitet, og der genereres kun et, når kaldet ikke selv angiver et. Oprettelse opdaterer samtidig navnet på en funktion, den genkender, i stedet for at kvittere med det gamle navn
- Rettelse: Læse-API'et returnerede itSystemName som null på "roller for bruger" (/api/read/user/{uuid}/roles), "roller for enhed" (/api/read/ous/{uuid}/roles) og "jobfunktionsroller pr. it-system" (/api/read/userroles/itsystems), fordi den automatiske objekt-mapning ikke længere fladede itSystem.name ud til itSystemName. Felterne udfyldes nu eksplicit
- Rettelse: Jobfunktionsroller i et skrivebeskyttet AD-it-system blev tilbudt i tildelingslisterne, selv om de aldrig kan tildeles - Rollekataloget skriver aldrig til sådanne it-systemer. Tildelingen fejlede med en tom HTTP 400 uden nogen forklaring. Rollerne tilbydes ikke længere, og afviste tildelinger svarer nu med en forklaring, som brugerfladen viser
- Rettelse: Historiske rapporter viste alle tildelinger som tildelt på opgraderingsdatoen, fordi kolonnen "Tildelt hvornår" blev læst fra den tekniske versionsdato på historik-rækken i stedet for den faktiske tildelingsdato. Tildelingsdatoen hentes nu fra tildelingens egen kilde. Eksisterende tildelinger får datoen udfyldt af et baggrundsjob efter opgraderingen, så opstarten ikke forlænges; indtil jobbet har nået en tildeling, viser den versionsdatoen som hidtil. Rapporter for datoer tilbage i tiden viser fortsat versionsdatoen for tildelinger, der siden er fjernet eller ændret
- Rettelse: Rapporten "Historiske rolletildelinger" satte attesteringsstatus på tildelinger, der var ophørt længe før attesteringen blev oprettet. Opslaget krævede kun, at attesteringen var gennemført efter tildelingens startdato, så en tildeling, der havde levet én dag flere måneder tidligere, kunne stå som "Bekræftet" med den seneste attesterings dato og attestant - altså en rolle, den ansvarlige aldrig har set. En attestering knyttes nu kun til de tildelinger, der var gyldige den dag, attesteringen blev oprettet, hvilket er præcis det øjebliksbillede, den ansvarlige blev spurgt om
- Rettelse: Tildelinger, der kom med en rollebuket, stod som "Uden for attestering" i rapporten, selv om lederen havde attesteret brugeren. Rollebuket-tildelinger gemmer rollebuket-id'et i feltet for "tildelt igennem", som rapporten tolkede som en enhed, og ledte derfor efter en enhedsattestering, der ikke findes. Rapporten afgør nu ud fra tildelingstypen, hvem der attesterer: direkte tildelinger - også dem der kommer med en rollebuket - afgøres pr. bruger af lederen i den ansvarlige enhed, mens tildelinger på en enhed eller på udvalgte titler afgøres som en del af rolleopbygningen i den enhed, rollen er tildelt på. Samtidig medtages attesteringer udført af en lederdelegat, som rapporten slet ikke slog op, og en it-systemtildeling kan ikke længere få status fra en attestering af en anden gruppe systemansvarlige
- Rettelse: "Uden for attestering" blev i rapporten brugt både om tildelinger, ingen er blevet spurgt om, og om tildelinger, der afventer en attestering. Igangværende attesteringer medtages nu: en tildeling, der endnu ikke er taget stilling til, står "Ikke attesteret", "Uden for attestering" bruges kun, når ingen attestering har omfattet tildelingen, og en beslutning truffet i en attestering, der ikke er gennemført endnu, vises med bemærkning, dato og attestant som fx "Bemærkning (attestering i gang)". Før kunne en fratagelse, en leder havde markeret, først ses i rapporten, når hele enheden var attesteret færdig
- Rettelse: I rapporten "Historiske rolletildelinger" stod kolonnen "Nedarvet" altid til "Nej". Den udledes nu af, om rollen er tildelt på en enhed

2026.06.15 - Release 2026r3
- [ROL-450](https://os2web.atlassian.net/browse/ROL-450) Rapport over rollebuketter / opmærkede rettigheder på enhed/stilling
- [ROL-460](https://os2web.atlassian.net/browse/ROL-460) Ændringsforslag: Optimering af Auditlog søgninger
- [ROL-480](https://os2web.atlassian.net/browse/ROL-480) Understøttelse af flere system-ansvarlige/ejere i It-systemer
- [ROL-499](https://os2web.atlassian.net/browse/ROL-499) Attesteringsmodul/Mailskabeloner: Manglende mailskabeloner til systemansvarlige ved (ekstra) følsomme attesteringer
- [ROL-500](https://os2web.atlassian.net/browse/ROL-500) Attestering: Gennemførte attesteringer skal vises på "Overblik over attesteringer"
- [ROL-503](https://os2web.atlassian.net/browse/ROL-503) Mailskabeloner: Mulighed for at ændre mailskabelon udsendt i forbindelse med rolletildelinger
- [ROL-520](https://os2web.atlassian.net/browse/ROL-520) Attestering: Ønske ved til-/fravalg af beskrivelsesfelt
- [ROL-522](https://os2web.atlassian.net/browse/ROL-522) Modernisering af visning af Mailskabeloner
- [ROL-523](https://os2web.atlassian.net/browse/ROL-523) Adviser: Tjek af advis: "Der er tilføjet en stilling til denne enhed, som ikke fandtes i forvejen"
- [ROL-525](https://os2web.atlassian.net/browse/ROL-525) Opdatering af Anmod/Godkend-mailskabeloner
- [ROL-526](https://os2web.atlassian.net/browse/ROL-526) Organisatorisk dataafgrænsning baseret på lederroller og tillidsfunktioner
- [ROL-530](https://os2web.atlassian.net/browse/ROL-530) Langsom ADSyncService bør optimeres
- [ROL-531](https://os2web.atlassian.net/browse/ROL-531) Anmod/godkend: Godkender er synlig på ”Anmod til medarbejder”, ”Mine anmodninger” og  ”Ventende anmodninger”
- [ROL-532](https://os2web.atlassian.net/browse/ROL-532) Mulighed for at undtage udvalgte enheder ved tildeling af jobfunktionsrolle på enhed med nedarvning
- [ROL-534](https://os2web.atlassian.net/browse/ROL-534) Beskrivelsesfelt fra rollekatalog til AD
- [ROL-538](https://os2web.atlassian.net/browse/ROL-538) Optimering af auditloggen
- [ROL-551](https://os2web.atlassian.net/browse/ROL-551) Global on/off-styring af navneopdatering på jobfunktionsroller i BackSync
- [ROL-562](https://os2web.atlassian.net/browse/ROL-562) Anmod/Godkend: Se ansættelse på medarbejder der skal tildeles en rolle

Øvrige fejlrettelser og mindre forbedringer:
- Anmod/godkend: Ledere kan anmode om roller til ledere af underliggende enheder
- Anmod/godkend: Nærmeste leders stillinger medtages i anmodningsguidens ansættelsesfilter
- Anmod/godkend: Smårettelser i tekster og rettet fejl i anmodningsloggen
- Attestering: Markant hurtigere indlæsning af "Rettighedsaktivitet"-fanen i enhedsattestering
- Attestering: Rettet at handlingsknapper (rediger/slet) på rollekatalog-systemroller blev vist for brugere uden administratorrettigheder
- Kitos-integration: Det opdages nu, når anvendelsen af et it-system fjernes i Kitos
- Rapporter: Valget "Vis systemroller" medtages nu, når en rapport hentes fra en skabelon
- Historik: Enhedstildelinger er udvidet med "tildelt af" samt start- og stopdato
- Mails: Afsendernavn kan nu konfigureres
- Tildelinger genberegnes nu ved oprydning af duplikerede brugere
- Rettet visning af HTML-tekst i dialogboks ved sletning af ikke-anvendte jobfunktionsroller
- Rettet validering af klientnavne
- API: Brugerroller fra slettede it-systemer returneres ikke længere i API v2
- API: extUuid er tilføjet på brugere (UserShallow)
- API: users-feltet på systemroller medtages ikke længere i læse-svar

2026.03.30 - Release 2026r2
- [ROL-412](https://os2web.atlassian.net/browse/ROL-412) Fjernelse af historik tabeller  
- [ROL-334](https://os2web.atlassian.net/browse/ROL-334) Øjeblikkelig notifikation ved tildeling til simple integrationer  
- [ROL-389](https://os2web.atlassian.net/browse/ROL-389) Begrænset antal rolletildelinger  
- [ROL-501](https://os2web.atlassian.net/browse/ROL-501) Opdatering af Java og Spring Boot  
- [ROL-517](https://os2web.atlassian.net/browse/ROL-517) Test-suite  
- [ROL-504](https://os2web.atlassian.net/browse/ROL-504) ADSyncService: Understøttelse af at mappe flere jobfunktionsroller til AD-grupper  
- [ROL-505](https://os2web.atlassian.net/browse/ROL-505) EntraID integration – understøtte multiple tenants  
- [ROL-506](https://os2web.atlassian.net/browse/ROL-506) Genvejsadministration  
- [ROL-508](https://os2web.atlassian.net/browse/ROL-508) Kopi af rollebuket  
- [ROL-509](https://os2web.atlassian.net/browse/ROL-509) Ny advis i OS2rollekatalog  
- [ROL-510](https://os2web.atlassian.net/browse/ROL-510) Mulighed for at udfylde sagsnummer ved tildeling af rollebuket (både til bruger og til enhed)  
- [ROL-511](https://os2web.atlassian.net/browse/ROL-511) Flere karakterer i rolle-/buketnavne  
- [ROL-513](https://os2web.atlassian.net/browse/ROL-513) Rolle/bukets navn synlig i dialogboks til rettighedstildeling til bruger/enhed  
- [ROL-514](https://os2web.atlassian.net/browse/ROL-514) Mulighed for at sætte enhedsfilter på rollebuketter  
- [ROL-515](https://os2web.atlassian.net/browse/ROL-515) Anmodningsopsætning synlig/redigerbar på jobfunktionsfanen på system  
- [ROL-516](https://os2web.atlassian.net/browse/ROL-516) Mulighed for at tildele anmodning til administrator

2026.01.23 - Release 2026r1
- [ROL-377](https://os2web.atlassian.net/browse/ROL-377) "Rolleopretter" - en ny afgrænset rolle til OS2rollekatalog  
- [ROL-385](https://os2web.atlassian.net/browse/ROL-385) Os2Rollekatalog - opdele "rolletildeler" rollen i to  
- [ROL-386](https://os2web.atlassian.net/browse/ROL-386) Udvidelse af rettigheder i Rollekataloget  
- [ROL-467](https://os2web.atlassian.net/browse/ROL-467) Anmod/Godkend: Ændre modtager af mails  
- [ROL-368](https://os2web.atlassian.net/browse/ROL-368) Tildeling af jobfunktionsroller på basis af tillidsfunktioner  
- [ROL-374](https://os2web.atlassian.net/browse/ROL-374) Tildele rettigheder til person registreret som leder på en enhed i OS2Rollekatalog  
- [ROL-478](https://os2web.atlassian.net/browse/ROL-478) Anmod/Godkend: Mulighed for at vælge dato ved anmodning af rettighed  
- [ROL-479](https://os2web.atlassian.net/browse/ROL-479) Anmod/Godkend: Tilføjelse af status kolonne for ledere  
- [ROL-429](https://os2web.atlassian.net/browse/ROL-429) Flere oplysninger i skabeloner til anmodning  
- [ROL-484](https://os2web.atlassian.net/browse/ROL-484) Attesteringsmodul decentral styret  
- [ROL-485](https://os2web.atlassian.net/browse/ROL-485) Ekstra bemærkningsfelt som ikke er låst  
- [ROL-493](https://os2web.atlassian.net/browse/ROL-493) Attesteringsmodul: Anmod om ændringer UDEN at angive hvilke JFR det drejer sig om OG attesteringsstatus bliver bekræftet.  
- [ROL-494](https://os2web.atlassian.net/browse/ROL-494) Attesteringsmodul: Der mangler en rapport til gennemført attestering af rolleopbygning  
- [ROL-482](https://os2web.atlassian.net/browse/ROL-482) Udfylde Sagsnummer ved direkte Jobfuntionsrolle tildeling  
- [ROL-496](https://os2web.atlassian.net/browse/ROL-496) Tidsbegrænsning på direkte tildelinger   
- [ROL-497](https://os2web.atlassian.net/browse/ROL-497) Anmod/Godkend - Udvidelse af historik  
- [ROL-498](https://os2web.atlassian.net/browse/ROL-498) Anmod/godkend - mulighed for at vise rollebuketter og jobfunktionsroller i en samlet liste

2025.09.29 - Release 2025r3
- [ROL-397](https://os2web.atlassian.net/browse/ROL-397) Udvide NemLog-in rollestyring med organisations-rettigheder
- [ROL-444](https://os2web.atlassian.net/browse/ROL-444) Attessteringsadministrator bør kunne se/læse alle attesteringerne
- [ROL-446](https://os2web.atlassian.net/browse/ROL-446) Advis: En bruger med direkte tildelte rettigheder har skiftet afdeling
- [ROL-447](https://os2web.atlassian.net/browse/ROL-447) Attestering: visning af dato for gennemførsel af attestering
- [ROL-448](https://os2web.atlassian.net/browse/ROL-448) Vil det være muligt at få kontakt e-mail på JFR?
- [ROL-456](https://os2web.atlassian.net/browse/ROL-456) OS2kitos integration - Rapport
- [ROL-452](https://os2web.atlassian.net/browse/ROL-452) Øvrige informationer ved notifikation på IT-system (simpel integration)
- [ROL-468](https://os2web.atlassian.net/browse/ROL-468) Mere overskuelig anmod/godkend opsætning
- [ROL-367](https://os2web.atlassian.net/browse/ROL-367) Brugersystemroller: Udvælg enheder, med nedarvning
- [ROL-382](https://os2web.atlassian.net/browse/ROL-382) Undtage personer selvom stillinger er markeret
- [ROL-457](https://os2web.atlassian.net/browse/ROL-457) Styrke': der vises kun den rolle med gennemslag pga styrke
- [ROL-466](https://os2web.atlassian.net/browse/ROL-466) Mulighed for at definere egne genveje
- [ROL-469](https://os2web.atlassian.net/browse/ROL-469) Sikring af forudsætninger ved tildeling af MS Licens AD-grupper
- [ROL-470](https://os2web.atlassian.net/browse/ROL-470) Beskrivende tekster
- [ROL-472](https://os2web.atlassian.net/browse/ROL-472) ADSyncService \ IT-systemGroup: Forbedring/forsimpling af interface
- [ROL-473](https://os2web.atlassian.net/browse/ROL-473) ADSyncService - håndtering af spærrede AD konti
- [ROL-475](https://os2web.atlassian.net/browse/ROL-475) Mulighed for at slå automatisk enhedsniveau til.

2025.06.18 - Anmod/Godkend - Release 2025r2
- [ROL-459](https://os2web.atlassian.net/browse/ROL-459) Ny anmod/godkend modul!
- [ROL-362](https://os2web.atlassian.net/browse/ROL-362) AD-integration: Benytte "Noter" i stedet for "Beskrivelse"
- [ROL-391](https://os2web.atlassian.net/browse/ROL-391) Opdatering af driftsdokumentation
- [ROL-398](https://os2web.atlassian.net/browse/ROL-398) Attestering: Opsætning af personlig godkender som supplement til 'Stedfortræder'
- [ROL-399](https://os2web.atlassian.net/browse/ROL-399) Attestering: Bedre overblik over opsatte stedfortrædere (+ evt personlige godkendere)
- [ROL-400](https://os2web.atlassian.net/browse/ROL-400) Universel indstilling til hele Rollekataloget
- [ROL-404](https://os2web.atlassian.net/browse/ROL-404) Der hentes data. Vent nogle få sekunder, og prøv derefter at klippe eller kopiere igen.
- [ROL-420](https://os2web.atlassian.net/browse/ROL-420) Attesteringsmodul: Attestering af enhedens rettigheder fungerer som på bruger
- [ROL-430](https://os2web.atlassian.net/browse/ROL-430) Advis: man viser systemejers ændringer (enhed/stilling) men ikke hvilke system man er ejer/ansvarlig for
- [ROL-432](https://os2web.atlassian.net/browse/ROL-432) Tildeling af Rollekatalogets egne roller
- [ROL-435](https://os2web.atlassian.net/browse/ROL-435) Tilføj versionsnummre til klient-download
- [ROL-436](https://os2web.atlassian.net/browse/ROL-436) API-vedligehold af systemansvarlig og systemejer
- [ROL-437](https://os2web.atlassian.net/browse/ROL-437) Mulighed for at trække en rapport i Rollekataloget via API. Det kan eksempelvis benyttes som en del af en automatisering af nogle processer i tildeling af Microsoft Licenser. Der er lavet en tilsvarende udvidelse af API til SOFD som delvist kan kopieres.
- [ROL-439](https://os2web.atlassian.net/browse/ROL-439) Udbyg AD-sync så roller i ikke-AD IT-systemer kan styre AD grupper
- [ROL-440](https://os2web.atlassian.net/browse/ROL-440) Udvidelse af ny funktion til udvælgelse af afgrænsning
- [ROL-441](https://os2web.atlassian.net/browse/ROL-441) Opmærke en rolletildeling med et sagsnummer
- [ROL-442](https://os2web.atlassian.net/browse/ROL-442) Ved opsætning af stedfortræder skal enheder sorteres alfabetisk
- [ROL-443](https://os2web.atlassian.net/browse/ROL-443) Tilføjelse til rapport
- [ROL-453](https://os2web.atlassian.net/browse/ROL-453) Azure integration - muliggør konfiguration af felt
- [ROL-455](https://os2web.atlassian.net/browse/ROL-455) Attestering: Beskrivelse af ændring ved attestering skal være valgfri eller kunne fjernes

2025.03.26 - Release 2025r1
- [ROL-363](https://os2web.atlassian.net/browse/ROL-363) Optimering af sync imellem ROL og AD grupper (kontinuerlig sync)
- [ROL-369](https://os2web.atlassian.net/browse/ROL-369) Modernisering af listen til udvælgelse af it-system
- [ROL-294](https://os2web.atlassian.net/browse/ROL-294) Attesteringsmodul: Kommentarer pr rolle
- [ROL-419](https://os2web.atlassian.net/browse/ROL-419) Rollekataloget: jobfunktionsroller bliver slettet
- [ROL-393](https://os2web.atlassian.net/browse/ROL-393) Nedarvning af rettigheder
- [ROL-405](https://os2web.atlassian.net/browse/ROL-405) Uhensigtsmæssighed på liste over bruger på en jobfunktionsrolle
- [ROL-409](https://os2web.atlassian.net/browse/ROL-409) Attestation: særlig rapport til revision
- [ROL-410](https://os2web.atlassian.net/browse/ROL-410) Attestation: Holdes altid åbent helt indtil næste attestationsrul
- [ROL-413](https://os2web.atlassian.net/browse/ROL-413) Kontakt e-mail på KOMBIT IT-systemer: Vil det være muligt at få Kontakt e-mail på KOMBIT systemer, så det er muligt at få en e-mail, når der er ændringer i rettighedstildelinger på IT-systemets JFR?
- [ROL-414](https://os2web.atlassian.net/browse/ROL-414) Attestation: Start dato når it-system "indmeldes" til attestation
- [ROL-415](https://os2web.atlassian.net/browse/ROL-415) Attestation: Primær ansættelse
- [ROL-419](https://os2web.atlassian.net/browse/ROL-419) Rollekataloget: jobfunktionsroller bliver slettet
- [ROL-438](https://os2web.atlassian.net/browse/ROL-438) Lange svartider

2024.11.06 - Release 2024r4
- [ROL-395](https://os2web.atlassian.net/browse/ROL-395) Auditlog - alt skal logges
- [ROL-376](https://os2web.atlassian.net/browse/ROL-376) Konfiguration af hvilket felt i AD rollenavne skal importeres fra
- [ROL-372](https://os2web.atlassian.net/browse/ROL-372) Kan ikke se bruger i oversigt over hvem Jobfunktionsrollen er tildelt
- [ROL-394](https://os2web.atlassian.net/browse/ROL-394) Attestering: det skal være muligt at fravælge mail til leder, når der opsat en stedfortræde
- [ROL-365](https://os2web.atlassian.net/browse/ROL-365) Forbedret administration af integration imellem ROL og AD
- [ROL-358](https://os2web.atlassian.net/browse/ROL-358) Mulighed for rapporter der afspejler fremtidige rettighedstildelinger

2024.11.06 - Release 2024r3
- [ROL-177](https://os2web.atlassian.net/browse/ROL-177) Alle AD sync services skal laves så de også kan bruges i Azure Ad
- [ROL-300](https://os2web.atlassian.net/browse/ROL-300) Vi benytter vægtning i OS2rollekatalog til at afgøre tildeling af jobfunktionsroller. Problemet er, en rapport fra IT-systemet i OS2rollekatalog ikke indeholder vægtningen.
- [ROL-322](https://os2web.atlassian.net/browse/ROL-322) Negativ/forbudt rolletildeling
- [ROL-324](https://os2web.atlassian.net/browse/ROL-324) Ekstra kolonne i brugeroversigten
- [ROL-335](https://os2web.atlassian.net/browse/ROL-335) Ændre funktionen ved at scrolle
- [ROL-344](https://os2web.atlassian.net/browse/ROL-344) Modtage email adviseringer på lokale fejl
- [ROL-355](https://os2web.atlassian.net/browse/ROL-355) AUDITLOG: den er kun tilgængelig via ADMINISTRATOR
- [ROL-384](https://os2web.atlassian.net/browse/ROL-384) Attestering: Udskudt dataafgrænsning skal fremgå af attesteringen
- [ROL-383](https://os2web.atlassian.net/browse/ROL-383) Udlæsning af navne på pnumre fra NemLog-in, og ændring i visningen
- [ROL-381](https://os2web.atlassian.net/browse/ROL-381) Allerede tildelte roller vises ikke
- [ROL-320](https://os2web.atlassian.net/browse/ROL-320) Attestering: Mulighed for at opmærke JFR med særlig SÆRLIG følsom (attesteres oftere endnu)

2024.08.28 - Release 2024r2
- [ROL-361](https://os2web.atlassian.net/browse/ROL-361) Attestering: Rapport tilføjelse
- [ROL-359](https://os2web.atlassian.net/browse/ROL-359) Oversigt / rapport over aktuelle attestering
- [ROL-346](https://os2web.atlassian.net/browse/ROL-346) Attestering: det er kun enheder, der er valgt attestering for, der skal vises i oversigten
- [ROL-343](https://os2web.atlassian.net/browse/ROL-343) Overblik over systemansvarlige i rollekataloget
- [ROL-339](https://os2web.atlassian.net/browse/ROL-339) Attestering: Mail til systemansvarlig, der skal godkende roller - men hvor er den hvor de skal godkende tildelinger?
- [ROL-338](https://os2web.atlassian.net/browse/ROL-338) Attestering: Ny mailskabelon
- [ROL-337](https://os2web.atlassian.net/browse/ROL-337) Attestering: Advis på om systemejer eller systemansvarlig fratræder
- [ROL-336](https://os2web.atlassian.net/browse/ROL-336) Attestering: Der sendes mails ud per enhed og ikke per leder - burde det ikke være per leder?
- [ROL-332](https://os2web.atlassian.net/browse/ROL-332) Attestering: Systemansvarlig: JFR der ikke er tildelt en enhed må ikke stå som 'Gennemført'
- [ROL-331](https://os2web.atlassian.net/browse/ROL-331) Attestering: Mere logisk opmærkning af om en rolle er følsom
- [ROL-330](https://os2web.atlassian.net/browse/ROL-330) IT System til brug i Attestering: Felt til angivelse af systemejer
- [ROL-328](https://os2web.atlassian.net/browse/ROL-328) Attestering - mulighed for selv at vælge tidsinterval for udsendelse af mailskabeloner
- [ROL-325](https://os2web.atlassian.net/browse/ROL-325) Excel eksport af faste rapporter
- [ROL-321](https://os2web.atlassian.net/browse/ROL-321) JFR: 'Følsom (attesteres oftere)' kan ikke ses på en JFR efter anmærkning
- [ROL-320](https://os2web.atlassian.net/browse/ROL-320) Attestering: Mulighed for at opmærke JFR med særlig SÆRLIG følsom (attesteres oftere endnu)
- [ROL-318](https://os2web.atlassian.net/browse/ROL-318) Ønske om forbedret audit log
- [ROL-314](https://os2web.atlassian.net/browse/ROL-314) Attesteringsmodul. Mulighed for at se om en attestering er følsom i rapport & oversigt.
- [ROL-310](https://os2web.atlassian.net/browse/ROL-310) Mailskabeloner i stedet for advis
- [ROL-370](https://os2web.atlassian.net/browse/ROL-370) Attestering: Attestering af AD konti
- [ROL-356](https://os2web.atlassian.net/browse/ROL-356) Opdater dokumentation

2024.05.07 - Release 2024r1
- [ROL-308](https://os2web.atlassian.net/browse/ROL-308) API nøgler i PAM løsning
- [ROL-329](https://os2web.atlassian.net/browse/ROL-329) Attestering: Ny mailskabelon
- [ROL-327](https://os2web.atlassian.net/browse/ROL-327) Ny rolle til rapport udtræk
- [ROL-312](https://os2web.atlassian.net/browse/ROL-312) Opgradere til Spring Boot 3.2 og Java 21
- [ROL-307](https://os2web.atlassian.net/browse/ROL-307) Emailskabeloner: beskrivelse
- [ROL-302](https://os2web.atlassian.net/browse/ROL-302) "Tildelingsperiode" fremgå af rapporter i Excel
- [ROL-316](https://os2web.atlassian.net/browse/ROL-316) Attesteringsmodul: konfigurabel indstilling om man ønsker at modtage ændringer eller ej
- [ROL-313](https://os2web.atlassian.net/browse/ROL-313) Fast rapport over it-systemer hvor attestering er fravalgt
- [ROL-303](https://os2web.atlassian.net/browse/ROL-303) Attestering: ændring af mail der sendes ved leders anmodning om ændringer
- [ROL-287](https://os2web.atlassian.net/browse/ROL-287) Udvidelse af administration af Rollebuketter igennem API
- [ROL-306](https://os2web.atlassian.net/browse/ROL-315) Attestering: Der ønskes en selvstændig 'fane' i Administration således alt opsætning til attesteringsmodulet foregår samlet
- [ROL-315](https://os2web.atlassian.net/browse/ROL-315) - Test af hvilke rettigheder rollekataloget roller giver adgang til

2023.12.28 - Release 2023r5
- [ROL-260](https://os2web.atlassian.net/browse/ROL-260) - Tilføje beskrivelsen fra Jobfunktionsrollen ved ændringsemailen på manuelle bestillinger
- [ROL-262](https://os2web.atlassian.net/browse/ROL-262) - Anmod om fjernelse af en rettighed
- [ROL-266](https://os2web.atlassian.net/browse/ROL-266) - Aktivt fravælge advisering på tildeling af manuelle it-systemer
- [ROL-278](https://os2web.atlassian.net/browse/ROL-278) - Bedre overblik over hvem der har fjernet en JFR (auditlog)
- [ROL-281](https://os2web.atlassian.net/browse/ROL-281) - Filtreret liste af brugere når man trækker en rapport
- [ROL-282](https://os2web.atlassian.net/browse/ROL-282) - Attestationsmodul. Visning af beskrivelse for jobfunktionsrolle
- [ROL-284](https://os2web.atlassian.net/browse/ROL-284) - Rapport: data, der udtrækkes på 'enhed'
- [ROL-291](https://os2web.atlassian.net/browse/ROL-291) - Attesteringsmodul. Mail-skabeloner: ny pladsholder
- [ROL-299](https://os2web.atlassian.net/browse/ROL-299) - Jobfunktionsroller og udskudt afgrænsning
- [ROL-304](https://os2web.atlassian.net/browse/ROL-304) - Ændre så medarbejdere uden rettigheder ikke attesteres, medmindre ad-attestering er slået til
- [ROL-304](https://os2web.atlassian.net/browse/ROL-304) - Fritag it-system fra attestering, it-systemet vil heller ikke fremgå i attesterings rapport
- [ROL-304](https://os2web.atlassian.net/browse/ROL-304) - Linje med "mail-skabeloner bliver aktiveret" når der gemmes indstillinger for attesterings modulet

2023.12.04 - Release 2023r4
- [ROL-268](https://os2web.atlassian.net/browse/ROL-268) - Mulighed for at redigere i allerede tildelt rettigheder
- [ROL-275](https://os2web.atlassian.net/browse/ROL-275) - Integration til Danmarks Miljøportals Provisioning API
- [ROL-274](https://os2web.atlassian.net/browse/ROL-274) - Ændringer til anmodningsprocessen
- [ROL-265](https://os2web.atlassian.net/browse/ROL-265) - Flere valgmuligheder for opsætning af forside på rollekataloget
- [ROL-263](https://os2web.atlassian.net/browse/ROL-263) - MitID Erhverv - omlægge rettighedsstyring til direkte tildeling på brugere fremfor brugergrupper
- [ROL-245](https://os2web.atlassian.net/browse/ROL-245) - Reservering af anmodninger
- [ROL-242](https://os2web.atlassian.net/browse/ROL-242) - Rapport: rollebuketter

2023.10.13 - Release 2023r3
- [ROL-210](https://os2web.atlassian.net/browse/ROL-210) - Nyt Attesteringsmodul

2023.07.31 - Release 2023r2
- [ROL-241](https://os2web.atlassian.net/browse/ROL-241) – Rollebuket: vis korrekt 'tildelt via'
- [ROL-245](https://os2web.atlassian.net/browse/ROL-245) – Reservering af anmodninger
- [ROL-263](https://os2web.atlassian.net/browse/ROL-263) – MitID Erhverv - omlægge rettighedsstyring til direkte tildeling på brugere fremfor brugergrupper

2023.04.21 - Release 2023r1
- [ROL-82](https://os2web.atlassian.net/browse/ROL-82) – Integration med NemLogin3 API'erne
- [ROL-239](https://os2web.atlassian.net/browse/ROL-239) – Email-advisering når en bruger med "simple" rettigheder bliver lukket
- [ROL-228](https://os2web.atlassian.net/browse/ROL-228) – Begrænset visning for ledere i anmod/godkend
- [ROL-163](https://os2web.atlassian.net/browse/ROL-163) – Fjerne muligheden for at brugere selv kan anmode om rettigheder
- [ROL-176](https://os2web.atlassian.net/browse/ROL-176) – Ændring af forside i Rollekataloget
- [ROL-233](https://os2web.atlassian.net/browse/ROL-233) – Filter til Jobfunktionsroller så man kan se alle der kan anmodes om
- [ROL-240](https://os2web.atlassian.net/browse/ROL-240) – Fremsøgning pr. medarbejder ved anmodning af rettigheder
- [ROL-243](https://os2web.atlassian.net/browse/ROL-243) – Dato-anmærkning på anmod funktionaliteten
- [ROL-244](https://os2web.atlassian.net/browse/ROL-244) – Navn i mailskabelon når en anmodning godkendes og tildeles
- [ROL-253](https://os2web.atlassian.net/browse/ROL-253) – Ændring af stedfortræderbegreb
- [ROL-246](https://os2web.atlassian.net/browse/ROL-246) – Understøtte "simple integrationer" på AD/SAML også
- [ROL-251](https://os2web.atlassian.net/browse/ROL-251) – Flydende højremenu på "enheder" siden
- [ROL-255](https://os2web.atlassian.net/browse/ROL-255) – integration til datafordeleren for at veksle p-numre
- [ROL-219](https://os2web.atlassian.net/browse/ROL-219) – Introdukion af flere organisations-domæner

2022.12.20 - Release 2022r7
- [ROL-163](https://os2web.atlassian.net/browse/ROL-163) – Fjerne muligheden for at brugere selv kan anmode om rettigheder
- [ROL-228](https://os2web.atlassian.net/browse/ROL-228) – Begrænset visning for ledere i anmod/godkend
- [ROL-233](https://os2web.atlassian.net/browse/ROL-233) – Filter til Jobfunktionsroller så man kan se alle der kan anmodes om
- [ROL-239](https://os2web.atlassian.net/browse/ROL-239) – Email-advisering når en bruger med "simple" rettigheder bliver lukket
- [ROL-240](https://os2web.atlassian.net/browse/ROL-240) – Fremsøgning pr. medarbejder ved anmodning af rettigheder
- [ROL-243](https://os2web.atlassian.net/browse/ROL-243) – Dato-anmærkning på anmod funktionaliteten
- [ROL-244](https://os2web.atlassian.net/browse/ROL-244) – Navn i mailskabelon når en anmodning godkendes og tildeles 

2022.11.18 - Release 2022r6
- [ROL-221](https://os2web.atlassian.net/browse/ROL-221) – Særskilt mailskabelon til anmod/godkend på manuelle it-systemer
- [ROL-224](https://os2web.atlassian.net/browse/ROL-224) – Højere synlighed af pause-markering på it-systemer
- [ROL-222](https://os2web.atlassian.net/browse/ROL-222) – Tilføj en "brugere" fane til rapporterne på rapportmodulet
- [ROL-229](https://os2web.atlassian.net/browse/ROL-229) – Tilpasse overskriften på modal dialogerne på fjern roller
- [ROL-230](https://os2web.atlassian.net/browse/ROL-230) – Ekstra kolonne til fanen "Brugere" under rettigheder

2022.09-12 - Release 2022r5
- [ROL-203](https://os2web.atlassian.net/browse/ROL-203) – Nye systemer skal som default ligge som 'skjult' i oversigten under IT Systemer
- [ROL-216](https://os2web.atlassian.net/browse/ROL-216) – Gruppering af roller i samme type (til licensstyring)
- [ROL-217](https://os2web.atlassian.net/browse/ROL-217) – Håndtere inaktive brugere i OS2rollekatalog

2022.07.27 - Release 2022r4
- [ROL-205](https://os2web.atlassian.net/browse/ROL-205) – Indlæsning af stedfortrædere via API
- [ROL-212](https://os2web.atlassian.net/browse/ROL-212) – Mail til leder ved tildeling af rolle fra manuelt it-system

2022.06.02 - Release 2022r3
- [ROL-186](https://os2web.atlassian.net/browse/ROL-186) – Visning af jobfunktionsroller i rollebuketter
- [ROL-193](https://os2web.atlassian.net/browse/ROL-193) – Rapport: Hvor kommer KLE fra?
- [ROL-201](https://os2web.atlassian.net/browse/ROL-201) – Udstille et API til ”streaming” udlæsning af auditlog
- [ROL-202](https://os2web.atlassian.net/browse/ROL-202) – In-line opdatering af autorisationsansvarlig
- [ROL-206](https://os2web.atlassian.net/browse/ROL-206) – Opgradere til Spring boot 2.6 og Java 17
- [ROL-204](https://os2web.atlassian.net/browse/ROL-204) – Vis eksisterende rettighedstildelinger
- [ROL-207](https://os2web.atlassian.net/browse/ROL-207) – ”Af-koble” jobfunktionsroller fra systemroller

2022.04.03 - Release 2022r2
- [ROL-171](https://os2web.atlassian.net/browse/ROL-133) - Understøtte rapporter på mere end 65335 rækker
- [ROL-180](https://os2web.atlassian.net/browse/ROL-168) - Integration til KOMBITs test-miljø
- [ROL-178](https://os2web.atlassian.net/browse/ROL-174) - Stedfortræder funktionalitet
- [ROL-182](https://os2web.atlassian.net/browse/ROL-188) - Understøttelse for tomme titler på enheder
- [ROL-183](https://os2web.atlassian.net/browse/ROL-195) - Vise synkroniseringsfejl til CICS i brugergrænsefladen
- [ROL-175](https://os2web.atlassian.net/browse/ROL-197) - Anvende pladsholdere i "emnet" i mailskabeloner
- [ROL-189](https://os2web.atlassian.net/browse/ROL-198) - Ad-hoc indlæsning af CICS roller

2022.02.08 - Release 2022r1

- [ROL-154](https://os2web.atlassian.net/browse/ROL-154) - Liste over konfigurable features
- [ROL-171](https://os2web.atlassian.net/browse/ROL-171) - Finere styring af API rettigheder
- [ROL-175](https://os2web.atlassian.net/browse/ROL-175) - Fjernelse af kode vedrørende organisationsændringer og ønske om ekstra advis’er i den forbindelse. 
- [ROL-178](https://os2web.atlassian.net/browse/ROL-178) - Læsevejledning til API dokumentationen
- [ROL-180](https://os2web.atlassian.net/browse/ROL-180) - Spore brugen af API'er
- [ROL-182](https://os2web.atlassian.net/browse/ROL-182) - Udarbejdelse af en liste over alle eksisterende API'er inkl. endpoints
- [ROL-183](https://os2web.atlassian.net/browse/ROL-183) - Versionsnummer på nye releases
- [ROL-189](https://os2web.atlassian.net/browse/ROL-189) - Mulighed for at aktivere/deaktivere Advis'er

## [Unreleased]

### Added
- [ROL-474](https://os2web.atlassian.net/browse/ROL-474) Tildeling af rolle til flere brugere på samme tid
- [ROL-544](https://os2web.atlassian.net/browse/ROL-544) Muligheden for, ved udskudte dataafgrænsninger, at gruppere valget af udskudt dataafgrænsning
- [ROL-552](https://os2web.atlassian.net/browse/ROL-552) Ny valgmulighed for visning af roller ved anmodning til medarbejder
- [ROL-559](https://os2web.atlassian.net/browse/ROL-559) Tilføjelse til notifikationsmail til tildeler

