-- LvbyteStudio D1 schema
-- 所有时间戳统一为 Unix 毫秒，方便 APK 与前端直接使用。

CREATE TABLE IF NOT EXISTS friends (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  url TEXT NOT NULL UNIQUE,
  avatar TEXT NOT NULL DEFAULT '',
  description TEXT NOT NULL DEFAULT '',
  keywords TEXT NOT NULL DEFAULT '',
  feed_url TEXT,
  feed_kind TEXT NOT NULL DEFAULT 'feed',
  feed_enabled INTEGER NOT NULL DEFAULT 1,
  visible INTEGER NOT NULL DEFAULT 1,
  sort INTEGER NOT NULL DEFAULT 0,
  fail_count INTEGER NOT NULL DEFAULT 0,
  last_fetched_at INTEGER,
  last_error TEXT,
  alive INTEGER NOT NULL DEFAULT 1,
  health_checked_at INTEGER,
  health_next_at INTEGER,
  health_fail_count INTEGER NOT NULL DEFAULT 0,
  health_code INTEGER,
  health_error TEXT NOT NULL DEFAULT '',
  etag TEXT,
  last_modified TEXT,
  next_fetch_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_friends_visible ON friends (visible, sort, id);
CREATE INDEX IF NOT EXISTS idx_friends_feed ON friends (feed_enabled, next_fetch_at);
CREATE INDEX IF NOT EXISTS idx_friends_health ON friends (visible, health_next_at);

CREATE TABLE IF NOT EXISTS friend_applications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  url TEXT NOT NULL,
  avatar TEXT NOT NULL DEFAULT '',
  description TEXT NOT NULL DEFAULT '',
  email TEXT NOT NULL DEFAULT '',
  message TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'pending',
  note TEXT NOT NULL DEFAULT '',
  ip_hash TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  reviewed_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_apps_status ON friend_applications (status, created_at);
CREATE INDEX IF NOT EXISTS idx_apps_url ON friend_applications (url);

CREATE TABLE IF NOT EXISTS talks (
  id TEXT PRIMARY KEY,
  content TEXT NOT NULL,
  images TEXT NOT NULL DEFAULT '[]',
  tags TEXT NOT NULL DEFAULT '[]',
  visible INTEGER NOT NULL DEFAULT 1,
  source TEXT NOT NULL DEFAULT 'apk',
  source_id TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_talks_source ON talks (source, source_id)
  WHERE source_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_talks_time ON talks (visible, created_at DESC);

CREATE TABLE IF NOT EXISTS feed_articles (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  friend_id INTEGER NOT NULL,
  title TEXT NOT NULL,
  link TEXT NOT NULL UNIQUE,
  author TEXT NOT NULL DEFAULT '',
  avatar TEXT NOT NULL DEFAULT '',
  summary TEXT NOT NULL DEFAULT '',
  published_at INTEGER NOT NULL,
  fetched_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_feed_time ON feed_articles (published_at DESC);

CREATE TABLE IF NOT EXISTS notifications (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  type TEXT NOT NULL,
  title TEXT NOT NULL,
  body TEXT NOT NULL DEFAULT '',
  data TEXT NOT NULL DEFAULT '{}',
  read INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications (read, id DESC);

CREATE TABLE IF NOT EXISTS sessions (
  id TEXT PRIMARY KEY,
  github_login TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  user_agent TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  last_seen_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_sessions_hash ON sessions (token_hash);

CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

INSERT INTO settings (key, value) VALUES ('schema_version', '1')
  ON CONFLICT(key) DO NOTHING;
