# KAuth Event Sourcing Service

## Overview
KAuth is an MVP authentication and device management service built with event sourcing principles. It uses EventStoreDB as the event store and supports optimistic updates, snapshotting, and CQRS-style projections. The system is designed for extensibility, supporting domains like Organism, Train, Device, and more.

## Architecture
- **Event Sourcing:** All state changes are captured as events. State is rebuilt by replaying events.
- **CQRS:** Commands mutate state via command handlers; queries use projections for fast reads.
- **Kotlin + Ktor:** The backend is written in Kotlin using the Ktor framework for HTTP APIs.
- **EventStoreDB:** Used as the event store for all domain events.
- **PostgreSQL:** Used for projections and read models.
- **Docker:** All services can be run in containers for easy local development and deployment.

## Domains
- **Organism:** Represents an organization or logical group. Supports creation, editing, soft deletion, and user role management.
- **Train:** Represents a train entity, linked to an organism. Supports creation, editing, and soft deletion.
- **Device:** Represents a device, linked to a train. Supports creation, editing (trainId and ports), and soft deletion.

## Getting Started
### Prerequisites
- Docker & Docker Compose
- Java 17+
- Gradle (or use the provided wrapper)

### Running Locally
1. **Clone the repository:**
   ```sh
   git clone <repo-url>
   cd kauth-event-sourcing
   ```
2. **Start dependencies:**
   ```sh
   docker-compose up -d
   ```
3. **Build the project:**
   ```sh
   ./gradlew build
   ```
4. **Run the service:**
   ```sh
   ./gradlew run
   ```

### Running Tests
```sh
./gradlew test
```

## API Overview
- REST endpoints are exposed via Ktor (see `OrganismApiRest.kt`, `TrainApiRest.kt`, `DeviceApiRest.kt`).
- Main endpoints:
  - `POST /organism/create` — Create an organism
  - `PUT /organism/{id}/edit` — Edit organism (partial updates supported)
  - `POST /train/create` — Create a train
  - `PUT /train/{id}/edit` — Edit train (partial updates supported)
  - `POST /device/create` — Create a device
  - `PUT /device/{id}/edit` — Edit device (partial updates for trainId and ports)
- See the source files for full endpoint details and request/response formats.

## Development Guide
- **Adding a Command/Event:**
  1. Add to the sealed interface in the domain object.
  2. Implement the handler and reducer.
  3. Register in the command state machine and event reducer.
  4. Add tests (see `src/test/kotlin/io/kauth/service/<domain>/<DomainTest>.kt`).
  5. Expose in API and REST if needed.
  6. Update projections and migrations if state shape changes.
- **Projections:**
  - Each domain has a projection object and SQL table.
  - Update the projection and add a migration if you change the state shape.
- **Migrations:**
  - Place migration SQL files in the `migrations/` directory.
  - Name them with a version and description (e.g., `V20250620__add_deleted_column_to_organims.sql`).

## References & Further Reading
- [EventStoreDB Docs](https://developers.eventstore.com/server/v23.10/installation.html#use-docker-compose)
- [Ktor](https://ktor.io/)
- [Event Sourcing Patterns](https://cqrs.wordpress.com/documents/building-event-storage/)
- [Simple Event Sourcing Auth Example](https://www.zilverline.com/blog/simple-event-sourcing-users-authentication-authorization-part-6)
- [Snapshotting Strategies](https://www.eventstore.com/blog/snapshotting-strategies)
- [Monitoring Ktor with Grafana](https://medium.com/@math21/how-to-monitor-a-ktor-server-using-grafana-bab54a9ac0dc)

---

For more details, see the `.rules/` directory for project conventions and best practices.
