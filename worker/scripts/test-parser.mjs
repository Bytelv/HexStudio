import assert from 'node:assert/strict';
import { __test } from '../src/index.js';

const rss = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
<channel>
  <title>Example Blog</title>
  <item>
    <title><![CDATA[Hello &amp; World]]></title>
    <link>https://example.com/posts/1</link>
    <pubDate>Wed, 30 Oct 2026 12:00:00 GMT</pubDate>
    <dc:creator>Alice</dc:creator>
    <description><![CDATA[<p>Summary <b>bold</b> text</p>]]></description>
  </item>
  <item>
    <title>Second</title>
    <link>https://example.com/posts/2</link>
    <pubDate>Thu, 31 Oct 2026 08:30:00 +0800</pubDate>
    <description>No author</description>
  </item>
  <item>
    <title>Duplicate</title>
    <link>https://example.com/posts/1</link>
    <pubDate>Fri, 01 Nov 2026 08:30:00 +0800</pubDate>
  </item>
</channel>
</rss>`;

const atom = `<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Atom Blog</title>
  <entry>
    <title>Atom Post</title>
    <link rel="alternate" href="/atom/post-1"/>
    <published>2026-10-29T10:00:00Z</published>
    <author><name>Bob</name></author>
    <summary>An atom summary</summary>
  </entry>
</feed>`;

const friend = { title: 'Friend Blog', avatar: 'https://img.example/avatar.png' };

const rssItems = __test.parseFeed(rss, 'https://example.com/atom.xml', friend);
assert.equal(rssItems.length, 2);
assert.equal(rssItems[0].title, 'Hello & World');
assert.equal(rssItems[0].link, 'https://example.com/posts/1');
assert.equal(rssItems[0].author, 'Alice');
assert.equal(rssItems[0].summary, 'Summary bold text');
assert.equal(rssItems[1].author, 'Friend Blog');

const atomItems = __test.parseFeed(atom, 'https://example.com/feed.xml', friend);
assert.equal(atomItems.length, 1);
assert.equal(atomItems[0].link, 'https://example.com/atom/post-1');
assert.equal(atomItems[0].author, 'Bob');

const undated = `<rss version="2.0"><channel><item>
<title>No date here</title><link>https://example.com/posts/undated</link>
<description>Still a valid item</description>
</item></channel></rss>`;
const undatedItems = __test.parseFeed(undated, 'https://example.com/feed.xml', friend);
assert.equal(undatedItems.length, 1);
assert.equal(undatedItems[0].published_at, 0, 'missing feed date must not become crawl time');

assert.equal(
  new Date(__test.dateFromText('发布于 2022年12月04日')).toISOString().slice(0, 10),
  '2022-12-04'
);
assert.equal(
  new Date(__test.dateFromText('SpeedUp! June 7, 2022')).toISOString().slice(0, 10),
  '2022-06-07'
);
assert.equal(__test.parseDateValue('1609459200000'), 1609459200000);

for (const code of [200, 301, 401, 403, 429, 499]) {
  assert.equal(__test.isAliveStatus(code), true, `HTTP ${code} should count as reachable`);
}
for (const code of [0, 404, 410, 500, 502, 526, 530]) {
  assert.equal(__test.isAliveStatus(code), false, `HTTP ${code} should count as unreachable`);
}

assert.equal(__test.looksLikeFeed(rss), true);
assert.equal(__test.looksLikeFeed(atom), true);
assert.equal(__test.looksLikeFeed('<html>nope</html>'), false);

const html = `<html><head>
<link rel="alternate" type="application/rss+xml" href="/rss.xml">
<link rel="alternate" type="application/atom+xml" href="https://cdn.example.com/atom.xml">
</head></html>`;
const candidates = __test.feedCandidates('https://example.com/blog/', html);
assert.ok(candidates.includes('https://example.com/rss.xml'));
assert.ok(candidates.includes('https://cdn.example.com/atom.xml'));

assert.equal(__test.decodeEntities('a &amp; b &#x1F600;'), 'a & b 😀');
assert.equal(__test.stripHtml('<p>Hello</p><script>bad()</script>world'), 'Hello world');

const sampleHtml = `<!doctype html><html><head>
<title>Site Name</title>
<meta property="og:title" content="Crawled Post">
<meta property="article:published_time" content="2026-10-30T10:00:00+08:00">
<meta name="description" content="A crawled summary">
</head><body>
<nav><a href="/about">About</a><a href="/tags/tech">Tech</a><a href="https://other.example/x">External</a></nav>
<article><h2><a href="/posts/hello-world">Hello World</a></h2></article>
<a href="/2026/10/30/another-post">Another Post</a>
<a href="/?p=123">WordPress Post</a>
</body></html>`;
const crawled = __test.extractArticleLinks(sampleHtml, 'https://example.com/');
const crawledUrls = crawled.map((item) => item.url);
assert.ok(crawledUrls.includes('https://example.com/posts/hello-world'));
assert.ok(crawledUrls.includes('https://example.com/2026/10/30/another-post'));
assert.ok(crawledUrls.includes('https://example.com/?p=123'));
assert.ok(!crawledUrls.includes('https://example.com/about'));
assert.ok(!crawledUrls.includes('https://example.com/tags/tech'));
assert.ok(!crawledUrls.some((url) => url.includes('other.example')));

const meta = __test.extractPageMeta(sampleHtml, 'https://example.com/posts/hello-world');
assert.equal(meta.title, 'Crawled Post');
assert.equal(meta.description, 'A crawled summary');
assert.ok(meta.published_at > 0);

const visibleDateHtml = `<html><head><title>Old Post</title></head><body>
<div class="post-meta">2022年12月04日</div><article><p>正文</p></article>
</body></html>`;
const visibleMeta = __test.extractPageMeta(
  visibleDateHtml,
  'https://nur512.cn/index.php/archives/179/'
);
assert.equal(
  new Date(visibleMeta.published_at).toISOString().slice(0, 10),
  '2022-12-04',
  'visible Chinese date should be parsed'
);

const numericTimeHtml = `<html><head><title>Numeric time</title></head><body>
<time datetime="1609459200000"></time></body></html>`;
const numericMeta = __test.extractPageMeta(numericTimeHtml, 'https://www.anzifan.com/post/demo');
assert.equal(
  new Date(numericMeta.published_at).toISOString().slice(0, 10),
  '2021-01-01',
  'numeric time datetime should be parsed'
);

const undatedHtml = `<html><head><title>Undated page</title></head><body><p>no date</p></body></html>`;
const undatedMeta = __test.extractPageMeta(undatedHtml, 'https://example.com/archives/1');
assert.equal(undatedMeta.published_at, 0, 'unknown page date must stay unknown');

const sitemap = `<?xml version="1.0"?><urlset><url><loc>https://example.com/posts/a</loc><lastmod>2026-10-29</lastmod></url><url><loc>https://example.com/about</loc></url></urlset>`;
const sitemapUrls = __test.parseSitemapUrls(sitemap, 'https://example.com/');
assert.equal(sitemapUrls.length, 2);
assert.equal(sitemapUrls[0].url, 'https://example.com/posts/a');
assert.equal(sitemapUrls[0].lastmod, '2026-10-29');

const dated = __test.dateFromUrl('https://example.com/2026/10/30/post');
assert.ok(Number.isFinite(dated));
assert.equal(new Date(dated).toISOString().slice(0, 10), '2026-10-30');

console.log('parser tests passed', {
  rss: rssItems.length,
  atom: atomItems.length,
  candidates: candidates.slice(0, 5),
});
