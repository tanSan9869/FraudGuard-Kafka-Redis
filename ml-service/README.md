# FraudGuard ML Service

This service provides an anomaly detection API to score transactions for the FraudGuard platform.

## Model Choice and Rationale
We are using an **Isolation Forest** model (`IsolationForest` from scikit-learn). 
- Since we do not have a real labeled fraud dataset, we must use an unsupervised anomaly detection approach.
- Isolation Forest is well-suited for high-dimensional data, is extremely fast to train and evaluate, and natively handles the concept of isolating anomalies based on random splits.

## Data
> **Note**: This model is trained on **synthetic data**.
Since real transaction data with known fraud labels isn't available for a portfolio project, we generate a synthetic dataset in `training/train.py`. The synthetic dataset includes:
- 95% "normal" transactions (typical daytime hours, smaller amounts, common types like DEPOSIT or PAYMENT).
- 5% "anomalies" (middle of the night, very large amounts, types like TRANSFER).

This demonstrates the end-to-end ML pipeline, but the model should *not* be considered a production-ready fraud detector.

## Feature Engineering
The features used for the model include:
- `amount`: Raw transaction amount.
- `amount_log`: Log-transformed amount (`log(1 + amount)`) because financial transaction amounts are heavily skewed.
- `hour_of_day` & `day_of_week`: Derived from `createdAt`.
- `type_encoded`: Ordinal encoding of the transaction type.

## Limitations and Future Work
- The model uses request-level features only. In a real-world scenario, historical aggregates (e.g., "count of transactions in the last hour for this account") are critical for fraud detection.
- We lack an MLOps pipeline (model registry, drift detection, automated retraining).
- Service-to-service authentication (e.g., JWT or mTLS) is currently not implemented but is recommended before production deployment.

## Fallback Policy
If the ML service is unreachable, times out, or returns a 500 error, the Spring Boot consumer is configured to fail-safe by setting the transaction status to `FLAGGED`. This ensures suspicious or unscorable transactions are manually reviewed rather than silently `APPROVED`.
