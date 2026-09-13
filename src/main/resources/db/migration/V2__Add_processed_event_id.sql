ALTER TABLE transactions ADD COLUMN processed_event_id UUID;
CREATE INDEX idx_transactions_processed_event_id ON transactions(processed_event_id);
