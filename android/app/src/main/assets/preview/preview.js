(function () {
  'use strict';

  var preview = document.getElementById('preview');

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, '&#96;');
  }

  function parseArgs(raw) {
    var tokens = String(raw || '').trim().split(/\s+/).filter(Boolean);
    var opts = {};
    var pos = [];
    tokens.forEach(function (token) {
      var idx = token.indexOf(':');
      if (idx > 0) {
        opts[token.slice(0, idx)] = token.slice(idx + 1);
      } else {
        pos.push(token);
      }
    });
    return { opts: opts, pos: pos };
  }

  function colorClass(opts) {
    var color = String(opts.color || '').trim();
    return color ? ' lv-note-' + color.replace(/[^a-zA-Z0-9_-]/g, '') : '';
  }

  function protectCode(text) {
    var blocks = [];
    var out = String(text).replace(/```[\s\S]*?```/g, function (block) {
      blocks.push(block);
      return '\u0000CODE' + (blocks.length - 1) + '\u0000';
    });
    return { text: out, blocks: blocks };
  }

  function restoreCode(text, blocks) {
    return String(text).replace(/\u0000CODE(\d+)\u0000/g, function (_, idx) {
      return blocks[Number(idx)] || '';
    });
  }

  function mdInline(text) {
    try {
      return marked.parseInline(String(text || ''), { gfm: true, breaks: false });
    } catch (e) {
      return escapeHtml(text);
    }
  }

  function renderInner(text) {
    var prepared = preprocess(String(text || ''));
    try {
      return marked.parse(prepared, { gfm: true, breaks: true });
    } catch (e) {
      return '<pre>' + escapeHtml(text) + '</pre>';
    }
  }

  function replaceBlock(text, tag, handler) {
    var re = new RegExp('\\{%\\s*' + tag + '\\s+([^%]*?)%\\}([\\s\\S]*?)\\{%\\s*end' + tag + '\\s*%\\}', 'gi');
    return String(text).replace(re, function (match, rawArgs, body) {
      return handler(rawArgs, body);
    });
  }

  function tabsBlock(rawArgs, body) {
    var args = parseArgs(rawArgs);
    var parts = String(body).split(/<!--\s*tab\s+(.*?)\s*-->/g);
    var tabs = [];
    for (var i = 1; i < parts.length; i += 2) {
      tabs.push({ title: parts[i], body: parts[i + 1] || '' });
    }
    if (!tabs.length) {
      tabs.push({ title: '标签一', body: body });
    }
    var active = Math.max(1, Math.min(Number(args.opts.active) || 1, tabs.length)) - 1;
    var nav = tabs.map(function (tab, index) {
      return '<button type="button" class="' + (index === active ? 'active' : '') + '" data-tab="' + index + '">' +
        escapeHtml(tab.title) + '</button>';
    }).join('');
    var panes = tabs.map(function (tab, index) {
      return '<div class="lv-tab-pane ' + (index === active ? 'active' : '') + '">' + renderInner(tab.body) + '</div>';
    }).join('');
    return '<div class="lv-tabs"><div class="lv-tabs-nav">' + nav + '</div>' + panes + '</div>';
  }

  function gridBlock(rawArgs, body) {
    var args = parseArgs(rawArgs);
    var cells = String(body).split(/<!--\s*cell\s*-->/g).filter(function (cell) {
      return cell.trim().length > 0;
    });
    var columns = Number(args.opts.c) || Math.max(1, cells.length);
    var style = 'grid-template-columns:repeat(' + columns + ',minmax(0,1fr));';
    if (args.opts.gap) style += 'gap:' + args.opts.gap + ';';
    var html = cells.map(function (cell) {
      return '<div class="lv-grid-cell">' + renderInner(cell) + '</div>';
    }).join('');
    return '<div class="lv-grid" style="' + escapeAttr(style) + '">' + html + '</div>';
  }

  function timelineBlock(rawArgs, body) {
    var parts = String(body).split(/<!--\s*node\s+(.*?)\s*-->/g);
    var html = '';
    for (var i = 1; i < parts.length; i += 2) {
      html += '<div class="lv-timeline-node"><div class="header">' + mdInline(parts[i]) + '</div>' +
        '<div class="body">' + renderInner(parts[i + 1] || '') + '</div></div>';
    }
    if (!html) html = '<div class="lv-timeline-node">' + renderInner(body) + '</div>';
    return '<div class="lv-timeline">' + html + '</div>';
  }

  function foldingBlock(rawArgs, body) {
    var args = parseArgs(rawArgs);
    var title = args.pos.length ? args.pos.join(' ') : '展开';
    var open = args.opts.open === 'true' ? ' open' : '';
    var color = colorClass(args.opts);
    return '<details class="lv-folding ' + color.trim() + '"' + open + '><summary>' +
      escapeHtml(title) + '</summary><div class="lv-fold-body">' + renderInner(body) + '</div></details>';
  }

  function inlineTag(text, tag, builder) {
    var re = new RegExp('\\{%\\s*' + tag + '\\s+([\\s\\S]*?)%\\}', 'gi');
    return String(text).replace(re, function (_, content) {
      return builder(String(content || '').trim());
    });
  }

  function preprocess(markdown) {
    var protectedBlock = protectCode(markdown);
    var text = protectedBlock.text;

    text = replaceBlock(text, 'folding', foldingBlock);
    text = replaceBlock(text, 'tabs', tabsBlock);
    text = replaceBlock(text, 'grid', gridBlock);
    text = replaceBlock(text, 'timeline', timelineBlock);
    text = replaceBlock(text, 'folders', function (rawArgs, body) {
      var args = parseArgs(rawArgs);
      return '<details class="lv-folding"><summary>' + escapeHtml(args.pos.join(' ') || '文件夹') +
        '</summary><div class="lv-fold-body">' + renderInner(body) + '</div></details>';
    });

    // note：{% note color:red 标题 内容 %}
    text = String(text).replace(/\{%\s*note\s+([^%]*?)%\}/gi, function (_, rawArgs) {
      var args = parseArgs(rawArgs);
      var color = colorClass(args.opts);
      var title = '';
      var content = '';
      if (args.pos.length <= 1) {
        content = args.pos.join(' ');
      } else {
        title = args.pos[0];
        content = args.pos.slice(1).join(' ');
      }
      return '<div class="lv-note' + color + '">' +
        (title ? '<div class="lv-note-title">' + escapeHtml(title) + '</div>' : '') +
        '<div class="lv-note-body">' + mdInline(content) + '</div></div>';
    });

    // image：{% image src [alt] %}
    text = String(text).replace(/\{%\s*image\s+([^%]*?)%\}/gi, function (_, rawArgs) {
      var args = parseArgs(rawArgs);
      var src = args.opts.src || args.pos[0] || '';
      var alt = args.pos[1] || '';
      var style = '';
      if (args.opts.width) style += 'width:' + args.opts.width + ';';
      if (args.opts.height) style += 'height:' + args.opts.height + ';';
      return '<figure class="lv-image"><img src="' + escapeAttr(src) + '" alt="' + escapeAttr(alt) +
        '" style="' + escapeAttr(style) + '" loading="lazy">' +
        (alt ? '<figcaption>' + escapeHtml(alt) + '</figcaption>' : '') + '</figure>';
    });

    // frame：{% frame iphone11 img:src [alt] %}
    text = String(text).replace(/\{%\s*frame\s+([^%]*?)%\}/gi, function (_, rawArgs) {
      var args = parseArgs(rawArgs);
      var device = args.pos[0] || 'phone';
      var src = args.opts.img || '';
      var alt = args.pos[1] || '';
      return '<figure class="lv-frame"><div class="lv-frame-device lv-device-' + escapeAttr(device) +
        '"><img src="' + escapeAttr(src) + '" alt="' + escapeAttr(alt) + '" loading="lazy"></div>' +
        (alt ? '<figcaption>' + escapeHtml(alt) + '</figcaption>' : '') + '</figure>';
    });

    // button：{% button [color:blue] text url %}
    text = String(text).replace(/\{%\s*button\s+([^%]*?)%\}/gi, function (_, rawArgs) {
      var args = parseArgs(rawArgs);
      var textValue = args.pos[0] || '按钮';
      var url = args.pos[1] || '#';
      var color = args.opts.color ? ' lv-color-' + args.opts.color.replace(/[^a-zA-Z0-9_-]/g, '') : '';
      return '<a class="lv-button' + color + '" href="' + escapeAttr(url) + '" target="_blank" rel="noopener noreferrer">' +
        escapeHtml(textValue) + '</a>';
    });

    // link：{% link url [title] %}
    text = String(text).replace(/\{%\s*link\s+([^%]*?)%\}/gi, function (_, rawArgs) {
      var args = parseArgs(rawArgs);
      var url = args.opts.url || args.pos[0] || '#';
      var title = args.opts.title || args.pos[1] || url;
      return '<a class="lv-link-card" href="' + escapeAttr(url) + '" target="_blank" rel="noopener noreferrer">' +
        escapeHtml(title) + '</a>';
    });

    // quot：{% quot [el:h2] text %}
    text = String(text).replace(/\{%\s*quot\s+([^%]*?)%\}/gi, function (_, rawArgs) {
      var args = parseArgs(rawArgs);
      var el = ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'div'].indexOf(args.opts.el) >= 0 ? args.opts.el : 'h2';
      return '<' + el + ' class="lv-quot">' + mdInline(args.pos.join(' ')) + '</' + el + '>';
    });

    // copy：{% copy text %}
    text = String(text).replace(/\{%\s*copy\s+([^%]*?)%\}/gi, function (_, rawArgs) {
      var args = parseArgs(rawArgs);
      return '<span class="lv-copy">' + escapeHtml(args.pos.join(' ') || rawArgs.trim()) + '</span>';
    });

    text = inlineTag(text, 'u', function (value) { return '<span class="lv-u">' + escapeHtml(value) + '</span>'; });
    text = inlineTag(text, 'emp', function (value) { return '<span class="lv-emp">' + escapeHtml(value) + '</span>'; });
    text = inlineTag(text, 'wavy', function (value) { return '<span class="lv-wavy">' + escapeHtml(value) + '</span>'; });
    text = inlineTag(text, 'kbd', function (value) { return '<kbd class="lv-kbd">' + escapeHtml(value) + '</kbd>'; });
    text = inlineTag(text, 'blur', function (value) { return '<span class="lv-blur">' + escapeHtml(value) + '</span>'; });
    text = inlineTag(text, 'psw', function (value) { return '<span class="lv-psw" title="' + escapeAttr(value) + '">••••</span>'; });
    text = inlineTag(text, 'mark', function (value) { return '<mark>' + escapeHtml(value) + '</mark>'; });

    // 未支持的标签：保留可见提示
    text = String(text).replace(/\{%\s*([a-zA-Z][\w-]*)([^%]*)%\}/g, function (_, name) {
      return '<span class="lv-unknown-tag">{% ' + escapeHtml(name) + ' %}</span>';
    });

    return restoreCode(text, protectedBlock.blocks);
  }

  function bindTabs() {
    document.querySelectorAll('.lv-tabs').forEach(function (root) {
      var buttons = root.querySelectorAll('.lv-tabs-nav button');
      var panes = root.querySelectorAll('.lv-tab-pane');
      buttons.forEach(function (button, index) {
        button.addEventListener('click', function () {
          buttons.forEach(function (b) { b.classList.remove('active'); });
          panes.forEach(function (p) { p.classList.remove('active'); });
          button.classList.add('active');
          if (panes[index]) panes[index].classList.add('active');
        });
      });
    });
  }

  window.renderPreview = function (markdown, dark) {
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    var source = String(markdown || '');
    var prepared = preprocess(source);
    var html;
    try {
      html = marked.parse(prepared, { gfm: true, breaks: true });
    } catch (e) {
      html = '<pre>' + escapeHtml(source) + '</pre>';
    }
    var safe = DOMPurify.sanitize(html, {
      ADD_ATTR: ['target', 'open', 'loading', 'rel']
    });
    preview.innerHTML = safe;
    preview.querySelectorAll('a[href]').forEach(function (a) {
      a.setAttribute('target', '_blank');
      a.setAttribute('rel', 'noopener noreferrer');
    });
    bindTabs();
    window.scrollTo(0, 0);
  };

  window.renderError = function (message) {
    preview.innerHTML = '<div class="lv-note lv-note-red"><div class="lv-note-title">预览失败</div><div class="lv-note-body">' +
      escapeHtml(message) + '</div></div>';
  };
})();
