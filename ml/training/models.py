"""Candidate models. Each is a Pipeline with a scaler so CV folds never leak scaling statistics."""
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.tree import DecisionTreeClassifier
from sklearn.ensemble import RandomForestClassifier
from sklearn.svm import SVC
from sklearn.neural_network import MLPClassifier
from xgboost import XGBClassifier


def candidate_models(seed=42, include_mlp=True):
    models = {
        "LogisticRegression": LogisticRegression(max_iter=2000, C=1.0, random_state=seed),
        "DecisionTree": DecisionTreeClassifier(max_depth=8, min_samples_leaf=10, random_state=seed),
        "RandomForest": RandomForestClassifier(n_estimators=300, min_samples_leaf=3, n_jobs=-1, random_state=seed),
        "SVM": SVC(C=2.0, kernel="rbf", probability=True, random_state=seed),
        "XGBoost": XGBClassifier(n_estimators=300, max_depth=5, learning_rate=0.05, subsample=0.9,
                                 colsample_bytree=0.9, eval_metric="mlogloss", random_state=seed, n_jobs=-1),
    }
    if include_mlp:
        models["MLP"] = MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=600, early_stopping=True, random_state=seed)
    return {name: Pipeline([("scaler", StandardScaler()), ("clf", est)]) for name, est in models.items()}
