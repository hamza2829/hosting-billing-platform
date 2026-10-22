CREATE TABLE payments(invoice_id uuid PRIMARY KEY, customer_id uuid NOT NULL, amount_minor bigint NOT NULL, currency varchar(3) NOT NULL, status varchar(30) NOT NULL, provider_reference varchar(200));
CREATE TABLE outbox(id uuid PRIMARY KEY, topic varchar(100) NOT NULL, event_key varchar(100) NOT NULL, payload text NOT NULL, correlation_id varchar(64) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz);
CREATE INDEX outbox_pending ON outbox(created_at) WHERE published_at IS NULL;
