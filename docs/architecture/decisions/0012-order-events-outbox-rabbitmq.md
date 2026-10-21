# ADR 0012: Order events through a transactional outbox and RabbitMQ

- **Status:** Accepted, 2026-10-21 (adopts RabbitMQ per the trigger in ADR 0002)

## Context
Merchants must hear about new orders and customers about the merchant's decisions (backlog B-37),
in their own language, as push notifications and in an inbox. Sending pushes inside the order
transaction would make checkout depend on Firebase, and a crash between "order saved" and "event
sent" would lose events. More consumers will follow (delivery partners, analytics, B-44, B-50).

## Decision
- **Transactional outbox.** `OrderService` writes `order.placed` / `order.status_changed` into
  `outbox_event` in the same transaction as the order change (`shared.events.EventOutbox`,
  propagation MANDATORY). An event exists exactly when the change was committed.
- **Publisher.** `OutboxPublisher` sends pending events every second, oldest first, in batches
  locked with `FOR UPDATE SKIP LOCKED` (safe with several replicas), with exponential backoff
  (2 s … 10 min) on failure. Delivery is at-least-once.
- **Two transports** (`dukkan.events.transport`):
  - `in-process` (default): the publisher calls this instance's handlers directly. Local
    development, tests and a single replica need no broker.
  - `rabbitmq`: RabbitMQ 4.3.6 via Spring AMQP. Topic exchange `dukkan.events` (routing key =
    event type), durable **quorum** queue `dukkan.backend.events` bound to `#`, dead-lettered to
    `dukkan.backend.events.dlq`. Publisher confirms and mandatory routing: an outbox row is only
    marked as sent once the broker has the message. Spring Boot's Rabbit auto-configuration is
    excluded so that nothing connects to a broker in the default mode; the health indicator is
    registered only with this transport.
- **Idempotent consumers.** `EventDispatcher` records `(event_id, handler)` in `processed_event`
  in the handler's own transaction, so redelivered events run once; a failing handler does not
  undo the others and is retried (outbox) or dead-lettered (RabbitMQ).
- **Notifications** (module `notification`): `OrderNotificationHandler` writes inbox entries in
  the recipient's language (de/en/ar, amounts in German format) and pushes them through Firebase
  Cloud Messaging HTTP v1 (`PushGateway`; service account → OAuth token, cached). Without FCM
  credentials only the inbox is used. Tokens FCM reports as gone are deleted.

## Consequences
- RabbitMQ is optional infrastructure: Compose profile `events`, `DUKKAN_EVENTS_TRANSPORT=rabbitmq`
  in production with several replicas. The outbox table needs housekeeping (published rows can be
  deleted after a retention period; backlog).
- Pushes can arrive twice in rare failure cases (at-least-once); the inbox entry is written once.
- The app's push registration is a port (`PushRegistration`) until a Firebase project exists;
  the inbox works without it.
