import joblib
import pandas as pd
import os

_ML_SERVICE_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
_DEFAULT_MODEL_PATH = os.path.join(_ML_SERVICE_ROOT, "models", "isoforest-v1.joblib")

class AnomalyModel:
    def __init__(self, model_path: str = _DEFAULT_MODEL_PATH):
        self.model_path = model_path
        self.model = None
        self.version = "isoforest-v1"
        self.threshold = 0.65

    def load(self):
        if not os.path.exists(self.model_path):
            raise FileNotFoundError(f"Model file not found at {self.model_path}")
        self.model = joblib.load(self.model_path)
        
    def predict(self, features_df: pd.DataFrame):
        if self.model is None:
            self.load()
            
        raw_score = self.model.decision_function(features_df)[0]
        
        # Invert score so higher is more anomalous
        anomaly_score = float(0.5 - raw_score)
        is_anomalous = anomaly_score >= self.threshold
        
        return {
            "anomalyScore": anomaly_score,
            "isAnomalous": is_anomalous,
            "threshold": self.threshold,
            "modelVersion": self.version
        }
