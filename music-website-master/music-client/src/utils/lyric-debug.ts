/**
 * 歌词链路诊断工具
 * 用法：在浏览器控制台输入 window.__lyricReport() 查看完整报告
 *       或观察控制台以 [LyricDebug] 开头的日志
 */

const STEPS: { step: number; label: string; data: any; ok: boolean; detail: string }[] = [];
let reportPrinted = false;

function record(step: number, label: string, ok: boolean, data: any, detail?: string) {
  const entry = { step, label, data, ok, detail: detail || '' };
  STEPS.push(entry);
  const icon = ok ? '✅' : '❌';
  const d = typeof data === 'string' ? (data.length > 80 ? data.slice(0, 80) + '...' : data) : JSON.stringify(data).slice(0, 120);
  console.log(`[LyricDebug] ${icon} Step${step} ${label}`, detail || '', data);
}

export const lyricDebug = {
  /** Step 1: SongList 点击时收到的 row 数据 */
  step1_rowData(row: any) {
    const hasLyric = typeof row.lyric === 'string' && row.lyric.length > 0;
    record(1, 'SongList 点击 → row.lyric', hasLyric, {
      id: row.id,
      name: row.name,
      hasLyric,
      lyricType: typeof row.lyric,
      lyricLen: row.lyric ? row.lyric.length : 0,
      lyricPreview: row.lyric ? row.lyric.slice(0, 60) : '(空)',
      allKeys: Object.keys(row),
    }, hasLyric ? '歌词字段存在' : '⚠️ row.lyric 为空或不存在！后端 SQL/ResultMap 没返回 lyric');
  },

  /** Step 2: playMusic 调用前，传给 store 的数据 */
  step2_storeDispatch(lyric: any) {
    const hasLyric = typeof lyric === 'string' && lyric.length > 0;
    record(2, 'playMusic → dispatch lyric', hasLyric, {
      hasLyric,
      lyricType: typeof lyric,
      lyricLen: lyric ? lyric.length : 0,
      lyricPreview: lyric ? lyric.slice(0, 60) : '(空)',
    }, hasLyric ? 'store 收到歌词' : '⚠️ dispatch 时 lyric 为空');
  },

  /** Step 3: store mutation 后的值 */
  step3_storeValue(lyric: any) {
    const hasLyric = typeof lyric === 'string' && lyric.length > 0;
    record(3, 'store.getters.lyric', hasLyric, {
      hasLyric,
      lyricType: typeof lyric,
      lyricLen: lyric ? lyric.length : 0,
      lyricPreview: lyric ? lyric.slice(0, 80) : '(空)',
    }, hasLyric ? 'store 中歌词正常' : '⚠️ store 中 lyric 为空');
  },

  /** Step 4: parseLyric 输入和输出 */
  step4_parseResult(input: any, output: any) {
    const inputOk = typeof input === 'string' && input.length > 0;
    const outputOk = Array.isArray(output) && output.length > 0;
    record(4, 'parseLyric()', inputOk && outputOk, {
      inputOk,
      inputLen: input ? input.length : 0,
      inputPreview: input ? input.slice(0, 80) : '(空)',
      outputOk,
      outputLen: output ? output.length : 0,
      firstLine: output?.[0],
    }, inputOk && outputOk ? `解析成功，${output.length} 行歌词`
      : !inputOk ? '⚠️ parseLyric 输入为空'
      : '⚠️ parseLyric 输出为空数组，检查 LRC 格式');
  },

  /** Step 5: Lyric.vue 中的 lyricLines */
  step5_lyricLines(lines: any[]) {
    const ok = Array.isArray(lines) && lines.length > 0;
    record(5, 'Lyric.vue → lyricLines', ok, {
      count: lines?.length || 0,
      sample: lines?.slice(0, 3),
    }, ok ? `${lines.length} 行歌词已就绪` : '⚠️ lyricLines 为空，歌词不会渲染');
  },

  /** Step 6: curTime 更新情况 */
  step6_curTime(curTime: number, duration: number, currentLine: number) {
    const ok = curTime > 0;
    record(6, `curTime=${curTime.toFixed(1)}s / duration=${duration.toFixed(1)}s`, ok, {
      curTime,
      duration,
      currentLine,
    }, ok ? `当前第 ${currentLine} 行` : '⚠️ curTime 未更新，检查音频是否在播放');
  },

  /** 打印完整诊断报告 */
  printReport() {
    if (reportPrinted) return;
    reportPrinted = true;

    console.log('\n╔══════════════════════════════════════╗');
    console.log('║     🎵 歌词链路诊断报告              ║');
    console.log('╠══════════════════════════════════════╣');

    if (STEPS.length === 0) {
      console.log('║  ⚠️  无诊断数据 — 请先点击一首歌曲    ║');
    } else {
      const allOk = STEPS.every(s => s.ok);
      for (const s of STEPS) {
        const icon = s.ok ? '✅' : '❌';
        console.log(`║ ${icon} Step${s.step} ${s.label}`);
        if (s.detail) console.log(`║    → ${s.detail}`);
      }
      console.log('╠══════════════════════════════════════╣');
      if (allOk) {
        console.log('║  ✅ 链路全通 — 如果歌词还不显示       ║');
        console.log('║     检查 CSS / DOM / 缓存            ║');
      } else {
        const firstFail = STEPS.find(s => !s.ok);
        console.log(`║  ❌ 断在 Step${firstFail?.step}：${firstFail?.label}`);
        console.log(`║     ${firstFail?.detail}`);
      }
    }
    console.log('╚══════════════════════════════════════╝\n');

    // 附加诊断：检查 DOM
    console.log('[LyricDebug] DOM 检查：');
    console.log('  .song-lyric 元素:', document.querySelector('.song-lyric'));
    console.log('  .lyric-wrapper 元素:', document.querySelector('.lyric-wrapper'));
    console.log('  .has-lyric li 数量:', document.querySelectorAll('.has-lyric li').length);
    console.log('  .active 元素:', document.querySelector('.has-lyric li.active'));
  },

  reset() {
    STEPS.length = 0;
    reportPrinted = false;
    console.log('[LyricDebug] 诊断数据已重置，请点击一首歌曲重新收集');
  },
};

// 挂到 window 上方便控制台调用
if (typeof window !== 'undefined') {
  (window as any).__lyricReport = () => lyricDebug.printReport();
  (window as any).__lyricReset = () => lyricDebug.reset();
}
