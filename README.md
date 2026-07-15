# Kitchensink

A Spring Boot 3.5 / Java 21 migration of the original JBoss EAP "kitchensink"
quickstart. It's a small member-registration application: a web form backed by
Spring MVC/Thymeleaf, a REST API backed by Spring Web, and persistence via
Spring Data MongoDB against a local MongoDB instance.

## Prerequisites

- JDK 21+
- Maven 3.9+ (no wrapper is checked in; use a locally installed `mvn`)
- A MongoDB instance running locally on the default port (`localhost:27017`,
  no auth) — see [Configuration](#configuration) to point at a different
  host/port/database. If it's unreachable at startup, the app fails fast
  (within a few seconds, not the driver's default 30s) with a clear error
  instead of starting up in a broken state.

The test suite doesn't need this: it runs against an embedded, ephemeral
MongoDB instance (via `de.flapdoodle.embed.mongo.spring30x`, test-scope only)
that's started and torn down automatically, so `mvn test` needs nothing
installed.

## Building

```
mvn clean package
```

This compiles the app, runs the unit/`@WebMvcTest`/`@DataMongoTest` test
suite, and produces an executable jar at `target/kitchensink-8.0.0.GA.jar`.

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

It connects to the local MongoDB instance described in
[Prerequisites](#prerequisites) and seeds one demo member the first time the
`member` collection is empty — unlike the old in-memory setup, data now
persists across restarts, so the seed only happens once.

If no MongoDB instance is reachable, the app fails to start with a short,
readable error instead of a raw driver stack trace (`MongoConnectionFailureAnalyzer`
in the config package handles this):

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Could not connect to MongoDB: Timed out while waiting for a server that matches WritableServerSelector...

Action:

Make sure a local MongoDB instance is installed and running, and that spring.data.mongodb.host/port
in application.properties match its address (defaults to localhost:27017).
```

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
  data/                         Spring Data MongoDB repository + id sequence generator
  model/                        Mongo document + Bean Validation constraints
  config/                       Startup seed data, Mongo timeout/error-message tuning
src/main/resources/
  templates/                    Thymeleaf views
  static/css/                   Stylesheet for the web UI
  application.properties        Server and MongoDB configuration
  META-INF/spring.factories     Registers MongoConnectionFailureAnalyzer
src/test/resources/
  application.properties        Overrides connection settings for the embedded-Mongo test slice
```

## Configuration

Key settings live in `src/main/resources/application.properties`:

- `server.servlet.context-path=/kitchensink` — matches the original
  deployment's context path.
- `spring.data.mongodb.host` / `.port` / `.database` — the local MongoDB
  instance the running app connects to (defaults to `localhost:27017`, no
  auth); not intended for production use as-is.
- `de.flapdoodle.mongodb.embedded.version` — only read when
  `de.flapdoodle.embed.mongo.spring30x` is on the classpath, which is
  test-scope only (see `pom.xml`); picks the embedded MongoDB version used by
  the `@DataMongoTest` slice. `src/test/resources/application.properties`
  overrides the real host/port for tests so they never touch your local
  instance.
- Member ids stay numeric (`Long`), matching the original REST API contract:
  since MongoDB's native `_id` is an ObjectId rather than a sequential number,
  `MemberSequenceGenerator` emulates auto-increment via an atomic counter
  document.
