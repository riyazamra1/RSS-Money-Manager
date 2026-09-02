# RSS Money Manager — Codex Instructions

## Mission
Build RSS Money Manager into a production-ready native Android personal finance app. Work autonomously through the project backlog, implementing, testing, fixing, and documenting each phase. Do not stop for routine questions.

## Project identity
- App: RSS Money Manager
- Application ID: `com.riyaz.rssmoneymanager`
- Package/namespace: `com.riyaz.rssmoneymanager`
- Native Android/Kotlin.
- Preserve existing RSS branding/assets exactly where they already exist. Never stretch, recolor, redraw, or redesign the RSS logo unless an explicit requirement says so.
- Clean, lightweight, modern UI. Light appearance is the default; support system/dark appearance where implemented.

## Engineering rules
1. Inspect the existing project before changing architecture or Gradle versions.
2. Preserve compatible existing versions unless a dependency requires a controlled upgrade.
3. Prefer Kotlin, Jetpack Compose where practical, MVVM/Clean Architecture, Room, Coroutines/Flow, DataStore, Navigation Compose, Hilt, and Kotlin Serialization.
4. Keep Room as the offline-first source of truth. Cloud services are optional synchronization/backup layers, never the only local data store.
5. Use precise decimal-safe money calculations. Do not use binary floating point for persisted monetary values; use integer minor units or an equivalent exact representation.
6. Every major feature must have tests. Run the Gradle build and relevant tests after changes and fix failures before continuing.
7. Do not commit secrets, API keys, Firebase service-account keys, signing keystores, passwords, tokens, financial databases, backups, or user data.
8. Do not delete production data or rewrite Git history.
9. Use focused commits and keep the repository buildable.
10. Continue through all backlog phases until the app is complete or a genuine protected human action is required.

## Entry page — mandatory specification
Order:
1. Date / Time / Recurring with icon
2. Count — default `1`
3. Amount — unit price
4. Total — read-only calculated field: `Count × Amount`
5. Description — short text
6. Items — optional and hidden when empty
7. Category — list view
8. Wallet — list view
9. Separator
10. Memo + camera/gallery photo attachment

Example: Count `5`, Amount `25`, Total `125`.

Normal transaction without items must not reserve permanent item-section space.

## Items specification
Items are optional. When at least one item exists, show the Items section and surface item information on the dashboard. Each item supports product name, quantity/count, unit price, and calculated line total. The transaction total can be derived from item line totals when items are used, while still respecting the explicit transaction amount model and validation rules.

## Smart Memory
Learn description-to-category mappings locally. Example: `eggs 🥚` → `House Expenses`. Normalize case, spacing and emoji variations; support partial matches; learn from corrections; use confidence and usage counts. Keep learning local unless the user explicitly enables backup/sync.

## Required product scope
Implement and integrate, in a coherent order:
- splash/branding and onboarding
- dashboard
- income, expense and transfer transactions
- count, amount and automatic total calculation
- optional line items
- accounts/wallets
- categories/subcategories
- multiple currencies
- transaction history/search/filter
- reports/charts
- budgets
- savings goals
- debt manager
- recurring transactions
- memo/photo attachments
- local Smart Memory
- PIN and biometric protection
- local backup/restore
- SD/USB backup through Storage Access Framework
- Google Drive backup/restore
- MEGA backup/restore where credentials/configuration are available
- optional Firebase Auth/Firestore/Storage/Crashlytics/FCM with strict security rules
- CSV/Excel export
- settings, appearance, notifications and accessibility
- robust error handling and empty/loading states
- unit/UI/instrumentation tests
- release build configuration and documentation

## Backup/security requirements
Use versioned backup formats, integrity checks/checksums, safe restore preview, merge/replace where appropriate, and an emergency backup before destructive restore. Never expose credentials in source control.

## Completion rule
Do not report a feature complete merely because code was written. Verify compilation/tests, inspect the resulting behavior where possible, record what was changed, and move to the next unfinished phase.
