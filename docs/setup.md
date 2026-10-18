# Reproducible Windows setup

Follow [README](../README.md). Use Python 3.10 and ml/requirements-lock.txt to recreate the tested dependency environment; preserve an existing ml/.venv. The licensed SWELL workbook is external and must be supplied via --input.

Run preprocessing, training, Python tests, then scripts/run-backend.ps1 from the root. Set Java 17/Maven on PATH and run scripts/run-java.ps1. It chooses the correct working directory and short workspace socket path. Consent is required for real collection.

Build with mvn package. The optional flags -Dstressml.integration=true and -Djavafx.ui.tests=true enable the real HTTP/JavaFX tests. Tests use in-memory H2. scripts/run-java.ps1 -Packaged launches the JAR; -Demo is explicitly synthetic and installs no real hooks.

Do not reset the H2 database, import sample_data.sql over personal data, or train real experiments from synthetic/ordinal desktop exports.
