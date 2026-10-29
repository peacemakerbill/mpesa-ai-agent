# Choosing the database

`companion-api` and `payment-service` can each run on **PostgreSQL**, **MySQL** or **MongoDB**. You pick one word.

## Switch it

Open `src/main/resources/application.yml` in the service and change the word after the colon:

```yaml
spring:
  profiles:
    active: ${DATABASE_TYPE:postgresql}     # postgresql, mysql or mongodb
```

Or leave the file alone and set `DATABASE_TYPE` in the environment or in the service's `.env`. The environment wins.
Each service can use a different database. The default is `postgresql`.

The connection details live in three small files next to `application.yml`:

| File | Used when | Address setting |
|---|---|---|
| `application-postgresql.yml` | `postgresql` | `POSTGRES_URL` (or `DB_URL`) |
| `application-mysql.yml` | `mysql` | `MYSQL_URL` (or `DB_URL`) |
| `application-mongodb.yml` | `mongodb` | `MONGODB_URI` |

SQL databases also read `DB_USER` and `DB_PASSWORD`. Defaults point at `localhost` with database names `companion` and `payments`.

## With Docker Compose

```bash
# PostgreSQL (default)
docker compose up --build

# MySQL
echo "DATABASE_TYPE=mysql" >> .env
docker compose --profile mysql up --build

# MongoDB
echo "DATABASE_TYPE=mongodb" >> .env
docker compose --profile mongodb up --build
```

The profile starts the matching database container. The services read `DATABASE_TYPE` from the `.env` next to `docker-compose.yml`.
The PostgreSQL container always starts too, because the compose file keeps it as the default. It stays idle on the other two.

## What changes underneath

| | PostgreSQL | MySQL | MongoDB |
|---|---|---|---|
| Schema | Flyway, `db/migration/postgresql` | Flyway, `db/migration/mysql` (UUIDs as `BINARY(16)`, `DATETIME(6)`, `utf8mb4_bin`) | Created by the service, indexes included. No migrations. |
| Two requests changing one record | Row lock | Row lock | Version number. The loser gets `409 Conflict` and tries again. |
| One change across several records | Transaction | Transaction | Not atomic. See below. |
| Repository code | Spring Data JPA | Spring Data JPA | Spring Data MongoDB |

The same model classes are mapped for both worlds, and the services only talk to small repository interfaces,
so no business code knows which database is running.

## MongoDB: read this before choosing it for payments

* **No multi-document transactions.** The service does not use a Mongo replica set. When one action changes two records
  (for example a payment and its operation), the two writes are not atomic. A crash between them can leave one done and one not.
  The reconciler in `payment-service` repairs lost callbacks, but this is a real weakening compared with SQL.
* **Idempotency still holds.** Unique indexes on the idempotency key and the M-Pesa receipt are created for you.
* **Row locks become version checks.** Two callbacks racing on the same record: one wins, the other gets `409`.
* If you need strict atomicity across records, choose PostgreSQL or MySQL. For `payment-service` that is the safer pick.

## Moving data

Switching the word does **not** move data. A new database starts empty. To keep requests, export them from the old
database yourself. Nothing here converts between SQL and MongoDB.

## Status: what was and was not tested

* The code for all three databases was written and checked for syntax only. **It has not been compiled**, because the
  sandbox that wrote it could not reach Maven Central.
* The tests use an in-memory H2 database in PostgreSQL mode. **MySQL and MongoDB were never run**, not even in a test.
  Run `mvn test` first, then start each service once per database with the compose profiles above and try the
  Journey tab of `console/console.html`.
* The Spring auto-configuration class names in the `exclude:` lists of the three profile files were written from memory.
  If a service refuses to start with "class not found" in the exclude list, correct the name for your Spring Boot version.
