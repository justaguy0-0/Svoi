-- Таблица версий приложения для системы обновлений
-- Запускать в Supabase Dashboard → SQL Editor

CREATE TABLE IF NOT EXISTS app_versions (
    id           SERIAL PRIMARY KEY,
    version_code INT         NOT NULL,          -- числовой код (versionCode из build.gradle)
    version_name TEXT        NOT NULL,          -- отображаемая версия ("1.2")
    changelog    TEXT        NOT NULL DEFAULT '',  -- блок "Что нового"
    download_url TEXT        NOT NULL,          -- ссылка на скачивание (GitHub Releases)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Только чтение, без авторизации (anon может читать)
ALTER TABLE app_versions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Public read app_versions"
    ON app_versions FOR SELECT
    USING (true);

-- Пример записи (заменить на реальные данные перед использованием):
-- INSERT INTO app_versions (version_code, version_name, changelog, download_url)
-- VALUES (
--     3,
--     '1.2',
--     '• Выбор цветовой палитры приложения
-- • Галочки прочтения в списке чатов
-- • Авто-воспроизведение следующего голосового сообщения
-- • Исправлена кнопка голосового после фокуса на поле ввода',
--     'https://github.com/justaguy0-0/Svoi/releases/tag/v1.2'
-- );
