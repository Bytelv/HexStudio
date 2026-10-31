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
  if (body.feed_url !== undefined) add('feed_url', normalizeUrl(body.feed_url) || null);
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
    `SELECT id, title, url, feed_url, feed_enabled, last_error, fail_count,
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

async function refreshFriend(env, friend) {
  const t = now();
  const next = t + 45 * 60 * 1000;
  let feedUrl = friend.feed_url || '';
  let feedResponse = null;
  let discovered = null;

  try {
    if (!feedUrl) {
      discovered = await discoverFeed(friend.url);
      feedUrl = discovered.feedUrl;
      feedResponse = discovered;
    } else {
      const headers = {};
      if (friend.etag) headers['If-None-Match'] = friend.etag;
      if (friend.last_modified) headers['If-Modified-Since'] = friend.last_modified;
      try {
        feedResponse = await fetchText(feedUrl, { userAgent: UA, headers });
      } catch (e) {
        // 订阅地址可能已经失效，重新发现一次
        discovered = await discoverFeed(friend.url);
        feedUrl = discovered.feedUrl;
        feedResponse = discovered;
      }
    }

    if (feedResponse.status === 304) {
      await dbRun(
        env,
        `UPDATE friends SET last_fetched_at = ?, last_error = NULL, fail_count = 0,
           next_fetch_at = ?, updated_at = ? WHERE id = ?`,
        t, next, t, friend.id
      );
      return { ok: true, friend_id: friend.id, not_modified: true, count: 0 };
    }
    if (!feedResponse.ok) throw new Error(`订阅返回 HTTP ${feedResponse.status}`);
    if (!looksLikeFeed(feedResponse.body)) throw new Error('返回内容不是 RSS/Atom');

    const items = parseFeed(feedResponse.body, feedUrl, friend);
    let inserted = 0;
    if (items.length) {
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
        await env.DB.batch(stmts);
        inserted = items.length;
      } catch {
        for (const s of stmts) {
          try { await s.run(); inserted++; } catch { /* duplicate */ }
        }
      }
    }

    await dbRun(
      env,
      `UPDATE friends SET feed_url = ?, etag = ?, last_modified = ?, last_fetched_at = ?,
         last_error = NULL, fail_count = 0, next_fetch_at = ?, updated_at = ? WHERE id = ?`,
      feedUrl,
      feedResponse.etag || null,
      feedResponse.lastModified || null,
      t,
      next,
      t,
      friend.id
    );
    return { ok: true, friend_id: friend.id, feed_url: feedUrl, count: items.length, inserted };
  } catch (e) {
    const message = clampStr(e?.message || e, 300) || '抓取失败';
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
};
