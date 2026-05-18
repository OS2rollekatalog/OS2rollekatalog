# OS2rollekatalog
Om OS2rollekatalog

OS2rollekatalog er en webapplikation til håndtering af roller og rettighedder på tværs af it-systemer. Systemmet sætter brugeradministratorerne i stand til, at danne jobfunktionsroller, tildele og fratage rettigheder på medarbejdere.
Lederne får mulighed for at danne sig et overblik over hvilke rettigheder medarbejderne har ved hjælp af rapporter der kan dannes direkte i brugergrænsefladen.

Dokumentation
Dokumentationen for OS2rollekatalog findes i mappen doc her på Github. samt på OS2rollekatalogs prduktside på OS2.eu

## Udvikling — hurtigere testkørsler

Selenium-testene starter en Keycloak-container som SAML-IdP. For at undgå at vente på den ~30s boot hver gang du kører testene lokalt, kan du aktivere testcontainers-container-genbrug ved at oprette (eller udvide) `~/.testcontainers.properties` med:

```
testcontainers.reuse.enable=true
```

Efter første kørsel overlever Keycloak-containeren mellem test-kørsler (testcontainers markerer den med `withReuse(true)` i `SamlIdpContainerConfiguration`). Efterfølgende kørsler genbruger den samme container og springer Keycloak-opstart over. I CI er flaget ikke sat, så pipeline-kørsler starter fra en ren tilstand.

MockMvc-integrationstests (der bruger `BaseIntegrationTest`) starter slet ingen SAML-container — de læser IdP-metadata fra classpath (`test-idp-metadata.xml`).

