/**
 * LvbyteStudio API
 *
 * 单站点 Hexo 管理后端：
 *  - 友链（公开 JSON + 申请）
 *  - 说说（APK 发布，网站直接读取）
 *  - 朋友圈（Cron 抓取好友 RSS/Atom）
 *  - 管理端会话（GitHub Device Flow 换取）
 *
 * 运行在 Cloudflare Workers + D1。所有时间戳均为 Unix 毫秒。
 */

const VERSION = '1.0.0';
const UA = 'LvbyteStudioBot/1.0 (+https://blog.lvbyte.top)';
const BROWSER_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36';

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

/* ------------------------------------------------------------------ */
/* 基础工具                                                            */
/* ------------------------------------------------------------------ */

const now = () => Date.now();

function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      ...corsHeaders(),
      ...extraHeaders,
    },
  });
}

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,PATCH,DELETE,OPTIONS',
    'Access-Control-Allow-Headers': 'Authorization,Content-Type',
    'Access-Control-Max-Age': '86400',
  };
}

function preflight() {
  return new Response(null, { status: 204, headers: corsHeaders() });
}

function clampStr(value, max) {
  return String(value == null ? '' : value).trim().slice(0, max);
}

function toInt(value, fallback = 0) {
  if (value === null || value === undefined || value === '') return fallback;
  const n = Number(value);
  return Number.isFinite(n) ? Math.trunc(n) : fallback;
}

function toBool(value, fallback = false) {
  if (value === undefined || value === null || value === '') return fallback;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  const s = String(value).toLowerCase();
  if (['1', 'true', 'yes', 'on'].includes(s)) return true;
  if (['0', 'false', 'no', 'off'].includes(s)) return false;
  return fallback;
}

function isHttpUrl(value) {
  try {
    const u = new URL(String(value));
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

function normalizeUrl(value) {
  const s = clampStr(value, 500);
  if (!isHttpUrl(s)) return '';
  try {
    const u = new URL(s);
    u.hash = '';
    return u.href;
  } catch {
    return '';
  }
}

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function decodeEntities(value) {
  return String(value == null ? '' : value)
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&#x([0-9a-f]+);/gi, (_, h) => {
      try { return String.fromCodePoint(parseInt(h, 16)); } catch { return ''; }
    })
    .replace(/&#(\d+);/g, (_, d) => {
      try { return String.fromCodePoint(parseInt(d, 10)); } catch { return ''; }
    })
    .replace(/&nbsp;/g, ' ')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}

function stripHtml(value) {
  let s = String(value == null ? '' : value);
  s = s.replace(/<script[\s\S]*?<\/script>/gi, ' ');
  s = s.replace(/<style[\s\S]*?<\/style>/gi, ' ');
  s = s.replace(/<br\s*\/?>/gi, ' ');
  s = s.replace(/<\/(p|div|li|h[1-6]|tr|blockquote)>/gi, ' ');
  s = s.replace(/<[^>]+>/g, '');
  s = decodeEntities(s);
  return s.replace(/\s+/g, ' ').trim();
}

