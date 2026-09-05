# AI Shopping Agent

A Spring Boot shopping API with a database-grounded AI assistant. Product data remains in MySQL; the assistant is only given product information returned by the catalog tools. It does not use a hardcoded product catalog or API key.

## Prerequisites

- Java 21
- MySQL with the existing `practice_db` database and its product data
- Maven (or use `mvnw.cmd` on Windows)
- Optional but recommended: [Ollama](https://ollama.com/) for free, local AI

## Configuration

Application settings come from environment variables. No secrets should be committed.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/practice_db` | MySQL JDBC URL |
| `DB_USERNAME` | `root` | MySQL user |
| `DB_PASSWORD` | empty | MySQL password |
| `AI_PROVIDER` | `ollama` | Label returned in chat responses |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama server URL |
| `OLLAMA_MODEL` | `llama3.1` | Ollama chat model |
| `AI_FALLBACK_ENABLED` | `true` | Return database-only matches if an AI provider is unavailable |

### Free local AI: Ollama (default)

Install Ollama, then download and run a tool-capable model:

```powershell
ollama pull llama3.1
ollama serve
```

The application defaults to Ollama. When it is unavailable, the chat endpoint safely returns a deterministic database-only result set when `AI_FALLBACK_ENABLED=true`.

### Optional OpenAI provider

OpenAI is excluded from the default build. To enable it, set a key only in your shell/session, activate the Spring `openai` profile, and activate the matching Maven profile:

```powershell
$env:OPENAI_API_KEY = "your-key"
$env:SPRING_PROFILES_ACTIVE = "openai"
$env:DB_PASSWORD = "your-mysql-password"
mvn -Popenai spring-boot:run
```

Optionally set `OPENAI_MODEL`. Never place the key in `application.properties` or source control.

## Run

```powershell
$env:DB_PASSWORD = "your-mysql-password"
.\mvnw.cmd spring-boot:run
```

Run tests:

```powershell
.\mvnw.cmd test
```

Tests use an in-memory H2 database and do not need your MySQL server or AI provider.

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

The assistant receives catalog candidates retrieved from MySQL and can call five Spring AI tools: name search, category search, budget search, details lookup, and product comparison. The system prompt restricts it to those returned results. The API also returns the database-derived product array separately, so clients can render authoritative product data without parsing the model text.

## Developed By

**Jatin Gupta**  
GitHub: [@jatingupta18](https://github.com/jatingupta18)  
LinkedIn: [Jatin Gupta](https://www.linkedin.com/in/jatingupta17)
