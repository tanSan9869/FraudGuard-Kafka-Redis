import pandas as pd
import numpy as np

def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Given a dataframe with raw transaction fields, compute the features
    used by the Isolation Forest model.
    """
    df = df.copy()
    
    # 1. Log transform amount (add 1 to avoid log(0))
    df['amount_log'] = np.log1p(df['amount'])
    
    # 2. Time features from createdAt
    if 'createdAt' in df.columns:
        df['createdAt'] = pd.to_datetime(df['createdAt'])
        df['hour_of_day'] = df['createdAt'].dt.hour
        df['day_of_week'] = df['createdAt'].dt.dayofweek
    else:
        df['hour_of_day'] = 12
        df['day_of_week'] = 3
        
    # 3. Transaction type
    type_mapping = {
        'DEPOSIT': 1,
        'WITHDRAWAL': 2,
        'TRANSFER': 3,
        'PAYMENT': 4,
        'PURCHASE': 4,
        'REFUND': 1
    }
    df['type_encoded'] = df['transactionType'].map(type_mapping).fillna(0)
    
    features = ['amount', 'amount_log', 'hour_of_day', 'day_of_week', 'type_encoded']
    return df[features]
