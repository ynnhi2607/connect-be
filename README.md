# Connect BE

Backend service for ConnectSpace, built with Spring Boot and Gradle multi-module.

## Requirements

- Java 21
- MySQL 8.x or compatible
- Gradle is not required globally because this project includes the Gradle Wrapper

Check your Java version:

```powershell
java -version
```

## Project Structure

```text
connect-be
|-- api   # Main Spring Boot application, runtime config, security config
`-- core  # Domain logic, controllers, services, repositories, JWT security
```

The application starts from the `api` module.

## Database Setup

By default, the backend connects to MySQL with these values:

```properties
DB_URL=jdbc:mysql://localhost:3306/connectspace
DB_USERNAME=dev
DB_PASSWORD=
JWT_SECRET=dev-only-change-this-secret-before-production
JWT_TTL_HOURS=24
```

Create the database before running the app:

```sql
CREATE DATABASE connectspace CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

If you want to use the default `dev` user:

```sql
CREATE USER 'dev'@'localhost' IDENTIFIED BY '';
GRANT ALL PRIVILEGES ON connectspace.* TO 'dev'@'localhost';
FLUSH PRIVILEGES;
```

If you use another MySQL username or password, set the environment variables before starting the app.

## Environment Variables

`.env.example` is only a reference file. Spring Boot does not automatically load `.env`, so set the variables in the same terminal where you run the backend.

PowerShell:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/connectspace"
$env:DB_USERNAME="dev"
$env:DB_PASSWORD=""
$env:JWT_SECRET="change-this-to-a-long-random-secret"
$env:JWT_TTL_HOURS="24"
```

Command Prompt:

```bat
set DB_URL=jdbc:mysql://localhost:3306/connectspace
set DB_USERNAME=dev
set DB_PASSWORD=
set JWT_SECRET=change-this-to-a-long-random-secret
set JWT_TTL_HOURS=24
```

## Run The Backend

From the `connect-be` directory:

```powershell
.\gradlew.bat :api:bootRun
```

On Git Bash, Linux, or macOS:

```bash
./gradlew :api:bootRun
```

The server runs at:

```text
http://localhost:8080
```

## Quick Check

Swagger UI:

```text
http://localhost:8080/swagger-ui/index.html
```

Register an account:

```powershell
curl -X POST "http://localhost:8080/api/auth/register" `
  -H "Content-Type: application/json" `
  -d '{"name":"Test User","email":"test@example.com","password":"Password123!"}'
```

Log in:

```powershell
curl -X POST "http://localhost:8080/api/auth/login" `
  -H "Content-Type: application/json" `
  -d '{"email":"test@example.com","password":"Password123!"}'
```

For authenticated APIs, send the JWT in the `Authorization` header:

```powershell
curl "http://localhost:8080/api/auth/me" `
  -H "Authorization: Bearer <token>"
```

## Run Tests

```powershell
.\gradlew.bat test
```

Tests use an in-memory H2 database, so MySQL is not required for tests.

## Build The Jar

```powershell
.\gradlew.bat :api:bootJar
```

The generated jar is located in:

```text
api/build/libs/
```

Run the jar:

```powershell
java -jar .\api\build\libs\api-0.0.1-SNAPSHOT.jar
```

## Common Issues

- `java: invalid source release: 21`: install JDK 21 or make sure `JAVA_HOME` points to JDK 21.
- `Communications link failure`: MySQL is not running, `DB_URL` is wrong, or the `connectspace` database has not been created.
- `Access denied for user`: `DB_USERNAME` or `DB_PASSWORD` is wrong, or the MySQL user does not have permission.
- `401 Unauthorized`: the endpoint requires `Authorization: Bearer <token>`.