function formatDateCN(ms, withTime = false) {
  const d = new Date(Number(ms) || 0);
  if (Number.isNaN(d.getTime())) return '';
  try {
    const parts = new Intl.DateTimeFormat('zh-CN', {
      timeZone: 'Asia/Shanghai',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).formatToParts(d);
    const get = (type) => parts.find((p) => p.type === type)?.value || '';
    const date = `${get('year')}-${get('month')}-${get('day')}`;
    return withTime ? `${date} ${get('hour')}:${get('minute')}` : date;
  } catch {
    return d.toISOString().slice(0, withTime ? 16 : 10).replace('T', ' ');
  }
}

async function sha256Hex(text) {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(String(text)));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function randomToken(bytes = 32) {
  const arr = new Uint8Array(bytes);
  crypto.getRandomValues(arr);
  let s = '';
  for (const b of arr) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

async function readJson(request) {
  const text = await request.text();
  if (text.length > 2_000_000) throw new HttpError(413, '请求体过大');
  if (!text.trim()) return {};
  try {
    const value = JSON.parse(text);
    if (value == null || typeof value !== 'object') throw new Error('not object');
    return value;
  } catch {
    throw new HttpError(400, '请求体不是合法 JSON');
  }
}

function parseJsonArray(text) {
  try {
    const value = JSON.parse(text);
    return Array.isArray(value) ? value : [];
  } catch {
    return [];
  }
}

function cleanArray(value, maxItems, maxLen) {
  if (!Array.isArray(value)) return [];
  const out = [];
  for (const item of value.slice(0, maxItems)) {
    const s = clampStr(item, maxLen);
    if (s) out.push(s);
  }
  return [...new Set(out)];
}

/* ------------------------------------------------------------------ */
/* D1 辅助                                                             */
/* ------------------------------------------------------------------ */

async function dbAll(env, sql, ...params) {
  const res = await env.DB.prepare(sql).bind(...params).all();
  return res.results || [];
}

async function dbFirst(env, sql, ...params) {
  return await env.DB.prepare(sql).bind(...params).first();
}

async function dbRun(env, sql, ...params) {
  return await env.DB.prepare(sql).bind(...params).run();
}

/* ------------------------------------------------------------------ */
/* 鉴权                                                                */
/* ------------------------------------------------------------------ */

function bearerToken(request) {
  const header = request.headers.get('Authorization') || '';
  const m = header.match(/^Bearer\s+(.+)$/i);
  return m ? m[1].trim() : '';
}

async function requireSession(request, env) {
  const token = bearerToken(request);
  if (!token) throw new HttpError(401, '请先登录');
  const tokenHash = await sha256Hex(token);
  const session = await dbFirst(
    env,
    'SELECT * FROM sessions WHERE token_hash = ? AND expires_at > ?',
    tokenHash,
    now()
  );
  if (!session) throw new HttpError(401, '登录状态已失效，请重新登录');
  return { session, token };
}

async function handleGithubLogin(request, env) {
  const body = await readJson(request);
  const accessToken = clampStr(body.access_token || body.token, 500);
  if (!accessToken) throw new HttpError(400, '缺少 access_token');

  const ghHeaders = {
    Authorization: `Bearer ${accessToken}`,
    Accept: 'application/vnd.github+json',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': UA,
  };

  let resp;
  try {
    resp = await fetch('https://api.github.com/user', { headers: ghHeaders });
  } catch {
    throw new HttpError(502, '连接 GitHub 失败');
  }
  if (!resp.ok) {
    if (resp.status === 401) throw new HttpError(401, 'GitHub Token 无效或已过期');
    throw new HttpError(502, `GitHub 返回 ${resp.status}`);
  }

  const user = await resp.json();
  const allowed = clampStr(env.ADMIN_GITHUB_LOGIN || 'Bytelv', 100).toLowerCase();
  if (String(user.login || '').toLowerCase() !== allowed) {
    throw new HttpError(403, '这个 GitHub 账号不是本站管理员');
  }

  const token = randomToken(32);
  const tokenHash = await sha256Hex(token);
  const created = now();
  const expires = created + 90 * 24 * 60 * 60 * 1000;
  const id = crypto.randomUUID();

  await dbRun(env, 'DELETE FROM sessions WHERE expires_at <= ?', created);
  await dbRun(
    env,
    'INSERT INTO sessions (id, github_login, token_hash, user_agent, created_at, expires_at, last_seen_at) VALUES (?,?,?,?,?,?,?)',
    id,
    String(user.login || ''),
    tokenHash,
    clampStr(request.headers.get('User-Agent'), 300),
    created,
    expires,
    created
  );

  return json({
    ok: true,
    token,
    expires_at: expires,
    user: {
      login: user.login,
      name: user.name || user.login,
      avatar_url: user.avatar_url || '',
      html_url: user.html_url || '',
    },
  });
}

/* ------------------------------------------------------------------ */
/* 公共接口                                                            */
/* ------------------------------------------------------------------ */

async function listFriends(env) {
  const rows = await dbAll(
    env,
    'SELECT title, url, avatar, description, keywords FROM friends WHERE visible = 1 ORDER BY sort ASC, id ASC'
  );
  return {
    version: 'v2',
    content: rows.map((r) => ({
      title: r.title,
      url: r.url,
      avatar: r.avatar || '',
      description: r.description || '',
      keywords: r.keywords || '',
    })),
  };
}

async function applyFriend(request, env) {
  const body = await readJson(request);

  // 蜜罐字段：正常用户不会填写
  if (clampStr(body.website || body.homepage, 200)) {
    return json({ ok: true, id: 0, silent: true });
  }

  const name = clampStr(body.name || body.title, 80);
  const url = normalizeUrl(body.url);
  const avatar = normalizeUrl(body.avatar);
  const description = clampStr(body.description || body.desc, 300);
  const email = clampStr(body.email, 120);
  const message = clampStr(body.message, 500);

  if (!name) throw new HttpError(400, '请填写网站名称');
  if (!url) throw new HttpError(400, '请填写正确的网址（需以 http:// 或 https:// 开头）');
  if (!description) throw new HttpError(400, '请填写网站简介');
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new HttpError(400, '邮箱格式不正确');
  }

  const existingFriend = await dbFirst(env, 'SELECT id FROM friends WHERE url = ?', url);
  if (existingFriend) {
    return json({ ok: true, duplicate: true, message: '这个网站已经在友链里了' });
  }
  const pending = await dbFirst(
    env,
    "SELECT id FROM friend_applications WHERE url = ? AND status = 'pending'",
    url
  );
  if (pending) {
    return json({ ok: true, duplicate: true, message: '这个网站已经提交过申请，请耐心等待审核' });
  }

  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  const ipHash = await sha256Hex(`${ip}|${env.IP_SALT || 'lvbyte-studio'}`);
  const since = now() - 24 * 60 * 60 * 1000;
  const recent = await dbFirst(
    env,
    'SELECT COUNT(*) AS n FROM friend_applications WHERE ip_hash = ? AND created_at > ?',
    ipHash,
    since
  );
  if (Number(recent?.n || 0) >= 5) {
    throw new HttpError(429, '今天提交的申请太多了，请明天再试');
  }

  const created = now();
  const result = await dbRun(
    env,
    `INSERT INTO friend_applications
       (name, url, avatar, description, email, message, status, ip_hash, created_at)
     VALUES (?,?,?,?,?,?,'pending',?,?)`,
    name, url, avatar, description, email, message, ipHash, created
  );
  const id = Number(result.meta?.last_row_id || 0);

  await dbRun(
    env,
    'INSERT INTO notifications (type, title, body, data, read, created_at) VALUES (?,?,?,?,0,?)',
    'friend_apply',
    '新的友链申请',
    `${name} 申请添加友链`,
    JSON.stringify({ application_id: id, name, url }),
    created
  );

  return json({ ok: true, id, message: '申请已提交，审核通过后会出现在友链页面' }, 201);
}

async function listTalks(request, env) {
  const url = new URL(request.url);
  const limit = Math.min(Math.max(toInt(url.searchParams.get('limit'), 60), 1), 100);
  const before = toInt(url.searchParams.get('before'), 0);
  const rows = before > 0
    ? await dbAll(
        env,
        'SELECT * FROM talks WHERE visible = 1 AND created_at < ? ORDER BY created_at DESC LIMIT ?',
        before,
        limit
      )
    : await dbAll(
        env,
        'SELECT * FROM talks WHERE visible = 1 ORDER BY created_at DESC LIMIT ?',
        limit
      );

  return json(
    {
      version: 1,
      content: rows.map(publicTalk),
    },
    200,
    { 'Cache-Control': 'public, max-age=30' }
  );
}

function publicTalk(row) {
  return {
    id: row.id,
    content: row.content,
    images: parseJsonArray(row.images),
    tags: parseJsonArray(row.tags),
    created_at: Number(row.created_at),
    created: formatDateCN(row.created_at, true),
    updated_at: Number(row.updated_at),
  };
}

async function listFeeds(request, env) {
  const url = new URL(request.url);
  const limit = Math.min(Math.max(toInt(url.searchParams.get('limit'), 80), 1), 200);
  const rows = await dbAll(
    env,
    `SELECT a.*, f.title AS friend_title, f.url AS friend_url
       FROM feed_articles a
       JOIN friends f ON f.id = a.friend_id
      WHERE f.visible = 1
      ORDER BY a.published_at DESC
      LIMIT ?`,
    limit
  );
  const totals = await dbFirst(
    env,
    'SELECT (SELECT COUNT(*) FROM friends WHERE visible = 1) AS friends_count, (SELECT COUNT(*) FROM feed_articles) AS article_count'
  );

  return json(
    {
      statistical_data: {
        friends_count: Number(totals?.friends_count || 0),
        article_count: Number(totals?.article_count || 0),
        updated_at: now(),
      },
      article_data: rows.map((r) => ({
        friend_id: r.friend_id,
        friend_title: r.friend_title,
        friend_url: r.friend_url,
        avatar: r.avatar || '',
        author: r.author || r.friend_title,
        created: formatDateCN(r.published_at),
        created_at: Number(r.published_at),
        link: r.link,
        title: r.title,
        summary: r.summary || '',
      })),
    },
    200,
    { 'Cache-Control': 'public, max-age=60' }
  );
}

/* ------------------------------------------------------------------ */
/* 管理端                                                              */
/* ------------------------------------------------------------------ */

async function adminStats(env) {
  const row = await dbFirst(
    env,
    `SELECT
       (SELECT COUNT(*) FROM friends) AS friends,
       (SELECT COUNT(*) FROM talks WHERE visible = 1) AS talks,
       (SELECT COUNT(*) FROM friend_applications WHERE status = 'pending') AS pending_applications,
       (SELECT COUNT(*) FROM notifications WHERE read = 0) AS unread_notifications,
       (SELECT COUNT(*) FROM feed_articles) AS articles,
       (SELECT COUNT(*) FROM friends WHERE last_error IS NOT NULL) AS feed_errors`
  );
  return json({
    ok: true,
    stats: {
      friends: Number(row?.friends || 0),
      talks: Number(row?.talks || 0),
      pending_applications: Number(row?.pending_applications || 0),
      unread_notifications: Number(row?.unread_notifications || 0),
      articles: Number(row?.articles || 0),
      feed_errors: Number(row?.feed_errors || 0),
    },
  });
}

async function adminMe(request, env, session) {
  await dbRun(env, 'UPDATE sessions SET last_seen_at = ? WHERE id = ?', now(), session.id);
  return json({
    ok: true,
    user: { login: session.github_login },
    session: { expires_at: session.expires_at, created_at: session.created_at },
    site: { name: env.SITE_NAME || '字节君', url: env.SITE_URL || 'https://blog.lvbyte.top' },
  });
}

async function adminApplications(request, env) {
  const url = new URL(request.url);
  const status = clampStr(url.searchParams.get('status') || 'pending', 20);
  const limit = Math.min(Math.max(toInt(url.searchParams.get('limit'), 100), 1), 200);
  const rows = status === 'all'
    ? await dbAll(env, 'SELECT * FROM friend_applications ORDER BY id DESC LIMIT ?', limit)
    : await dbAll(
        env,
        'SELECT * FROM friend_applications WHERE status = ? ORDER BY id DESC LIMIT ?',
        status,
        limit
      );
  return json({
    ok: true,
    content: rows.map((r) => ({
      ...r,
      created: formatDateCN(r.created_at, true),
      reviewed: r.reviewed_at ? formatDateCN(r.reviewed_at, true) : '',
    })),
  });
}

async function approveApplication(env, id) {
  const app = await dbFirst(env, 'SELECT * FROM friend_applications WHERE id = ?', id);
  if (!app) throw new HttpError(404, '申请不存在');
  if (app.status === 'approved') {
    return json({ ok: true, message: '该申请已经通过', application: app });
  }

  const existing = await dbFirst(env, 'SELECT * FROM friends WHERE url = ?', app.url);
  let friendId;
  const t = now();
  if (existing) {
    await dbRun(
      env,
      `UPDATE friends SET
         title = COALESCE(NULLIF(?, ''), title),
         avatar = COALESCE(NULLIF(?, ''), avatar),
         description = COALESCE(NULLIF(?, ''), description),
         visible = 1,
         updated_at = ?
       WHERE id = ?`,
      app.name, app.avatar, app.description, t, existing.id
    );
    friendId = existing.id;
  } else {
    const sortRow = await dbFirst(env, 'SELECT COALESCE(MAX(sort), 0) AS m FROM friends');
    const result = await dbRun(
      env,
      `INSERT INTO friends
         (title, url, avatar, description, keywords, feed_enabled, visible, sort, created_at, updated_at)
       VALUES (?,?,?,?,'',1,1,?,?,?)`,
      app.name, app.url, app.avatar, app.description, Number(sortRow?.m || 0) + 1, t, t
    );
    friendId = Number(result.meta?.last_row_id || 0);
  }

  await dbRun(
    env,
    "UPDATE friend_applications SET status = 'approved', reviewed_at = ? WHERE id = ?",
    t,
    id
  );
  await dbRun(
    env,
    "UPDATE notifications SET read = 1 WHERE type = 'friend_apply' AND data LIKE ?",
    `%"application_id":${id}%`
  );

  return json({ ok: true, friend_id: friendId, message: '已通过，友链已上线' });
}

async function rejectApplication(request, env, id) {
  const body = await readJson(request);
  const reason = clampStr(body.reason || body.note, 300);
  const app = await dbFirst(env, 'SELECT * FROM friend_applications WHERE id = ?', id);
  if (!app) throw new HttpError(404, '申请不存在');
  await dbRun(
    env,
    "UPDATE friend_applications SET status = 'rejected', note = ?, reviewed_at = ? WHERE id = ?",
    reason,
    now(),
    id
  );
  await dbRun(
    env,
    "UPDATE notifications SET read = 1 WHERE type = 'friend_apply' AND data LIKE ?",
    `%"application_id":${id}%`
  );
  return json({ ok: true, message: '已拒绝' });
}

async function adminFriends(env) {
  const rows = await dbAll(env, 'SELECT * FROM friends ORDER BY sort ASC, id ASC');
  return json({
    ok: true,
    content: rows.map((r) => ({
      ...r,
      feed_enabled: !!r.feed_enabled,
      visible: !!r.visible,
      last_fetched: r.last_fetched_at ? formatDateCN(r.last_fetched_at, true) : '',
    })),
  });
}

async function createFriend(request, env) {
  const body = await readJson(request);
  const title = clampStr(body.title || body.name, 80);
  const url = normalizeUrl(body.url);
  if (!title) throw new HttpError(400, '缺少网站名称');
  if (!url) throw new HttpError(400, '网址不合法');
  const existing = await dbFirst(env, 'SELECT id FROM friends WHERE url = ?', url);
  if (existing) throw new HttpError(409, '这个网址已经在友链里了');

  const t = now();
  const sortRow = await dbFirst(env, 'SELECT COALESCE(MAX(sort), 0) AS m FROM friends');
  const result = await dbRun(
    env,
    `INSERT INTO friends
       (title, url, avatar, description, keywords, feed_url, feed_enabled, visible, sort, created_at, updated_at)
     VALUES (?,?,?,?,?,?,?,?,?,?,?)`,
    title,
    url,
    normalizeUrl(body.avatar),
    clampStr(body.description, 300),
    clampStr(body.keywords, 200),
    normalizeUrl(body.feed_url) || null,
    toBool(body.feed_enabled, true) ? 1 : 0,
    toBool(body.visible, true) ? 1 : 0,
    toInt(body.sort, Number(sortRow?.m || 0) + 1),
    t,
    t
  );
  return json({ ok: true, id: Number(result.meta?.last_row_id || 0) }, 201);
}

async function updateFriend(request, env, id) {
  const body = await readJson(request);
  const existing = await dbFirst(env, 'SELECT * FROM friends WHERE id = ?', id);
  if (!existing) throw new HttpError(404, '友链不存在');

  const fields = [];
  const params = [];
  const add = (column, value) => {
    fields.push(`${column} = ?`);
    params.push(value);
  };

  if (body.title !== undefined) add('title', clampStr(body.title, 80) || existing.title);
  if (body.url !== undefined) {
    const u = normalizeUrl(body.url);
    if (!u) throw new HttpError(400, '网址不合法');
    add('url', u);
  }
  if (body.avatar !== undefined) add('avatar', normalizeUrl(body.avatar));
  if (body.description !== undefined) add('description', clampStr(body.description, 300));
  if (body.keywords !== undefined) add('keywords', clampStr(body.keywords, 200));
  if (body.feed_url !== undefined) {
    const nextFeedUrl = normalizeUrl(body.feed_url) || null;
    add('feed_url', nextFeedUrl);
    if (nextFeedUrl !== (existing.feed_url || null)) add('feed_kind', 'feed');
  }
  if (body.feed_enabled !== undefined) add('feed_enabled', toBool(body.feed_enabled, true) ? 1 : 0);
  if (body.visible !== undefined) add('visible', toBool(body.visible, true) ? 1 : 0);
  if (body.sort !== undefined) add('sort', toInt(body.sort, existing.sort));

  if (!fields.length) return json({ ok: true, message: '没有需要更新的字段' });
  add('updated_at', now());
  add('last_error', null);
  add('fail_count', 0);
  params.push(id);
  await dbRun(env, `UPDATE friends SET ${fields.join(', ')} WHERE id = ?`, ...params);
  return json({ ok: true });
}

async function deleteFriend(env, id) {
  const existing = await dbFirst(env, 'SELECT id FROM friends WHERE id = ?', id);
  if (!existing) throw new HttpError(404, '友链不存在');
  await env.DB.batch([
    env.DB.prepare('DELETE FROM feed_articles WHERE friend_id = ?').bind(id),
    env.DB.prepare('DELETE FROM friends WHERE id = ?').bind(id),
  ]);
  return json({ ok: true });
}

async function importFriends(request, env) {
  const body = await readJson(request);
  const list = Array.isArray(body.friends) ? body.friends : Array.isArray(body.content) ? body.content : [];
  if (!list.length) throw new HttpError(400, '没有可导入的友链');
  if (list.length > 500) throw new HttpError(400, '一次最多导入 500 条');

  const t = now();
  let imported = 0;
  let skipped = 0;
  for (let i = 0; i < list.length; i++) {
    const item = list[i] || {};
    const url = normalizeUrl(item.url);
    const title = clampStr(item.title || item.name, 80);
    if (!url || !title) { skipped++; continue; }
    await dbRun(
      env,
      `INSERT INTO friends
         (title, url, avatar, description, keywords, feed_enabled, visible, sort, created_at, updated_at)
       VALUES (?,?,?,?,?,1,1,?,?,?)
       ON CONFLICT(url) DO UPDATE SET
         title = excluded.title,
         avatar = excluded.avatar,
         description = excluded.description,
         keywords = excluded.keywords,
         updated_at = excluded.updated_at`,
      title,
      url,
      normalizeUrl(item.avatar),
      clampStr(item.description, 300),
      clampStr(item.keywords, 200),
      i,
      t,
      t
    );
    imported++;
  }
  return json({ ok: true, imported, skipped });
}

async function adminTalks(request, env) {
  const url = new URL(request.url);
  const limit = Math.min(Math.max(toInt(url.searchParams.get('limit'), 100), 1), 500);
  const rows = await dbAll(env, 'SELECT * FROM talks ORDER BY created_at DESC LIMIT ?', limit);
  return json({ ok: true, content: rows.map((r) => ({ ...publicTalk(r), visible: !!r.visible, source: r.source, source_id: r.source_id })) });
}

async function createTalk(request, env) {
  const body = await readJson(request);
  const content = clampStr(body.content, 5000);
  if (!content) throw new HttpError(400, '说说内容不能为空');
  const images = cleanArray(body.images, 9, 800).map((u) => normalizeUrl(u)).filter(Boolean);
  const tags = cleanArray(body.tags, 10, 40);
  const created = toInt(body.created_at, now()) || now();
  const t = now();
  const id = clampStr(body.id, 80) || crypto.randomUUID();
  const source = clampStr(body.source, 40) || 'apk';
  const sourceId = clampStr(body.source_id, 120) || id;

  await dbRun(
    env,
    `INSERT INTO talks (id, content, images, tags, visible, source, source_id, created_at, updated_at)
     VALUES (?,?,?,?,1,?,?,?,?)`,
    id, content, JSON.stringify(images), JSON.stringify(tags), source, sourceId, created, t
  );
  return json({ ok: true, id, talk: publicTalk({ id, content, images: JSON.stringify(images), tags: JSON.stringify(tags), created_at: created, updated_at: t }) }, 201);
}

async function updateTalk(request, env, id) {
  const body = await readJson(request);
  const existing = await dbFirst(env, 'SELECT * FROM talks WHERE id = ?', id);
  if (!existing) throw new HttpError(404, '说说不存在');
  const fields = [];
  const params = [];
  if (body.content !== undefined) {
    const content = clampStr(body.content, 5000);
    if (!content) throw new HttpError(400, '说说内容不能为空');
    fields.push('content = ?'); params.push(content);
  }
  if (body.images !== undefined) {
    const images = cleanArray(body.images, 9, 800).map((u) => normalizeUrl(u)).filter(Boolean);
    fields.push('images = ?'); params.push(JSON.stringify(images));
  }
  if (body.tags !== undefined) {
    fields.push('tags = ?'); params.push(JSON.stringify(cleanArray(body.tags, 10, 40)));
  }
  if (body.visible !== undefined) {
    fields.push('visible = ?'); params.push(toBool(body.visible, true) ? 1 : 0);
  }
  if (!fields.length) return json({ ok: true });
  fields.push('updated_at = ?'); params.push(now());
  params.push(id);
  await dbRun(env, `UPDATE talks SET ${fields.join(', ')} WHERE id = ?`, ...params);
  return json({ ok: true });
}

async function deleteTalk(env, id) {
  const existing = await dbFirst(env, 'SELECT id FROM talks WHERE id = ?', id);
  if (!existing) throw new HttpError(404, '说说不存在');
  await dbRun(env, 'DELETE FROM talks WHERE id = ?', id);
  return json({ ok: true });
}

async function importTalks(request, env) {
  const body = await readJson(request);
  const list = Array.isArray(body.talks) ? body.talks : Array.isArray(body.content) ? body.content : [];
  if (!list.length) throw new HttpError(400, '没有可导入的说说');
  if (list.length > 1000) throw new HttpError(400, '一次最多导入 1000 条');

  let imported = 0;
  let skipped = 0;
  const t = now();
  for (const raw of list) {
    const item = raw || {};
    const content = clampStr(item.content || item.text, 5000);
    if (!content) { skipped++; continue; }
    const imageSource = Array.isArray(item.images)
      ? item.images
      : Array.isArray(item.resourceList)
        ? item.resourceList.map((r) => (typeof r === 'string' ? r : r.externalLink || r.url || ''))
        : [];
    const images = cleanArray(imageSource, 9, 800)
      .map((u) => normalizeUrl(u))
      .filter(Boolean);
    const tags = cleanArray(item.tags, 10, 40);
    const created = toInt(item.created_at || item.createdTs, t) || t;
    const source = clampStr(item.source, 40) || 'import';
    const sourceId = clampStr(item.source_id || item.id, 120) || null;
    const id = crypto.randomUUID();
    try {
      const res = await dbRun(
        env,
        `INSERT OR IGNORE INTO talks (id, content, images, tags, visible, source, source_id, created_at, updated_at)
         VALUES (?,?,?,?,1,?,?,?,?)`,
        id, content, JSON.stringify(images), JSON.stringify(tags), source, sourceId, created, t
      );
      if (Number(res.meta?.changes || 0) > 0) imported++;
      else skipped++;
    } catch {
      skipped++;
    }
  }
  return json({ ok: true, imported, skipped });
}

async function adminNotifications(request, env) {
  const url = new URL(request.url);
  const unread = toBool(url.searchParams.get('unread'), false);
  const limit = Math.min(Math.max(toInt(url.searchParams.get('limit'), 50), 1), 200);
  const rows = unread
    ? await dbAll(env, 'SELECT * FROM notifications WHERE read = 0 ORDER BY id DESC LIMIT ?', limit)
    : await dbAll(env, 'SELECT * FROM notifications ORDER BY id DESC LIMIT ?', limit);
  const unreadRow = await dbFirst(env, 'SELECT COUNT(*) AS n FROM notifications WHERE read = 0');
  return json({
    ok: true,
    unread_count: Number(unreadRow?.n || 0),
    content: rows.map((r) => ({
      ...r,
      read: !!r.read,
      data: (() => { try { return JSON.parse(r.data); } catch { return {}; } })(),
      created: formatDateCN(r.created_at, true),
    })),
  });
}

async function markNotifications(request, env) {
  const body = await readJson(request);
  if (toBool(body.all, false)) {
    await dbRun(env, 'UPDATE notifications SET read = 1 WHERE read = 0');
    return json({ ok: true });
  }
  const ids = Array.isArray(body.ids) ? body.ids.map((x) => toInt(x, 0)).filter((x) => x > 0).slice(0, 200) : [];
  if (!ids.length) throw new HttpError(400, '缺少通知 id');
  const placeholders = ids.map(() => '?').join(',');
  await dbRun(env, `UPDATE notifications SET read = 1 WHERE id IN (${placeholders})`, ...ids);
  return json({ ok: true });
}

async function adminFeedStatus(env) {
  const rows = await dbAll(
    env,
    `SELECT id, title, url, feed_url, feed_kind, feed_enabled, last_error, fail_count,
            last_fetched_at, next_fetch_at
       FROM friends ORDER BY sort ASC, id ASC`
  );
  return json({
    ok: true,
    content: rows.map((r) => ({
      ...r,
      feed_enabled: !!r.feed_enabled,
      last_fetched: r.last_fetched_at ? formatDateCN(r.last_fetched_at, true) : '',
      next_fetch: r.next_fetch_at ? formatDateCN(r.next_fetch_at, true) : '',
    })),
  });
}

async function adminExport(env) {
  const [friends, applications, talks, articles, notifications] = await Promise.all([
    dbAll(env, 'SELECT * FROM friends ORDER BY id ASC'),
    dbAll(env, 'SELECT * FROM friend_applications ORDER BY id ASC'),
    dbAll(env, 'SELECT * FROM talks ORDER BY created_at DESC'),
    dbAll(env, 'SELECT * FROM feed_articles ORDER BY published_at DESC LIMIT 5000'),
    dbAll(env, 'SELECT * FROM notifications ORDER BY id DESC LIMIT 5000'),
  ]);
  return json({
    ok: true,
    exported_at: now(),
    data: { friends, applications, talks, articles, notifications },
  });
}

/* ------------------------------------------------------------------ */
/* 朋友圈抓取                                                          */
/* ------------------------------------------------------------------ */

function escapeRegExp(s) {
  return String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function getTag(xml, name) {
  const re = new RegExp(`<${escapeRegExp(name)}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${escapeRegExp(name)}>`, 'i');
  const m = re.exec(String(xml || ''));
  if (!m) return '';
  return decodeEntities(m[1]).trim();
}

function getAttr(tag, name) {
  const re = new RegExp(`\\b${escapeRegExp(name)}\\s*=\\s*("([^"]*)"|'([^']*)'|([^\\s>]+))`, 'i');
  const m = re.exec(String(tag || ''));
  if (!m) return '';
  return decodeEntities(m[2] || m[3] || m[4] || '').trim();
}

function looksLikeFeed(body) {
  const head = String(body || '').slice(0, 2000);
  return /<rss[\s>]/i.test(head) || /<feed[\s>]/i.test(head) || /<rdf:RDF[\s>]/i.test(head);
}

function atomLink(entry) {
  const tags = String(entry).match(/<link\b[^>]*>/gi) || [];
  let fallback = '';
  for (const tag of tags) {
    const href = getAttr(tag, 'href');
    if (!href) continue;
    const rel = getAttr(tag, 'rel');
    if (!rel || rel === 'alternate') return href;
    if (!fallback) fallback = href;
  }
  return fallback;
}

function parseFeed(body, feedUrl, friend) {
  const xml = String(body || '');
  const isAtom = /<feed[\s>]/i.test(xml.slice(0, 2000));
  const blocks = isAtom
    ? xml.match(/<entry\b[\s\S]*?<\/entry>/gi) || []
    : xml.match(/<item\b[\s\S]*?<\/item>/gi) || [];
  const items = [];
  const seen = new Set();
  const t = now();

  for (const block of blocks.slice(0, 30)) {
    let title = stripHtml(getTag(block, 'title'));
    let link = '';
    let rawDate = '';
    let author = '';
    let summary = '';

    if (isAtom) {
      link = atomLink(block);
      rawDate = getTag(block, 'published') || getTag(block, 'updated') || getTag(block, 'created');
      author = stripHtml(getTag(block, 'name')) || stripHtml(getTag(block, 'author'));
      summary = getTag(block, 'summary') || getTag(block, 'content');
    } else {
      link = stripHtml(getTag(block, 'link')) || stripHtml(getTag(block, 'guid'));
      rawDate = getTag(block, 'pubDate') || getTag(block, 'published') || getTag(block, 'updated') || getTag(block, 'dc:date');
      author = stripHtml(getTag(block, 'dc:creator')) || stripHtml(getTag(block, 'author'));
      summary = getTag(block, 'content:encoded') || getTag(block, 'description') || getTag(block, 'summary');
      if (!link) {
        const linkTags = block.match(/<link\b[^>]*>/gi) || [];
        for (const tag of linkTags) {
          const href = getAttr(tag, 'href');
          if (href) { link = href; break; }
        }
      }
    }

    if (!title) title = stripHtml(summary).slice(0, 80);
    if (!title) continue;
    try { link = new URL(link, feedUrl).href; } catch { link = ''; }
    if (!link || seen.has(link)) continue;
    seen.add(link);

    let published = Date.parse(rawDate);
    if (!Number.isFinite(published)) published = t;
    published = Math.min(published, t);

    const cleanSummary = stripHtml(summary).slice(0, 220);
    items.push({
      title,
      link,
      author: author || friend.title || '',
      avatar: friend.avatar || '',
      summary: cleanSummary,
      published_at: published,
    });
  }
  return items;
}

async function fetchText(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeout || 12000);
  try {
    const resp = await fetch(url, {
      ...options,
      signal: controller.signal,
      redirect: 'follow',
      headers: {
        'User-Agent': options.userAgent || UA,
        Accept: 'application/rss+xml, application/atom+xml, application/xml, text/xml, text/html, */*',
        ...(options.headers || {}),
      },
    });
    const body = resp.status === 304 ? '' : await resp.text();
    return {
      ok: resp.ok,
      status: resp.status,
      url: resp.url || url,
      body,
      contentType: resp.headers.get('content-type') || '',
      etag: resp.headers.get('etag') || '',
      lastModified: resp.headers.get('last-modified') || '',
    };
  } finally {
    clearTimeout(timer);
  }
}

function feedCandidates(siteUrl, html) {
  const out = [];
  const seen = new Set();
  const add = (href) => {
    if (!href) return;
    try {
      const u = new URL(href, siteUrl).href;
      if (!seen.has(u)) { seen.add(u); out.push(u); }
    } catch { /* ignore */ }
  };

  const linkTags = html.match(/<link\b[^>]*>/gi) || [];
  for (const tag of linkTags) {
    const rel = getAttr(tag, 'rel').toLowerCase();
    const type = getAttr(tag, 'type').toLowerCase();
    if (!rel.includes('alternate')) continue;
    if (!/rss|atom|xml/.test(type)) continue;
    add(getAttr(tag, 'href'));
  }
  // 某些站点把订阅入口写成 a 标签
  const anchors = html.match(/<a\b[^>]*>/gi) || [];
  for (const tag of anchors) {
    const href = getAttr(tag, 'href');
    if (!href) continue;
    if (/atom\.xml|rss\.xml|feed\.xml|\/feed\/?($|\?)|index\.xml/i.test(href)) add(href);
  }
  if (/\.xml(\?|$)/i.test(siteUrl)) add(siteUrl);

  for (const path of ['atom.xml', 'feed.xml', 'rss.xml', 'index.xml', '/atom.xml', '/feed', '/feed/', '/rss.xml', '/rss', '/feed.xml']) {
    add(path);
  }
  return out;
}

async function discoverFeed(siteUrl) {
  let site;
  try {
    site = await fetchText(siteUrl, { userAgent: BROWSER_UA });
  } catch {
    site = await fetchText(siteUrl, { userAgent: UA });
  }
  if (!site.ok) throw new Error(`站点返回 HTTP ${site.status}`);

  const finalUrl = site.url || siteUrl;
  if (looksLikeFeed(site.body)) {
    return { ok: true, status: 200, feedUrl: finalUrl, body: site.body, etag: site.etag, lastModified: site.lastModified };
  }
  if (/rss|atom|xml/i.test(site.contentType) && looksLikeFeed(site.body)) {
    return { ok: true, status: 200, feedUrl: finalUrl, body: site.body, etag: site.etag, lastModified: site.lastModified };
  }

  const candidates = feedCandidates(finalUrl, site.body);
  let lastError = '没有找到 RSS/Atom 订阅地址';
  for (const candidate of candidates) {
    try {
      const r = await fetchText(candidate, { userAgent: UA, timeout: 9000 });
      if (r.ok && looksLikeFeed(r.body)) {
        return {
          ok: true,
          status: 200,
          feedUrl: r.url || candidate,
          body: r.body,
          etag: r.etag,
          lastModified: r.lastModified,
        };
      }
      lastError = `候选订阅返回 HTTP ${r.status}`;
    } catch (e) {
      lastError = e?.name === 'AbortError' ? '候选订阅超时' : String(e?.message || e);
    }
  }
  throw new Error(lastError);
}

/* ------------------------------------------------------------------ */
/* 朋友圈 HTML / Sitemap 爬虫回退                                       */
/* ------------------------------------------------------------------ */

const CRAWL_MAX_ITEMS = 8;
const CRAWL_FETCH_PAGES = 3;
const CRAWL_ARTICLE_HINT = /\/(?:posts?|articles?|p|blog|notes?|essays?|tech|life)\/|(?:19|20)\d{2}[/-]\d{1,2}/i;
const CRAWL_SKIP_PATH = /(^\/$|\/(?:about|about-me|friend|friends|link|links|tag|tags|category|categories|search|login|signin|signup|register|comment|comments|page|author|cdn-cgi|wp-admin|wp-login|feed|rss|sitemap|robots|privacy|terms|contact)(?:\/|$)|^\/archives?\/?$|\.(?:css|js|mjs|json|xml|txt|png|jpe?g|gif|webp|svg|ico|pdf|zip|rar|gz|mp4|mp3)$)/i;

function metaContent(html, key) {
  const wanted = String(key || '').toLowerCase();
  for (const tag of String(html || '').match(/<meta\b[^>]*>/gi) || []) {
    const prop = (getAttr(tag, 'property') || getAttr(tag, 'name') || '').toLowerCase();
    if (prop === wanted) {
      const content = getAttr(tag, 'content');
      if (content) return content;
    }
  }
  return '';
}

function dateFromUrl(url) {
  try {
    const path = new URL(url).pathname;
    let match = path.match(/\/(20\d{2})[/-](\d{1,2})(?:[/-](\d{1,2}))?(?:[/-]|$)/);
    if (match) {
      return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3] || 1));
    }
    match = path.match(/\/(20\d{2})(\d{2})(\d{2})(?:[/-]|$)/);
    if (match) return Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  } catch { /* ignore */ }
  return NaN;
}

