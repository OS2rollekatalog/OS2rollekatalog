## OS2rollekatalog Docker Konfiguration
Der frigives docker images på dockerhub i forbindelse med releases.
Disse frigives på nuværende tidspunkt til Linux, Windows 2016 og Windows 2019. Man kan se de respektive images her

* [Linux](https://hub.docker.com/r/rollekatalog/linux)
* [Windows 2016](https://hub.docker.com/r/rollekatalog/windows)
* [Windows 2019](https://hub.docker.com/r/rollekatalog/windows-2019)

Alle nye images (siden 2021) anvender samme konfiguration, og nedenfor er vist et eksempel på konfiguration vha Docker Compose.
Den primære konfiguration består af en række miljøvariable, som kan sættes op på tilsvarende måde i andre orkestreringsværktøjer.
Ud over miljøvariablene, skal der være adgang til relevante certifikatfiler, fx via et mounted volume. Docker Compose konfigurationen
nedenfor viser hvordan dette kan sættes op

### Afhængigheder
OS2rollekatalog har 3 primære afhængigheder for at kunne starte op. Ud over dette er der en række optionelle afhængigheder. Alle er listet nedenfor

**Krævede afhængigheder**

* **SAML Identity Provider Metadata**. OS2rollekatalog forudsætter at man har en SAML Identity Provider til at håndtere login til rollekatalogets brugergrænseflade.
* **Certifikat til SAML**. OS2rollekatalog agerer i ovenstående kontext som en SAML Service Provider, og skal her udstyres med et certifikat keystore, som skal være tilgængelig som en PKCS#12 fil (p12/pfx)
* **SQL Database**. OS2rollekatalog er afhængig af en SQL database til at gemme sine data. Både SQL Server 2017+ og MySQL 5.7+ understøttes.
* 
**Optionelle afhængigheder**

* **KOMBIT certifikat**. OS2rollekatalog kan integrere til KOMBITs administrationsmodul. Hvis man ønsker dette, så skal man have registreret et certifikat i KOMBITs Adminisrationsmodul, og det tilhørende certifikat keystore skal være tilgængelig som en PKCS#12 fil (p12/pfx)
* **CICS certifikat**. OS2rollekatlaog kan integrere til KSP/CICS. Hvis man ønsker dette, så skal man have registreret et certifikat hos KMD, hvor det tilhørende certifikat keystore skal være tilgængelig som en PKCS#12 fil (p12/pfx)

### Docker Compose
Below is a very simple docker-compose.yml file, which shows the configuration of OS2rollekatalog. The example uses the Linux image. If one of the Windows images
is used, change the image tag to reflect this

    version: "2.0"
    services:
      rollekatalog:
        image: rollekatalog/linux:2021-05-21
        ports:
          # the internal port is 8090 - map to the external port here. If no loadbalancer/ssl-offloader is used,
          # you'll probably want to expose it on port 443 instead. Check the documentation for SSL configuration
          # if OS2rollekatalog needs to handle HTTPS/SSL internally - by default it is exposed as HTTP
          - 8090:8090
        environment:
          # basic customer information - the ApiKey is the secret key used to access the API's, and should
          # be a strong password (e.g. a random UUID).
          rc.customer.cvr: "12345678"
          rc.customer.apikey: "00000000-0000-4000-0000-000000000000"

          # database must be running somewhere - MySQL and SQL Server is supported. The example shows MySQL
          # if SQL Server is used, change the url and driver to reflect this
          spring.datasource.username: "rollekatalog"
          spring.datasource.password: "00000000-0000-4000-0000-000000000000"
          spring.datasource.url: "jdbc:mysql://mysql.domain.com/rollekatalog"
          spring.datasource.driver-class-name: "com.mysql.cj.jdbc.Driver"
  
          # SAML setup for OS2rollekatalog (url endpoints and entityId). It is recommended to use the same
          # values for all three fields (the servername section only contains the FQDN without the protocol though),
          # but this is not a requirement
          saml.baseUrl: "https://kunde.rollekatalog.dk"
          saml.entityId: "https://kunde.rollekatalog.dk"
          saml.proxy.servername: "kunde.rollekatalog.dk"
  
          # SAML setup for the Identity Provider used for login - this points to the SAML metadata file exposed
          # by the Identity Provider
          saml.idp.metadatafile: "url:https://kunde.dk/adfs/sso/FederationMetadata.xml"

          # SAML setup for the keystore used by OS2rollekatalog - points to a PKCS#12 file and its password
          saml.keystore.location: "file:///home/cert/saml.pfx"
          saml.keystore.password: "password"

          # If running a multi-container setup, only enable scheduling on ONE of the containers. It should
          # be enabled on a container to ensure that batch/scheduled jobs are executed
          rc.scheduled.enabled: "true"
          
          # If you want to use the "title" feature in OS2rollekatalog, enable it with this flag. This allows
          # assigning roles to titles (i.e. all users with a given title within a given OrgUnit)
          rc.titles.enabled: "true"
          
          # If you want to integrate with KSP/CICS, you need to add the following section.
          # Reading from CICS is enabled with the first flag, and sending data (assignments) back to CICS is
          # enabled with the next flag. The losid field should contain Kaldenavn Kort for the top-level OrgUnit
          # inside LOS, and finally the path and password to the certificate keystore used by the integration
          rc.integrations.kspcics.enabled: "true"
          rc.integrations.kspcics.enabledOutgoing: "true"
          rc.integrations.kspcics.losid: "KOMMUNE"
          rc.integrations.kspcics.keystoreLocation: "/home/cert/cics.pfx"
          rc.integrations.kspcics.keystorePassword: "password"
          
          # If you want to integrate with KOMBIT Administrationsmodul, you need to add the following section
          # the domain is filled with the "Jobfunktionsrolle domæne" used in KOMBIT, and the keystore section
          # should point to the certificate keystore used for the integration
          rc.integrations.kombit.enabled: "true"
          rc.integrations.kombit.domain: "kommune.dk"
          rc.integrations.kombit.keystoreLocation: "/home/cert/kombit.pfx"
          rc.integrations.kombit.keystorePassword: "password"
          
          # If you want OS2rollekatalog to be able to send emails, fill out this section with the credentials
          # to access the email server
          rc.integrations.email.enabled: "true"
          rc.integrations.email.from: "rollekatalog@kommune.dk"
          rc.integrations.email.username: "rollekatalog"
          rc.integrations.email.password: "password"
          rc.integrations.email.host: "smtp.kommune.dk"
        volumes:
          # map a local folder (here the "cert" folder) to an internal folder. The filepaths used above
          # points to the internal folder name.
          - ./cert:/home/cert
