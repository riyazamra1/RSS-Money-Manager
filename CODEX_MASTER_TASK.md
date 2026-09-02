# RSS Money Manager — Autonomous Codex Master Task

## Objective
Complete this repository as a production-ready RSS Money Manager Android application. Work autonomously from the current repository state. Inspect first, implement incrementally, build/test after each major phase, fix failures, and continue until all requirements are implemented.

## Non-negotiable entry screen
The transaction entry screen must be clean and use this exact order:

1. Date / Time / Recurring icon
2. Count — default `1`
3. Amount — unit amount
4. Total — read-only, automatically calculated as `Count × Amount`
5. Description
6. Items — optional; completely hidden when there are no items
7. Category — list selection
8. Wallet — list selection
9. Separator
10. Memo + camera/gallery photo attachment

Example: Count `5`, Amount `25`, Total `125`.

## Items
- Optional, not mandatory.
- When one or more items exist, show the Items section and expose item information on the dashboard.
- Item fields: product name, quantity/count, unit price, line total.
- Calculate line totals exactly.
- Support adding, editing, removing and reordering items.
- Validate item totals and transaction totals.

## Product scope
Implement all of the following while preserving a coherent architecture:

- RSS splash/logo and onboarding
- dashboard/home
- expense, income and transfer
- accounts/wallets
- categories/subcategories
- multiple currencies
- transaction history, search and filters
- reports and charts
- budgets
- savings goals
- debt tracking
- recurring transactions
- memo and receipt/product photos
- Smart Memory category learning
- PIN/biometric security
- local backup/restore
- SD/USB backup using Storage Access Framework
- Google Drive backup/restore
- MEGA backup/restore when credentials/configuration are available
- optional Firebase Auth/Firestore/Storage/Crashlytics/FCM
- CSV/Excel export
- settings, appearance, notifications and accessibility
- tests, error states and release build

## Smart Memory
Learn mappings from transaction Description to Category/Subcategory locally. Example: `eggs 🥚` → `House Expenses`.

Normalize case, whitespace and emoji variation. Support partial matches and corrections. Maintain usage count/confidence and prefer stronger recent mappings. Never send learning data to a cloud service unless the user explicitly enables backup/sync.

## Architecture
Use the existing project as the starting point. Prefer Kotlin, Jetpack Compose, MVVM/Clean Architecture, Room, Coroutines/Flow, DataStore, Navigation Compose, Hilt and Kotlin Serialization where compatible with the existing build. Avoid unnecessary dependency churn.

Room is the offline-first source of truth. Use exact monetary arithmetic (integer minor units or another decimal-safe representation), never persisted binary floating point.

## Security
Never commit API keys, passwords, OAuth secrets, Firebase service-account keys, signing keys/keystores, `.env` files, user PINs, financial databases, backups, or private credentials.

## Git and verification
- Keep commits focused and descriptive.
- Never force-push or rewrite history.
- Run formatting/lint/tests and the Android build.
- Fix compile/test failures before proceeding.
- Do not call a phase complete without verification evidence.
- Keep generated build artifacts out of source control.

## Autonomous execution policy
Do not stop to ask routine design or implementation questions. Make reasonable engineering decisions consistent with this document and existing repository conventions. Only stop when a protected human action is genuinely required, such as supplying a secret/credential, approving a paid service, or handling a signing key.

## Final acceptance
The app must compile successfully, tests must pass, the entry flow must implement Count/Amount/Total correctly, optional Items must behave as specified, Smart Memory must work locally, and all implemented features must have appropriate empty/loading/error states. Update README/status documentation with the actual completed state.
