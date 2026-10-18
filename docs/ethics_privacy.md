# Ethics, privacy and scope

Real collection requires explicit user consent before hooks or a database session start. Only derived timing, counts, geometry and app categories are stored. Native key codes are transiently categorized; typed text, window titles, clipboard and screenshots are not persisted by the app. Foreground executable names are used transiently for categorization and switch counts.

The API runs on loopback and external Gemini rephrasing is disabled. Monitoring state remains visible; the app can be paused or quit. Deleting the current participant's data requires the app's explicit confirmation; restart after deletion before beginning another session. Database tests use in-memory H2. The audit does not test deletion against personal data.

This project is for noncommercial research under the SWELL download agreement. Preserve dataset attribution; do not redistribute raw data, derived participant records, local databases or models. The MIT source-code license is separate from dataset rights. Obtain appropriate consent and institutional review for volunteer studies.

Predictions estimate questionnaire scores. They do not diagnose stress or other conditions. Current grouped-CV results are worse than the mean-score baseline. UI alert preferences and activity/history heuristics have no demonstrated clinical validity.
