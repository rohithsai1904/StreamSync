CREATE TABLE IF NOT EXISTS customers (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE customers REPLICA IDENTITY FULL;

INSERT INTO customers (name, email)
VALUES ('Example Customer', 'customer@example.com')
ON CONFLICT (email) DO NOTHING;
