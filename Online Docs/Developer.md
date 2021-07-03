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


### ADFS Attribute Store


### ADSyncService applikationen
