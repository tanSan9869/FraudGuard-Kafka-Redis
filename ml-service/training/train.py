# pyrefly: ignore [missing-source-for-stubs]
import pandas as pd
# pyrefly: ignore [missing-import]
import numpy as np
# pyrefly: ignore [missing-source-for-stubs]
from sklearn.ensemble import IsolationForest
# pyrefly: ignore [missing-import]
import joblib
import os
import sys

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.features import engineer_features

def generate_synthetic_data(n_samples=10000):
    np.random.seed(42)
    
    normal_amounts = np.random.lognormal(mean=3, sigma=1, size=n_samples)
    normal_hours = np.random.normal(loc=14, scale=3, size=n_samples) % 24
    normal_days = np.random.randint(0, 5, size=n_samples)
    normal_types = np.random.choice(['DEPOSIT', 'WITHDRAWAL', 'PAYMENT'], size=n_samples, p=[0.2, 0.4, 0.4])
    
    n_fraud = int(n_samples * 0.05)
    fraud_amounts = np.random.lognormal(mean=8, sigma=2, size=n_fraud)
    fraud_hours = np.random.normal(loc=3, scale=1, size=n_fraud) % 24
    fraud_days = np.random.randint(5, 7, size=n_fraud)
    fraud_types = np.random.choice(['TRANSFER', 'WITHDRAWAL'], size=n_fraud, p=[0.7, 0.3])
    
    amounts = np.concatenate([normal_amounts, fraud_amounts])
    hours = np.concatenate([normal_hours, fraud_hours])
    days = np.concatenate([normal_days, fraud_days])
    types = np.concatenate([normal_types, fraud_types])
    
    dates = [pd.Timestamp('2023-01-01') + pd.Timedelta(hours=int(h), days=int(d)) for h, d in zip(hours, days)]
    
    df = pd.DataFrame({
        'amount': amounts,
        'createdAt': dates,
        'transactionType': types
    })
    
    return df

def train():
    print("Generating synthetic data...")
    df = generate_synthetic_data(10000)
    
    print("Engineering features...")
    X = engineer_features(df)
    
    print("Training Isolation Forest model...")
    clf = IsolationForest(n_estimators=100, contamination=0.05, random_state=42)
    clf.fit(X)
    
    os.makedirs(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'models'), exist_ok=True)
    
    model_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'models', 'isoforest-v1.joblib')
    print(f"Saving model to {model_path}...")
    joblib.dump(clf, model_path)
    print("Training complete!")

if __name__ == "__main__":
    train()
