## OS2rollekatalog opstartsvejledning til udvikling
OS2rollekatalog består af flere selvstændige applikationer, samlet i samme koderepository. Den primære applikation er selve OS2rollekatalog applikationen, men der ligger også 3 andre applikationer i samme kodebase. Alle applikationer er beskrevet i dette dokument, herunder hvordan man kompilerer og afvikler dem som udvikler

### OS2rollekatalog applikationen
OS2rollekatalog er en Java applikation, der kræver minimum JDK 11 for at kompilere. Der anvendes Apache Maven som værktøj til at kompilere og starte applikationen.

Kodebasen er opbygget af 2 maven moduler, med en parent POM fil, der er placeret i roden af kodebasen, samt en POM fil i hvert modul

    .
    ├── pom.xml
    ├── ui
    │    └── pom.xml
    └── webjar
         └── pom.xml

#### Forudsætninger
Før man kan starte OS2rollekatalog, så skal man have en lokal SQL database kørende. Applikationen gør brug af nedenstående konfigurationsfil når man afvikler lokalt på sit udviklermiljø. Her kan man ændre forbindelsesparametrene til den SQL database man ønsker at køre.

    ui/config/application.properties

Her er de disse 2 sektioner man kan tilpasse og kommentere ind/ud. Der er en sektion til hhv MySQL og en til SQL Server. Anvend den sektion der passer til den SQL databasen som ønskes anvendt

    # MYSQL
    spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
    spring.datasource.url=jdbc:mysql://localhost/rc?useSSL=false&serverTimezone=Europe/Copenhagen
    spring.datasource.username=root
    spring.datasource.password=Test1234

    # MSSQL
    spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
    spring.datasource.url=jdbc:sqlserver://localhost:1433;DatabaseName=rc
    spring.datasource.username=sa
    spring.datasource.password=Test1234

OS2rollekatalog forudsætter at man har oprettet selve databasen (dvs rod-skemaet), men rollekataloget håndterer selv at oprette de nødvendige tabller, hvilket klares af Flyway frameworket.

OS2rollekatalog forudsætter endvidere at man har en Microsoft AD FS server der kan håndtere login til applikationen. Det er udenfor scope af denne vejledning at beskrive hvordan man opsætter en AD FS server, men det underliggende AD skal have en bruger med brugernavnet "user1", da dette er default-administratoren i rollekataloget når man kører i udvikler-mode. Med denne bruger kan man logge ind som administrator i et nyopsat test-rollekatalog.

Man skal pege på den AD FS server man opsætter via application.properties filen, hvor man skal tilrette denne linje

    saml.idp.metadatafile=url:https://demo-adfs.digital-identity.dk/FederationMetadata/2007-06/FederationMetadata.xml

#### Kompilering og afvikling
Man kan kompilere applikationen vha Maven - følgende kommando anvendes til formålet. Bemærk at man ikke her afvikler testcases, da det har en række andre forudsætninger. Se dokumentationen for afvikling af tests for at se hvordan man får alle testcases afviklet.

    $ mvn clean install -Dmaven.test.skip=true

Ovenstående afvikles i roden af kodebasen, så både webjar og ui modulerne bliver kompileret. Efter man har kompileret begge moduler, kan man nøjes med at kompilere inde fra UI modulet fremover, da "install" delen installerer webjar modulet i ens lokale maven repository.

UI modulet kan re-kompileres og afvikles vha følgende maven kommando'er

    $ mvn clean package -Dmaven.test.skip=true
    $ mvn spring-boot:run

Når man kører den sidste kommando, så starter den kompilerede applikation op, og rollekataloger er så kørende lokalt på ens udviklermaskine.

Applikationen er nu tilgængelig på port 8090 under https, dvs man kan ramme den på

    https://localhost:8090/
    
Hvis man har nedenstående setting i application.properties filen, så dannes automatisk nogle basale stamdata (brugere, enheder m.m.), så man kan foretage et login. Det er dog vigtigt at den AD FS server man har opsat kan foretage et login med en af de brugere som findes i applikationen (se ovenfor)

    environment.dev=true

