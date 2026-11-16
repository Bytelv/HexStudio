-- 2026-11-16：点赞 + Waline 评论提醒
-- 线上库执行：wrangler d1 execute lvbyte-studio-db --remote --file=./migrations/20261116_likes_and_comments.sql
-- （schema.sql 里也同步了同样的建表语句，新库直接跑 schema.sql 即可）

CREATE TABLE IF NOT EXISTS likes (
  path TEXT NOT NULL,
  visitor TEXT NOT NULL,
  ip_hash TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  PRIMARY KEY (path, visitor)
);

CREATE INDEX IF NOT EXISTS idx_likes_path ON likes (path);
CREATE INDEX IF NOT EXISTS idx_likes_ip ON likes (ip_hash, created_at);

-- comments 同步游标：记录已经通知过的最大 Waline objectId，避免重复提醒
INSERT INTO settings (key, value) VALUES ('waline_last_comment_id', '0')
  ON CONFLICT(key) DO NOTHING;
