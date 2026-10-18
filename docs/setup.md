# Setup

The active pipeline is real SWELL questionnaire-score regression. Follow [README.md](../README.md) for verified PowerShell commands, the existing virtual environment and Java socket workaround. Run preprocessing, training, experiments, Python tests, backend startup, Java tests, then JavaFX startup in that order.

The former synthetic training commands are obsolete. Synthetic generators are available only for explicitly labeled UI demonstrations and tests; the real training path rejects synthetic records. Do not replace or reset the H2 database.
