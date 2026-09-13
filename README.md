# 🛡️ FraudGuard

**Real-time transaction fraud detection system** powered by a Spring Boot microservice, Apache Kafka event pipeline, and a Python ML scoring service using Isolation Forest anomaly detection.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [ML Service](#ml-service)
- [Configuration](#configuration)
- [Testing](#testing)

---

## Overview

FraudGuard is a full-stack fraud detection pipeline that scores financial transactions in real-time. When a transaction is submitted via REST API, it flows through an event-driven pipeline — persisted to PostgreSQL, streamed through Kafka, scored by a machine learning model, and resolved with a final verdict (`APPROVED` or `FLAGGED`).

### Key Features

- **Event-driven architecture** — Kafka decouples ingestion from scoring for async, scalable processing
- **ML-powered anomaly detection** — Isolation Forest model trained on synthetic transaction data
- **Redis caching** — Frequently accessed transactions and account queries are cached
- **Redis rate limiting** — Per-account rate limiting on transaction submission (100 req/min)
- **Idempotent processing** — Kafka consumer tracks `processedEventId` to prevent duplicate scoring
- **Dead Letter Queue** — Failed messages are routed to `transactions.dlq` after retries
- **Database migrations** — Schema managed via Flyway for safe, versioned evolution
- **Interactive demo UI** — HTML/CSS/JS console to submit transactions and watch the pipeline in real-time

---

## Architecture

```
┌──────────────┐       ┌────────────────────────────────────────────────────────────┐
│  Demo UI     │       │                    Spring Boot (port 8080)                 │
│  (HTML/JS)   │──────▶│                                                            │
└──────────────┘  HTTP │  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
                       │  │ Transaction  │    │  Rate Limit  │    │    CORS      │  │
                       │  │ Controller   │    │   Filter     │    │   Config     │  │
                       │  └──────┬───────┘    └──────────────┘    └──────────────┘  │
                       │         │                                                  │
                       │  ┌──────▼────────────────────────────┐                     │
                       │  │     TransactionServiceImpl        │                     │
                       │  │  • Save to PostgreSQL (PENDING)   │                     │
                       │  │  • Publish to Kafka               │                     │
                       │  └──────┬────────────────────────────┘                     │
                       │         │                                                  │
                       │  ┌──────▼────────────────────────────┐                     │
                       │  │   TransactionEventConsumer        │                     │
                       │  │  • Consume from Kafka             │                     │
                       │  │  • Call ML Service via REST       │◀─── MlServiceClient │
                       │  │  • Update DB (APPROVED/FLAGGED)   │                     │
                       │  │  • Publish to processed topic     │                     │
                       │  └───────────────────────────────────┘                     │
                       └────────────────────────────────────────────────────────────┘
                                        │                  ▲
                                        │                  │  HTTP POST /score
                                        ▼                  │
                              ┌──────────────────┐   ┌─────┴──────────────┐
                              │   Apache Kafka   │   │   ML Service       │
                              │                  │   │   (FastAPI :8000)   │
                              │  transactions.   │   │                    │
                              │    raw           │   │  Isolation Forest  │
                              │    processed     │   │  Anomaly Detection │
                              │    dlq           │   └────────────────────┘
                              └──────────────────┘
                                        │
          ┌─────────────────────────────┼─────────────────────────────┐
          ▼                             ▼                             ▼
┌──────────────────┐       ┌──────────────────┐          ┌──────────────────┐
│   PostgreSQL     │       │      Redis       │          │    Kafka UI      │
│   (port 5432)    │       │   (port 6379)    │          │   (port 8082)    │
│                  │       │                  │          │                  │
│  transactions    │       │  • TX cache      │          │  Topic browser   │
│  table           │       │  • Rate limits   │          │  Consumer groups │
└──────────────────┘       └──────────────────┘          └──────────────────┘
```

---

## Tech Stack

### Backend (Java)

| Technology | Purpose |
|---|---|
| **Spring Boot 4.1** | Application framework |
| **Spring Data JPA** | ORM / PostgreSQL access |
| **Spring Kafka** | Kafka producer & consumer |
| **Spring Data Redis** | Caching & rate limiting |
| **Flyway** | Database schema migrations |
| **Lombok** | Boilerplate reduction |
| **Jackson** | JSON serialization (with JSR-310 for dates) |
| **Bean Validation** | Request DTO validation |

### ML Service (Python)

| Technology | Purpose |
|---|---|
| **FastAPI** | REST API framework |
| **scikit-learn** | Isolation Forest model |
| **pandas / numpy** | Feature engineering |
| **joblib** | Model serialization |
| **Pydantic** | Request/response validation |

### Infrastructure (Docker)

| Service | Image | Port |
|---|---|---|
| **PostgreSQL 16** | `postgres:16` | `5432` |
| **Apache Kafka** | `confluentinc/cp-kafka:7.5.0` (KRaft mode) | `9092` |
| **Kafka UI** | `provectuslabs/kafka-ui:latest` | `8082` |
| **Redis 7** | `redis:7` | `6379` |

### Frontend

| Technology | Purpose |
|---|---|
| **HTML / CSS / JS** | Transaction submission console |

---

## Project Structure

```
FraudGuard/
├── src/main/java/com/fraudguard/
│   ├── FraudGuardApplication.java          # Spring Boot entry point
│   ├── config/
│   │   ├── KafkaConfig.java                # Kafka topics, producer/consumer factories, DLQ
│   │   ├── RedisConfig.java                # Redis connection & serialization
│   │   └── CorsConfig.java                 # CORS policy for frontend
│   ├── controller/
│   │   └── TransactionController.java      # REST API endpoints
│   ├── dto/
│   │   ├── TransactionRequestDTO.java      # Incoming transaction request
│   │   ├── TransactionResponseDTO.java     # API response
│   │   ├── TransactionEvent.java           # Kafka event envelope
│   │   ├── TransactionStatusUpdateDTO.java # Status update request
│   │   ├── MlScoreRequest.java             # Request to ML service
│   │   └── MlScoreResponse.java            # Response from ML service
│   ├── entity/
│   │   ├── Transaction.java                # JPA entity
│   │   ├── TransactionStatus.java          # Enum: PENDING, APPROVED, FLAGGED
│   │   └── TransactionType.java            # Enum: DEPOSIT, WITHDRAWAL, TRANSFER, etc.
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java     # Centralized error handling
│   │   └── ResourceNotFoundException.java  # 404 exception
│   ├── filter/
│   │   ├── RateLimitFilter.java            # Redis-backed per-account rate limiter
│   │   └── CachedBodyHttpServletRequest.java # Request body caching for filters
│   ├── repository/
│   │   └── TransactionRepository.java      # JPA repository
│   └── service/
│       ├── TransactionService.java         # Service interface
│       ├── TransactionServiceImpl.java     # Core business logic
│       ├── TransactionEventConsumer.java   # Kafka consumer + ML scoring
│       ├── TransactionCacheService.java    # Redis cache operations
│       └── MlServiceClient.java           # REST client to ML service
├── src/main/resources/
│   ├── application.yml                     # App configuration (local + test profiles)
│   └── db/migration/
│       ├── V1__Create_transactions_table.sql
│       ├── V2__Add_processed_event_id.sql
│       └── V3__Add_anomaly_score_and_model_version.sql
├── ml-service/
│   ├── app/
│   │   ├── main.py                         # FastAPI app with /score endpoint
│   │   ├── model.py                        # Isolation Forest model loader
│   │   ├── features.py                     # Feature engineering pipeline
│   │   └── schemas.py                      # Pydantic request/response models
│   ├── models/
│   │   └── isoforest-v1.joblib             # Trained model artifact
│   ├── training/
│   │   └── train.py                        # Synthetic data generation & model training
│   ├── tests/
│   └── requirements.txt
├── docker-compose.yml                      # PostgreSQL, Kafka, Redis, Kafka UI
├── fraudguard-demo.html                    # Interactive demo UI
├── pom.xml                                 # Maven build configuration
└── README.md
```

---

## How It Works

### Transaction Lifecycle

```
 Submit ──▶ PENDING ──▶ Kafka ──▶ ML Score ──▶ APPROVED / FLAGGED
```

1. **Submit** — Client sends a `POST /api/v1/transactions` request
2. **Persist** — Transaction is saved to PostgreSQL with status `PENDING`
3. **Publish** — A `TransactionEvent` is published to the `transactions.raw` Kafka topic
4. **Consume** — The `TransactionEventConsumer` picks up the event from Kafka
5. **Score** — Consumer calls the ML service at `POST http://localhost:8000/score`
6. **Verdict** — Based on the anomaly score vs. threshold (0.65):
   - Score < 0.65 → **APPROVED** ✅
   - Score ≥ 0.65 → **FLAGGED** 🚩
7. **Update** — Transaction is updated in PostgreSQL, cache is evicted
8. **Forward** — Processed event is published to `transactions.processed` topic

### ML Scoring Pipeline

The Isolation Forest model evaluates 5 engineered features:

| Feature | Description |
|---|---|
| `amount` | Raw transaction amount |
| `amount_log` | Log-transformed amount: `log(amount + 1)` |
| `hour_of_day` | Hour extracted from transaction timestamp (0-23) |
| `day_of_week` | Day of week (0=Monday, 6=Sunday) |
| `type_encoded` | Numeric encoding of transaction type |

**What triggers FLAGGED:**
- Very large amounts (>$5,000+)
- Transactions at unusual hours (2-5 AM)
- Weekend transactions
- High-risk types like `TRANSFER` or `WITHDRAWAL`

### Error Handling & Resilience

- **Idempotency** — Each event has a unique `eventId`; the consumer tracks `processedEventId` to skip duplicates
- **Retry + DLQ** — Failed messages retry 2x (1s backoff), then route to `transactions.dlq`
- **ML fallback** — If the ML service is unavailable, the transaction is automatically `FLAGGED`
- **Rate limiting** — Redis-backed sliding window: 100 requests/minute per account

---

## Getting Started

### Prerequisites

- **Java 17+**
- **Maven 3.9+**
- **Python 3.10+**
- **Docker & Docker Compose**

### 1. Start Infrastructure

```bash
docker compose up -d
```

This starts PostgreSQL, Kafka (KRaft mode), Kafka UI, and Redis.

### 2. Start the ML Service

```bash
cd ml-service
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # macOS/Linux
pip install -r requirements.txt
uvicorn app.main:app --port 8000
```

Verify at: http://localhost:8000/health

### 3. Start the Spring Boot API

```bash
# From project root
./mvnw spring-boot:run
```

Or:

```bash
mvn spring-boot:run
```

API runs at: http://localhost:8080

### 4. Open the Demo UI

Open `fraudguard-demo.html` via the API:

```
http://localhost:8080/fraudguard-demo.html
```

> **Note:** Open through the API (not as a local file) to avoid CORS issues with API calls.

---

## API Reference

Base URL: `http://localhost:8080/api/v1/transactions`

### Create Transaction

```http
POST /api/v1/transactions
Content-Type: application/json

{
  "accountId": "acc-demo-01",
  "amount": 249.99,
  "currency": "USD",
  "merchant": "Amazon",
  "transactionType": "PURCHASE"
}
```

**Response** (202 Accepted):

```json
{
  "id": "8308ef27-271a-42c0-99a3-72e22753bd2b",
  "accountId": "acc-demo-01",
  "amount": 249.99,
  "currency": "USD",
  "merchant": "Amazon",
  "transactionType": "PURCHASE",
  "status": "PENDING",
  "anomalyScore": null,
  "modelVersion": null,
  "createdAt": "2026-09-13T09:45:00Z",
  "updatedAt": "2026-09-13T09:45:00Z"
}
```

### Get Transaction by ID

```http
GET /api/v1/transactions/{id}
```

Poll this endpoint to watch the status change from `PENDING` → `APPROVED`/`FLAGGED`.

### List Transactions

```http
GET /api/v1/transactions?accountId=acc-demo-01&status=APPROVED&page=0&size=20&sort=createdAt,desc
```

All query parameters are optional.

### Get Transactions by Account

```http
GET /api/v1/transactions/account/{accountId}
```

### Update Transaction Status

```http
PATCH /api/v1/transactions/{id}/status
Content-Type: application/json

{
  "status": "APPROVED"
}
```

### Transaction Types

`DEPOSIT` | `WITHDRAWAL` | `TRANSFER` | `PAYMENT` | `PURCHASE` | `REFUND`

### Transaction Statuses

`PENDING` | `APPROVED` | `FLAGGED`

---

## ML Service

### Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Service info |
| `GET` | `/health` | Health check (returns model status) |
| `POST` | `/score` | Score a transaction |
| `GET` | `/docs` | Interactive Swagger docs |

### Score Request

```http
POST http://localhost:8000/score
Content-Type: application/json

{
  "id": "8308ef27-271a-42c0-99a3-72e22753bd2b",
  "accountId": "acc-demo-01",
  "amount": 249.99,
  "currency": "USD",
  "merchant": "Amazon",
  "transactionType": "PURCHASE",
  "createdAt": "2026-09-13T09:45:00Z"
}
```

### Score Response

```json
{
  "transactionId": "8308ef27-271a-42c0-99a3-72e22753bd2b",
  "anomalyScore": 0.4009,
  "isAnomalous": false,
  "threshold": 0.65,
  "modelVersion": "isoforest-v1"
}
```

### Re-training the Model

```bash
cd ml-service
python training/train.py
```

This generates 10,000 synthetic transactions (95% normal, 5% fraudulent) and trains a new Isolation Forest model saved to `models/isoforest-v1.joblib`.

---

## Configuration

### Application (`application.yml`)

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Spring Boot HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/fraudguard_db` | PostgreSQL connection |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker |
| `rate-limit.requests-per-minute` | `100` | Rate limit per account |
| `ml.service.url` | `http://localhost:8000/score` | ML scoring endpoint |

### Kafka Topics

| Topic | Partitions | Description |
|---|---|---|
| `transactions.raw` | 3 | Newly submitted transactions |
| `transactions.processed` | 3 | Scored transactions (APPROVED/FLAGGED) |
| `transactions.dlq` | 1 | Failed messages after retry exhaustion |

### ML Model

| Parameter | Value |
|---|---|
| Model | Isolation Forest |
| Estimators | 100 |
| Contamination | 0.05 (5%) |
| Threshold | 0.65 |
| Version | `isoforest-v1` |

---

## Testing

### Spring Boot Tests

```bash
./mvnw test
```

Uses Testcontainers for PostgreSQL and Spring Embedded Kafka. WireMock stubs the ML service.

### ML Service Tests

```bash
cd ml-service
pytest
```

---

## Useful Links (Local)

| Service | URL |
|---|---|
| Spring Boot API | http://localhost:8080 |
| ML Service | http://localhost:8000 |
| ML Swagger Docs | http://localhost:8000/docs |
| Kafka UI | http://localhost:8082 |
| Demo UI | http://localhost:8080/fraudguard-demo.html |

---

## License

This project is for educational and demonstration purposes.
