# Byg vejledning

## Digital-identity 
Brug role-catalogue-deploy repo

## Andre
### Krav
- Java 25 (Amazon Corretto anbefales)
- Maven Wrapper (`./mvnw`) – ligger i `ui/` mappen
- Docker

### Byg Docker image lokalt

#### 1. Gå til ui-mappen
```bash
cd ui
```

#### 2. Byg projektet og Docker image
```bash
./mvnw spring-boot:build-image -DskipTests
```

Dette bygger automatisk et Docker image via Spring Boot's Paketo Buildpacks integration. Intet behov for en Dockerfile.

#### 3. Kør image lokalt
```bash
docker run -p 8080:8080 rollekatalog:latest
```

> Applikationen er tilgængelig på http://localhost:8080

> **Bemærk:** Applikationen kræver at database adresse, credentials og applikationsspecifikke secrets er sat som environment variabler ved opstart.
> Se den fulde dokumentation her: https://os2rollekatalog.github.io/OS2rollekatalog-docs/hosting.html