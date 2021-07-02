## OS2rollekatalog SSL konfiguration
OS2rollekatalog er designet til at blive driftet bag en loadbalancer eller SSL offloading komponent. Man kan dog godt udstille rollekataloget direkte, men i
så fald skal man opsætte SSL direkte i rollekatalogets konfiguration.

Når man kører rollekataloget lokalt på sin egen computer, fx når man udvikler/tester, så har kodebasen et indbygget dummy SSL certifikat, som kan bruges til
formålet. Dette er slået til i udvikler-konfiguration, og man skal ikke gøre noget for at gør brug af dette.

Hvis man afvikler Docker containeren, så er der ikke nogen SSL slået til som default, og man skal i stedet gøre følgende

**1) skaf et SSL certifikat**
Man skal skaffe et gyldigt SSL certifikat hos en udbyder af SSL certifikater. Den præcise proces for dette afhænger af udbyderen af SSL certifikater, og
er ikke dokumenteret her.

Det vigtige er dog at man ender med at have et PKCS#12 certifikat keystore. Dvs en fil der hedder noget med .pfx eller .p12. Samt at man kender kodeordet til
denne fil, da det skal bruges i konfigurationen af rollekataloget.

**2) konfiguration af rollekatalog**
Konfigurationen af SSL håndteres via miljøvariable. Følgende sektion skal tilføjes til ens Docker Compose konfigurationsfil, på lige fod med de andre
konfigurationsindstillinger. Bemærk at kodeordet skal angives 2 gange - årsagen er at en PKCS#12 fil kan have individuelle kodeord på nøgle, samt overordnet kodeord
til selve filen (i praksis er de dog altid ens ;))

    server.ssl.enabled: "true"
    server.ssl.key-store: "/home/cert/ssl.pfx"
    server.ssl.key-store-password: "Test1234"
    server.ssl.key-password: "Test1234"

Ovenstående konfiguration kræver blot en genstart af Docke Containeren, hvorefter rollekataloget vil køre på HTTPS og anvende dette SSL certifikat
