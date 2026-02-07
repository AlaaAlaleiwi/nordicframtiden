CREATE TABLE IF NOT EXISTS public.app_user_permissions (
  user_id bigint NOT NULL,
  permission varchar(50) NOT NULL,
  CONSTRAINT fk_app_user_permissions_user
    FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_app_user_permissions_user_id
  ON public.app_user_permissions(user_id);