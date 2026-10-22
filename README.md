# Hosting Billing Platform

[![CI](https://github.com/hamza2829/hosting-billing-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/hamza2829/hosting-billing-platform/actions/workflows/ci.yml)

A simplified event driven hosting-company backend built with **Java 21 and Spring Boot 3.5**. Customers have monthly subscriptions, invoices trigger payments, and every event becomes a searchable audit record. All fixtures are synthetic and the payment provider is a local mock.

```text
REST client + X-API-Key + X-Correlation-ID
     |+     v
billing-service (8081) ---> PostgreSQL billing schema
     |                        | invoice + outbox in one transaction
     +------ InvoiceCreated --+----> Kafka: billing.events
                                     |+                                     v
                              payment-service (8082)
                                     | stable invoice idempotency key
                                     v
                              Mock payment API (8090)
                                     |+                        PostgreSQL payments schema + outbox
                                     |+                    PaymentReceived / PaymentFailed
                                     |+                   +-----------------+-------------------+
                   v                                     v
        billing-service invoice state         audit-search-service (8083)
                   ^                                     |+                   |                               Elasticsearch
              Kafka events                     /search, /alerts,
                                             /invoices/{id}/audit

Exhausted payment retries -> outbox -> billing.events.DLT -> audit + alert
Correlation IDs: HTTP headers -> outbox -> Kafka headers -> logs + audit
```

## Services

| Service              | Responsibilities                                                                                                                                                           |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| billing-service      | Customer, subscription and invoice REST API; validation; global errors; Spring Data JPA; PostgreSQL; Flyway; actual monthly scheduler; transactional outbox                |
| payment-service      | Kafka consumer; HTTP payment client; stable provider idempotency keys; persisted payment state; exponential retry backoff; PaymentReceived, PaymentFailed and dead letters |
| audit-search-service | Consumes domain events and dead letters; indexes event UUIDs into Elasticsearch; customer, status, time range and full text search; invoice history and searchable alerts  |
| common               | Versioned event contract, Kafka topics, HTTP authentication, correlation context, structured logging configuration and reusable outbox publisher                           |

The payment service produces payment outcomes in the normal flow. Billing consumes them to update invoice state. Billing can also publish the same PaymentReceived and PaymentFailed contracts through its explicit reconciliation endpoint. It does not republish consumed events, preventing a feedback loop.

## Run

Requires Docker Compose v2 with Linux containers and Node.js 22 or newer for setup and smoke scripts. Java 21 and Maven 3.9 are needed only for a local source build.

```sh
git clone https://github.com/hamza2829/hosting-billing-platform.git
cd hosting-billing-platform
node tests/setup.mjs
docker compose up -d --build --wait --wait-timeout 240
node tests/smoke.mjs
```

Setup creates random API_KEY and DATABASE_PASSWORD values in ignored `.env`. No secret values are printed. Public ports bind to localhost. Swagger UI is available at `http://localhost:8081/swagger-ui/index.html`, with the same path on 8082 and 8083. OpenAPI JSON is at `/v3/api-docs`, health at `/actuator/health`, and Prometheus metrics at `/actuator/prometheus` on each service.

The monthly job defaults to midnight UTC on the first of each month. To exercise its real timer quickly, start Compose with `BILLING_MONTHLY_CRON='*/5 * * * * *'`. The CI job does this automatically. The smoke suite requires an accelerated scheduler. In PowerShell, set `$env:BILLING_MONTHLY_CRON='*/5 * * * * *'` before starting Compose.

For local source checks:

```sh
mvn -B -ntp spotless:check verify
python3 -m unittest discover -s tests -p 'test_*.py' -v
```

## API walkthrough

Authenticated endpoints require `X-API-Key` from `.env`. Pass `X-Correlation-ID` to trace a request through the services. Missing or unsafe correlation IDs are replaced with generated UUIDs. Health, metrics and generated API docs are public on localhost.

| Method and path                           | Service  | Body or filters                                                               |
| ----------------------------------------- | -------- | ----------------------------------------------------------------------------- |
| POST /customers                           | billing  | name, email                                                                   |
| GET /customers, /customers/{id}           | billing  | Customer list or UUID                                                         |
| POST /subscriptions                       | billing  | customerId, plan, amountMinor, currency, startsOn                             |
| GET /subscriptions                        | billing  | Subscription list                                                             |
| POST /invoices                            | billing  | subscriptionId, period such as 2026-10                                        |
| GET /invoices, /invoices/{id}             | billing  | Invoice list or UUID                                                          |
| POST /invoices/{id}/payment-notifications | billing  | received boolean and synthetic provider reference for operator reconciliation |
| GET /payments, /payments/{invoiceId}      | payments | Persisted payment state                                                       |
| GET /search                               | audit    | customer UUID, status, from/to ISO instants, q full text                      |
| GET /invoices/{id}/audit                  | audit    | Invoice history ordered by event time                                         |
| GET /alerts                               | audit    | Failed payments, dead letters and invalid-event alerts                        |

Amounts are integer minor units, for example 1200 means EUR 12.00. Supported currencies are EUR and USD. Monthly invoices use the subscription's fixed amount without tax, proration or discounts. A unique database constraint and subscription row lock enforce one invoice per subscription and period. The scheduler excludes subscriptions whose start date is after that month's end.

Example bodies, using only synthetic data:

```json
{ "name": "Synthetic Hosting Customer", "email": "synthetic@example.test" }
```

```json
{
  "customerId": "UUID_FROM_CUSTOMER_RESPONSE",
  "plan": "Synthetic VPS",
  "amountMinor": 1200,
  "currency": "EUR",
  "startsOn": "2026-10-01"
}
```

```json
{ "subscriptionId": "UUID_FROM_SUBSCRIPTION_RESPONSE", "period": "2026-10" }
```

`POST /invoices` returns the invoice initially in OPEN state. Poll its GET endpoint for PAID or FAILED while Kafka processing completes. Posting the same subscription and period returns the existing invoice. A PAID invoice cannot regress to FAILED when a late failure arrives.

## Reliability and monitoring

Database writes and outbox inserts share one PostgreSQL transaction. The publisher locks pending rows with SKIP LOCKED, waits for Kafka acknowledgment, then marks them published. A crash between acknowledgment and database commit can redeliver an event. Billing deduplicates event UUIDs, payments deduplicate invoice UUIDs, and audit documents use event UUIDs as Elasticsearch IDs.

Every payment attempt uses `invoice-{invoiceId}` as its provider idempotency key. The demo provider persists unique keys and charge results in SQLite, including the case where a charge is saved but a 503 response is returned. This models a lost response. Retry safety depends on the real provider honoring that key and retaining its result; this project does not claim universal exactly-once external side effects.

A business decline produces PaymentFailed immediately. Transient HTTP failures receive three total attempts with initial 300 ms backoff and a multiplier of two. Exhaustion writes PaymentFailed and a dead letter envelope to the payment outbox before the consumer accepts recovery. Dead letters retain the original payload and source-derived stable identity. They and failed payment events become searchable alerts and ERROR-level JSON logs. An ambiguous failure requires operator reconciliation, not a new payment key.

The audit consumer retries Elasticsearch failures indefinitely to avoid dropping events during an index outage. This blocks the affected partition until recovery. Search is eventually consistent with Kafka and Elasticsearch refresh. History is ordered by event time, not a global distributed transaction order. Search and alert results are limited to 100 hits in this prototype.

The `X-Correlation-ID` Kafka header is restored into SLF4J MDC for each consumer invocation, retry callback and outbox publication. Spring Boot emits structured JSON console logs, and the audit projection includes the correlation ID. The smoke suite checks it in all three service logs, the provider request and the Elasticsearch records.

## Verified results

The local Java 21 reactor build and **28 JUnit tests** passed, including four embedded WireMock HTTP tests. Four Python tests for the mock provider also passed. Full Compose results will be added only from a completed run. CI retains Surefire XML, the smoke report and service logs as an execution artifact.

## Scope and limitations

The payment API is a mock, not Stripe or a live payment account. WireMock is used in Java tests; Compose uses a separate SQLite-backed HTTP mock so replay and response-loss behavior can be inspected. Its `/control` endpoint injects success, decline, outage and lost-response modes for synthetic tests. It is not a production integration.

This is a single-node local demonstration. Kafka has one broker and replication factor one. The PostgreSQL schemas share one database account. Elasticsearch security is disabled inside the private Compose network. HTTP and Kafka have no TLS, per-user authorization or tenant isolation. The shared API key is not a substitute for production authentication. Do not expose these ports publicly or use real customer or payment data.

There is no tax engine, proration, refunds, dunning, card vault, real provider reconciliation, subscription editing, persistent Kafka volume, external paging or performance/load benchmark. Invoice data and payment keys persist in their volumes, but a production system also needs broker durability, retention policies and operational recovery tooling. Reconciliation notifications are an operator demo endpoint, not a verified provider webhook.

Run `docker compose down` to stop the project. Add `-v` only when intentionally deleting the project's database, provider and Elasticsearch data.