function extractPageMeta(html, pageUrl, fallbackTitle = '') {
  const source = String(html || '').slice(0, 120_000);
  const pageTitle = stripHtml(
    metaContent(source, 'og:title') ||
    metaContent(source, 'twitter:title') ||
    getTag(source, 'title') ||
    ''
  );
  const fallback = stripHtml(fallbackTitle);
  const genericFallback = /^(阅读全文|阅读更多|继续阅读|详情|查看更多|read more|more)$/i.test(fallback);
  let title = pageTitle || fallback;
  // 有些主题的 <title> 会拼上站点名/摘要/日期；首页链接文字通常更干净
  if (fallback && !genericFallback && fallback.length >= 6 && title.length > fallback.length + 12) {
    title = fallback;
  }
  title = title.slice(0, 160);
  const description = stripHtml(
    metaContent(source, 'og:description') ||
    metaContent(source, 'twitter:description') ||
    metaContent(source, 'description') ||
    ''
  ).slice(0, 220);
  let rawDate = metaContent(source, 'article:published_time') ||
    metaContent(source, 'og:article:published_time') ||
    metaContent(source, 'og:published_time') ||
    metaContent(source, 'datePublished') ||
    metaContent(source, 'date') ||
    metaContent(source, 'pubdate') ||
    '';
  if (!rawDate) {
    const jsonLd = source.match(/"datePublished"\s*:\s*"([^"]+)"/i);
    if (jsonLd) rawDate = jsonLd[1];
  }
  if (!rawDate) {
    const timeTag = (source.match(/<time\b[^>]*>/gi) || [])
      .map((tag) => getAttr(tag, 'datetime'))
      .find(Boolean);
    if (timeTag) rawDate = timeTag;
  }
  let published = Date.parse(rawDate);
  if (!Number.isFinite(published)) published = dateFromUrl(pageUrl);
  if (!Number.isFinite(published)) published = now();
  return { title, description, published_at: Math.min(published, now()) };
}

