## OS2rollekatalog afvikling af testcases
OS2rollekatalog kodebasen har en række testcases indbygget. Nogle af disse anvendes til at danne dokumentation af API'et på OS2rollekatalog (og som en sideeffekt teste at de fungerer ;)), og nogle af testene er testcases der afvikles mod brugergrænsefladen.

Når man afvikler tests på OS2rollekatalog, så afvikles som udgangspunkt alle testcases.

Den nemmeste måde at afvikle alle tests, er blot at køre følgende kommando

    $ mvn compile test

Dette vil formodentligt fejle hvis man endnu ikke har fået sat alle afhængigheder op. Det anbefales at man starter med at få etableret et udviklingsmiljø, hvor man kan kompilere og afvikle OS2rollekatalog. Der er en vejledning til dette i samme folder som denne vejledning.

Når man kan kompilere og afvikle OS2rollekatalog, er der en række ekstra trin der skal på plads før man kan afvikle testcases.

### Database opsætning
For ikke at smadre evt udviklerdata man måtte have i sin lokale SQL database, afvikles testcases mod et andet database-schema. Dette skema er opsat i filen

    ui/src/test/resources/test.properties

Det skal sikres at den konfigurerede databaseforbindelse matcher en faktisk kørende SQL database. Det anbefales at anvende et andet schema-navn til afviklingen af tests, end det man bruger til lokal udvikling, da data i databasen vil blive overskrevet ved afvikling af testcases.

### AD FS brugerkonto
Alle tests af brugergrænsefladen afvikles ved at der foretages et login ind mod en kørende AD FS server. I samme konfigurationsfil som databaseforbindelsen opsættes til testcases, er det også muligt at angive brugernavn og kodeord til den bruger man anvender til testcases. Det anbefales at brugernavnet er "user1", da de testdata der dannes under afviklingen af testcases forventer at administratoren i løsningen har dette brugernavn.

### Afvikling af dokumentationstests
Alle tests der tester (og dokumenterer) API'erne ligger i folderen

    ui/src/test/java/dk/digitalidentity/rc/test/documentation/

Disse tests kan afvikles uden de store forudsætninger, og danner metadata om de testede services som en sideeffekt af testen. Man kan afvikle dem individuelt inde fra et IDE som Eclipse eller Intellij, men hvis man ønsker at der skal dannes API dokumentation, skal man afvikle alle tests fra Maven.

Ved succesfuld afvikling af alle testcases, dannes en API dokumentationsfil i denne folder

    ui/TODO

### Afvikling af brugergrænsefladetests
Alle tests af brugergrænsefladen afvikles vha Selenium, og der anvendes en Chromedriver som den browser der afvikler de enkelte tests. I kodebasen ligger en binær kopi af Chromedriver applikationen, men denne er bygget til Linux. Hvis man ønsker at afvikle brugergrænsefladetests fra andre platforme, skal man [downloade](https://chromedriver.chromium.org/downloads) en kopi til ens operativsystem, og overskrive den version der ligger i kodebasen.

På nuværende tidspunkt anvendes version 2.37 af ChromeDriver, og man bør anvende samme version til ens operativsystem hvis man skal være sikker på at tests kan afvikles uden kodeændringer.

Når den rette ChromeDriver er installeret, kan brugergrænseflade test afvikles. Bemærk at tests afvikles med en synlig browser, så man visuelt kan inspicere hvad testene udfører af handlinger. Til afvikling af tests på et CI setup kan man overveje at afvikle tests headless (kræver kodeændringer i konfigurationen af ChromeDriveren).
