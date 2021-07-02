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