function cleanLinkText(value) {
  return stripHtml(value).replace(/\s+/g, ' ').trim();
}

function shouldSkipCrawlLink(url, siteUrl) {
  try {
    const u = new URL(url);
    const site = new URL(siteUrl);
    if (u.origin !== site.origin) return true;
    if (!/^https?:$/.test(u.protocol)) return true;
    const isQueryPost = /(?:^|[?&])p=\d+(?:&|$)/.test(u.search);
    if (u.pathname === '/' && isQueryPost) return false;
    if (CRAWL_SKIP_PATH.test(u.pathname)) return true;
    return false;
  } catch {
    return true;
  }
}

function linkScore(url, text, siteUrl, lastmod = '') {
  let score = 0;
  try {
    const u = new URL(url);
    const path = u.pathname;
    if (CRAWL_ARTICLE_HINT.test(path)) score += 5;
    if (/(?:^|[?&])p=\d+(?:&|$)/.test(u.search)) score += 5;
    if (path.split('/').filter(Boolean).length >= 2) score += 1;
    if (/(?:19|20)\d{2}[/-]\d{1,2}/.test(path)) score += 2;
    const textLength = cleanLinkText(text).length;
    if (textLength >= 6) score += 2;
    if (textLength >= 12) score += 2;
    if (textLength >= 20) score += 1;
    if (lastmod) score += 2;
    if (shouldSkipCrawlLink(url, siteUrl)) score -= 100;
  } catch {
    score = -100;
  }
  return score;
}

