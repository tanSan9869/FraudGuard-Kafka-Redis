import pytest
from fastapi.testclient import TestClient
import pandas as pd
import sys
import os

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app.main import app
from app.features import engineer_features
from app.model import AnomalyModel

client = TestClient(app)

def test_engineer_features():
    df_raw = pd.DataFrame([{
        "amount": 100.0,
        "transactionType": "DEPOSIT",
        "createdAt": "2023-01-01T12:00:00Z"
    }])
    
    features = engineer_features(df_raw)
    
    assert "amount_log" in features.columns
    assert "hour_of_day" in features.columns
    assert features.iloc[0]["hour_of_day"] == 12
    assert features.iloc[0]["type_encoded"] == 1.0

def test_schema_validation():
    # Missing required fields should return 422
    response = client.post("/score", json={
        "amount": 100.0
    })
    assert response.status_code == 422
