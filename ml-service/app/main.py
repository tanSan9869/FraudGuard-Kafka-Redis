from fastapi import FastAPI, HTTPException
import pandas as pd
from app.schemas import TransactionRequest, ScoreResponse
from app.model import AnomalyModel
from app.features import engineer_features
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="FraudGuard ML Service")

model = AnomalyModel()

@app.on_event("startup")
async def startup_event():
    logger.info("Loading ML Model...")
    try:
        model.load()
        logger.info(f"Model {model.version} loaded successfully.")
    except Exception as e:
        logger.error(f"Failed to load model: {e}")

@app.get("/")
def root():
    return {"service": "FraudGuard ML", "health": "/health", "score": "/score", "docs": "/docs"}

@app.get("/health")
def health_check():
    if model.model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    return {"status": "up", "model": model.version}

@app.post("/score", response_model=ScoreResponse)
def score_transaction(request: TransactionRequest):
    try:
        if model.model is None:
            raise HTTPException(status_code=503, detail="Model not loaded")
            
        df_raw = pd.DataFrame([request.model_dump()])
        features_df = engineer_features(df_raw)
        result = model.predict(features_df)
        
        return ScoreResponse(
            transactionId=request.id,
            anomalyScore=result["anomalyScore"],
            isAnomalous=result["isAnomalous"],
            threshold=result["threshold"],
            modelVersion=result["modelVersion"]
        )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error scoring transaction {request.id}: {str(e)}")
        raise HTTPException(status_code=500, detail="Internal server error during scoring")