function extractArticleLinks(html, siteUrl, limit = 12) {
  const source = String(html || '').slice(0, 800_000);
  const raw = [];
  const seen = new Set();
  const add = (href, text, lastmod = '') => {
    if (!href) return;
    let url;
    try {
      url = new URL(href, siteUrl).href.split('#')[0];
    } catch {
      return;
    }
    if (!url || seen.has(url) || url === siteUrl) return;
    if (shouldSkipCrawlLink(url, siteUrl)) return;
    const title = cleanLinkText(text);
    if (title.length < 3) return;
    seen.add(url);
    raw.push({ url, title, lastmod });
  };

  for (const block of source.match(/<article\b[\s\S]*?<\/article>/gi) || []) {
    const match = block.match(/<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/i);
    if (!match) continue;
    const heading = block.match(/<h[1-4]\b[^>]*>([\s\S]*?)<\/h[1-4]>/i);
    add(match[1], heading ? stripHtml(heading[1]) : match[2]);
  }

  for (const match of source.matchAll(/<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
    add(match[1], match[2]);
  }

  return raw
    .map((item) => ({ ...item, score: linkScore(item.url, item.title, siteUrl, item.lastmod) }))
    .filter((item) => item.score > 3)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit);
}

function parseSitemapUrls(xml, baseUrl) {
  const source = String(xml || '').slice(0, 1_000_000);
  const out = [];
  const seen = new Set();
  for (const block of source.match(/<url\b[\s\S]*?<\/url>/gi) || []) {
    const loc = stripHtml(getTag(block, 'loc'));
    if (!loc) continue;
    let url;
    try {
      url = new URL(loc, baseUrl).href.split('#')[0];
    } catch {
      continue;
    }
    if (seen.has(url)) continue;
    seen.add(url);
    out.push({ url, lastmod: stripHtml(getTag(block, 'lastmod')) });
  }
  return out;
}

