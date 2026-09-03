# RSS Money Manager — UI Reference Specification

Use the KTW Money Manager app as a UX reference only. Do not copy proprietary assets, branding, screenshots, or source code. RSS Money Manager keeps its own RSS branding and implementation.

Reference checked: Google Play app `com.ktwapps.walletmanager`.

## Design direction
- Clean, compact native Android finance UI.
- Every primary tab is a true full-screen destination.
- Persistent bottom navigation with Home, Transactions, Add, Accounts, and More.
- Clear balance/income/expense hierarchy.
- Card/list based sections with readable amounts and dates.
- Light, dark, and system appearance.
- RSS logo is the only app branding; do not alter, recolor, stretch, or redesign it.

## Primary screens
1. Home / Dashboard
   - Total balance
   - Income and expenses
   - Date/month overview
   - Quick actions
   - Recent transactions
   - Wallet/account summary
2. Transactions
   - Day/week/month/year/custom date filtering
   - Search
   - Income/expense/transfer filters
   - Full-screen transaction details/editing
3. Add Transaction — full screen
   - Date / time / recurring with icon
   - Count and Amount side-by-side
   - Total read-only
   - Locked calculation: Count × Amount = Total
   - Count defaults to 1
   - Description
   - Items section hidden until an item exists
   - Category list view
   - Wallet list view
   - Separator
   - Memo and camera/product photo access
4. Accounts / Wallets
   - Cash, bank, card, e-wallet and savings accounts
   - Balances and account details
   - Add/edit/archive
5. Budgets
   - Overall and category budgets
   - Progress indicators
   - Threshold notifications
6. Categories
   - Expense/income categories
   - Subcategories
   - Add/edit/delete
7. Savings Goals
   - Goal amount, current progress, target date
8. Debts
   - Money owed / owed to user
   - Repayment tracking and reminders
9. Reports / Charts
   - Income vs expense
   - Category breakdown
   - Trends
   - Date-range filters
10. Recurring Transactions
    - Schedule, next occurrence, pause/resume
11. More / Settings
    - Appearance
    - Security / app lock
    - Backup & restore
    - Export CSV/Excel
    - Data management

## Smart Memory
When the user repeatedly describes an entry, learn the preferred category without silently changing a manually selected category. Example: `eggs 🥚` should learn/select `House Expenses` after sufficient evidence. The user must be able to view, edit, reset, or disable learned mappings.

## Backup targets
- Google Drive
- MEGA
- Additional cloud providers through a provider abstraction
- Local export/import
- Firebase integration for authentication/sync metadata where appropriate

## Quality gates
- No primary tab may open as a dialog or partial embedded panel.
- Entry calculation must remain correct for decimal currency values.
- Total must not be manually editable.
- Empty Items must not consume visible dashboard space.
- Build must pass before an APK is announced as ready.
