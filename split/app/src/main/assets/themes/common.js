/*
 * أدوات مشتركة لكل الثيمات. يُحمَّل قبل سكربت الثيم:
 *   <script src="common.js"></script>
 * يتكفل بـ: لون الثيم (--accent)، الخلفية المخصصة، شريط التنبيهات، وحدات السرعة،
 * وبيانات وهمية عند فتح الثيم في متصفح عادي للمعاينة.
 * الثيم يكتب window.onSahraData(d) كالمعتاد.
 */
(function () {
  var css =
    'html.has-wp body{background:transparent!important}' +
    '#sahra-wp{position:fixed;inset:0;z-index:-1;background-size:cover;background-position:center;display:none}' +
    '#sahra-wp:after{content:"";position:absolute;inset:0;background:#000;opacity:var(--wp-dim,.55)}' +
    '#sahra-alerts{position:fixed;top:1.4vh;left:50%;transform:translateX(-50%);z-index:50;display:flex;flex-direction:column;gap:.8vh;align-items:center;pointer-events:none;direction:rtl}' +
    '#sahra-alerts div{padding:.9vh 1.6vw;border-radius:5vh;font-weight:700;font-size:clamp(14px,1.4vw,2.6vh);color:#fff;' +
    'box-shadow:0 6px 24px rgba(0,0,0,.45);animation:sahraIn .3s ease-out}' +
    '#sahra-alerts .warn{background:#b7791f}#sahra-alerts .danger{background:#c53030;animation:sahraIn .3s ease-out,sahraPulse 1.2s infinite}' +
    '@keyframes sahraIn{from{opacity:0;transform:translateY(-8px)}to{opacity:1}}' +
    '@keyframes sahraPulse{50%{filter:brightness(1.35)}}' +
    // على شاشة العدادات لا لمس، فتختفي الأزرار
    'html.cluster button,html.cluster .btns{display:none!important}' +
    // ثقب الملاحة: الصفحة شفافة، وخلفيتها الأصلية تنتقل إلى طبقة تُقص مع باقي الصفحة
    'html.nav-hole,html.nav-hole body{background:transparent!important}' +
    '#sahra-back{position:fixed;inset:0;z-index:-2;display:none}html.nav-hole #sahra-back{display:block}' +
    '#sahra-nav{position:fixed;z-index:60;border:3px dashed #F3B300;border-radius:8px;display:none;pointer-events:none;' +
    'color:#F3B300;font:700 18px Kufi,sans-serif;align-items:flex-start;justify-content:center;padding-top:8px;box-sizing:border-box}';
  var st = document.createElement('style');
  st.textContent = css;
  document.head.appendChild(st);

  var wp, alertsBox, back, navBox, lastWp = null, lastAlerts = '', lastHole = '';
  function ensure() {
    if (wp) return;
    wp = document.createElement('div'); wp.id = 'sahra-wp';
    alertsBox = document.createElement('div'); alertsBox.id = 'sahra-alerts';
    back = document.createElement('div'); back.id = 'sahra-back';
    navBox = document.createElement('div'); navBox.id = 'sahra-nav'; navBox.textContent = 'الملاحة';
    document.body.insertBefore(wp, document.body.firstChild);
    document.body.insertBefore(back, document.body.firstChild);
    document.body.appendChild(alertsBox);
    document.body.appendChild(navBox);
  }

  function pct(v) { return (Math.max(0, Math.min(1, +v || 0)) * 100).toFixed(3) + '%'; }

  /*
   * نافذة الملاحة. حين تكون الملاحة على العدادات داخل الثيم: تصير الصفحة شفافة ويُقص مستطيل النافذة
   * من الصفحة كلها (مضلع خارجي مع عقارب الساعة وداخلي عكسها)، فتظهر الخريطة من خلفه.
   * وفي المحرر حين تُحدَّد النافذة: إطار متقطع يبيّن مكانها.
   */
  function applyNav(n, sel) {
    n = n || {};
    var root = document.documentElement;
    var hole = !!(n.on && !n.full);
    var x1 = pct(n.x), y1 = pct(n.y), x2 = pct((+n.x || 0) + (+n.w || 0)), y2 = pct((+n.y || 0) + (+n.h || 0));
    var key = hole ? [x1, y1, x2, y2].join() : '';
    if (key !== lastHole) {
      lastHole = key;
      if (hole) {
        // انقل الخلفية الأصلية إلى طبقة خلف المحتوى قبل جعل الصفحة شفافة
        if (!root.classList.contains('nav-hole')) {
          var hs = getComputedStyle(root), bs = getComputedStyle(document.body);
          back.style.backgroundColor = bs.backgroundColor !== 'rgba(0, 0, 0, 0)' ? bs.backgroundColor : hs.backgroundColor;
          back.style.backgroundImage = bs.backgroundImage !== 'none' ? bs.backgroundImage : hs.backgroundImage;
        }
        root.classList.add('nav-hole');
        root.style.clipPath = 'polygon(0 0,100% 0,100% 100%,0 100%,0 ' + y1 + ',' + x1 + ' ' + y1 + ',' + x1 + ' ' + y2 + ',' +
          x2 + ' ' + y2 + ',' + x2 + ' ' + y1 + ',' + x1 + ' ' + y1 + ',0 ' + y1 + ')';
      } else {
        root.classList.remove('nav-hole');
        root.style.clipPath = '';
      }
    }
    if (sel === 'nav') {
      navBox.style.left = x1; navBox.style.top = y1;
      navBox.style.width = pct(n.w); navBox.style.height = pct(n.h);
      navBox.style.display = 'flex';
    } else navBox.style.display = 'none';
  }

  var Sahra = window.Sahra = {
    /** السرعة بالوحدة التي اختارها السائق */
    speed: function (d) { var v = d.speed || 0; return (d.style && d.style.units === 'mph') ? v / 1.609 : v; },
    unit: function (d) { return (d.style && d.style.units === 'mph') ? 'mph' : 'كم/س'; },
    speedMax: function (d) { var m = (d.style && d.style.speedMax) || 220; return (d.style && d.style.units === 'mph') ? Math.round(m / 1.609 / 10) * 10 : m; },
    rpmMax: function (d) { return (d.style && d.style.rpmMax) || 7000; },
    accent: function (d) { return (d.style && d.style.accent) || null; },
    design: function (d) { return (d.style && d.style.design) || {}; },
    /** شكل الإبرة في مربع 200×200 ومركزه (100,100) */
    needlePath: function (style) {
      if (style === 'thin') return 'M99.2 24 L100.8 24 L101.6 108 L98.4 108 Z';
      if (style === 'arrow') return 'M100 22 L106 38 L102 38 L102.5 100 L97.5 100 L98 38 L94 38 Z';
      return 'M100 30 L103 100 L97 100 Z';
    },
    num: function (v, digits, suffix) {
      return (v === null || v === undefined || !isFinite(v)) ? '—' : Number(v).toFixed(digits || 0) + (suffix || '');
    },
    int: function (v) { return (v === null || v === undefined || !isFinite(v)) ? '—' : Math.round(v).toLocaleString('en-US'); },
    host: function (fn, arg) {
      try { if (window.SahraHost) { arg === undefined ? window.SahraHost[fn]() : window.SahraHost[fn](arg); } } catch (e) {}
    },
    clock: function () { var t = new Date(); return ('0' + t.getHours()).slice(-2) + ':' + ('0' + t.getMinutes()).slice(-2); }
  };

  // تصميم المصمم: ألوان العناصر والحجم والموضع والتبديل، كمتغيرات CSS يستخدمها كل ثيم
  var lastDesign = '';
  function applyDesign(g) {
    var key = JSON.stringify(g);
    if (key === lastDesign) return;
    lastDesign = key;
    var r = document.documentElement.style;
    [['needleColor', '--needle'], ['tickColor', '--tick'], ['numberColor', '--num'], ['panelColor', '--panel-c']].forEach(function (p) {
      if (g[p[0]]) r.setProperty(p[1], g[p[0]]); else r.removeProperty(p[1]);
    });
    r.setProperty('--g-scale', ((g.scale || 100) / 100).toString());
    r.setProperty('--g-shift', (g.shift || 0) + 'vw');
    document.documentElement.classList.toggle('swap', !!g.swap);
    document.documentElement.setAttribute('data-needle', g.needleStyle || 'classic');
  }

  window.__sahra = function (d) {
    ensure();
    document.documentElement.classList.toggle('cluster', d.layout === 'cluster');
    // تصحيح الحجم: إن كانت الصفحة أكبر من النافذة الحقيقية (خطأ الكثافة على شاشة العدادات) نصغّرها لتطابقها
    if (d.view && d.view.w > 0 && d.view.h > 0) {
      var k = Math.min(d.view.w / devicePixelRatio / innerWidth, d.view.h / devicePixelRatio / innerHeight);
      // تحجيم الصفحة كلها (يشمل وحدات vw/vh) من الزاوية العليا اليسرى
      var z = Math.abs(k - 1) > 0.01 ? 'scale(' + k.toFixed(4) + ')' : '';
      var h = document.documentElement.style;
      if (h.transform !== z) { h.transform = z; h.transformOrigin = '0 0'; h.overflow = 'hidden'; }
    }
    // قياسات للضبط: مقاس الصفحة كما يراه الثيم، ومقاس النافذة كما يراه التطبيق
    var dbg = document.getElementById('sahra-dbg');
    if (d.debug) {
      if (!dbg) { dbg = document.createElement('div'); dbg.id = 'sahra-dbg';
        dbg.style.cssText = 'position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);z-index:99;background:#ffd400;color:#000;font:bold 22px monospace;padding:10px 16px;border-radius:8px;direction:ltr;white-space:pre';
        document.body.appendChild(dbg); }
      dbg.textContent = 'page ' + innerWidth + 'x' + innerHeight + ' dpr ' + devicePixelRatio + ' fit ' + (document.documentElement.style.transform || 'none') + '\n' + (d.debug || '');
    } else if (dbg) dbg.remove();
    var s = d.style || {};
    if (s.accent) document.documentElement.style.setProperty('--accent', s.accent);
    applyDesign(s.design || {});
    if (s.wallpaper !== lastWp) {
      lastWp = s.wallpaper || null;
      if (lastWp) {
        wp.style.backgroundImage = 'url("' + lastWp + '")';
        wp.style.display = 'block';
        document.documentElement.classList.add('has-wp');
      } else {
        wp.style.display = 'none';
        document.documentElement.classList.remove('has-wp');
      }
    }
    if (s.wallpaperDim != null) document.documentElement.style.setProperty('--wp-dim', s.wallpaperDim);
    var key = JSON.stringify(d.alerts || []);
    if (key !== lastAlerts) {
      lastAlerts = key;
      alertsBox.innerHTML = '';
      (d.alerts || []).slice(0, 2).forEach(function (a) {
        var el = document.createElement('div'); el.className = a.level; el.textContent = a.text; alertsBox.appendChild(el);
      });
    }
    applyNav(d.nav, d.edit && d.edit.sel);
    if (window.onSahraData) window.onSahraData(d);
  };

  // معاينة في المتصفح: بيانات متحركة وهمية
  window.addEventListener('load', function () {
    if (window.SahraHost) { if (window.SahraData) window.__sahra(window.SahraData); return; }
    var t = 0, q = new URLSearchParams(location.search);
    setInterval(function () {
      t += 0.1;
      var c = (Math.sin(t / 6) + 1) / 2;
      window.__sahra({
        speed: c * 140, rpm: 900 + c * 3800 + 300 * Math.sin(t * 1.7), gear: c < 0.01 ? 'N' : 'D',
        fuel: q.has('lowfuel') ? 9 : 62, range: 438, odometer: 23518, coolant: 88, outsideTemp: 34, insideTemp: 23,
        batteryVoltage: 14.1, batteryLevel: 86, steering: 25 * Math.sin(t / 2), pitch: 12 * Math.sin(t / 3.1),
        roll: 8 * Math.sin(t / 2.3), compass: (t * 6) % 360, altitude: 612 + 40 * Math.sin(t / 9), demo: true,
        media: { title: 'أغنية تجريبية', artist: 'فنان', playing: true, art: '' },
        style: { accent: q.get('accent') || null, speedMax: 220, rpmMax: 7000, units: q.get('units') || 'kmh', wallpaper: null, wallpaperDim: 0.55,
          design: { needleStyle: q.get('needle') || 'classic', swap: q.has('swap'), scale: +(q.get('scale') || 100), shift: +(q.get('shift') || 0),
            needleColor: q.get('nc') ? '#' + q.get('nc') : '', tickColor: q.get('tc') ? '#' + q.get('tc') : '',
            numberColor: q.get('num') ? '#' + q.get('num') : '', panelColor: q.get('pc') ? '#' + q.get('pc') : '' } },
        alerts: q.has('lowfuel') ? [{ id: 'fuel', text: 'الوقود منخفض', level: 'warn' }] : [],
        gearLabel: c < 0.01 ? 'N' : 'D' + (1 + Math.floor(c * 7)), gearNo: 1 + Math.floor(c * 7),
        tires: [33, 32.6, q.has('lowtire') ? 26 : 31.1, 32.8], tireTemps: [31, 30, 29, 30], tireWarnPsi: 28,
        journey: 36.4, trip: 2538.3, avgConsumption: 15.7, baro: 950,
        nav: { on: q.has('nav'), full: false, x: 0.3125, y: 0.15, w: 0.375, h: 0.64 },
        edit: { sel: q.get('sel') || '' },
        layout: q.get('layout') || 'full'
      });
    }, 100);
  });
})();