function sitemapChildren(xml, baseUrl) {
  const source = String(xml || '').slice(0, 500_000);
  const out = [];
  for (const block of source.match(/<sitemap\b[\s\S]*?<\/sitemap>/gi) || []) {
    const loc = stripHtml(getTag(block, 'loc'));
    if (!loc) continue;
    try {
      out.push(new URL(loc, baseUrl).href);
    } catch { /* ignore */ }
  }
  return out.slice(0, 3);
}

async function discoverSitemap(siteUrl) {
  const candidates = [];
  const seen = new Set();
  const add = (value) => {
    if (!value) return;
    try {
      const url = new URL(value, siteUrl).href;
      if (!seen.has(url)) {
        seen.add(url);
        candidates.push(url);
      }
    } catch { /* ignore */ }
  };

  try {
    const robots = await fetchText(new URL('/robots.txt', siteUrl).href, {
      userAgent: BROWSER_UA,
      timeout: 8000,
    });
    if (robots.ok) {
      for (const match of robots.body.matchAll(/^\s*Sitemap:\s*(\S+)\s*$/gim)) add(match[1]);
    }
  } catch { /* ignore */ }

  for (const path of ['/sitemap.xml', '/sitemap_index.xml', '/sitemap-index.xml', '/wp-sitemap.xml']) add(path);

  for (const candidate of candidates.slice(0, 6)) {
    try {
      const res = await fetchText(candidate, { userAgent: BROWSER_UA, timeout: 7000 });
      if (!res.ok) continue;
      const body = res.body || '';
      if (/<sitemapindex[\s>]/i.test(body)) {
        for (const child of sitemapChildren(body, res.url || candidate)) {
          const childRes = await fetchText(child, { userAgent: BROWSER_UA, timeout: 7000 });
          if (!childRes.ok) continue;
          const urls = parseSitemapUrls(childRes.body, childRes.url || child);
          if (urls.length) return { sourceUrl: candidate, urls };
        }
      } else if (/<urlset[\s>]/i.test(body)) {
        const urls = parseSitemapUrls(body, res.url || candidate);
        if (urls.length) return { sourceUrl: candidate, urls };
      }
    } catch { /* try next */ }
  }
  return null;
}

