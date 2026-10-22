CREATE TABLE customers(id uuid PRIMARY KEY, name varchar(100) NOT NULL, email varchar(200) NOT NULL UNIQUE);
CREATE TABLE subscriptions(id uuid PRIMARY KEY, customer_id uuid NOT NULL REFERENCES customers(id), plan varchar(100) NOT NULL, amount_minor bigint NOT NULL CHECK(amount_minor>0), currency varchar(3) NOT NULL, starts_on date NOT NULL, active boolean NOT NULL);
CREATE TABLE invoices(id uuid PRIMARY KEY, subscription_id uuid NOT NULL REFERENCES subscriptions(id), customer_id uuid NOT NULL REFERENCES customers(id), period varchar(7) NOT NULL, amount_minor bigint NOT NULL, currency varchar(3) NOT NULL, status varchar(20) NOT NULL, created_at timestamptz NOT NULL, UNIQUE(subscription_id,period));
CREATE TABLE processed_events(id uuid PRIMARY KEY);
CREATE TABLE outbox(id uuid PRIMARY KEY, topic varchar(100) NOT NULL, event_key varchar(100) NOT NULL, payload text NOT NULL, correlation_id varchar(64) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz);
CREATE INDEX outbox_pending ON outbox(created_at) WHERE published_at IS NULL;
