CREATE TABLE IF NOT EXISTS app_setting (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(200) NOT NULL,
    setting_value VARCHAR(2000) NOT NULL,
    CONSTRAINT uk_app_setting_key UNIQUE (setting_key)
);
