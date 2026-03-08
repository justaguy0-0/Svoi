# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Assemble debug APK (from project root)
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run a single test class
./gradlew test --tests "com.example.svoi.ExampleUnitTest"

# Clean build
./gradlew clean assembleDebug
```

There is no lint configuration beyond the Android default. Run `./gradlew lint` for a lint report.

## Environment Setup

Before building, `local.properties` must contain Supabase credentials (never commit this file — it's in `.gitignore`):

```
SUPABASE_URL=https://<project-id>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
```

These are injected into `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY` via `app/build.gradle.kts`.

**Supabase email confirmation must be disabled** in the Supabase Dashboard (Authentication → Settings) for the invite-key registration flow to work without email verification.

## Architecture

Single-module Android app. Package root: `com.example.svoi`.

### Dependency graph

```
UI Screens → ViewModels (AndroidViewModel) → Repositories → Supabase SDK
```

No DI framework (no Hilt). All singletons live on `SvoiApp` as lazy properties and are accessed from ViewModels via `(application as SvoiApp).xRepository`.

### Data layer (`data/`)

- **`SvoiApp`** — Application class. Creates the `SupabaseClient` (Auth, Postgrest, Realtime, Storage) and instantiates all four repositories.
- **`EncryptedPrefsManager`** — Persists Supabase session tokens (access + refresh + expiresAt) in `EncryptedSharedPreferences`. Session is manually saved after sign-in and restored via `supabase.auth.importSession()` on app start in `MainActivity`.
- **Repositories** — Thin wrappers around the Supabase Kotlin SDK (`supabase-kt 2.6.0` / Ktor `2.3.12`). All return `null` or empty lists on error (no exceptions bubble to the UI). Key pattern: `supabase.from("table").select { filter { ... } }.decodeList<Model>()`.
- **Realtime** — `MessageRepository` exposes `messageInsertFlow(chatId)` and `messageUpdateFlow(chatId)` as `Flow<Message>` backed by `supabase.channel(...)` + `postgresChangeFlow`. Channels are created per call — callers are responsible for lifecycle.

### UI layer (`ui/`)

Each feature folder contains a `Screen.kt` (Composable) and a `ViewModel.kt`. The Screen receives only lambdas for navigation — no NavController reference inside screens.

- **`auth/`** — Shared `AuthViewModel`. Login and invite-key validation are separate screens; setup profile is a third step.
- **`chatlist/`** — Loads chats on init, subscribes to `messageInsertFlow("")` (broad channel) to refresh the list on any new message.
- **`chat/`** — `ChatViewModel.init(chatId)` is idempotent (guarded by `if (this.chatId == chatId) return`). Messages are enriched with sender `Profile` from an in-memory `profileCache: Map<String, Profile>`. Pagination via `loadMoreMessages()` prepends older messages.
- **`newchat/`** — `NewChatViewModel` is shared between `UserSearchScreen` and `CreateGroupScreen`. User search is debounced 300ms via a `StateFlow` + `debounce`.

### Navigation (`navigation/NavGraph.kt`)

String-based routes defined in `Routes` object. `startDestination` is determined in `MainActivity` by attempting session restore — either `Routes.CHAT_LIST` or `Routes.LOGIN`.

### Supabase schema

SQL scripts in `supabase/` must be run in order in Supabase Dashboard → SQL Editor:
1. `01_schema.sql` — all tables + indexes
2. `02_functions_triggers.sql` — helper functions (`is_chat_member`, `is_chat_admin`) + trigger that auto-creates `profiles` and `user_presence` rows on `auth.users` insert
3. `03_rls.sql` — Row Level Security policies (all tables have RLS enabled)
4. `04_realtime.sql` — enables Realtime publication for `messages`, `message_reads`, `user_presence`, `chat_members`, `pinned_messages`

Storage bucket `chat-media` must be created manually (public: off). File paths follow `{chatId}/{uuid}/{filename}`.

### Auth flow

New user: `InviteKeyScreen` (validate key, anon role) → `SetupProfileScreen` (signUp with email + set profile + mark key used) → `ChatListScreen`.

Returning user: `LoginScreen` (email + password) → `ChatListScreen`.

On app start: `AuthRepository.restoreSession()` → if tokens saved in `EncryptedPrefsManager`, calls `supabase.auth.importSession()` → skips login screen.

### Key conventions

- All timestamps are ISO-8601 strings (Supabase default). Formatting helpers live in `util/Extensions.kt` as extension functions on `String` and `Long`.
- Avatar: personal chats → emoji on colored circle; group chats → first letter of group name on fixed dark circle (`#455A64`). Component: `ui/components/Avatar.kt`.
- `AvatarColors` (list of hex strings) in `ui/theme/Color.kt` is used for both the profile setup picker and the profile editor.
- Theme is light-only (no dynamic color, no dark theme). Primary color: `#1E88E5`.
