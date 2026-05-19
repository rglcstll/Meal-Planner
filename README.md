# Meal-Planner

## Run App (Auto-start Embedded Tomcat)

Start the app with embedded Tomcat:

```powershell
.\start-mealplanner.cmd
```

Or run directly with Maven wrapper:

```powershell
.\mvnw.cmd spring-boot:run
```

## Deployment Persistence Setup (Prod)

This project keeps Spring Data JPA and uses Flyway for schema evolution in production.

Key production behavior:

- `spring.jpa.hibernate.ddl-auto=validate`
- `spring.jpa.show-sql=false`
- Flyway enabled with baseline mode for existing live DBs:
  - `spring.flyway.baseline-on-migrate=true`
  - `spring.flyway.baseline-version=1`

Required production environment variables:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"
$env:DB_URL="jdbc:mysql://<host>:3306/<db>"
$env:DB_USERNAME="<db-user>"
$env:DB_PASSWORD="<db-password>"
$env:MAIL_USERNAME="<mail-user>"
$env:MAIL_PASSWORD="<mail-password>"
$env:APP_BASE_URL="https://<your-domain>/mealplanner"
$env:CORS_ALLOWED_ORIGINS="https://<frontend-domain>"
```

Migration files are under `src/main/resources/db/migration`.
`schema.sql` is no longer used for production schema evolution.

## Local Smoke Test

Set required environment variables for local runtime (examples):

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD=""
$env:MAIL_USERNAME="your-mail@example.com"
$env:MAIL_PASSWORD="your-mail-app-password"
```

Run test smoke check:

```powershell
.\mvnw.cmd test
```

If your Windows profile path contains spaces and wrapper resolution fails, set a space-free Maven home:

```powershell
$env:MAVEN_USER_HOME="D:\tmp\.m2"
.\mvnw.cmd test
```
