# Enhedstildeling — wizard-flow og forretningsregler

Udviklerdokumentation for wizarden, der tildeler en userrole eller rolegroup til en
org-enhed. Beskriver hvilke tildelinger der overhovedet er gyldige, hvordan flowet
er skruet sammen, og hvilke designvalg der ikke kan læses direkte ud af koden.

## De tre spørgsmål

Wizardens første trin stiller tre uafhængige spørgsmål:

1. **`Mål`** — hvem i enheden gælder tildelingen for? `Alle`, `Udvalgte stillinger` (kun
   personer med bestemte stillinger), `Leder`, `Udvalgte tillidsfunktioner` (personer med
   bestemte tillidsfunktioner).
2. **`Undtagelser`** — en valgfri undtagelse fra målgruppen: `Ingen`, `Enheder` (undtagen
   bestemte underenheder), `Brugere` (undtagen bestemte brugere), `Stillinger` (undtagen
   personer med bestemte stillinger).
3. **`Nedarvning`** — slår tildelingen igennem på underenheder?

## Gyldige kombinationer

### Reglerne i klartekst

Disse fem begrænsninger er autoriteten. Alt, de ikke udelukker, er gyldigt:

1. **`Leder` som målgruppe** → ingen undtagelser tilladt.
2. **`Funktioner` som målgruppe** → ingen undtagelser tilladt.
3. **`Stillinger` som målgruppe** → kun `Brugere` kan vælges som undtagelse.
4. **`Brugere` undtaget** → ingen nedarvning.
5. **`Enheder` undtaget** → skal nedarve.

Det er *begrænsninger*, de undtager 19 af de 32 kombinationer, så **13 er gyldige**. Tabellen
nedenfor er den udfoldede form, som viser alle muligheder og hvilke der er gyldige som konsekvens af de ovenstående regler.

### Sandhedstabel

| Målgruppe | Undtagelse | Nedarv | Gyldig? |
|---|---|---|---|
| Alle | Ingen | JA | ✓ |
| Alle | Ingen | NEJ | ✓ |
| Alle | Stillinger | JA | ✓ |
| Alle | Stillinger | NEJ | ✓ |
| Alle | Enheder | JA | ✓ |
| Alle | Enheder | NEJ | – |
| Alle | Brugere | JA | – |
| Alle | Brugere | NEJ | ✓ |
| Stillinger | Ingen | JA | ✓ |
| Stillinger | Ingen | NEJ | ✓ |
| Stillinger | Stillinger | JA | – |
| Stillinger | Stillinger | NEJ | – |
| Stillinger | Enheder | JA | – |
| Stillinger | Enheder | NEJ | – |
| Stillinger | Brugere | JA | – |
| Stillinger | Brugere | NEJ | ✓ |
| Tillidsfunktioner | Ingen | JA | ✓ |
| Tillidsfunktioner | Ingen | NEJ | ✓ |
| Tillidsfunktioner | Stillinger | * | – |
| Tillidsfunktioner | Enheder | * | – |
| Tillidsfunktioner | Brugere | * | – |
| Leder | Ingen | JA | ✓ |
| Leder | Ingen | NEJ | ✓ |
| Leder | Stillinger | * | – |
| Leder | Enheder | * | – |
| Leder | Brugere | * | – |

## Wizard-flowet

Wizarden består af tre faste dele og et variabelt midterstykke:

1. **Vælg omfang** — de tre spørgsmål ovenfor.
2. **Nul eller flere udfyldningstrin** — ét per valg, der kræver konkrete data:
   stillinger, tillidsfunktioner, undtagne brugere eller undtagne enheder.
3. **Kvittering** — opsummering samt start-/slutdato og sagsnummer.

Midterstykket følger direkte af omfanget: valget af mål og undtagelse afgør, hvilke
trin der overhovedet skal stilles, og i hvilken rækkefølge.

**Serveren ejer flowet.** Hvert trin sendes til serveren, som validerer svaret,
udleder hvilket trin der kommer næst, henter de data, trinnet skal bruge, og sender
det færdigrenderede trin tilbage. Klienten viser det, den får, og samler svarene op
undervejs; den beregner ikke selv rækkefølgen.

Det har to konsekvenser, der er værd at holde fast i:

- **Trin-sekvensen genudledes ved hver indsendelse** og modtages aldrig fra klienten.
- **Kun ét trin findes i DOM'en ad gangen.** Der ligger ikke skjulte formularer for
  kommende trin. Til gengæld sendes hele det akkumulerede valg med hver
  indsendelse.

Ved den afsluttende indsendelse gemmes tildelingen.

## Redigering af en eksisterende tildeling

Redigering bruger samme wizard, med to forskelle:

- **Omfanget er låst.** Målgruppe, undtagelse og nedarvning vises som de blev valgt,
  men kan ikke ændres. Man retter *hvem* inden for det valgte omfang — ikke selve
  omfanget. Skal omfanget ændres, sletter man tildelingen og opretter en ny.
- **Trinnene er forudfyldt** med det, der allerede er gemt.

**En tildeling, hvis omfang ikke svarer til nogen gyldig kombination, kan ikke
redigeres.**

## Forholdet til den eksisterende tildelings-service

Selve persisteringen sker gennem de eksisterende service metoder, som er ældre end
wizarden og taler i flade flag frem for i mål og undtagelser. Oversættelsen mellem
de to sprog sker ét sted, og de flade flag optræder ikke andre steder i wizarden.

## Designvalg, der ikke er synlige i koden

**Undtagne enheder sendes kun som de øverst valgte.** At undtage en enhed dækker
automatisk dens underenheder, så kun de øverste valgte knuder sendes. Ellers ville
markeringen af en stor gren blive til tusindvis af overflødige enheds-id'er.

**"Relevante stillinger" er ikke kun stillinger, nogen har.** Stillingsvælgeren
viser foreningen af tre kilder: stillinger som nogen faktisk besidder i enheden i dag;
stillinger som org-importen erklærer for enheden; og stillinger, der allerede indgår i
en eksisterende tildeling på enheden. Den sidste kilde er der, for at en forældet
men stadig tildelt stilling ikke forsvinder ud af vælgeren og dermed ud af
tildelingen ved næste redigering. Ved nedarvning udvides opslaget til hele det
aktive undertræ.
