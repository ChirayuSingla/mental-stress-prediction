# 4. Setup and reproduction (student laptop, all free tools)

## 4.1 Install
1. **Java 17 (Temurin/OpenJDK)** — https://adoptium.net → verify `java -version`.
2. **Maven 3.9** — https://maven.apache.org/download.cgi → add `bin` to PATH → `mvn -v`.
3. **Python 3.10+** — https://www.python.org → `python --version`.
4. **MySQL 8 Community** — https://dev.mysql.com/downloads/ (or skip and use embedded H2, see 4.3).
5. (Windows) nothing extra. (Linux) `sudo apt install xdotool` for foreground-app detection. (macOS) grant Accessibility permission to the Java process.

## 4.2 ML service
```bash
cd ml
python -m venv .venv && source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python synthetic/generate_synthetic.py --participants 20 --sessions 8 --out data/demo_synthetic.csv   # DEMO data
python training/train.py --data data/demo_synthetic.csv --out models/ --results results/               # Experiment 1 + saves model
python evaluation/run_experiments.py --data data/demo_synthetic.csv --results results/                 # Experiments 2–5 + graphs
export GEMINI_API_KEY=...        # optional, free tier; omit to use rule-based text only
uvicorn api.server:app --port 8000
```
Check http://127.0.0.1:8000/health and http://127.0.0.1:8000/docs.

## 4.3 Database
```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/sample_data.sql   # optional
```
Edit `java-app/src/main/resources/application.properties` (or drop an `application.properties` beside the jar, or set env vars
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `APP_MODE`, `APP_PARTICIPANT_CODE`). For a zero-install demo uncomment the H2 URL — the app creates the schema itself.

## 4.4 Java application
```bash
cd java-app
mvn clean test          # unit tests
mvn javafx:run          # run (mode from application.properties: demo | real)
mvn package && java -jar target/stress-prediction-app-1.0.0.jar   # shaded jar
```

## 4.5 Real-data study
1. Set `app.mode=real`, unique `app.participant.code` per volunteer, obtain consent (consent dialog + written form).
2. Volunteers complete ≥ 3 calibration sessions, then normal sessions; at the end of each session they tap Low/Moderate/High
   (or complete your chosen validated questionnaire) — this is the label.
3. Reports → Export CSV from every participant; concatenate; run `train.py` / `run_experiments.py` on the merged file
   (`data_mode` will be `REAL`). Results then populate the paper tables.

## 4.6 Tests
`cd ml && pytest` · `cd java-app && mvn test`
