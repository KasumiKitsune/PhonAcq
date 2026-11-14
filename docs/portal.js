(function () {
  "use strict";

  var SVG_NS = "http://www.w3.org/2000/svg";

  function svgEl(tag, attrs, text) {
    var el = document.createElementNS(SVG_NS, tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) {
        el.setAttribute(k, String(attrs[k]));
      });
    }
    if (typeof text === "string") {
      el.textContent = text;
    }
    return el;
  }

  function byId(id) {
    return document.getElementById(id);
  }

  // =========================================================================
  // 真实苏州方言声韵调与看图调查样本数据
  // =========================================================================
  var SPEAKERS = {
    SPK01: {
      id: "SPK01",
      short: "沈",
      name: "沈阿婆 · SPK01",
      meta: "女 · 71岁 · 苏州城区老派 ⇄",
      avatarBg: "#DBEAFE",
      avatarFg: "#1E3A8A",
      f0Scale: 1.0,
      formantScale: 1.0
    },
    SPK02: {
      id: "SPK02",
      short: "周",
      name: "周老伯 · SPK02",
      meta: "男 · 74岁 · 苏州城区老派 ⇄",
      avatarBg: "#CCFBF1",
      avatarFg: "#134E4A",
      f0Scale: 0.61,
      formantScale: 0.87
    }
  };

  var WORDS = [
    {
      num: "01",
      char: "衣",
      cat: "01 · 阴平 · 闭前元音",
      gloss: "阴平 44 · 衣服（例：一件新衣裳）",
      tRef: "[44]",
      ipaLabel: "衣 [i]",
      dur: 0.68,
      f0Curve: [238, 236, 235, 234, 232],
      f1: 295,
      f2: 2290,
      f3: 3080,
      takes: 3,
      hasImage: false,
      defaultNotes: {
        SPK01: "[i⁴⁴] 擦音轻微，元音平稳",
        SPK02: "[i⁴⁴] 起音略带舌叶摩擦"
      }
    },
    {
      num: "05",
      char: "时",
      cat: "05 · 阳平 · 浊擦音",
      gloss: "阳平 24 · 时间（例：辰光、时候）",
      tRef: "[24]",
      ipaLabel: "时 [ɿ]",
      dur: 0.72,
      f0Curve: [152, 166, 188, 212, 230],
      f1: 365,
      f2: 1550,
      f3: 2710,
      takes: 2,
      hasImage: false,
      defaultNotes: {
        SPK01: "[zɿ²⁴] 清音浊流起始清晰",
        SPK02: "[zɿ²⁴] 前段微降后稳步扬升"
      }
    },
    {
      num: "07",
      char: "果",
      cat: "07 · 上声 · 后半闭",
      gloss: "上声 51 · 水果（例：果子）",
      tRef: "[51]",
      ipaLabel: "果 [u]",
      dur: 0.64,
      f0Curve: [246, 224, 192, 162, 138],
      f1: 430,
      f2: 885,
      f3: 2520,
      takes: 1,
      hasImage: false,
      defaultNotes: {
        SPK01: "[ku⁵¹] 高降调干脆，收尾微嘎裂",
        SPK02: "[ku⁵¹] 唇形圆拢，高降明显"
      }
    },
    {
      num: "09",
      char: "菜",
      cat: "09 · 阴去 · 曲折调",
      gloss: "阴去 412 · 青菜（例：小菜场）",
      tRef: "[412]",
      ipaLabel: "菜 [E]",
      dur: 0.76,
      f0Curve: [224, 174, 146, 164, 188],
      f1: 565,
      f2: 1960,
      f3: 2810,
      takes: 2,
      hasImage: false,
      defaultNotes: {
        SPK01: "[tsʰE⁴¹²] 单念降升曲折清楚",
        SPK02: "[tsʰE⁴¹²] 尾段微扬，单元音化"
      }
    },
    {
      num: "11",
      char: "笔",
      cat: "11 · 阴入 · 促声韵",
      gloss: "阴入 5 · 毛笔（短促喉塞尾）",
      tRef: "[5]",
      ipaLabel: "笔 [iɪʔ]",
      dur: 0.34,
      f0Curve: [248, 246, 243, 240, 236],
      f1: 345,
      f2: 2140,
      f3: 2940,
      takes: 1,
      hasImage: false,
      defaultNotes: {
        SPK01: "[piɪʔ⁵] 短促高平，喉塞收尾[-ʔ]",
        SPK02: "[piɪʔ⁵] 促声时长仅约舒声一半"
      }
    },
    {
      num: "12",
      char: "日头",
      cat: "12 · 看图识词 · 自然天文",
      gloss: "日常口语 · 太阳（看图自然说出）",
      tRef: "[5+24]",
      ipaLabel: "日头 [ɤ]",
      dur: 0.84,
      f0Curve: [236, 176, 168, 198, 224],
      f1: 510,
      f2: 1380,
      f3: 2680,
      takes: 2,
      hasImage: true,
      defaultNotes: {
        SPK01: "[niəʔ⁵ dɤ²⁴] 看图径说“日头”，未受字面干扰",
        SPK02: "[niəʔ⁵ dɤ²⁴] 白读鼻音声母，连读顺畅"
      }
    }
  ];

  var IPA_BANKS = {
    tone: {
      label: "调值符号",
      items: ["⁴⁴", "²⁴", "⁵¹", "⁴¹²", "²³¹", "ʔ⁵", "ʔ²³", "轻声"]
    },
    init: {
      label: "常用声母",
      items: ["pʰ", "tʰ", "tsʰ", "z", "ʑ", "ȵ", "ŋ", "ɦ"]
    },
    fin: {
      label: "方言韵母",
      items: ["ɿ", "ʮ", "ɪ", "E", "ø", "ɤ", "ɑ̃", "əʔ"]
    },
    tag: {
      label: "田野标签",
      items: ["[白读]", "[文读]", "[老派]", "[喉塞尾]", "[清音浊流]", "[待复核]"]
    }
  };

  // =========================================================================
  // 1. 主工作台 SVG 沙盘状态与渲染
  // =========================================================================
  var studioState = {
    mode: "adv", // 'adv' | 'simple'
    cleanPrompt: false,
    spkKey: "SPK01",
    wordIdx: 0,
    ipaCat: "tone",
    isRecording: false,
    isPlaying: false,
    recTimer: null,
    playRaf: null,
    notes: {},
    takesOverride: {}
  };

  // 初始化备注拷贝
  WORDS.forEach(function (w, idx) {
    studioState.notes["SPK01_" + idx] = w.defaultNotes.SPK01;
    studioState.notes["SPK02_" + idx] = w.defaultNotes.SPK02;
    studioState.takesOverride["SPK01_" + idx] = w.takes;
    studioState.takesOverride["SPK02_" + idx] = Math.max(1, w.takes - (idx % 2));
  });

  function currentNoteKey() {
    return studioState.spkKey + "_" + studioState.wordIdx;
  }

  function renderWordlistRows() {
    var container = byId("svg-wordlist-rows");
    if (!container) return;
    while (container.firstChild) container.removeChild(container.firstChild);

    WORDS.forEach(function (w, idx) {
      var active = idx === studioState.wordIdx;
      var y = idx * 72;
      var g = svgEl("g", {
        class: "svg-clickable word-row",
        transform: "translate(0, " + y + ")"
      });

      g.appendChild(
        svgEl("rect", {
          class: "row-bg",
          x: 0,
          y: 0,
          width: 212,
          height: 62,
          rx: 10,
          fill: active ? "#DBEAFE" : "#F8FAFC",
          stroke: active ? "#60A5FA" : "#E2E8F0",
          "stroke-width": active ? "1.6" : "1"
        })
      );

      // 序号胶囊
      g.appendChild(
        svgEl("rect", {
          x: 10,
          y: 12,
          width: 28,
          height: 20,
          rx: 6,
          fill: active ? "#1D4ED8" : "#E2E8F0"
        })
      );
      g.appendChild(
        svgEl(
          "text",
          {
            x: 24,
            y: 26,
            "text-anchor": "middle",
            "font-size": "10.5",
            "font-weight": "800",
            fill: active ? "#FFFFFF" : "#475569"
          },
          w.num
        )
      );

      // 词目文字
      g.appendChild(
        svgEl(
          "text",
          {
            x: 46,
            y: 27,
            "font-size": "15",
            "font-weight": "800",
            fill: active ? "#1E3A8A" : "#0F172A"
          },
          w.char + (w.hasImage ? " [图]" : "")
        )
      );

      // 简短记音预览
      var noteStr = studioState.notes[studioState.spkKey + "_" + idx] || "";
      if (noteStr.length > 15) noteStr = noteStr.slice(0, 15) + "…";
      g.appendChild(
        svgEl(
          "text",
          {
            x: 12,
            y: 48,
            "font-size": "10.5",
            "font-weight": "600",
            fill: active ? "#1E40AF" : "#64748B"
          },
          noteStr
        )
      );

      // 已录绿勾或遍数徽章
      var takeCnt = studioState.takesOverride[studioState.spkKey + "_" + idx] || 1;
      g.appendChild(
        svgEl("rect", {
          x: 164,
          y: 12,
          width: 38,
          height: 20,
          rx: 10,
          fill: "#CCFBF1"
        })
      );
      g.appendChild(
        svgEl(
          "text",
          {
            x: 183,
            y: 25.5,
            "text-anchor": "middle",
            "font-size": "10",
            "font-weight": "800",
            fill: "#134E4A"
          },
          takeCnt + "遍"
        )
      );

      g.addEventListener("click", function () {
        studioState.wordIdx = idx;
        renderStudio();
      });

      container.appendChild(g);
    });
  }

  function renderIpaTabsAndChips() {
    var tabsGroup = byId("svg-ipa-tabs");
    var chipsGroup = byId("svg-ipa-chips");
    if (!tabsGroup || !chipsGroup) return;

    while (tabsGroup.firstChild) tabsGroup.removeChild(tabsGroup.firstChild);
    while (chipsGroup.firstChild) chipsGroup.removeChild(chipsGroup.firstChild);

    var keys = ["tone", "init", "fin", "tag"];
    keys.forEach(function (key, idx) {
      var active = studioState.ipaCat === key;
      var x = idx * 107;
      var tg = svgEl("g", {
        class: "svg-clickable",
        transform: "translate(" + x + ", 0)"
      });
      tg.appendChild(
        svgEl("rect", {
          x: 0,
          y: 0,
          width: 101,
          height: 26,
          rx: 7,
          fill: active ? "#1D4ED8" : "#E2E8F0"
        })
      );
      tg.appendChild(
        svgEl(
          "text",
          {
            x: 50.5,
            y: 17,
            "text-anchor": "middle",
            "font-size": "11",
            "font-weight": "800",
            fill: active ? "#FFFFFF" : "#475569"
          },
          IPA_BANKS[key].label
        )
      );
      tg.addEventListener("click", function () {
        studioState.ipaCat = key;
        renderIpaTabsAndChips();
      });
      tabsGroup.appendChild(tg);
    });

    var symbols = IPA_BANKS[studioState.ipaCat].items;
    symbols.forEach(function (sym, i) {
      var col = i % 4;
      var row = Math.floor(i / 4);
      var cx = col * 107;
      var cy = row * 44;
      var cg = svgEl("g", {
        class: "svg-clickable ipa-chip",
        transform: "translate(" + cx + ", " + cy + ")"
      });
      cg.appendChild(
        svgEl("rect", {
          x: 0,
          y: 0,
          width: 101,
          height: 36,
          rx: 8,
          fill: "#FFFFFF",
          stroke: "#CBD5E1"
        })
      );
      cg.appendChild(
        svgEl(
          "text",
          {
            x: 50.5,
            y: 23,
            "text-anchor": "middle",
            "font-size": "13",
            "font-weight": "700",
            fill: "#0F172A"
          },
          sym
        )
      );
      cg.addEventListener("click", function () {
        var k = currentNoteKey();
        var cur = studioState.notes[k] || "";
        studioState.notes[k] = (cur + " " + sym).trim();
        renderStudio();
      });
      chipsGroup.appendChild(cg);
    });
  }

  // 根据频率映射至语图 Y 坐标 (160..350 对应 4000..0 Hz)
  function freqToSpecY(hz) {
    var clamped = Math.max(100, Math.min(3800, hz));
    return 350 - (clamped / 4000) * 190;
  }

  // 根据 F0 映射至语图 Pitch 视觉高度 (100..300 Hz -> 322..196)
  function f0ToSpecY(f0) {
    var clamped = Math.max(85, Math.min(290, f0));
    return 326 - ((clamped - 85) / 205) * 132;
  }

  // 映射 F1 / F2 到右下角元音四边形 (x: 860..1096 对应 F2 2500..750; y: 424..608 对应 F1 250..800)
  function formantToMiniVowelXY(f1, f2) {
    var x = 860 + ((2500 - Math.max(750, Math.min(2500, f2))) / 1750) * 236;
    var y = 424 + ((Math.max(250, Math.min(800, f1)) - 250) / 550) * 184;
    return { x: Math.round(x), y: Math.round(y) };
  }

  function getScaledWordMetrics(word, spk) {
    var f0Arr = word.f0Curve.map(function (v) {
      return Math.round(v * spk.f0Scale);
    });
    var f0Mean = Math.round(
      f0Arr.reduce(function (a, b) {
        return a + b;
      }, 0) / f0Arr.length
    );
    return {
      f0Arr: f0Arr,
      f0Mean: f0Mean,
      f1: Math.round(word.f1 * spk.formantScale),
      f2: Math.round(word.f2 * spk.formantScale),
      f3: Math.round(word.f3 * spk.formantScale)
    };
  }

  function renderStudio() {
    var spk = SPEAKERS[studioState.spkKey];
    var word = WORDS[studioState.wordIdx];
    var m = getScaledWordMetrics(word, spk);

    // 同步顶部 HTML 工具栏按钮激活态
    byId("btn-mode-adv").classList.toggle("active", studioState.mode === "adv");
    byId("btn-mode-simple").classList.toggle("active", studioState.mode === "simple");
    byId("btn-clean-prompt").classList.toggle("active", studioState.cleanPrompt);
    byId("btn-spk-01").classList.toggle("active", studioState.spkKey === "SPK01");
    byId("btn-spk-02").classList.toggle("active", studioState.spkKey === "SPK02");

    // 同步 SVG 顶栏胶囊
    var isAdv = studioState.mode === "adv";
    byId("svg-mode-pill-bg").setAttribute("fill", isAdv ? "#DBEAFE" : "#CCFBF1");
    byId("svg-mode-pill-bg").setAttribute("stroke", isAdv ? "#93C5FD" : "#5EEAD4");
    byId("svg-mode-pill-txt").setAttribute("fill", isAdv ? "#1E3A8A" : "#134E4A");
    byId("svg-mode-pill-txt").textContent = isAdv ? "进阶模式 · 切换" : "简洁模式 · 切换";

    byId("svg-clean-pill-bg").setAttribute("fill", studioState.cleanPrompt ? "#FEF3C7" : "#F8FAFC");
    byId("svg-clean-pill-bg").setAttribute("stroke", studioState.cleanPrompt ? "#F59E0B" : "#CBD5E1");
    byId("svg-clean-pill-txt").setAttribute("fill", studioState.cleanPrompt ? "#92400E" : "#334155");
    byId("svg-clean-pill-txt").textContent = studioState.cleanPrompt ? "退出看读" : "看读大字";

    // 发音人信息
    byId("svg-spk-avatar").setAttribute("fill", spk.avatarBg);
    byId("svg-spk-initial").setAttribute("fill", spk.avatarFg);
    byId("svg-spk-initial").textContent = spk.short;
    byId("svg-spk-name").textContent = spk.name;
    byId("svg-spk-meta").textContent = spk.meta;

    // 左栏词表
    renderWordlistRows();

    // 中栏自适应宽度（进阶三栏 vs 简洁双栏）
    byId("svg-right-pane").setAttribute("display", isAdv ? "inline" : "none");
    byId("svg-center-bg").setAttribute("width", isAdv ? "516" : "900");
    byId("svg-center-inner").setAttribute("transform", isAdv ? "translate(522, 0)" : "translate(714, 0)");

    byId("svg-word-cat").textContent = word.cat;
    var takeCount = studioState.takesOverride[currentNoteKey()] || 1;
    byId("svg-take-badge").textContent = "★ 采用第 " + takeCount + " 遍 (共" + takeCount + "遍)";

    // 看图词卡与大字排版
    var picGroup = byId("svg-pic-prompt");
    var charEl = byId("svg-target-char");
    var glossEl = byId("svg-target-gloss");

    if (word.hasImage) {
      picGroup.setAttribute("display", "inline");
      charEl.setAttribute("y", studioState.cleanPrompt ? "312" : "268");
      charEl.setAttribute("font-size", studioState.cleanPrompt ? "64" : "42");
      glossEl.setAttribute("y", "294");
    } else {
      picGroup.setAttribute("display", "none");
      charEl.setAttribute("y", studioState.cleanPrompt ? "282" : "212");
      charEl.setAttribute("font-size", studioState.cleanPrompt ? "128" : "86");
      glossEl.setAttribute("y", "256");
    }
    charEl.textContent = word.char;
    glossEl.textContent = word.gloss;
    glossEl.setAttribute("display", studioState.cleanPrompt ? "none" : "inline");

    // 记音面板 vs 简洁引导面板
    if (studioState.cleanPrompt) {
      byId("svg-ipa-panel").setAttribute("display", "none");
      byId("svg-simple-panel").setAttribute("display", "none");
    } else if (isAdv) {
      byId("svg-ipa-panel").setAttribute("display", "inline");
      byId("svg-simple-panel").setAttribute("display", "none");
      byId("svg-note-text").textContent = studioState.notes[currentNoteKey()] || "（点击下方音标按钮追加记音…）";
      renderIpaTabsAndChips();
    } else {
      byId("svg-ipa-panel").setAttribute("display", "none");
      byId("svg-simple-panel").setAttribute("display", "inline");
    }

    // 右栏声学视图更新
    if (isAdv) {
      byId("svg-right-sub").textContent = "五度参考 " + word.tRef;
      byId("svg-metric-dur").textContent = word.dur.toFixed(2) + " s";
      byId("svg-metric-f0").textContent = m.f0Mean + " Hz";
      byId("svg-metric-formants").textContent = m.f1 + " / " + m.f2;

      var bandW = word.dur < 0.45 ? 164 : 280; // 入声字直观体现短促时长
      var bandX = 838;
      var yF1 = freqToSpecY(m.f1);
      var yF2 = freqToSpecY(m.f2);
      var yF3 = freqToSpecY(m.f3);

      byId("svg-spec-f1-band").setAttribute("x", String(bandX));
      byId("svg-spec-f1-band").setAttribute("width", String(bandW));
      byId("svg-spec-f1-band").setAttribute("y", String(yF1 - 11));

      byId("svg-spec-f2-band").setAttribute("x", String(bandX));
      byId("svg-spec-f2-band").setAttribute("width", String(bandW));
      byId("svg-spec-f2-band").setAttribute("y", String(yF2 - 10));

      byId("svg-spec-f3-band").setAttribute("x", String(bandX));
      byId("svg-spec-f3-band").setAttribute("width", String(bandW));
      byId("svg-spec-f3-band").setAttribute("y", String(yF3 - 8));

      var endX = bandX + bandW - 14;
      var midX = Math.round((852 + endX) / 2);
      byId("svg-f1-track").setAttribute("d", "M 852 " + yF1 + " L " + midX + " " + yF1 + " L " + endX + " " + yF1);
      byId("svg-f2-track").setAttribute("d", "M 852 " + yF2 + " L " + midX + " " + yF2 + " L " + endX + " " + yF2);
      byId("svg-f3-track").setAttribute("d", "M 852 " + yF3 + " L " + midX + " " + yF3 + " L " + endX + " " + yF3);

      // 构造 5 点平滑 F0 曲线
      var pts = m.f0Arr.map(function (hz, i) {
        var px = 852 + (i / (m.f0Arr.length - 1)) * (endX - 852);
        var py = f0ToSpecY(hz);
        return { x: Math.round(px), y: Math.round(py) };
      });
      var dStr = "M " + pts[0].x + " " + pts[0].y;
      for (var j = 1; j < pts.length; j++) {
        var prev = pts[j - 1];
        var curr = pts[j];
        var cx = Math.round((prev.x + curr.x) / 2);
        dStr += " Q " + prev.x + " " + prev.y + " " + cx + " " + Math.round((prev.y + curr.y) / 2);
      }
      dStr += " T " + pts[pts.length - 1].x + " " + pts[pts.length - 1].y;
      byId("svg-spec-f0-glow").setAttribute("d", dStr);
      byId("svg-spec-f0-path").setAttribute("d", dStr);

      // 更新元音图落点
      var vxy = formantToMiniVowelXY(m.f1, m.f2);
      byId("svg-mini-vowel-ring").setAttribute("cx", String(vxy.x));
      byId("svg-mini-vowel-ring").setAttribute("cy", String(vxy.y));
      byId("svg-mini-vowel-dot").setAttribute("cx", String(vxy.x));
      byId("svg-mini-vowel-dot").setAttribute("cy", String(vxy.y));
      var lblX = vxy.x > 1030 ? vxy.x - 56 : vxy.x + 14;
      byId("svg-mini-vowel-label").setAttribute("x", String(lblX));
      byId("svg-mini-vowel-label").setAttribute("y", String(vxy.y + 4));
      byId("svg-mini-vowel-label").textContent = word.ipaLabel;
    }
  }

  // Web Audio 纯前端合成当前字条的基频调值轮廓（供用户点击“试听调值”直观听辨五度走势）
  var audioCtx = null;
  function playPitchContourAudio(f0Arr, durSec) {
    try {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      if (!audioCtx) audioCtx = new AC();
      if (audioCtx.state === "suspended") audioCtx.resume();

      var now = audioCtx.currentTime;
      var osc = audioCtx.createOscillator();
      var osc2 = audioCtx.createOscillator();
      var filter = audioCtx.createBiquadFilter();
      var gain = audioCtx.createGain();

      osc.type = "triangle";
      osc2.type = "sine";
      filter.type = "lowpass";
      filter.frequency.setValueAtTime(950, now);

      osc.frequency.setValueAtTime(f0Arr[0], now);
      osc2.frequency.setValueAtTime(f0Arr[0] * 2, now);
      for (var i = 1; i < f0Arr.length; i++) {
        var t = now + (i / (f0Arr.length - 1)) * durSec;
        osc.frequency.linearRampToValueAtTime(f0Arr[i], t);
        osc2.frequency.linearRampToValueAtTime(f0Arr[i] * 2, t);
      }

      gain.gain.setValueAtTime(0.001, now);
      gain.gain.linearRampToValueAtTime(0.14, now + 0.04);
      gain.gain.setValueAtTime(0.13, now + Math.max(0.06, durSec - 0.06));
      gain.gain.exponentialRampToValueAtTime(0.0008, now + durSec + 0.03);

      osc.connect(filter);
      osc2.connect(filter);
      filter.connect(gain);
      gain.connect(audioCtx.destination);

      osc.start(now);
      osc2.start(now);
      osc.stop(now + durSec + 0.05);
      osc2.stop(now + durSec + 0.05);
    } catch (e) {
      // 忽略浏览器静音策略限制
    }
  }

  function toggleStudioRecording() {
    studioState.isRecording = !studioState.isRecording;
    var pulse = byId("svg-rec-pulse");
    var recLabel = byId("svg-rec-label");
    var recBg = byId("svg-rec-bg");
    var meterFill = byId("svg-meter-fill");
    var snrLabel = byId("svg-snr-label");

    if (studioState.isRecording) {
      pulse.setAttribute("opacity", "0.55");
      pulse.classList.add("rec-pulse-ring");
      recBg.setAttribute("fill", "#991B1B");
      recLabel.textContent = "停止保存";

      studioState.recTimer = setInterval(function () {
        var w = 220 + Math.round(Math.random() * 185);
        var db = (-18 + (w - 220) / 18).toFixed(1);
        meterFill.setAttribute("width", String(w));
        meterFill.setAttribute("fill", w > 395 ? "#D97706" : "#0F766E");
        snrLabel.textContent = db + " dBFS · 正在收音（防吞字缓冲中）";
      }, 110);
    } else {
      if (studioState.recTimer) clearInterval(studioState.recTimer);
      pulse.setAttribute("opacity", "0");
      pulse.classList.remove("rec-pulse-ring");
      recBg.setAttribute("fill", "#DC2626");
      recLabel.textContent = "开始重录";
      meterFill.setAttribute("width", "312");
      meterFill.setAttribute("fill", "#0F766E");
      snrLabel.textContent = "-11.8 dBFS · SNR 35 dB · 已新增优选遍";

      var k = currentNoteKey();
      studioState.takesOverride[k] = (studioState.takesOverride[k] || 1) + 1;
      renderStudio();
    }
  }

  function triggerStudioPlay() {
    if (studioState.isPlaying) return;
    studioState.isPlaying = true;

    var spk = SPEAKERS[studioState.spkKey];
    var word = WORDS[studioState.wordIdx];
    var m = getScaledWordMetrics(word, spk);

    playPitchContourAudio(m.f0Arr, word.dur);

    var playLine = byId("svg-playhead-line");
    var playLbl = byId("svg-play-label");
    playLbl.textContent = "▶ 播放中…";
    playLine.setAttribute("opacity", "1");

    var startX = 852;
    var endX = word.dur < 0.45 ? 988 : 1104;
    var durMs = word.dur * 1000;
    var t0 = performance.now();

    function step(now) {
      var p = Math.min(1, (now - t0) / durMs);
      var x = startX + p * (endX - startX);
      playLine.setAttribute("x1", x.toFixed(1));
      playLine.setAttribute("x2", x.toFixed(1));
      if (p < 1) {
        studioState.playRaf = requestAnimationFrame(step);
      } else {
        playLine.setAttribute("opacity", "0");
        playLbl.textContent = "▶ 试听调值";
        studioState.isPlaying = false;
      }
    }
    studioState.playRaf = requestAnimationFrame(step);
  }

  function bindStudioEvents() {
    byId("btn-mode-adv").addEventListener("click", function () {
      studioState.mode = "adv";
      renderStudio();
    });
    byId("btn-mode-simple").addEventListener("click", function () {
      studioState.mode = "simple";
      renderStudio();
    });
    byId("svg-mode-pill").addEventListener("click", function () {
      studioState.mode = studioState.mode === "adv" ? "simple" : "adv";
      renderStudio();
    });

    function toggleClean() {
      studioState.cleanPrompt = !studioState.cleanPrompt;
      renderStudio();
    }
    byId("btn-clean-prompt").addEventListener("click", toggleClean);
    byId("svg-clean-pill").addEventListener("click", toggleClean);

    byId("btn-spk-01").addEventListener("click", function () {
      studioState.spkKey = "SPK01";
      renderStudio();
    });
    byId("btn-spk-02").addEventListener("click", function () {
      studioState.spkKey = "SPK02";
      renderStudio();
    });
    byId("svg-spk-pill").addEventListener("click", function () {
      studioState.spkKey = studioState.spkKey === "SPK01" ? "SPK02" : "SPK01";
      renderStudio();
    });

    byId("svg-prev-btn").addEventListener("click", function () {
      studioState.wordIdx = (studioState.wordIdx - 1 + WORDS.length) % WORDS.length;
      renderStudio();
    });
    byId("svg-next-btn").addEventListener("click", function () {
      studioState.wordIdx = (studioState.wordIdx + 1) % WORDS.length;
      renderStudio();
    });

    byId("svg-rec-btn").addEventListener("click", toggleStudioRecording);
    byId("svg-play-btn").addEventListener("click", triggerStudioPlay);

    byId("svg-note-clear").addEventListener("click", function () {
      var w = WORDS[studioState.wordIdx];
      studioState.notes[currentNoteKey()] = w.defaultNotes[studioState.spkKey];
      renderStudio();
    });

    byId("svg-derive-btn").addEventListener("click", function () {
      var k = currentNoteKey();
      var txt = studioState.notes[k] || "";
      if (txt.indexOf("[已派生异读]") === -1) {
        studioState.notes[k] = txt + " · [已派生异读]";
      }
      byId("svg-derive-txt").textContent = "✓ 已派生异读词条";
      setTimeout(function () {
        byId("svg-derive-txt").textContent = "+ 派生文白异读";
      }, 1400);
      renderStudio();
    });

    // 语图悬停十字准星实时测量
    var specHit = byId("svg-spec-hit");
    var crossGroup = byId("svg-crosshair-group");
    var svgRoot = byId("studio-svg");

    specHit.addEventListener("mousemove", function (evt) {
      var pt = svgRoot.createSVGPoint();
      pt.x = evt.clientX;
      pt.y = evt.clientY;
      var ctm = svgRoot.getScreenCTM();
      if (!ctm) return;
      var loc = pt.matrixTransform(ctm.inverse());

      var x = Math.max(842, Math.min(1118, loc.x));
      var word = WORDS[studioState.wordIdx];
      var spk = SPEAKERS[studioState.spkKey];
      var m = getScaledWordMetrics(word, spk);

      var ratio = (x - 842) / (1118 - 842);
      var idxFloat = ratio * (m.f0Arr.length - 1);
      var i0 = Math.floor(idxFloat);
      var i1 = Math.min(m.f0Arr.length - 1, i0 + 1);
      var frac = idxFloat - i0;
      var interpF0 = Math.round(m.f0Arr[i0] * (1 - frac) + m.f0Arr[i1] * frac);
      var dotY = f0ToSpecY(interpF0);
      var tVal = (ratio * word.dur).toFixed(2);

      crossGroup.setAttribute("opacity", "1");
      byId("svg-crosshair-line").setAttribute("x1", x.toFixed(1));
      byId("svg-crosshair-line").setAttribute("x2", x.toFixed(1));
      byId("svg-crosshair-dot").setAttribute("cx", x.toFixed(1));
      byId("svg-crosshair-dot").setAttribute("cy", dotY.toFixed(1));

      var boxX = Math.max(820, Math.min(918, x - 109));
      byId("svg-crosshair-box").setAttribute("x", boxX.toFixed(1));
      byId("svg-crosshair-text").setAttribute("x", (boxX + 109).toFixed(1));
      byId("svg-crosshair-text").textContent =
        "t=" + tVal + "s · F0=" + interpF0 + "Hz · F1=" + m.f1 + " · F2=" + m.f2;
    });

    specHit.addEventListener("mouseleave", function () {
      crossGroup.setAttribute("opacity", "0");
    });

    // 键盘快捷键体验（当焦点不在输入框且位于页面首屏附近时）
    window.addEventListener("keydown", function (e) {
      if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA")) return;
      if (window.scrollY > 620) return;
      if (e.code === "Space") {
        e.preventDefault();
        toggleStudioRecording();
      } else if (e.code === "ArrowDown" || e.code === "ArrowRight") {
        e.preventDefault();
        studioState.wordIdx = (studioState.wordIdx + 1) % WORDS.length;
        renderStudio();
      } else if (e.code === "ArrowUp" || e.code === "ArrowLeft") {
        e.preventDefault();
        studioState.wordIdx = (studioState.wordIdx - 1 + WORDS.length) % WORDS.length;
        renderStudio();
      } else if (e.code === "Enter") {
        e.preventDefault();
        triggerStudioPlay();
      }
    });
  }

  // =========================================================================
  // 2. 看图发音与防诱导词卡 SVG 沙盘
  // =========================================================================
  var PIC_CARDS = [
    {
      indexLabel: "看图识词 · 自然天文 (01 / 03)",
      title: "日头",
      cue: "调查目标：引导自然说出本地口语词「日头 [niəʔ⁵ dɤ²⁴]」，避免直接念出书面语「太阳」",
      draw: function (g) {
        // 暖黄天空 + 红日 + 江南粉墙黛瓦
        g.appendChild(svgEl("rect", { x: -290, y: -125, width: 580, height: 250, fill: "#FEF9C3" }));
        g.appendChild(svgEl("circle", { cx: 110, cy: -48, r: 48, fill: "#FDE68A", opacity: "0.7" }));
        g.appendChild(svgEl("circle", { cx: 110, cy: -48, r: 31, fill: "#F59E0B" }));
        g.appendChild(
          svgEl("path", {
            d: "M -290 65 Q -140 15 10 58 T 290 42 L 290 130 L -290 130 Z",
            fill: "#CBD5E1",
            opacity: "0.6"
          })
        );
        g.appendChild(
          svgEl("rect", {
            x: -210,
            y: 18,
            width: 230,
            height: 105,
            fill: "#FFFFFF",
            stroke: "#94A3B8",
            "stroke-width": "1.6"
          })
        );
        g.appendChild(
          svgEl("path", {
            d: "M -228 22 Q -95 -16 38 22 L 28 34 L -218 34 Z",
            fill: "#1E293B"
          })
        );
        g.appendChild(
          svgEl("rect", {
            x: -124,
            y: 48,
            width: 58,
            height: 52,
            rx: 4,
            fill: "#F1F5F9",
            stroke: "#64748B",
            "stroke-width": "1.5"
          })
        );
        g.appendChild(svgEl("line", { x1: -95, y1: 48, x2: -95, y2: 100, stroke: "#64748B", "stroke-width": "1.4" }));
        g.appendChild(svgEl("line", { x1: -124, y1: 74, x2: -66, y2: 74, stroke: "#64748B", "stroke-width": "1.4" }));
      }
    },
    {
      indexLabel: "看图识词 · 服饰穿戴 (02 / 03)",
      title: "衣裳",
      cue: "调查目标：引导说出「衣裳 [i⁴⁴ zɑ̃]」或「衫」，观察连读变调与轻声化现象",
      draw: function (g) {
        g.appendChild(svgEl("rect", { x: -290, y: -125, width: 580, height: 250, fill: "#EFF6FF" }));
        // 竹竿晾衣架
        g.appendChild(svgEl("line", { x1: -220, y1: -68, x2: 220, y2: -68, stroke: "#B45309", "stroke-width": "6", "stroke-linecap": "round" }));
        // 传统对襟布衫轮廓
        g.appendChild(
          svgEl("path", {
            d: "M -118 -64 L -48 -64 L -28 -48 L 28 -48 L 48 -64 L 118 -64 L 118 -20 L 66 -20 L 66 86 L -66 86 L -66 -20 L -118 -20 Z",
            fill: "#DBEAFE",
            stroke: "#1D4ED8",
            "stroke-width": "2.4",
            "stroke-linejoin": "round"
          })
        );
        // 对襟中缝与盘扣细节（放大可见）
        g.appendChild(svgEl("line", { x1: 0, y1: -48, x2: 0, y2: 86, stroke: "#1E40AF", "stroke-width": "2" }));
        [-22, 4, 30, 56].forEach(function (py) {
          g.appendChild(svgEl("line", { x1: -10, y1: py, x2: 10, y2: py, stroke: "#1E3A8A", "stroke-width": "2.2", "stroke-linecap": "round" }));
          g.appendChild(svgEl("circle", { cx: 0, cy: py, r: 2.6, fill: "#1E3A8A" }));
        });
      }
    },
    {
      indexLabel: "看图识词 · 房舍器具 (03 / 03)",
      title: "吊桶",
      cue: "调查目标：区分井边汲水用的「吊桶」与挑水用的「水桶」，放大可看清铁箍与提梁细节",
      draw: function (g) {
        g.appendChild(svgEl("rect", { x: -290, y: -125, width: 580, height: 250, fill: "#F0FDFA" }));
        // 提梁铁环
        g.appendChild(
          svgEl("path", {
            d: "M -68 -24 A 68 62 0 0 1 68 -24",
            fill: "none",
            stroke: "#334155",
            "stroke-width": "4"
          })
        );
        // 木桶桶身
        g.appendChild(
          svgEl("polygon", {
            points: "-72,-24 72,-24 56,84 -56,84",
            fill: "#FDE68A",
            stroke: "#92400E",
            "stroke-width": "2.5"
          })
        );
        // 木板竖纹
        [-36, 0, 36].forEach(function (bx) {
          g.appendChild(svgEl("line", { x1: bx, y1: -24, x2: bx * 0.78, y2: 84, stroke: "#B45309", "stroke-width": "1.6" }));
        });
        // 两道铁箍
        g.appendChild(svgEl("rect", { x: -69, y: -4, width: 138, height: 8, rx: 3, fill: "#475569" }));
        g.appendChild(svgEl("rect", { x: -61, y: 50, width: 122, height: 8, rx: 3, fill: "#47534E" }));
      }
    }
  ];

  var picState = {
    idx: 0,
    hideChar: false,
    zoomed: false
  };

  function renderPicCard() {
    var card = PIC_CARDS[picState.idx];
    byId("pic-svg-index").textContent = card.indexLabel;
    byId("pic-svg-title").textContent = card.title;
    byId("pic-svg-cue").textContent = card.cue;

    var art = byId("pic-svg-artwork");
    while (art.firstChild) art.removeChild(art.firstChild);
    card.draw(art);

    var scale = picState.zoomed ? 1.62 : 1;
    art.setAttribute("transform", "translate(320, 180) scale(" + scale + ")");
    byId("pic-svg-mode-badge").textContent = picState.zoomed ? "当前 1.6x 局部放大态" : "上图下字 · 支持双指缩放";

    byId("pic-svg-char-group").setAttribute("display", picState.hideChar ? "none" : "inline");
    byId("pic-svg-hidden-placeholder").setAttribute("display", picState.hideChar ? "inline" : "none");

    byId("btn-pic-hide-char").classList.toggle("active", picState.hideChar);
    byId("btn-pic-zoom").classList.toggle("active", picState.zoomed);

    var tabBtns = document.querySelectorAll("#pic-item-tabs .seg-btn");
    tabBtns.forEach(function (b) {
      b.classList.toggle("active", Number(b.getAttribute("data-pic")) === picState.idx);
    });
  }

  function bindPicCardEvents() {
    var tabBtns = document.querySelectorAll("#pic-item-tabs .seg-btn");
    tabBtns.forEach(function (b) {
      b.addEventListener("click", function () {
        picState.idx = Number(b.getAttribute("data-pic"));
        renderPicCard();
      });
    });

    byId("btn-pic-hide-char").addEventListener("click", function () {
      picState.hideChar = !picState.hideChar;
      renderPicCard();
    });

    byId("btn-pic-zoom").addEventListener("click", function () {
      picState.zoomed = !picState.zoomed;
      renderPicCard();
    });
  }

  // =========================================================================
  // 3. 局域网双设备无线联动沙盘
  // =========================================================================
  var LAN_THEMES = {
    xuan: {
      bg: "#FAF6EE",
      border: "#E2DDD2",
      main: "#27241D",
      sub: "#57534E",
      muted: "#78716C"
    },
    eink: {
      bg: "#F4F4F2",
      border: "#D4D4D0",
      main: "#18181B",
      sub: "#3F3F46",
      muted: "#52525B"
    },
    dark: {
      bg: "#14181F",
      border: "#334155",
      main: "#FDE047",
      sub: "#E2E8F0",
      muted: "#94A3B8"
    }
  };

  var lanState = {
    order: [0, 1, 2, 3, 4, 5],
    pos: 0,
    shuffled: false,
    theme: "xuan",
    resting: false,
    recording: false
  };

  function renderLanSandbox() {
    var wIdx = lanState.order[lanState.pos];
    var word = WORDS[wIdx];
    var th = LAN_THEMES[lanState.theme];

    byId("lan-order-badge").textContent = lanState.shuffled ? "随机打乱模式" : "顺序模式";
    byId("lan-shuffle-bg").setAttribute("fill", lanState.shuffled ? "#DBEAFE" : "#F1F5F9");
    byId("lan-shuffle-bg").setAttribute("stroke", lanState.shuffled ? "#60A5FA" : "#CBD5E1");
    byId("lan-shuffle-txt").setAttribute("fill", lanState.shuffled ? "#1E3A8A" : "#334155");

    byId("lan-remote-idx").textContent =
      "当前推送词条 (" + (lanState.pos + 1) + " / " + lanState.order.length + ")" + (lanState.shuffled ? " · 已随机排布" : "");
    byId("lan-remote-char").textContent = word.char;
    byId("lan-remote-note").textContent = "目标音系：" + word.cat.split("·").slice(1).join("·").trim() + " " + word.tRef;

    // 主题选中态边框
    byId("lan-theme-xuan-bg").setAttribute("stroke", lanState.theme === "xuan" ? "#1D4ED8" : "#CBD5E1");
    byId("lan-theme-xuan-bg").setAttribute("stroke-width", lanState.theme === "xuan" ? "2.2" : "1.2");
    byId("lan-theme-eink-bg").setAttribute("stroke", lanState.theme === "eink" ? "#1D4ED8" : "#CBD5E1");
    byId("lan-theme-eink-bg").setAttribute("stroke-width", lanState.theme === "eink" ? "2.2" : "1.2");
    byId("lan-theme-dark-bg").setAttribute("stroke", lanState.theme === "dark" ? "#60A5FA" : "#334155");
    byId("lan-theme-dark-bg").setAttribute("stroke-width", lanState.theme === "dark" ? "2.4" : "1.2");

    // 平板配色更新
    byId("lan-tablet-screen").setAttribute("fill", th.bg);
    byId("lan-tablet-screen").setAttribute("stroke", th.border);
    byId("lan-tablet-header").setAttribute("fill", th.sub);
    byId("lan-tablet-char").setAttribute("fill", th.main);
    byId("lan-tablet-char").setAttribute("font-size", word.char.length > 1 ? "98" : "126");
    byId("lan-tablet-char").textContent = word.char;
    byId("lan-tablet-sub").setAttribute("fill", th.sub);
    byId("lan-tablet-progress").setAttribute("fill", th.muted);
    byId("lan-tablet-progress").textContent =
      "第 " + (lanState.pos + 1) + " / " + lanState.order.length + " 项 · " + (lanState.shuffled ? "随机防诱导顺序" : "已锁定防误触翻页");

    byId("lan-rest-title").setAttribute("fill", th.main);
    byId("lan-rest-sub").setAttribute("fill", th.sub);

    // 休息模式切换
    byId("lan-tablet-prompt-group").setAttribute("display", lanState.resting ? "none" : "inline");
    byId("lan-tablet-rest-group").setAttribute("display", lanState.resting ? "inline" : "none");
    byId("lan-rest-bg").setAttribute("fill", lanState.resting ? "#FEF3C7" : "#F8FAFC");
    byId("lan-rest-bg").setAttribute("stroke", lanState.resting ? "#F59E0B" : "#CBD5E1");
    byId("lan-rest-txt").textContent = lanState.resting ? "结束休息 · 恢复字卡显示" : "切换「中场休息」护眼提示屏";

    // 远程录音同步态
    byId("lan-tablet-rec-ring").setAttribute("opacity", lanState.recording ? "1" : "0");
    byId("lan-tablet-rec-pill").setAttribute("opacity", lanState.recording ? "1" : "0");
    byId("lan-rec-bg").setAttribute("fill", lanState.recording ? "#991B1B" : "#DC2626");
    byId("lan-rec-txt").textContent = lanState.recording ? "停止收音并回传归档" : "远程启动看读端录音";
    byId("lan-wave-line").setAttribute("stroke", lanState.recording ? "#DC2626" : "#0F766E");
    byId("lan-sync-caption").textContent = lanState.recording ? "48kHz 实时收音 + 低延迟耳返" : "字卡推送 + 状态同步";
  }

  function bindLanEvents() {
    byId("lan-btn-prev").addEventListener("click", function () {
      lanState.resting = false;
      lanState.pos = (lanState.pos - 1 + lanState.order.length) % lanState.order.length;
      renderLanSandbox();
    });
    byId("lan-btn-next").addEventListener("click", function () {
      lanState.resting = false;
      lanState.pos = (lanState.pos + 1) % lanState.order.length;
      renderLanSandbox();
    });
    byId("lan-btn-shuffle").addEventListener("click", function () {
      lanState.shuffled = !lanState.shuffled;
      if (lanState.shuffled) {
        var arr = [0, 1, 2, 3, 4, 5];
        for (var i = arr.length - 1; i > 0; i--) {
          var j = Math.floor(Math.random() * (i + 1));
          var tmp = arr[i];
          arr[i] = arr[j];
          arr[j] = tmp;
        }
        // 确保切换后立即看到顺序变化
        if (arr[0] === 0) {
          var swap = arr[0];
          arr[0] = arr[2];
          arr[2] = swap;
        }
        lanState.order = arr;
      } else {
        lanState.order = [0, 1, 2, 3, 4, 5];
      }
      lanState.pos = 0;
      lanState.resting = false;
      renderLanSandbox();
    });

    byId("lan-theme-xuan").addEventListener("click", function () {
      lanState.theme = "xuan";
      renderLanSandbox();
    });
    byId("lan-theme-eink").addEventListener("click", function () {
      lanState.theme = "eink";
      renderLanSandbox();
    });
    byId("lan-theme-dark").addEventListener("click", function () {
      lanState.theme = "dark";
      renderLanSandbox();
    });

    byId("lan-btn-rest").addEventListener("click", function () {
      lanState.resting = !lanState.resting;
      renderLanSandbox();
    });
    byId("lan-btn-rec").addEventListener("click", function () {
      lanState.resting = false;
      lanState.recording = !lanState.recording;
      renderLanSandbox();
    });
  }

  // =========================================================================
  // 4. 全表声调轮廓聚类 & 元音格局图 SVG
  // =========================================================================
  var TONE_CATS = [
    { id: "yinping", name: "阴平 44", color: "#1D4ED8", durRatio: 1.0, tPts: [4.25, 4.22, 4.2, 4.16, 4.12, 4.08, 4.04] },
    { id: "yangping", name: "阳平 24", color: "#0F766E", durRatio: 1.0, tPts: [1.85, 2.05, 2.42, 2.88, 3.35, 3.74, 4.02] },
    { id: "shang", name: "上声 51", color: "#D97706", durRatio: 1.0, tPts: [4.72, 4.35, 3.76, 3.02, 2.25, 1.58, 1.12] },
    { id: "yinqu", name: "阴去 412", color: "#7C3AED", durRatio: 1.0, tPts: [3.95, 3.05, 2.02, 1.38, 1.48, 1.88, 2.32] },
    { id: "yinru", name: "阴入 5 (促声)", color: "#DC2626", durRatio: 0.44, tPts: [4.68, 4.64, 4.58, 4.5, 4.42] }
  ];

  var VOWELS_DATA = [
    { sym: "[i]", desc: "衣 · 闭前不圆唇", f1: 295, f2: 2290, rx: 32, ry: 22, color: "#1D4ED8" },
    { sym: "[ɿ]", desc: "时 · 舌尖前高元音", f1: 365, f2: 1550, rx: 36, ry: 24, color: "#0F766E" },
    { sym: "[u]", desc: "果 · 后高圆唇元音", f1: 425, f2: 885, rx: 34, ry: 26, color: "#D97706" },
    { sym: "[E]", desc: "菜 · 前半开单元音", f1: 565, f2: 1960, rx: 38, ry: 28, color: "#7C3AED" },
    { sym: "[ɑ]", desc: "茶 · 后开低元音", f1: 745, f2: 1180, rx: 42, ry: 28, color: "#DC2626" }
  ];

  var chartState = {
    tab: "tone", // 'tone' | 'vowel'
    activeTone: "all",
    useBark: true,
    showLobanov: false
  };

  function hzToBark(hz) {
    return (26.81 * hz) / (1960 + hz) - 0.53;
  }

  function renderAcousticChart() {
    var root = byId("acoustic-svg-content");
    if (!root) return;
    while (root.firstChild) root.removeChild(root.firstChild);

    byId("btn-chart-tone").classList.toggle("active", chartState.tab === "tone");
    byId("btn-chart-vowel").classList.toggle("active", chartState.tab === "vowel");
    byId("tone-filter-toolbar").style.display = chartState.tab === "tone" ? "flex" : "none";
    byId("vowel-filter-toolbar").style.display = chartState.tab === "vowel" ? "flex" : "none";

    if (chartState.tab === "tone") {
      renderToneClusterView(root);
    } else {
      renderVowelEllipseView(root);
    }
  }

  function renderToneClusterView(root) {
    var plotL = 96, plotR = 810, plotT = 54, plotB = 408;
    var plotW = plotR - plotL, plotH = plotB - plotT;

    root.appendChild(svgEl("text", { x: plotL, y: 32, "font-size": "14", "font-weight": "800", fill: "#0F172A" }, "全表声调轮廓聚类调域图（沈阿婆 SPK01 · 对数 T 值五度空间）"));

    // 1..5 度水平网格线
    for (var deg = 1; deg <= 5; deg++) {
      var gy = plotB - ((deg - 0.8) / 4.4) * plotH;
      root.appendChild(svgEl("line", { x1: plotL, y1: gy, x2: plotR, y2: gy, stroke: "#E2E8F0", "stroke-width": "1.2", "stroke-dasharray": "4 4" }));
      root.appendChild(svgEl("text", { x: plotL - 14, y: gy + 4, "text-anchor": "end", "font-size": "12", "font-weight": "800", fill: "#475569" }, deg + " 度"));
    }
    root.appendChild(svgEl("rect", { x: plotL, y: plotT, width: plotW, height: plotH, rx: 10, fill: "none", stroke: "#CBD5E1" }));
    root.appendChild(svgEl("text", { x: (plotL + plotR) / 2, y: 442, "text-anchor": "middle", "font-size": "11.5", "font-weight": "700", fill: "#64748B" }, "归一化时长百分比 (0% → 100%，阴入保留真实促声时长比例 44%)"));

    TONE_CATS.forEach(function (tc) {
      var focused = chartState.activeTone === "all" || chartState.activeTone === tc.id;
      var op = focused ? "1" : "0.16";
      var bandOp = focused ? "0.15" : "0.03";

      var pts = tc.tPts.map(function (tv, idx) {
        var x = plotL + 24 + (idx / (tc.tPts.length - 1)) * (plotW - 48) * tc.durRatio;
        var y = plotB - ((tv - 0.8) / 4.4) * plotH;
        return { x: x, y: y };
      });

      // ±1 SD 误差带多边形
      var upper = pts.map(function (p) { return p.x.toFixed(1) + "," + (p.y - 13).toFixed(1); }).join(" ");
      var lower = pts.slice().reverse().map(function (p) { return p.x.toFixed(1) + "," + (p.y + 13).toFixed(1); }).join(" ");
      root.appendChild(svgEl("polygon", { points: upper + " " + lower, fill: tc.color, opacity: bandOp }));

      // 均值折线/平滑轨迹
      var d = pts.map(function (p, i) { return (i === 0 ? "M " : "L ") + p.x.toFixed(1) + " " + p.y.toFixed(1); }).join(" ");
      root.appendChild(
        svgEl("path", {
          d: d,
          fill: "none",
          stroke: tc.color,
          "stroke-width": focused && chartState.activeTone !== "all" ? "4.5" : "3.2",
          "stroke-linecap": "round",
          "stroke-linejoin": "round",
          opacity: op
        })
      );

      var last = pts[pts.length - 1];
      root.appendChild(svgEl("circle", { cx: last.x, cy: last.y, r: 4.5, fill: tc.color, opacity: op }));
      root.appendChild(
        svgEl(
          "text",
          {
            x: last.x + 10,
            y: last.y + 4,
            "font-size": "12",
            "font-weight": "800",
            fill: tc.color,
            opacity: op
          },
          tc.name
        )
      );
    });

    // 右侧统计摘要卡片
    root.appendChild(svgEl("rect", { x: 842, y: 54, width: 270, height: 354, rx: 12, fill: "#F8FAFC", stroke: "#CBD5E1" }));
    root.appendChild(svgEl("text", { x: 862, y: 84, "font-size": "13", "font-weight": "800", fill: "#0F172A" }, "调类核查与导出说明"));
    var infoLines = [
      "• 自动过滤清辅音弯头与末尾嘎裂段",
      "• 支持等距 9 点 / 11 点时间归一化",
      "• 促声（入声）按相对时长比例绘制，",
      "  避免被强行拉伸至与舒声等长",
      "• 阴影带表示同调类字组的 ±1 SD 离散度，",
      "  若单字明显偏离可立即回到词表复听"
    ];
    infoLines.forEach(function (line, i) {
      root.appendChild(svgEl("text", { x: 862, y: 116 + i * 24, "font-size": "11.5", "font-weight": "600", fill: "#475569" }, line));
    });

    root.appendChild(svgEl("rect", { x: 862, y: 278, width: 230, height: 108, rx: 10, fill: "#FFFFFF", stroke: "#E2E8F0" }));
    root.appendChild(svgEl("text", { x: 876, y: 302, "font-size": "11.5", "font-weight": "800", fill: "#0F766E" }, "一键导出宏观声调报告"));
    root.appendChild(svgEl("text", { x: 876, y: 324, "font-size": "11", "font-weight": "600", fill: "#64748B" }, "包含高清调域图 .png 与各调类"));
    root.appendChild(svgEl("text", { x: 876, y: 344, "font-size": "11", "font-weight": "600", fill: "#64748B" }, "均值/标准差明细工作簿 .xlsx，"));
    root.appendChild(svgEl("text", { x: 876, y: 364, "font-size": "11", "font-weight": "600", fill: "#64748B" }, "可直接插图用于调查报告或论文。"));
  }

  function renderVowelEllipseView(root) {
    var plotL = 96, plotR = 810, plotT = 54, plotB = 408;
    var plotW = plotR - plotL, plotH = plotB - plotT;

    var modeTitle = chartState.useBark ? "Bark 听觉感知标度" : "Hz 线性频率标度";
    root.appendChild(
      svgEl(
        "text",
        { x: plotL, y: 32, "font-size": "14", "font-weight": "800", fill: "#0F172A" },
        "全表单元音 1-SD 离散椭圆图（" + modeTitle + (chartState.showLobanov ? " · 已叠加周老伯 Lobanov 声道归一化" : "") + "）"
      )
    );

    root.appendChild(svgEl("rect", { x: plotL, y: plotT, width: plotW, height: plotH, rx: 10, fill: "#F8FAFC", stroke: "#CBD5E1" }));

    // 网格线
    for (var gx = 1; gx <= 4; gx++) {
      var lx = plotL + (gx / 5) * plotW;
      root.appendChild(svgEl("line", { x1: lx, y1: plotT, x2: lx, y2: plotB, stroke: "#E2E8F0", "stroke-dasharray": "4 4" }));
    }
    for (var gy = 1; gy <= 3; gy++) {
      var ly = plotT + (gy / 4) * plotH;
      root.appendChild(svgEl("line", { x1: plotL, y1: ly, x2: plotR, y2: ly, stroke: "#E2E8F0", "stroke-dasharray": "4 4" }));
    }

    root.appendChild(svgEl("text", { x: (plotL + plotR) / 2, y: 442, "text-anchor": "middle", "font-size": "11.5", "font-weight": "700", fill: "#64748B" }, "← F2 舌位前移 (" + (chartState.useBark ? "15.0 → 6.5 Bark" : "2500 → 700 Hz") + ")"));
    root.appendChild(svgEl("text", { x: 40, y: (plotT + plotB) / 2, "text-anchor": "middle", "font-size": "11.5", "font-weight": "700", fill: "#64748B" }, "F1 舌位降低 ↓"));

    function mapCoord(f1, f2) {
      if (chartState.useBark) {
        var b1 = hzToBark(f1), b2 = hzToBark(f2);
        return {
          x: plotL + ((14.6 - b2) / (14.6 - 6.8)) * plotW,
          y: plotT + ((b1 - 2.4) / (7.6 - 2.4)) * plotH
        };
      }
      return {
        x: plotL + ((2500 - f2) / 1800) * plotW,
        y: plotT + ((f1 - 230) / 620) * plotH
      };
    }

    // 外围连线（元音格局包络）
    var hullOrder = [0, 3, 4, 2, 1, 0];
    var hullPts = hullOrder
      .map(function (idx) {
        var p = mapCoord(VOWELS_DATA[idx].f1, VOWELS_DATA[idx].f2);
        return p.x.toFixed(1) + "," + p.y.toFixed(1);
      })
      .join(" ");
    root.appendChild(svgEl("polyline", { points: hullPts, fill: "none", stroke: "#94A3B8", "stroke-width": "1.5", "stroke-dasharray": "5 4" }));

    VOWELS_DATA.forEach(function (v) {
      var c = mapCoord(v.f1, v.f2);
      root.appendChild(svgEl("ellipse", { cx: c.x, cy: c.y, rx: v.rx, ry: v.ry, fill: v.color, opacity: "0.14" }));
      root.appendChild(svgEl("ellipse", { cx: c.x, cy: c.y, rx: v.rx, ry: v.ry, fill: "none", stroke: v.color, "stroke-width": "2" }));
      root.appendChild(svgEl("circle", { cx: c.x, cy: c.y, r: 5, fill: v.color }));
      root.appendChild(svgEl("text", { x: c.x + 12, y: c.y - 8, "font-size": "15", "font-weight": "800", fill: v.color }, v.sym));

      if (chartState.showLobanov) {
        // Lobanov 归一化后男女声道差异消除，中心高度收敛
        var cMale = { x: c.x + 11, y: c.y + 8 };
        root.appendChild(
          svgEl("ellipse", {
            cx: cMale.x,
            cy: cMale.y,
            rx: v.rx * 0.94,
            ry: v.ry * 0.94,
            fill: "none",
            stroke: "#0F172A",
            "stroke-width": "1.8",
            "stroke-dasharray": "4 3"
          })
        );
        root.appendChild(svgEl("rect", { x: cMale.x - 4, y: cMale.y - 4, width: 8, height: 8, fill: "#0F172A" }));
      }
    });

    // 右侧图例卡片
    root.appendChild(svgEl("rect", { x: 842, y: 54, width: 270, height: 354, rx: 12, fill: "#F8FAFC", stroke: "#CBD5E1" }));
    root.appendChild(svgEl("text", { x: 862, y: 84, "font-size": "13", "font-weight": "800", fill: "#0F172A" }, "元音稳态段采样与归一化"));
    VOWELS_DATA.forEach(function (v, idx) {
      var ly = 114 + idx * 34;
      root.appendChild(svgEl("circle", { cx: 870, cy: ly - 4, r: 5.5, fill: v.color }));
      root.appendChild(svgEl("text", { x: 884, y: ly, "font-size": "12", "font-weight": "700", fill: "#0F172A" }, v.desc));
    });

    root.appendChild(svgEl("rect", { x: 862, y: 292, width: 230, height: 96, rx: 10, fill: "#FFFFFF", stroke: "#E2E8F0" }));
    root.appendChild(svgEl("text", { x: 876, y: 316, "font-size": "11.5", "font-weight": "800", fill: "#1D4ED8" }, "Lobanov Z-Score 对比说明"));
    root.appendChild(svgEl("text", { x: 876, y: 338, "font-size": "11", "font-weight": "600", fill: "#64748B" }, "实线圆点：沈阿婆 (SPK01 女 71岁)"));
    root.appendChild(svgEl("text", { x: 876, y: 358, "font-size": "11", "font-weight": "600", fill: "#64748B" }, "虚线方块：周老伯 (SPK02 男 74岁)"));
    root.appendChild(svgEl("text", { x: 876, y: 376, "font-size": "10.5", "font-weight": "600", fill: "#0F766E" }, "开启归一化后可消除男女生理声道差"));
  }

  function bindAcousticEvents() {
    byId("btn-chart-tone").addEventListener("click", function () {
      chartState.tab = "tone";
      renderAcousticChart();
    });
    byId("btn-chart-vowel").addEventListener("click", function () {
      chartState.tab = "vowel";
      renderAcousticChart();
    });

    var toneBtns = document.querySelectorAll("#tone-filter-toolbar .chip-toggle");
    toneBtns.forEach(function (btn) {
      btn.addEventListener("click", function () {
        chartState.activeTone = btn.getAttribute("data-tone");
        toneBtns.forEach(function (b) {
          b.classList.toggle("active", b === btn);
        });
        renderAcousticChart();
      });
    });

    byId("btn-vowel-bark").addEventListener("click", function () {
      chartState.useBark = !chartState.useBark;
      byId("btn-vowel-bark").classList.toggle("active", chartState.useBark);
      byId("btn-vowel-bark").textContent = chartState.useBark ? "Bark 感知标度" : "Hz 线性标度";
      renderAcousticChart();
    });

    byId("btn-vowel-lobanov").addEventListener("click", function () {
      chartState.showLobanov = !chartState.showLobanov;
      byId("btn-vowel-lobanov").classList.toggle("active", chartState.showLobanov);
      renderAcousticChart();
    });
  }

  // =========================================================================
  // 5. 多格式规范导出包目录树与内部结构预览器 SVG
  // =========================================================================
  var EXPORT_PKGS = {
    adv: {
      rootName: "苏州方言调查_进阶导出包.zip",
      files: [
        { id: "xlsx", name: "acoustic_summary.xlsx", tag: "原生工作簿", kind: "xlsx" },
        { id: "tg", name: "audio/SPK01_001_衣.TextGrid", tag: "四层对齐标注", kind: "textgrid" },
        { id: "wav", name: "audio/SPK01_001_衣.wav", tag: "48kHz 16bit", kind: "wav" },
        { id: "img", name: "images/012_日头.jpg", tag: "词卡配图备份", kind: "meta" },
        { id: "json", name: "speaker.json & experiment.json", tag: "支持无损导回", kind: "meta" }
      ]
    },
    simple: {
      rootName: "苏州方言调查_纯净归档包.zip",
      files: [
        { id: "s_xlsx", name: "录音汇总表.xlsx", tag: "双工作表中文表头", kind: "xlsx_simple" },
        { id: "s_wav1", name: "录音文件/沈阿婆/001_衣.wav", tag: "按发音人分夹", kind: "wav" },
        { id: "s_wav2", name: "录音文件/周老伯/001_衣.wav", tag: "独立建档不覆盖", kind: "wav" },
        { id: "s_list", name: "调查字表清单.txt", tag: "UTF-8 纯文本", kind: "meta" }
      ]
    },
    teproj: {
      rootName: "苏州方言调查.teproj (PhonTracer)",
      files: [
        { id: "tp_manifest", name: "project_manifest.json", tag: "自动起止边界", kind: "teproj_json" },
        { id: "tp_wav", name: "audio/SPK01_001_衣.wav", tag: "直连声谱精修", kind: "wav" },
        { id: "tp_tg", name: "annotations/SPK01_001_衣.TextGrid", tag: "四层兼容结构", kind: "textgrid" }
      ]
    },
    collection: {
      rootName: "苏州方言调查_SPK01.Collection (Praat)",
      files: [
        { id: "pr_col", name: "Praat Object List (Sound + TextGrid + Table)", tag: "拖入即全载入", kind: "collection_view" },
        { id: "pr_tg", name: "内嵌对象: TextGrid SPK01_001_衣", tag: "同名自动配对", kind: "textgrid" }
      ]
    }
  };

  var exportState = {
    pkg: "adv",
    fileIdx: 0
  };

  function renderExportSandbox() {
    var treeG = byId("export-svg-tree");
    var prevG = byId("export-svg-preview");
    if (!treeG || !prevG) return;

    while (treeG.firstChild) treeG.removeChild(treeG.firstChild);
    while (prevG.firstChild) prevG.removeChild(prevG.firstChild);

    var pkgData = EXPORT_PKGS[exportState.pkg];
    if (exportState.fileIdx >= pkgData.files.length) exportState.fileIdx = 0;

    // 左侧目录树卡片
    treeG.appendChild(svgEl("rect", { x: 20, y: 20, width: 386, height: 400, rx: 14, fill: "#FFFFFF", stroke: "#CBD5E1" }));
    treeG.appendChild(svgEl("text", { x: 40, y: 52, "font-size": "13", "font-weight": "800", fill: "#0F172A" }, "📦 " + pkgData.rootName));
    treeG.appendChild(svgEl("line", { x1: 36, y1: 66, x2: 390, y2: 66, stroke: "#E2E8F0" }));

    pkgData.files.forEach(function (f, idx) {
      var active = idx === exportState.fileIdx;
      var fy = 82 + idx * 62;
      var fg = svgEl("g", { class: "svg-clickable", transform: "translate(36, " + fy + ")" });

      fg.appendChild(
        svgEl("rect", {
          x: 0,
          y: 0,
          width: 354,
          height: 50,
          rx: 10,
          fill: active ? "#DBEAFE" : "#F8FAFC",
          stroke: active ? "#60A5FA" : "#E2E8F0"
        })
      );
      fg.appendChild(
        svgEl(
          "text",
          {
            x: 14,
            y: 23,
            "font-size": "12",
            "font-weight": "800",
            fill: active ? "#1E3A8A" : "#0F172A"
          },
          f.name
        )
      );
      fg.appendChild(
        svgEl(
          "text",
          {
            x: 14,
            y: 40,
            "font-size": "10.5",
            "font-weight": "600",
            fill: active ? "#1D4ED8" : "#64748B"
          },
          "点击预览内部结构 · " + f.tag
        )
      );

      fg.addEventListener("click", function () {
        exportState.fileIdx = idx;
        renderExportSandbox();
      });
      treeG.appendChild(fg);
    });

    // 右侧结构化文件预览面板
    prevG.appendChild(svgEl("rect", { x: 422, y: 20, width: 698, height: 400, rx: 14, fill: "#FFFFFF", stroke: "#CBD5E1" }));
    var activeFile = pkgData.files[exportState.fileIdx];
    renderExportFilePreview(prevG, activeFile);
  }

  function renderExportFilePreview(g, file) {
    g.appendChild(svgEl("text", { x: 446, y: 52, "font-size": "13.5", "font-weight": "800", fill: "#0F172A" }, "文件内部预览：" + file.name));
    g.appendChild(svgEl("rect", { x: 966, y: 34, width: 130, height: 24, rx: 12, fill: "#CCFBF1" }));
    g.appendChild(svgEl("text", { x: 1031, y: 50, "text-anchor": "middle", "font-size": "10.5", "font-weight": "800", fill: "#134E4A" }, file.tag));
    g.appendChild(svgEl("line", { x1: 442, y1: 68, x2: 1098, y2: 68, stroke: "#E2E8F0" }));

    if (file.kind === "xlsx" || file.kind === "xlsx_simple") {
      // 原生冻结表头 Excel 工作簿表格预览
      var headers =
        file.kind === "xlsx"
          ? ["发音人", "序号", "词目", "记音备注", "时长(s)", "均值F0", "五度参考", "F1 / F2 (Hz)"]
          : ["发音人姓名", "序号", "调查字词", "现场记音备注", "有效时长", "采用遍数", "收音质检", "音频相对路径"];
      var rows =
        file.kind === "xlsx"
          ? [
              ["SPK01_沈阿婆", "001", "衣", "[i⁴⁴] 擦音轻微", "0.68", "235 Hz", "[44]", "295 / 2290"],
              ["SPK01_沈阿婆", "005", "时", "[zɿ²⁴] 清音浊流", "0.72", "190 Hz", "[24]", "365 / 1550"],
              ["SPK01_沈阿婆", "011", "笔", "[piɪʔ⁵] 喉塞收尾", "0.34", "243 Hz", "[5]", "345 / 2140"],
              ["SPK01_沈阿婆", "012", "日头", "[niəʔ⁵ dɤ²⁴]", "0.84", "201 Hz", "[5+24]", "510 / 1380"],
              ["SPK02_周老伯", "001", "衣", "[i⁴⁴] 舌叶摩擦", "0.65", "143 Hz", "[44]", "257 / 1992"]
            ]
          : [
              ["沈阿婆", "001", "衣", "[i⁴⁴] 擦音轻微", "0.68 s", "第3遍 ★", "优良 (34dB)", "沈阿婆/001_衣.wav"],
              ["沈阿婆", "005", "时", "[zɿ²⁴] 清音浊流", "0.72 s", "第2遍 ★", "优良 (33dB)", "沈阿婆/005_时.wav"],
              ["沈阿婆", "012", "日头", "看图说出日头", "0.84 s", "第2遍 ★", "优良 (35dB)", "沈阿婆/012_日头.wav"],
              ["周老伯", "001", "衣", "[i⁴⁴] 稳健", "0.65 s", "第2遍 ★", "优良 (32dB)", "周老伯/001_衣.wav"]
            ];

      var colW = 81;
      g.appendChild(svgEl("rect", { x: 446, y: 86, width: 648, height: 34, rx: 6, fill: "#1E3A8A" }));
      headers.forEach(function (h, ci) {
        g.appendChild(svgEl("text", { x: 454 + ci * colW, y: 107, "font-size": "10.5", "font-weight": "800", fill: "#FFFFFF" }, h));
      });

      rows.forEach(function (r, ri) {
        var ry = 120 + ri * 42;
        g.appendChild(svgEl("rect", { x: 446, y: ry, width: 648, height: 42, fill: ri % 2 === 0 ? "#F8FAFC" : "#FFFFFF", stroke: "#E2E8F0" }));
        r.forEach(function (cell, ci) {
          g.appendChild(svgEl("text", { x: 454 + ci * colW, y: ry + 25, "font-size": "10.5", "font-weight": ci === 2 ? "800" : "600", fill: "#0F172A" }, cell));
        });
      });

      g.appendChild(
        svgEl(
          "text",
          { x: 446, y: 388, "font-size": "11.5", "font-weight": "700", fill: "#0F766E" },
          "✓ 原生 OpenXML .xlsx 工作簿（非 CSV 伪装），表头默认冻结，支持 Excel / WPS / R readxl / Python openpyxl 直读"
        )
      );
    } else if (file.kind === "textgrid") {
      // Praat 四层 TextGrid 对齐示意图
      g.appendChild(svgEl("rect", { x: 446, y: 86, width: 648, height: 74, rx: 8, fill: "#F8FAFC", stroke: "#CBD5E1" }));
      // 波形示意
      var waveD = "M 466 123 L 540 123 ";
      for (var wx = 544; wx <= 980; wx += 8) {
        var amp = Math.sin((wx - 540) * 0.09) * 24 + (wx % 16 === 0 ? 8 : -8);
        waveD += "L " + wx + " " + Math.round(123 + amp) + " ";
      }
      waveD += "L 1074 123";
      g.appendChild(svgEl("path", { d: waveD, fill: "none", stroke: "#1D4ED8", "stroke-width": "1.6" }));
      // 自动静音边界虚线
      g.appendChild(svgEl("line", { x1: 540, y1: 86, x2: 540, y2: 348, stroke: "#DC2626", "stroke-width": "1.6", "stroke-dasharray": "4 3" }));
      g.appendChild(svgEl("line", { x1: 984, y1: 86, x2: 984, y2: 348, stroke: "#DC2626", "stroke-width": "1.6", "stroke-dasharray": "4 3" }));

      var tiers = [
        { name: "Tier 1 · Character", val: "衣" },
        { name: "Tier 2 · IPA Note", val: "[i⁴⁴] 擦音轻微，元音平稳" },
        { name: "Tier 3 · Tone Ref", val: "阴平 44" },
        { name: "Tier 4 · Segment", val: "onset=0.14s  offset=0.82s (自动裁除首尾静音)" }
      ];
      tiers.forEach(function (t, ti) {
        var ty = 172 + ti * 44;
        g.appendChild(svgEl("rect", { x: 446, y: ty, width: 648, height: 38, rx: 6, fill: "#FEF9C3", stroke: "#FDE047" }));
        g.appendChild(svgEl("text", { x: 458, y: ty + 23, "font-size": "10.5", "font-weight": "800", fill: "#854D0E" }, t.name));
        g.appendChild(svgEl("text", { x: 762, y: ty + 23, "text-anchor": "middle", "font-size": "12", "font-weight": "800", fill: "#0F172A" }, t.val));
      });
      g.appendChild(svgEl("text", { x: 446, y: 388, "font-size": "11.5", "font-weight": "700", fill: "#0F766E" }, "✓ 层级数量与名称可在设置中自定义，支持直接拖入 Praat 或 PhonTracer 精修起止边界"));
    } else if (file.kind === "teproj_json") {
      var codeLines = [
        '{  "format": "PhonTracer.Project.v2",',
        '   "projectTitle": "苏州方言声韵调与日常词调查",',
        '   "sampleRateHz": 48000,  "channels": 1,  "bitDepth": 16,',
        '   "items": [',
        '     { "id": "001", "speaker": "SPK01", "word": "衣", "group": "阴平",',
        '       "wav": "audio/SPK01_001_衣.wav", "onsetSec": 0.142, "offsetSec": 0.824,',
        '       "ipaNote": "[i⁴⁴] 擦音轻微，元音平稳", "f0Ref": [238, 235, 232] }',
        "   ]",
        "}"
      ];
      g.appendChild(svgEl("rect", { x: 446, y: 86, width: 648, height: 266, rx: 10, fill: "#0F172A" }));
      codeLines.forEach(function (ln, li) {
        g.appendChild(svgEl("text", { x: 468, y: 118 + li * 26, "font-family": "monospace", "font-size": "12", fill: "#E2E8F0" }, ln));
      });
      g.appendChild(svgEl("text", { x: 446, y: 388, "font-size": "11.5", "font-weight": "700", fill: "#1D4ED8" }, "✓ 在 PhonTracer 中打开 .teproj 即可直接继承分组、记音与自动预切分边界，免去二次对齐"));
    } else {
      // 通用音频与集合对象规格卡片
      g.appendChild(svgEl("rect", { x: 446, y: 92, width: 648, height: 252, rx: 12, fill: "#F8FAFC", stroke: "#CBD5E1" }));
      var specs = [
        "• 编码格式：标准 RIFF WAVE 无压缩线性 PCM（16-bit Little-Endian 单声道）",
        "• 采样频率：44,100 Hz 或 48,000 Hz（桌面端支持外置 USB 声卡直选）",
        "• 命名规范：支持 {Speaker}_{Index}_{Word} 等自定义占位符模板与发音人分目录",
        "• 多遍备份：未选中的历史重录音频可按需随 takes/ 目录保留，不丢失田野异读线索",
        "• 无损恢复：导出的 .zip 归档包可随时重新导入任意平台的 PhonAcq 客户端继续工作"
      ];
      specs.forEach(function (s, si) {
        g.appendChild(svgEl("text", { x: 470, y: 136 + si * 40, "font-size": "12.5", "font-weight": "700", fill: "#1E293B" }, s));
      });
      g.appendChild(svgEl("text", { x: 446, y: 388, "font-size": "11.5", "font-weight": "700", fill: "#0F766E" }, "✓ Android 手机、Windows 与 macOS 三端工程压缩包完全互通"));
    }
  }

  function bindExportEvents() {
    var pkgBtns = document.querySelectorAll("#export-pkg-tabs .seg-btn");
    pkgBtns.forEach(function (btn) {
      btn.addEventListener("click", function () {
        exportState.pkg = btn.getAttribute("data-pkg");
        exportState.fileIdx = 0;
        pkgBtns.forEach(function (b) {
          b.classList.toggle("active", b === btn);
        });
        renderExportSandbox();
      });
    });
  }

  // =========================================================================
  // 启动全部 5 组交互 SVG 沙盘
  // =========================================================================
  document.addEventListener("DOMContentLoaded", function () {
    bindStudioEvents();
    renderStudio();

    bindPicCardEvents();
    renderPicCard();

    bindLanEvents();
    renderLanSandbox();

    bindAcousticEvents();
    renderAcousticChart();

    bindExportEvents();
    renderExportSandbox();
  });
})();
