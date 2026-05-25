# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android app in `app/` with package root `com.example.svoi`.

- `app/src/main/java/com/example/svoi/` contains Kotlin source.
- `data/model/` holds serializable Supabase models.
- `data/repository/` contains thin Supabase repository wrappers.
- `ui/<feature>/` contains Compose screens and ViewModels.
- `navigation/NavGraph.kt` defines string routes in `Routes`.
- `app/src/main/res/` contains Android resources, fonts, drawables, and raw animations.
- `app/src/test/` and `app/src/androidTest/` contain unit and instrumented tests.
- `supabase/` contains SQL migration/setup scripts to run manually in Supabase.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
.\gradlew.bat lint
```

`assembleDebug` builds the debug APK. `installDebug` deploys to a connected device or emulator. `test` runs JVM tests, `connectedAndroidTest` requires a device/emulator, and `lint` produces Android lint reports.

## Coding Style & Naming Conventions

Kotlin uses standard Android style: 4-space indentation, `PascalCase` for classes/composables, `camelCase` for functions and properties, and `UPPER_SNAKE_CASE` only for constants. Prefer existing Compose patterns: screens receive navigation lambdas, while ViewModels access repositories via `(application as SvoiApp)`. Keep models `@Serializable` and map Supabase snake_case fields with `@SerialName`.

## Testing Guidelines

Use JUnit for local tests and AndroidX test/Espresso for instrumented tests. Name test classes after the unit under test, for example `ChatRepositoryTest` or `ExampleUnitTest`. For UI or navigation changes, run at least `.\gradlew.bat :app:assembleDebug`; add targeted tests when changing shared data or repository behavior.

## Commit & Pull Request Guidelines

Recent commits use short descriptive messages such as `Add whats new announcements screen` or `Update announcement type labels`. Keep commits focused and imperative. PRs should include a concise summary, manual test notes, linked issue if applicable, and screenshots or recordings for visible UI changes.

## Security & Configuration Tips

Do not commit `local.properties`, Supabase/Firebase keys, keystores, or generated secrets. Supabase values are injected through `BuildConfig` from `local.properties`. Avoid changing auth, push notifications, or SQL scripts unless the task explicitly requires it.

## App-Specific Architecture Notes

- The app uses self-hosted Supabase at `https://api.svoilink.ru`.
- Runtime code should use `BuildConfig.SUPABASE_URL` as the main backend URL.
- `BuildConfig.SUPABASE_DIRECT_URL` may still exist for compatibility/fallback generation, but it should not be used for runtime proxy switching.
- `BuildConfig.SUPABASE_STORAGE_URL` is used for public Storage URLs and should not be removed unless all usages are checked.
- Do not reintroduce proxy/direct server switching UI or logic.

## Supabase Database Notes

- SQL files in `supabase/` are manual setup/migration scripts, not standard Supabase CLI migrations.
- Run SQL scripts manually in Supabase Studio unless the user explicitly asks for another method.
- Do not edit SQL migration/setup files unless the task explicitly requires database changes.
- RLS is enabled on app tables. Repository changes must respect authenticated user policies.
- `app_announcements` stores application announcements.
- `app_announcement_reads` stores per-user read/acknowledged modal announcements.
- Modal announcements use `show_as_modal = true` and should be shown once after login.
- The “Что нового” screen shows recent active announcements but must not mark them as read.

## Push Notification Notes

- Push notifications are sent by `supabase/functions/send-push/index.ts`.
- Android displays data-only FCM notifications in `SvoiFirebaseMessagingService.kt`.
- FCM `message.data` values must always be strings.
- Do not use reserved/invalid FCM data keys such as `message_type`; use `msg_type`.
- Keep old `title` and `body` fields in push payload for compatibility.
- Do not change Firebase service account, Supabase service role keys, or `.env` files.

## UI/UX Notes

- The app UI is in Russian.
- Keep Compose UI consistent with the existing style.
- For important modal announcements, use the custom bottom sheet in `ChatListScreen.kt`.
- Important modal announcements should close only through the “Понятно” button, not by swipe dismiss, so read tracking stays correct.
- Settings screen contains the “Что нового” entry and should not include the old proxy toggle.

## Manual Verification Checklist

After relevant changes, check:
- registration/login still works;
- chat list opens;
- messages send and receive;
- media upload/download works through `chat-media`;
- push notifications still arrive;
- modal announcements show once and mark as read;
- “Настройки -> Что нового” opens and shows recent announcements;
- `.\gradlew.bat :app:assembleDebug` succeeds.

## Git Safety

- Before committing, run `git status` and ensure no secrets are staged.
- Never commit `local.properties`, `.env`, Firebase service account JSON, generated secrets, keystores, or server-only config.
- Keep commits focused. Prefer short imperative commit messages.

## Codex Efficiency Rules

- Prefer targeted analysis over full-project scans. Use `rg`/search first, then inspect only relevant files.
- Do not print full file contents unless explicitly requested. Summarize findings and cite file paths instead.
- Before editing, identify the smallest set of files needed for the task.
- Make minimal focused changes. Avoid broad refactors unless the user explicitly asks.
- Preserve existing architecture, naming, UI style, and repository patterns.
- Do not rewrite working code only for style improvements.
- Avoid generating large explanations after every command. Report only what changed, what was checked, and what remains.
- When debugging, first find the exact error source from logs/diffs before proposing large changes.
- When adding a feature, prefer extending existing models/repositories/ViewModels instead of creating parallel systems.
- Do not run expensive commands repeatedly. Run `.\gradlew.bat :app:assembleDebug` once after code changes unless a fix requires another run.
- If a command fails due to network/cache/environment issues, report it briefly and do not keep retrying blindly.

## Response Format

For most tasks, answer in this compact format:

1. Changed files
2. What changed
3. Build/test result
4. Manual checks needed
5. Risks or notes

Do not include long code snippets or full diffs unless requested.

## Scope Control

- Do not modify unrelated files.
- Do not change Gradle versions, Kotlin versions, dependencies, package names, or app IDs unless explicitly requested.
- Do not touch authentication, push notifications, Supabase config, or SQL scripts unless the task explicitly involves them.
- Do not create commits unless the user explicitly asks.
- Do not edit generated files or secret/config files.