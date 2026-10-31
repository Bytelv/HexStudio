-- 友链存活检测：每日检查一次，失败后按小时重试并自动恢复
ALTER TABLE friends ADD COLUMN alive INTEGER NOT NULL DEFAULT 1;
ALTER TABLE friends ADD COLUMN health_checked_at INTEGER;
ALTER TABLE friends ADD COLUMN health_next_at INTEGER;
ALTER TABLE friends ADD COLUMN health_fail_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE friends ADD COLUMN health_code INTEGER;
ALTER TABLE friends ADD COLUMN health_error TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_friends_health ON friends (visible, health_next_at);

UPDATE friends SET health_next_at = 0 WHERE health_next_at IS NULL;
