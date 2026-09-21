# 5. Ethics, privacy and security

* **Consent**: explicit dialog before monitoring; written consent form for study participants; withdrawal at any time.
* **Data minimisation**: only timing/count/speed/category metadata. No characters, passwords, window titles, screenshots, clipboard.
  Application names are mapped to a category in memory and discarded.
* **Transparency**: monitoring state is always visible (ACTIVE / PAUSED); DEMO MODE banner when data is synthetic.
* **Control**: pause monitoring; delete all data (cascading delete of sessions, features, predictions, baseline).
* **Storage**: local MySQL/H2; pseudonymous participant codes; PBKDF2 password hashing if login is enabled; prepared statements everywhere.
* **Third parties**: only the optional Gemini call leaves the machine and it carries rule text + three aggregate numbers.
* **Scope statement** (in-app and in paper): behavioural research prediction tool; not a medical diagnosis of stress, depression,
  anxiety or burnout.
* **Institutional approval**: obtain your department/IRB ethics clearance before collecting data from volunteers; choose the
  self-report instrument (e.g. PSS-4, single-item stress VAS) with your supervisor — verify instrument licences.
* **Not spyware**: no hidden operation, no remote upload, no employer access; the user is the data owner.