### ItSystem Master applikationen
OS2rollekatalog kodebasen har også en "master" applikation. Det er en lille web-applikation, hvor administratorer (oprettet i applikationen) kan logge ind og vedligeholde stamdata på it-systemer. De enkelte OS2rollekatalog installationer kan vælge at abonnere på it-systemer vedligeholdt i master applikationen. På den måde kan man nemt oprette og vedligeholde mange it-systemer, som gøres tilgængelig for alle der gør brug af OS2rollekatalog.

Applikationen er en Java 11 applikation, og der anvendes Apache Maven som værktøj til at kompilere og afvikle kodebasen.

Applikationen ligger i folderen ItSystemMaster, og er afhængig af webjar modulet (så det skal kompileres først, og evt installeres i det lokale Maven repository via install kommandoen).

Applikationen forudsætter også at der kører en lokal SQL database, men den understøtter dog kun MySQL, så hvis man ønsker at afvikle denne lokalt, så skal man have en MySQL 5.7 database kørende.

Forbindelsesoplysningerne til databaseb opsættes i config/application.properties filen, lige som for OS2rollekatalog, og igen håndterer Flyway frameworket oprettelsen af tabeller m.m., men selve database-skemaet skal være oprettet på forkant.

Kompilering og afviklingen af applikationen er identisk med OS2rollekatalog, dvs man kørerer disse kommandoer

    $ mvn clean package -Dmaven.test.skip=true
    $ mvn spring-boot:run

Hvorefter applikationen er tilgængelig på port 8091 under HTTPS, dvs man kan ramme den på

    https://localhost:8091/

### ADFS Attribute Store
Dette attribute store er en .NET 4.6 applikation, og kan åbnes i Visual Studio 2019, og kompileres herinde. Grundlæggende er applikationen "bare" implementationen af et enkelt Interface, udstillet af Microsoft. Dette Interface anvendes af en AD FS server til at hente oplysninger om en bruger på login tidspunktet (fx roller ;)).

Når man kompilerer applikationen fra Visual Studio, så skal man sikre at der er tilføjet en Reference til den korrekte version af ClaimsPolicy.dll filen. Der ligger en folder i kodebasen med 3 versioner af denne DLL, en til hver Windows Server version (2012R2, 2016 og 2019). Det er vigtigt at der linkes til den version der matcher den Windows Server som attribute storet skal installeres på.

Når man har kompileret applikationen, så dannes en enkelt DLL (RoleCatalogueAttributeStore.dll), som manuelt kopieres ind på den Windows Server hvor man har sin AD FS server kørende. Den skal kopieres til folderen

    c:\windows\adfs
    
Der ligger en vejledning i Word under docs folderen til selve installationen og konfigurationen af dette attribute store.

### ADSyncService applikationen
Denne applikation er en .NET 4.6 applikation, og kan åbnes i Visual Studio 2019. Når man kompilerer applikationen inde fra Visual Studio, danner den en fuld applikation der kan installeres som en Windows Service.

For at lette installationen er der lavet et [InnoSetup](https://jrsoftware.org/isinfo.php) script, som ligger i Installer folderen. Hvis man afvikle det vha InnoSetup, så bygger den en EXE installer med den seneste kompilerede udgave af applikationen.

Denne EXE installer kan afvikles på en Windows Service, hvor den vil installere applikationen og sætte den op som en Windows Service.

Der ligger en vejledning i Word under docs folderen til selve isntallationen og konfigurationen af denne Windows Service.

### RoleCatalogueImporter applikationen
Denne applikation er en .NET 4.6 applikation, og kan åbnes i Visual Studio 2019. Når man kompilerer applikationen inde fra Visual Studio, danner den en fuld applikation der kan installeres som en Windows Service.

For at lette installationen er der lavet et [InnoSetup](https://jrsoftware.org/isinfo.php) script, som ligger i Installer folderen. Hvis man afvikle det vha InnoSetup, så bygger den en EXE installer med den seneste kompilerede udgave af applikationen.

Denne EXE installer kan afvikles på en Windows Service, hvor den vil installere applikationen og sætte den op som en Windows Service.

Der ligger en vejledning i Word under docs folderen til selve isntallationen og konfigurationen af denne Windows Service.