async function crawlFromCandidates(candidates, friend) {
  const selected = candidates.slice(0, CRAWL_MAX_ITEMS);
  const metaResults = await Promise.all(
    selected.slice(0, CRAWL_FETCH_PAGES).map(async (candidate) => {
      try {
        const page = await fetchText(candidate.url, {
          userAgent: BROWSER_UA,
          timeout: 6000,
          headers: { Accept: 'text/html,application/xhtml+xml' },
        });
        if (!page.ok) return null;
        return extractPageMeta(page.body, candidate.url, candidate.title);
      } catch {
        return null;
      }
    })
  );

  const items = [];
  const seen = new Set();
  for (const [index, candidate] of selected.entries()) {
    let title = candidate.title || '';
    let summary = '';
    let published = dateFromUrl(candidate.url);
    if (!Number.isFinite(published) && candidate.lastmod) published = Date.parse(candidate.lastmod);
    if (!Number.isFinite(published)) published = now();
    const meta = metaResults[index];
    if (meta) {
      if (meta.title) title = meta.title;
      if (meta.description) summary = meta.description;
      if (Number.isFinite(meta.published_at)) published = meta.published_at;
    }

    if (!title) {
      try {
        const path = new URL(candidate.url).pathname;
        const tail = decodeURIComponent(path.split('/').filter(Boolean).pop() || '')
          .replace(/\.html?$/i, '')
          .replace(/[-_]+/g, ' ')
          .trim();
        title = tail || '未命名文章';
      } catch {
        title = '未命名文章';
      }
    }
    if (seen.has(candidate.url)) continue;
    seen.add(candidate.url);
    items.push({
      title: title.slice(0, 200),
      link: candidate.url,
      author: friend.title || '',
      avatar: friend.avatar || '',
      summary: summary.slice(0, 220),
      published_at: Math.min(published, now()),
    });
  }
  return items;
}

async function crawlFriendSite(friend, preferred = 'auto') {
  const t = now();
  let sitemapError = '';

  if (preferred !== 'html') {
    try {
      const sitemap = await discoverSitemap(friend.url);
      if (sitemap) {
        const urls = sitemap.urls
          .filter((entry) => !shouldSkipCrawlLink(entry.url, friend.url))
          .map((entry) => ({
            ...entry,
            title: '',
            score: linkScore(entry.url, '', friend.url, entry.lastmod),
          }))
          .filter((entry) => entry.score > 0)
          .sort((a, b) => (Date.parse(b.lastmod) || 0) - (Date.parse(a.lastmod) || 0))
          .slice(0, CRAWL_MAX_ITEMS);
        if (urls.length) {
          const items = await crawlFromCandidates(urls, friend);
          if (items.length) return { mode: 'sitemap', sourceUrl: sitemap.sourceUrl, items, at: t };
        }
      }
    } catch (e) {
      sitemapError = String(e?.message || e);
    }
  }

  try {
    const home = await fetchText(friend.url, { userAgent: BROWSER_UA, timeout: 12000 });
    if (!home.ok) throw new Error(`主页返回 HTTP ${home.status}`);
    const candidates = extractArticleLinks(home.body, home.url || friend.url);
    if (!candidates.length) throw new Error('主页没有找到可识别的文章链接');
    const items = await crawlFromCandidates(candidates, friend);
    if (!items.length) throw new Error('没有解析到文章元数据');
    return { mode: 'html', sourceUrl: home.url || friend.url, items, at: t };
  } catch (e) {
    const htmlError = String(e?.message || e);
    throw new Error(sitemapError ? `${sitemapError}；${htmlError}` : htmlError);
  }
}


async function insertArticles(env, friend, items, t) {
  if (!items.length) return 0;
  let inserted = 0;
  const stmts = items.map((item) =>
    env.DB.prepare(
      `INSERT OR IGNORE INTO feed_articles
         (friend_id, title, link, author, avatar, summary, published_at, fetched_at)
       VALUES (?,?,?,?,?,?,?,?)`
    ).bind(
      friend.id, item.title, item.link, item.author, item.avatar, item.summary, item.published_at, t
    )
  );
  try {
    const results = await env.DB.batch(stmts);
    inserted = results.reduce((sum, result) => sum + Number(result?.meta?.changes || 0), 0);
  } catch {
    for (const statement of stmts) {
      try {
        const result = await statement.run();
        inserted += Number(result?.meta?.changes || 0);
      } catch { /* duplicate / bad row */ }
    }
  }
  return inserted;
}

async function refreshFriend(env, friend) {
  const t = now();
  const feedNext = t + 45 * 60 * 1000;
  const crawlNext = t + 90 * 60 * 1000;
  let feedUrl = friend.feed_url || '';
  const feedKind = friend.feed_kind || 'feed';
  let feedError = '';

  if (feedKind === 'feed') {
    let feedResponse = null;
    try {
      if (!feedUrl) {
        const discovered = await discoverFeed(friend.url);
        feedUrl = discovered.feedUrl;
        feedResponse = discovered;
      } else {
        const headers = {};
        if (friend.etag) headers['If-None-Match'] = friend.etag;
        if (friend.last_modified) headers['If-Modified-Since'] = friend.last_modified;
        feedResponse = await fetchText(feedUrl, { userAgent: UA, headers });
      }
    } catch (e) {
      feedError = clampStr(String(e?.message || e), 200) || '订阅抓取失败';
      feedResponse = null;
    }

    // 已有订阅地址失效时，再尝试重新发现一次
    if (!feedResponse && feedUrl) {
      try {
        const discovered = await discoverFeed(friend.url);
        feedUrl = discovered.feedUrl;
        feedResponse = discovered;
        feedError = '';
      } catch (e) {
        feedError = clampStr(String(e?.message || e), 200) || feedError;
      }
    }

    if (feedResponse) {
      if (feedResponse.status === 304) {
        await dbRun(
          env,
          `UPDATE friends SET last_fetched_at = ?, last_error = NULL, fail_count = 0,
             feed_kind = 'feed', next_fetch_at = ?, updated_at = ? WHERE id = ?`,
          t, feedNext, t, friend.id
        );
        return { ok: true, friend_id: friend.id, mode: 'feed', not_modified: true, count: 0 };
      }
      if (!feedResponse.ok) {
        feedError = `订阅返回 HTTP ${feedResponse.status}`;
      } else if (!looksLikeFeed(feedResponse.body)) {
        feedError = '返回内容不是 RSS/Atom';
      } else {
        const items = parseFeed(feedResponse.body, feedUrl, friend);
        const inserted = await insertArticles(env, friend, items, t);
        await dbRun(
          env,
          `UPDATE friends SET feed_url = ?, feed_kind = 'feed', etag = ?, last_modified = ?,
             last_fetched_at = ?, last_error = NULL, fail_count = 0, next_fetch_at = ?, updated_at = ?
           WHERE id = ?`,
          feedUrl,
          feedResponse.etag || null,
          feedResponse.lastModified || null,
          t,
          feedNext,
          t,
          friend.id
        );
        return {
          ok: true,
          friend_id: friend.id,
          mode: 'feed',
          feed_url: feedUrl,
          count: items.length,
          inserted,
        };
      }
    }
  }

  // 没有 RSS/Atom、或订阅已失效：尝试 sitemap → 首页 HTML
  try {
    const crawl = await crawlFriendSite(friend, feedKind === 'html' ? 'html' : 'auto');
    const inserted = await insertArticles(env, friend, crawl.items, t);
    await dbRun(
      env,
      `UPDATE friends SET feed_url = ?, feed_kind = ?, etag = NULL, last_modified = NULL,
         last_fetched_at = ?, last_error = NULL, fail_count = 0, next_fetch_at = ?, updated_at = ?
       WHERE id = ?`,
      crawl.sourceUrl,
      crawl.mode,
      t,
      crawlNext,
      t,
      friend.id
    );
    return {
      ok: true,
      friend_id: friend.id,
      mode: crawl.mode,
      source: crawl.sourceUrl,
      count: crawl.items.length,
      inserted,
    };
  } catch (e) {
    const crawlError = clampStr(String(e?.message || e), 200) || '爬取失败';
    const message = clampStr(
      feedError ? `${feedError}；爬取失败：${crawlError}` : `爬取失败：${crawlError}`,
      300
    );
    const failCount = Number(friend.fail_count || 0) + 1;
    const backoff = Math.min(failCount, 5) * 30 * 60 * 1000;
    await dbRun(
      env,
      `UPDATE friends SET last_error = ?, fail_count = ?, next_fetch_at = ?, updated_at = ? WHERE id = ?`,
      message,
      failCount,
      t + 30 * 60 * 1000 + backoff,
      t,
      friend.id
    );
    return { ok: false, friend_id: friend.id, error: message };
  }
}

