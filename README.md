# Kitchensink

A Spring Boot 3.5 / Java 21 migration of the original JBoss EAP "kitchensink"
quickstart. It's a small member-registration application: a web form backed by
Spring MVC/Thymeleaf, a REST API backed by Spring Web, and persistence via
Spring Data JPA against an in-memory H2 database.

## Prerequisites

- JDK 21+
- Maven 3.9+ (no wrapper is checked in; use a locally installed `mvn`)

## Building

```
mvn clean package
```

This compiles the app, runs the unit/`@WebMvcTest`/`@DataJpaTest` test suite, and
produces an executable jar at `target/kitchensink-8.0.0.GA.jar`.

## Running

Either run it directly with Maven:

```
mvn spring-boot:run
```

or build the jar first and run it:

```
mvn clean package
java -jar target/kitchensink-8.0.0.GA.jar
```

The app starts on port `8080` under the context path `/kitchensink`, so the
registration page is at:

```
http://localhost:8080/kitchensink/
```

It uses an in-memory H2 database that's recreated (schema + seed data) on every
startup — no external database setup is required.

## Testing

Run the unit and integration test suite (in-process, no running server needed):

```
mvn test
```

There's also a black-box integration test, `RemoteMemberRegistrationIT`, that
makes real HTTP calls against an already-running instance of the app rather
than an in-process test context. Start the app separately (see Running above),
then run:

```
mvn verify -Premote-it
```

## Features

- **Member registration form** — name/email/phone fields with live server-side
  validation (Jakarta Bean Validation), inline per-field error messages, and a
  "Registered!" confirmation on success.
- **Member listing** — all registered members are shown in a table, ordered by
  name, with a link to each member's REST resource.
- **Duplicate-email protection** — both the form and the REST API reject a
  second registration using an email address that's already taken.
- **REST API** — the same registration/listing functionality is available as
  JSON over HTTP for programmatic clients.

## Endpoints

### Web UI

| Method | Path         | Description                                                             |
|--------|--------------|--------------------------------------------------------------------------|
| GET    | `/`          | Registration form plus the current member list.                        |
| POST   | `/register`  | Submits the form. Redisplays the form with field errors on failure, or redirects to `/` with a success message. |

### REST API

All paths below are relative to the context path (e.g. `/kitchensink/rest/members`).

| Method | Path                  | Description                                                    |
|--------|-----------------------|-----------------------------------------------------------------|
| GET    | `/rest/members`       | Lists all members, ordered by name, as JSON.                   |
| GET    | `/rest/members/{id}`  | Looks up a single member by numeric id. `404` if not found.    |
| POST   | `/rest/members`       | Creates a member from a JSON body. `200` on success, `400` with a map of field violations on validation failure, `409` if the email is already taken. |

Example member JSON:

```json
{
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "phoneNumber": "2125551212"
}
```

## Project structure

```
src/main/java/com/example/kitchensink/
  KitchensinkApplication.java   Spring Boot entry point
  controller/                   Spring MVC controller for the web UI
  rest/                         Spring Web REST controller (JSON API)
  service/                      Registration business logic
  data/                         Spring Data JPA repository
  model/                        JPA entity + Bean Validation constraints
src/main/resources/
  templates/                    Thymeleaf views
  static/css/                   Stylesheet for the web UI
  application.properties        Server, datasource, and JPA configuration
  import.sql                    Seed data loaded on startup
```

## Configuration

Key settings live in `src/main/resources/application.properties`:

- `server.servlet.context-path=/kitchensink` — matches the original
  deployment's context path.
- `spring.datasource.*` — in-memory H2, recreated on every startup
  (`spring.jpa.hibernate.ddl-auto=create-drop`); not intended for production
  use.
