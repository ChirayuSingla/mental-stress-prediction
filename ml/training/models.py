"""Candidate regression models for SWELL-KW continuous stress prediction.

Each model is wrapped in a scikit-learn Pipeline with a StandardScaler so that
scaling parameters are fitted strictly on the training fold during cross-validation,
completely preventing preprocessing leakage into validation folds.
"""

from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer
from sklearn.dummy import DummyRegressor
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import Ridge, LinearRegression
from sklearn.tree import DecisionTreeRegressor
from sklearn.ensemble import RandomForestRegressor
from sklearn.svm import SVR
from sklearn.neural_network import MLPRegressor
from xgboost import XGBRegressor


def candidate_regressors(seed: int = 42, include_mlp: bool = True):
    """Return dictionary of regression candidate pipelines.

    Models represent the continuous regression counterparts of the project's
    candidate algorithm family (Linear/Ridge, DecisionTree, RandomForest,
    SupportVectorRegression, GradientBoostedTrees/XGBoost, and MultiLayerPerceptron).
    """
    models = {
        "DummyMean": DummyRegressor(strategy="mean"),
        "Ridge": Ridge(alpha=1.0, random_state=seed),
        "DecisionTree": DecisionTreeRegressor(max_depth=4, min_samples_leaf=3, random_state=seed),
        "RandomForest": RandomForestRegressor(
            n_estimators=200, min_samples_leaf=2, max_depth=6, n_jobs=-1, random_state=seed
        ),
        "SVR": SVR(C=2.0, epsilon=0.2, kernel="rbf"),
        "XGBoost": XGBRegressor(
            n_estimators=100,
            max_depth=3,
            learning_rate=0.05,
            subsample=0.85,
            colsample_bytree=0.85,
            random_state=seed,
            n_jobs=-1,
        ),
    }

    if include_mlp:
        models["MLP"] = MLPRegressor(
            hidden_layer_sizes=(32, 16),
            max_iter=800,
            learning_rate_init=0.01,
            random_state=seed,
        )

    return {
        name: Pipeline([("imputer", SimpleImputer(strategy="median", keep_empty_features=True)),
                        ("scaler", StandardScaler()), ("reg", est)])
        for name, est in models.items()
    }


# candidate_models alias for seamless backwards compatibility
def candidate_models(seed: int = 42, include_mlp: bool = True):
    return candidate_regressors(seed=seed, include_mlp=include_mlp)
