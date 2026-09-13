ALTER TABLE transactions 
ADD COLUMN anomaly_score DOUBLE PRECISION,
ADD COLUMN model_version VARCHAR(255);
