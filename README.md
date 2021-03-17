# OS2rollekatalog

OS2rollekatalog består i grove træk af en web-baseret brugergrænseflade til at administrere rollerne i rollekataloget, samt en service-snitflade til at lave opslag på enkelt-brugere, for at få brugerens tildelte roller.
Løsningen kan driftes lokalt i kommunen, men tilbydes også en som Software-as-a-Service (SaaS) løsning.
Løsningen indeholder en integration til Active Directory Federation Services (AD FS), så den kan anvendes til at udstede SAML tokens til de fagsystemer der understøtter single-signon med SAML – dette dækker bl.a. de KOMBIT-leverede monopolbrudsystemer.
Fagsystemer der ønsker at trække brugernes rettigheder direkte ud af rollekataloget, kan gøre dette ved at bruge den snitflade der udstilles til formålet.

Brugergrænsefladen er web-baseret, og er optimeret til moderne web-browsere som Microsft Edge Chromium, Chrome, Firefox og lignende moderne web-browsere. Løsningen bør IKKE anvendes i Internet Explorer.
Løsningen er baseret på et Bootstrap theme kaldet ’Angle’ , der kræver køb af en 1-gangs licens på $18 for at anvende, hvis man selv hoster løsningen

Login til brugergrænsefladen:
Login til OS2rollekataloget håndteres via SAML single-signon, og kommunen kan fx anvende sin eksisterende AD FS opsætning til at håndtere login til Rollekataloget.
Når man er tildelt adgang til Rollekatalogets brugergrænseflade, kan man tilgå alt funktionaliteten i Rollekataloget.

Administration og modellering af roller: 
Rollekataloget håndterer 3 niveauer af roller

Brugersystemroller: Disse roller er ejet af det enkelte fagsystem, og udstilles som byggeklodser i brugergrænsefladen. Man kan ikke ændre i disse roller, da de er ejet af det fagsystem de kommer fra. 

Jobfunktionsroller: Disse roller bygges af Brugersystemroller, og er myndigheds-specifikke. Til disse roller er det muligt at knytte dataafgrænsninger jf KOMBITs rettighedsmodel. Disse roller er bundet til ét bestemt it-system.
 
Rollebuketter: Rollebuketter er en måde at gruppere Jobfunktionsroller, så man fx kan opbygge en rolle der dækker over flere it-systemer.

Tildeling af roller til brugerne: 
Tildeling af roller til brugerne kan i OS2rollektalog ske på en række måder: 

- Tildeling til enhed: I brugergrænsefladen kan man tildele en rolle til en enhed. Når man tildeler en rolle til en enhed, betyder det at alle brugere der har et ansættelsesforhold i denne enhed automatisk tildeles denne rolle. Hvis rollen fjernes fra enheden, så fjernes den også automatisk fra alle medarbejdere i denne enhed. Ligeledes hvis en medarbejder ophører med at arbejde i enheden, så fjernes rollen også automatisk.
- Tildelt til en enhed med undtagelser: Rollen er tildelt til en hel enhed med undtagelse af navngivende medarbejdere. 
- Tildeling til stilling: I brugergrænsefladen kan man også tildele en rolle til en brugers stilling i en given enhed. Det betyder at brugeren har denne rolle så længe brugeren har denne stilling. Hvis brugeren mister stillingen, så fjernes rollen automatisk fra brugeren. Vacante stillinger vises i 6 mdr. i OS2rollekatalog
- Tildeling direkte til brugeren: I brugergrænsefladen kan man også tildele en rolle direkte til en bruger. I dette tilfælde er der ingen automatik der fjerner rollen igen, dette skal gøres manuelt. 
- Tildelt direkte til brugeren, men med en opmærkning der angiver at de har den fordi de er ansat i en bestemt enhed: Her fjernes rollen, hvis brugeren forlader enheden
- Tildelt på et stillingskryds: Dvs. kombinationen af en stilling og en enhed, og medarbejderen opfylder begge kriterier (indplacering og stilling)
- Tildelt via nedarving: Rollen er tildelt en enhed der ligger over den enhed som brugeren er indplaceret i, og selve rollen er opmærket med et nedarvnings-flag, der gør at underliggende enheder (og deres brugere) skal have rettigheden

Understøttede dataafgrænsningstyper
Brugergrænsefladen har optimerede UI komponenter til de fælleskommunale dataafgrænsningstyper KLE, Organisation, Enhed og Følsomhed. 

API til bruger-opslag
Der udstilles et API til at slå op hvilke roller en bruger er tildelt, API’et tager et bruger-id og et it-system som input, hvorefter den svarer med en JSON struktur, der indeholder de roller som brugeren er tildelt til netop dette it-system.
Svaret indeholder også en OIO-BPP enkodet struktur, der kan sendes til de fagsystemer der understøtter OIO-BPP.
Svaret indeholder også en korrekt formateret NameID/Subject, der kan anvendes af AD FS til at udstede et rammearkitektur-kompatibelt token.

Read Only API: 
Rollekataloget udstiller følgende data i systemet via et read-only API, som kan anvendes til forskellige formål, herunder rapportering m.m.
-	List alle Jobfunktionsroller
-	List alle Rollebuketter
-	List alle roller tildelt en medarbejder
-	List alle roller tildelt en enhed
-	List alle medarbejdere der er tildelt en bestemt rolle (enten direkte eller indirekte via organisatorisk indplacering)

API til rolletildeling: 
Der udstilles et API til at fjernstyre rolletildelinger, dvs følgende operationer
-	Tildel/fjern rolle til medarbejder
-	Tildel/fjern rolle til enhed

AD FS integration
Sammen med OS2rollekataloget medfølger et såkaldt Custom Attribute Store til AD FS, der kan kalde Rollekataloget og udstiller et KOMBIT-kompatibelt SAML token på baggrund af dette opslag.

API til indlæsning af Organisationsdata
OS2rollekatalog understøtter at man kan indlæse sine Organisatoriske data (enheder og medarbejdere, samt KLE opmærkning af enhederne) via et API, der tager en fuld organisation i JSON format.

Teknisk design
Løsningen udvikles i Java (Spring til backend, Thymeleaf til frontend og Hibernate til datalaget), og pakketeres som et Docker Image, klar til deployment i en Docker Host.
Som database anvendes MySQL under udviklingen, og Aurora til drift (det er også muligt at anvende MySQL til drift hvis man ønsker at drifte løsningen selv).
