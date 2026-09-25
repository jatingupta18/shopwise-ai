# AI Shopping Agent

A Spring Boot shopping API with a database-grounded AI assistant. The assistant is only given product information returned by the catalog tools. It does not use a hardcoded product catalog or API key.

## Prerequisites

- Java 21
- H2 for default local development and tests (included)
- Optional MySQL or PostgreSQL database for persistent deployments
- Maven (or use `mvnw.cmd` on Windows)
- Optional but recommended: [Ollama](https://ollama.com/) for free, local AI

## Configuration

Application settings come from environment variables. No secrets should be committed.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:h2:mem:shopping;MODE=MySQL;DB_CLOSE_DELAY=-1` | JDBC URL for H2, MySQL, or PostgreSQL |
| `DB_USERNAME` | `sa` | Database user |
| `DB_PASSWORD` | empty | Database password |
| `DB_DRIVER` | `org.h2.Driver` | JDBC driver class; use `org.postgresql.Driver` for PostgreSQL |
| `DB_PLATFORM` | `org.hibernate.dialect.H2Dialect` | Hibernate dialect; use `org.hibernate.dialect.PostgreSQLDialect` for PostgreSQL |
| `AI_PROVIDER` | `ollama` | Label returned in chat responses |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama server URL |
| `OLLAMA_MODEL` | `llama3.2` | Ollama chat model |
| `OLLAMA_MAX_RETRY_ATTEMPTS` | `1` | Spring AI attempts for an unavailable Ollama provider |
| `OLLAMA_CONNECT_TIMEOUT` | `2s` | Maximum time to establish an Ollama connection |
| `OLLAMA_READ_TIMEOUT` | `15s` | Maximum time waiting for an Ollama response |
| `AI_FALLBACK_ENABLED` | `true` | Return database-only matches if an AI provider is unavailable |

### Free local AI: Ollama (default)

Install Ollama, then download and run a tool-capable model:

```powershell
ollama pull llama3.2
ollama serve
```

The application defaults to Ollama. When it is unavailable, the chat endpoint safely returns a deterministic database-only result set when `AI_FALLBACK_ENABLED=true`.
Ollama connection failures are attempted once by default, so unavailable production instances return the database fallback without Spring AI's exponential retry delay. Increase the timeout values only when a reachable Ollama server needs more time to respond.

### Optional OpenAI provider

OpenAI is excluded from the default build. To enable it, set a key only in your shell/session, activate the Spring `openai` profile, and activate the matching Maven profile:

```powershell
$env:OPENAI_API_KEY = "your-key"
$env:SPRING_PROFILES_ACTIVE = "openai"
$env:DB_PASSWORD = "your-database-password"
mvn -Popenai spring-boot:run
```

Optionally set `OPENAI_MODEL`. Never place the key in `application.properties` or source control.

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

Run tests:

```powershell
.\mvnw.cmd test
```

Tests use an in-memory H2 database and do not need your MySQL server or AI provider.

### Render PostgreSQL

Keep the default H2 configuration for local development. For a persistent Render PostgreSQL database, configure these environment variables in the Render service (never commit the password):

```text
DB_URL=jdbc:postgresql://<host>:5432/<database>?sslmode=require
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
DB_DRIVER=org.postgresql.Driver
DB_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
```

Use the hostname, database name, user, and password supplied by Render. The application uses `spring.jpa.hibernate.ddl-auto=update`, so tables are created or updated on the first connection. The seed loader inserts sample products only when the product table is empty, preventing duplicate seed data on restarts.

## API

Existing catalog endpoints are retained:

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/products` | List products |
| `GET` | `/api/products/{id}` | Get a product |
| `GET` | `/api/products/category/{category}` | Find by category |
| `GET` | `/api/products/search?name={name}` | Search names |
| `POST` | `/api/products` | Create a product |
| `POST` | `/api/chat` | Ask the AI shopping assistant |

### Chat example

```http
POST http://localhost:8080/api/chat
Content-Type: application/json

{
  "message": "I need a laptop under 60000"
}
```

The response contains an `answer`, a `products` array populated from the database, the `provider`, and whether `fallbackUsed` was needed.

## How grounding works

The assistant receives catalog candidates retrieved from the configured database and can call five Spring AI tools: name search, category search, budget search, details lookup, and product comparison. The system prompt restricts it to those returned results. The API also returns the database-derived product array separately, so clients can render authoritative product data without parsing the model text.

## Developed By

**Jatin Kumar Gupta**  
GitHub: [@jatingupta18](https://github.com/jatingupta18)  
LinkedIn: [Jatin Gupta](https://www.linkedin.com/in/jatingupta17)
