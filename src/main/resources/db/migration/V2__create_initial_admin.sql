INSERT INTO app_user (username, password_hash, enabled)
VALUES ('admin', '$2a$10$$2a$10$.oGILrMtm44Y3WCEyXElY.11pBxUYhC3jzkQCuGQGDh2wQTihD.t.', true);

INSERT INTO app_user_role (user_id, role)
SELECT id, 'ADMIN'
FROM app_user
WHERE username = 'admin';