async function processNextFeed(env) {
  const t = now();
  const friend = await dbFirst(
    env,
    `SELECT * FROM friends
      WHERE feed_enabled = 1 AND visible = 1
        AND (next_fetch_at IS NULL OR next_fetch_at <= ?)
      ORDER BY COALESCE(next_fetch_at, 0) ASC, id ASC
      LIMIT 1`,
    t
  );
  if (!friend) return { ok: true, idle: true };
  await dbRun(env, 'UPDATE friends SET next_fetch_at = ? WHERE id = ?', t + 10 * 60 * 1000, friend.id);
  return refreshFriend(env, friend);
}

async function manualFeedRefresh(request, env) {
  const body = await readJson(request);
  const friendId = toInt(body.friend_id, 0);
  if (friendId > 0) {
    const friend = await dbFirst(env, 'SELECT * FROM friends WHERE id = ?', friendId);
    if (!friend) throw new HttpError(404, '友链不存在');
    const result = await refreshFriend(env, friend);
    return json({ ok: true, results: [result] });
  }
  const limit = Math.min(Math.max(toInt(body.limit, 3), 1), 5);
  const results = [];
  for (let i = 0; i < limit; i++) {
    const result = await processNextFeed(env);
    if (result.idle) break;
    results.push(result);
  }
  return json({ ok: true, results });
}

/* ------------------------------------------------------------------ */
/* 路由                                                                */
/* ------------------------------------------------------------------ */

async function handleAdmin(request, env, path, method) {
  const { session } = await requireSession(request, env);

  if (path === '/admin/me' && method === 'GET') return adminMe(request, env, session);
  if (path === '/admin/session' && method === 'DELETE') {
    await dbRun(env, 'DELETE FROM sessions WHERE id = ?', session.id);
    return json({ ok: true });
  }
  if (path === '/admin/stats' && method === 'GET') return adminStats(env);

  if (path === '/admin/applications' && method === 'GET') return adminApplications(request, env);
  let m = path.match(/^\/admin\/applications\/(\d+)\/approve$/);
  if (m && method === 'POST') return approveApplication(env, Number(m[1]));
  m = path.match(/^\/admin\/applications\/(\d+)\/reject$/);
  if (m && method === 'POST') return rejectApplication(request, env, Number(m[1]));

  if (path === '/admin/friends' && method === 'GET') return adminFriends(env);
  if (path === '/admin/friends' && method === 'POST') return createFriend(request, env);
  if (path === '/admin/friends/import' && method === 'POST') return importFriends(request, env);
  m = path.match(/^\/admin\/friends\/(\d+)$/);
  if (m && method === 'PATCH') return updateFriend(request, env, Number(m[1]));
  if (m && method === 'DELETE') return deleteFriend(env, Number(m[1]));

  if (path === '/admin/talks' && method === 'GET') return adminTalks(request, env);
  if (path === '/admin/talks' && method === 'POST') return createTalk(request, env);
  if (path === '/admin/talks/import' && method === 'POST') return importTalks(request, env);
  m = path.match(/^\/admin\/talks\/([^/]+)$/);
  if (m && method === 'PATCH') return updateTalk(request, env, decodeURIComponent(m[1]));
  if (m && method === 'DELETE') return deleteTalk(env, decodeURIComponent(m[1]));

  if (path === '/admin/notifications' && method === 'GET') return adminNotifications(request, env);
  if (path === '/admin/notifications/read' && method === 'POST') return markNotifications(request, env);

  if (path === '/admin/feed-status' && method === 'GET') return adminFeedStatus(env);
  if (path === '/admin/feeds/refresh' && method === 'POST') return manualFeedRefresh(request, env);
  if (path === '/admin/export' && method === 'GET') return adminExport(env);

  throw new HttpError(404, '接口不存在');
}

async function route(request, env) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, '') || '/';
  const method = request.method.toUpperCase();

  if (method === 'OPTIONS') return preflight();

  if (path === '/' && method === 'GET') {
    return json({
      name: 'LvbyteStudio API',
      version: VERSION,
      site: env.SITE_URL || 'https://blog.lvbyte.top',
      endpoints: ['/health', '/friends', '/friends/apply', '/talks', '/feeds'],
    });
  }

  if (path === '/health' && method === 'GET') {
    return json({ ok: true, version: VERSION, time: now() });
  }

  if (path === '/friends' && method === 'GET') {
    return json(await listFriends(env), 200, { 'Cache-Control': 'public, max-age=60' });
  }
  if (path === '/friends/apply' && method === 'POST') return applyFriend(request, env);
  if (path === '/talks' && method === 'GET') return listTalks(request, env);
  if (path === '/feeds' && method === 'GET') return listFeeds(request, env);
  if (path === '/auth/github' && method === 'POST') return handleGithubLogin(request, env);

  if (path.startsWith('/admin/')) return handleAdmin(request, env, path, method);

  throw new HttpError(404, '接口不存在');
}

async function cleanup(env) {
  const t = now();
  await dbRun(env, 'DELETE FROM sessions WHERE expires_at <= ?', t);
  await dbRun(env, 'DELETE FROM feed_articles WHERE fetched_at < ?', t - 180 * 24 * 60 * 60 * 1000);
  await dbRun(env, 'DELETE FROM notifications WHERE read = 1 AND created_at < ?', t - 90 * 24 * 60 * 60 * 1000);
}

export default {
  async fetch(request, env) {
    try {
      return await route(request, env);
    } catch (e) {
      if (e instanceof HttpError) {
        return json({ ok: false, error: e.message }, e.status);
      }
      console.error('unhandled', e);
      return json({ ok: false, error: '服务器内部错误' }, 500);
    }
  },

  async scheduled(event, env, ctx) {
    ctx.waitUntil(
      (async () => {
        try {
          const result = await processNextFeed(env);
          console.log('feed tick', JSON.stringify(result));
          const minute = new Date().getUTCMinutes();
          if (minute === 0) await cleanup(env);
        } catch (e) {
          console.error('feed tick failed', e);
        }
      })()
    );
  },
};

/* 供本地单元测试使用（部署时也会保留，不影响运行） */
export const __test = {
  parseFeed,
  looksLikeFeed,
  feedCandidates,
  stripHtml,
  decodeEntities,
  formatDateCN,
  extractArticleLinks,
  extractPageMeta,
  parseSitemapUrls,
  dateFromUrl,
  linkScore,
  shouldSkipCrawlLink,
};
