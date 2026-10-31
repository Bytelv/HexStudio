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

console.log('parser tests passed', {
  rss: rssItems.length,
  atom: atomItems.length,
  candidates: candidates.slice(0, 5),
});
