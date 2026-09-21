# Mental Stress Prediction Using Typing Patterns, Mouse Movements, and Application Usage

A privacy-preserving, personalised, multimodal behavioural stress-estimation research system.  
Built with **Java 17 + JavaFX** desktop application, **Python (scikit-learn / XGBoost / SHAP / FastAPI)** machine-learning service, and **embedded H2 / MySQL 8** storage.

> **Research Reference:** Extends the framework of:  
> Naegelin et al., *"An interpretable machine learning approach to multimodal stress detection in a simulated office environment."* *Journal of Biomedical Informatics*, 139:104299, 2023.

> **Disclaimer:** This software is a behavioural research and estimation tool. It does **not** diagnose, treat, or clinically evaluate stress, fatigue, burnout, depression, anxiety, or any medical condition.

---

## Table of Contents
1. [Core Concept & Architectural Overview](#core-concept--architectural-overview)
2. [Background Monitoring & High-Stress Alert Popups](#background-monitoring--high-stress-alert-popups)
3. [Codebase Structure & Key Components](#codebase-structure--key-components)
4. [Prerequisites](#prerequisites)
5. [Step-by-Step Terminal Instructions (How to Run)](#step-by-step-terminal-instructions-how-to-run)
6. [How to Train the Models & Run Experiments](#how-to-train-the-models--run-experiments)
7. [How the Code Works (Under the Hood)](#how-the-code-works-under-the-hood)
8. [What Should Be Your Next Steps](#what-should-be-your-next-steps)

---

## Core Concept & Architectural Overview

Standard stress detection models often fall into the **"one-fits-all" trap**: an individual who naturally types at 80 WPM with high variability might be misclassified as stressed, while an individual who normally types at 35 WPM might not trigger any alert even under acute pressure.

### Personalised Baseline Innovation
This system learns each user's unique behavioural baseline during their initial calibration sessions (default: first 3 sessions or 30 windows). For all subsequent windows, the system extracts **deviation-from-baseline** features (`dev_*` and `z_*`) alongside absolute metrics:
- Relative percentage shift: $\text{dev}(x) = \frac{x - \mu_{\text{base}}}{\mu_{\text{base}}}$
- Standardised Z-score shift: $z(x) = \frac{x - \mu_{\text{base}}}{\sigma_{\text{base}}}$

### Privacy-Preserving Architecture
- **No Keystroke Content:** Keystrokes are captured strictly as anonymous timestamps and durations (hold time, inter-key latency). Key values and typed text are discarded immediately in memory.
- **No Window Titles / URLs:** Active processes are mapped to coarse categories (`IDE`, `BROWSER`, `DOCUMENT`, `COMMUNICATION`, `ENTERTAINMENT`, `OTHER`). Process names and titles are discarded.
- **No Screen Recording / Video:** Only physics-inspired movement metadata (mouse speed, acceleration, direction changes, clicks) is collected.

```
+-----------------------------------------------------------------------------------+
|                        JavaFX Desktop App (Java 17)                               |
|                                                                                   |
|  [Keyboard Monitor]     [Mouse Monitor]      [Application Monitor]                |
|  (Timestamps only)      (Speed, Jerk, Clicks) (Process -> Category)               |
|            \                   |                    /                             |
|             +------------------+-------------------+                              |
|                                | (every 60s)                                      |
|                                v                                                  |
|                     [Feature Aggregator] (26 absolute metrics)                    |
|                                |                                                  |
|                                v                                                  |
|                     [Personal Baseline Engine] (18 deviation metrics)             |
|                                |                                                  |
|                                v (HTTP POST /predict)                             |
+--------------------------------|--------------------------------------------------+
                                 |
                                 v
+-----------------------------------------------------------------------------------+
|                        FastAPI ML Service (Python 3.10+)                          |
|                                                                                   |
|  1. Preprocessing Pipeline (One-hot encoding + standard scaler)                   |
|  2. Trained Classifier (RandomForest / XGBoost) -> Stress Probability & Score     |
|  3. SHAP TreeExplainer -> Top contributing factors with human-readable reasoning  |
|  4. Composite Fatigue Index (0-100) & Longitudinal Burnout Risk Indicator         |
|  5. Contextual Recommendation Engine (Rule-based + optional Gemini AI phrasing)   |
+--------------------------------|--------------------------------------------------+
                                 |
                                 v (JSON Response)
+-----------------------------------------------------------------------------------+
|                     JavaFX Dashboard & Local Database (H2 / MySQL)                |
|                                                                                   |
|  - Real-time Stress Gauges & Fatigue/Burnout Warning Indicators                   |
|  - Feature Breakdown & SHAP Factor Explanations                                   |
|  - Session History, Trend Charts, and CSV/PDF Report Exporter                     |
+-----------------------------------------------------------------------------------+
```

---

## Background Monitoring & High-Stress Alert Popups

The desktop application is engineered for non-disruptive, real-time background operation:

### 1. Seamless Background Execution & System Tray
- **No Interruption on Window Close:** When the user clicks the `[X]` close button on the desktop window, the app does not terminate. Instead, it minimizes to the Windows System Tray and continues monitoring behavioural telemetry in the background.
- **System Tray Controls:** Right-clicking the tray icon provides instant actions:
  - **Open Dashboard:** Restores the main window to the front.
  - **Take a 5-Min Break:** Logs an instant micro-break and resets continuous work fatigue counters.
  - **Pause / Resume Monitoring:** Temporarily toggles telemetry capture without closing the app.
  - **Exit Application:** Fully shuts down background threads, saves session state, and closes the application.
- **Quit via In-App Settings:** Inside the application, the **Settings** view also provides an explicit **"Quit & Exit Application"** button.

### 2. High-Stress Floating Alert Popups & OS Notifications
Whenever the ML model detects elevated or high stress ($\text{Stress Score} \ge 60$ or classification = High Stress):
- **Windows Action Center Notification:** A native Windows notification balloon is issued via `SystemTray.displayMessage` with the current stress score and a concise recommendation.
- **Floating On-Screen Alert Card:** A non-modal, top-level JavaFX popup (`HighStressPopup`) appears in the bottom-right corner of the primary display. It provides:
  - Warning badge and exact score (e.g. `⚠️ HIGH STRESS DETECTED (78/100)`).
  - Primary contributing factor identified by SHAP (e.g. `Key factor: typing rhythm variability = 0.42`).
  - Contextual recommendation (e.g. `Take a 5-minute break and stretch`).
  - Action buttons: `[ ☕ Take Break ]` (logs break and auto-dismisses), `[ 📊 Dashboard ]` (restores main window), and `[ ✕ Dismiss ]`.
  - Auto-dismiss countdown (25 seconds) so it never lingers indefinitely.
- **Debounce Cooldown:** Configured via `notification.cooldown.seconds` (default: 20s in demo mode, 300s in real-data mode) in `application.properties` to ensure the user is not spammed every minute.

### 3. Screen-Safe Window Centering & Bezel Boundaries
- The main window queries the primary display's visual working bounds (`Screen.getPrimary().getVisualBounds()`), strictly accounting for Windows taskbars and display scaling (100%, 125%, 150%).
- The window is centered on screen so that it never crosses top or bottom bezels.
- Content views are wrapped in an edge-to-edge transparent `ScrollPane`, ensuring that charts, cards, and recommendations stay navigable and never overflow off-screen on smaller laptop screens.

---

## Codebase Structure & Key Components

```
mental-stress-prediction/
├── java-app/                         # JavaFX Desktop Application (Java 17)
│   ├── src/main/java/com/stressml/
│   │   ├── monitor/                  # JNativeHook & JNA hooks (Keyboard, Mouse, ActiveWindow)
│   │   ├── feature/                  # FeatureAggregator: rolling 60s window accumulator
│   │   ├── baseline/                 # BaselineEngine: calibration, mean/std/IQR profiles
│   │   ├── ml/                       # MlClient: HTTP/1.1 client connecting to FastAPI
│   │   ├── db/                       # DatabaseManager (HikariCP connection pool, H2/MySQL)
│   │   ├── recommendation/           # Local fallback rule recommendation engine
│   │   ├── ui/                       # JavaFX Views (Dashboard, Behaviour, History, Explanation, Reports)
│   │   ├── App.java                  # JavaFX Application entry point
│   │   └── Launcher.java             # Fat-jar bootstrap launcher
│   ├── src/main/resources/
│   │   ├── application.properties    # App mode, database URL, polling intervals
│   │   ├── schema.sql                # Relational schema (tables, foreign keys, indices)
│   │   └── styles.css                # UI design stylesheet
│   └── pom.xml                       # Maven build configuration
│
├── ml/                               # Machine Learning Service (Python 3.10+)
│   ├── api/
│   │   ├── server.py                 # FastAPI prediction & health service (/health, /predict)
│   │   └── recommendation.py         # Rule-based recommendations + optional Gemini rephraser
│   ├── synthetic/
│   │   └── generate_synthetic.py     # Demo generator simulating 20 participants with baseline drift
│   ├── preprocessing/
│   │   ├── features.py               # Feature schemas (Absolute, Deviation, Groups A-F)
│   │   ├── baseline.py               # Training-time baseline profile calculation
│   │   └── preprocess.py             # Data cleaning, one-hot encoding, matrix builder
│   ├── training/
│   │   ├── models.py                 # 6 candidate pipelines (LR, DT, RF, SVM, XGB, MLP)
│   │   └── train.py                  # Experiment 1: GroupKFold CV, model selection, serialization
│   ├── evaluation/
│   │   ├── plots.py                  # Matplotlib/Seaborn visualization routines
│   │   └── run_experiments.py        # Experiments 2–5 (ablation, generic vs personal, SHAP, trends)
│   ├── tests/
│   │   └── test_pipeline.py          # Pytest suite testing baseline, fatigue, and API contracts
│   ├── config.yaml                   # Model seeds, CV folds, baseline session count
│   └── requirements.txt              # Python dependencies
│
├── database/                         # Database schema & sample seed data
│   ├── schema.sql                    # Normalized 3NF MySQL / H2 schema
│   ├── sample_data.sql               # Seed records for demonstration
│   └── er_diagram.md                 # Entity-Relationship diagram
│
└── docs/                             # Full Academic Documentation
    ├── architecture.md               # Data flow & component interactions
    ├── methodology.md                # Mathematical formulas & feature engineering details
    ├── experiments.md                # Experiments 1–5 methodology & results
    ├── ethics_privacy.md             # Consent protocol & privacy safeguards
    ├── research_paper_sections_3_to_6.md # Ready-to-use research paper sections
    ├── review2_ppt_outline.md        # Presentation slide-by-slide structure
    └── viva_questions.md             # 25+ viva questions with precise academic answers
```

---

## Prerequisites

Before running, make sure the following are installed:
1. **Python 3.10+**: Run `python --version`
2. **Java 17 LTS**: Run `java -version` (e.g., Eclipse Adoptium Temurin 17)
3. **Apache Maven 3.9+**: Run `mvn -version`

---

## Step-by-Step Terminal Instructions (How to Run)

The application operates as two collaborative processes:
- **Terminal 1:** Python FastAPI ML service (`http://127.0.0.1:8000`)
- **Terminal 2:** JavaFX desktop application

### Terminal 1: Start the Python ML Service

Open a **PowerShell** terminal in the project root:

```powershell
# 1. Enter the ml directory
cd ml

# 2. (First-time setup only) Create virtual environment & install packages
python -m venv .venv
.\.venv\Scripts\pip.exe install -r requirements.txt

# 3. Start the FastAPI server (choose either 3A or 3B)

# Option 3A: Direct execution (recommended, avoids PowerShell script execution policy issues)
.\.venv\Scripts\uvicorn.exe api.server:app --host 127.0.0.1 --port 8000

# Option 3B: If activating the environment first
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.venv\Scripts\Activate.ps1
uvicorn api.server:app --host 127.0.0.1 --port 8000
```

#### Verification:
- Open your browser to `http://127.0.0.1:8000/health`. It will return:
  ```json
  {"status":"ok","model_loaded":true,"model":"RandomForest","data_mode":"SYNTHETIC_DEMO","gemini_enabled":false}
  ```
- Interactive OpenAPI documentation is available at `http://127.0.0.1:8000/docs`.

---

### Terminal 2: Start the JavaFX Desktop App

Open a **second PowerShell** terminal in the project root:

```powershell
# 1. Set Java 17 and Maven in PATH for this session (adjust paths if needed)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;C:\Users\chirayu singla\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin;$env:Path"

# 2. Enter the java-app directory
cd java-app

# 3. Launch via Maven JavaFX plugin
mvn javafx:run
```

#### Alternative: Launch the Packaged Standalone JAR
If you do not want to launch through Maven every time:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

cd java-app
java -jar target/stress-prediction-app-1.0.0.jar
```

The desktop UI window **"Behavioural Stress Monitor — DEMO MODE"** will open.

---

## How to Train the Models & Run Experiments

All machine-learning scripts are located in `ml/`. Activate your virtual environment first:

```powershell
cd ml
.\.venv\Scripts\Activate.ps1
```

### 1. Generate the Synthetic Dataset
Generates realistic behavioural patterns across 20 participants with individual baseline calibration and stress response shifts:
```powershell
python synthetic/generate_synthetic.py --participants 20 --sessions 8 --windows 30 --out data/demo_synthetic.csv
```
*Output:* `ml/data/demo_synthetic.csv` (4,800 windows).

### 2. Experiment 1: Train & Compare Candidate Models
Trains 6 candidate algorithms (`LogisticRegression`, `DecisionTree`, `RandomForest`, `SVM`, `XGBoost`, `MLP`) using **GroupKFold cross-validation** (grouping by participant to prevent data leakage):
```powershell
python training/train.py --data data/demo_synthetic.csv --out models/ --results results/
```
*Outputs:*
- Best model saved to `ml/models/stress_model.joblib`
- Metadata saved to `ml/models/model_meta.json`
- Baseline profiles saved to `ml/models/baseline_profiles.json`
- Comparison table & chart: `ml/results/exp1_model_comparison.csv` and `.png`
- Confusion matrix: `ml/results/exp1_confusion_matrix.csv` and `.png`

### 3. Experiments 2–5: Ablation, Explainability & Longitudinal Trends
Runs the complete scientific evaluation suite:
```powershell
python evaluation/run_experiments.py --data data/demo_synthetic.csv --results results/
```
*Outputs generated in `ml/results/`:*
- **Experiment 2 (Feature Group Ablation):** Evaluates Keyboard alone, Mouse alone, App alone, Keyboard+Mouse, and Full Multimodal. Shows why multimodal fusion outperforms unimodal baselines (`exp2_feature_groups.png`).
- **Experiment 3 (Generic vs. Personalised):** Directly compares absolute features alone vs. absolute + personal baseline deviation features (`exp3_personalised_vs_generic.png`).
- **Experiment 4 (Feature Importance & SHAP):** Produces Random Forest Gini impurity importance, permutation importance, and SHAP summary plots for high stress (`exp4_feature_importance_rf.png`, `exp4_shap_summary_high.png`).
- **Experiment 5 (Stress Trajectory):** Maps multi-session stress scores to visualize session fatigue and longitudinal patterns (`exp5_stress_trend.png`).

### 4. Run Automated Test Suites
```powershell
# Python tests (ML pipeline, baseline maths, fatigue & burnout bounds)
pytest tests/

# Java tests (BaselineEngine, FeatureAggregator, DatabaseManager, Recommender)
cd ../java-app
mvn test
```

---

## How the Code Works (Under the Hood)

### 1. Real-Time Feature Aggregation
Every 60 seconds, `FeatureAggregator` computes 26 absolute behavioural indicators:
- **Keyboard Dynamics:** Typing speed (WPM), keystrokes/min, mean key hold time (ms), key-to-key latency (ms), backspace rate, error rate, pause count, mean pause length, typing bursts, typing rhythm CV.
- **Mouse Kinematics:** Distance travelled (px), mean speed (px/s), acceleration ($\text{px}/\text{s}^2$), click rate, double-click rate, mean click distance, idle time, direction changes (trajectory jerkiness), speed CV.
- **Application & Context:** Active application category (`IDE`, `BROWSER`, `DOCUMENT`, etc.), time in active app, switch count, switch rate, continuous work duration, idle duration.

### 2. Personal Baseline Calibration
`BaselineEngine` computes mean $\mu$ and standard deviation $\sigma$ across 9 critical metrics during calibration sessions. For every subsequent monitoring window, it produces 18 normalized features:
- `dev_typing_speed_wpm`, `z_typing_speed_wpm`
- `dev_avg_key_hold_ms`, `z_avg_key_hold_ms`
- `dev_avg_key_latency_ms`, `z_avg_key_latency_ms`
- `dev_error_rate`, `z_error_rate`
- `dev_mouse_speed_px_s`, `z_mouse_speed_px_s`
- `dev_click_rate`, `z_click_rate`
- `dev_app_switch_rate`, `z_app_switch_rate`
- `dev_idle_duration_s`, `z_idle_duration_s`
- `dev_continuous_work_s`, `z_continuous_work_s`

### 3. Interpretable Machine Learning & Explainability
1. **Classification:** RandomForest or XGBoost outputs class probabilities: $P(\text{Low})$, $P(\text{Moderate})$, $P(\text{High})$.
2. **Stress Score:** Continuous expected value index:
   $$\text{Stress Score} = 100 \times \left(0 \cdot P(\text{Low}) + 0.5 \cdot P(\text{Moderate}) + 1.0 \cdot P(\text{High})\right)$$
3. **Fatigue Index:** Rule-based composite (0–100):
   $$\text{Fatigue} = 40 \cdot \min\left(\frac{t_{\text{work}}}{2\text{h}}, 1\right) + 20 \cdot \max(0, -\text{dev}_{\text{wpm}}) + 15 \cdot \max(0, \text{dev}_{\text{err}}) + 15 \cdot \max(0, \text{dev}_{\text{lat}}) + 10 \cdot \text{idle\_drop}$$
4. **Burnout Risk Indicator:** Analyzes multi-session longitudinal history (stress persistence across last 10 sessions, trend slope, proportion of prolonged work without breaks).
5. **SHAP Factor Attribution:** Computes exact Tree SHAP values, ranking the top features driving the current prediction with clear explanations (e.g., *"cursor direction changes = 50 (increases stress)"*).

---

## What Should Be Your Next Steps

### Step 1: Explore the Desktop App Features in Demo Mode
1. Click through the sidebar views:
   - **Dashboard:** Observe the live stress dial, probability distribution, and fatigue index.
   - **Behaviour:** Review raw keyboard, mouse, and application usage telemetry.
   - **Explanation:** Inspect the real-time SHAP factor breakdown explaining *why* the model made its decision.
   - **History & Reports:** Test exporting session data to CSV and generating study summary reports.
2. Review the generated graphs in `ml/results/` to understand the model performance and ablation insights.

### Step 2: (Optional) Enable Google Gemini Free Tier Rephrasing
The recommendation engine uses rule-based logic by default. If you want empathetic natural-language explanations:
1. Obtain a free API key from [Google AI Studio](https://aistudio.google.com/).
2. In Terminal 1 before starting `uvicorn`, set the environment variable:
   ```powershell
   $env:GEMINI_API_KEY = "your-api-key-here"
   uvicorn api.server:app --host 127.0.0.1 --port 8000
   ```
   *(Note: No keystrokes or sensitive raw data are ever transmitted; only aggregate anonymous numbers and rule texts are sent).*

### Step 3: Transitioning from DEMO Mode to Real-Data Collection
When you are ready to conduct a participant study or collect your own behavioural data:
1. Open `java-app/src/main/resources/application.properties` and change:
   ```properties
   app.mode=real
   app.participant.code=P101
   ```
2. When launched in `real` mode, the app presents an **Informed Consent Dialog** detailing privacy guarantees.
3. The participant completes at least 3 calibration sessions to establish their personal baseline.
4. At the end of each session, the participant enters a brief validated self-report score (e.g., PSS-4 or visual analog scale 0–2).
5. Use **Reports -> Export CSV** from each participant, concatenate the files, and run `python training/train.py --data your_real_data.csv` to train models on real data.

### Step 4: Prepare Academic Deliverables (Paper, PPT, Viva)
The `docs/` folder contains comprehensive materials for submission and defense:
- **Research Paper Sections:** [`docs/research_paper_sections_3_to_6.md`](file:///docs/research_paper_sections_3_to_6.md) contains ready-written methodology, experimental setup, results, and discussion with LaTeX table templates.
- **Presentation Outline:** [`docs/review2_ppt_outline.md`](file:///docs/review2_ppt_outline.md) provides a slide-by-slide guide with speaking points for project evaluations.
- **Viva Voce Preparation:** [`docs/viva_questions.md`](file:///docs/viva_questions.md) contains 25+ technical viva questions covering data leakage prevention, SHAP mechanics, GroupKFold reasoning, and privacy engineering.
