<template>
  <div class="song-container">
    <el-image class="song-pic" fit="contain" :src="attachImageUrl(songPic)" />
    <ul class="song-info">
      <li>歌手：{{ singerName }}</li>
      <li>歌曲：{{ songTitle }}</li>
    </ul>
  </div>
  <div class="container">
    <div class="lyric-container">
      <!-- 星空歌词区 -->
      <div class="song-lyric" ref="lyricBoxRef">
        <!-- 星空背景层 -->
        <div class="star-field">
          <div class="stars stars-sm"></div>
          <div class="stars stars-md"></div>
          <div class="stars stars-lg"></div>
          <div class="shooting-star"></div>
        </div>
        <!-- 渐变遮罩 -->
        <div class="lyric-mask-top"></div>
        <div class="lyric-mask-bottom"></div>
        <!-- 歌词滚动层 -->
        <transition name="lyric-fade">
          <div
            v-if="lyricLines.length"
            key="has-lyric"
            class="lyric-wrapper"
            :style="{ transform: 'translateY(' + lyricOffset + 'px)' }"
          >
            <p
              v-for="(item, index) in lyricLines"
              :key="index"
              :class="{ active: index === currentLine, prev: index < currentLine, next: index > currentLine }"
            >
              {{ item.text }}
            </p>
          </div>
          <div v-else key="no-lyric" class="no-lyric">
            <span>暂无歌词，请尽情欣赏</span>
          </div>
        </transition>
      </div>
      <comment :playId="songId" :type="0"></comment>
    </div>
  </div>
</template>

<script lang="ts">
import { computed, defineComponent, ref, watch, onMounted } from "vue";
import { useStore } from "vuex";
import { useRoute } from "vue-router";
import Comment from "@/components/Comment.vue";
import { parseLyric } from "@/utils";
import { HttpManager } from "@/api";
import { lyricDebug } from "@/utils/lyric-debug";

export default defineComponent({
  components: {
    Comment,
  },
  setup() {
    const store = useStore();
    const route = useRoute();

    const lyricBoxRef = ref<HTMLElement | null>(null);
    const currentLine = ref(0);
    const lyricLines = ref<{ time: number; text: string }[]>([]);
    const lyricOffset = ref(0);
    const lineHeight = 52;
    const lyricPadTop = 280; // 与 CSS .lyric-wrapper padding-top 一致

    const songId = computed(() => store.getters.songId);
    const lyric = computed(() => store.getters.lyric);
    const currentPlayList = computed(() => store.getters.currentPlayList);
    const currentPlayIndex = computed(() => store.getters.currentPlayIndex);
    const curTime = computed(() => store.getters.curTime);
    const songTitle = computed(() => store.getters.songTitle);
    const singerName = computed(() => store.getters.singerName);
    const songPic = computed(() => store.getters.songPic);

    const routeSongId = computed(() => Number(route.params.id));

    function loadLyric() {
      const rawLyric = currentPlayList.value[currentPlayIndex.value]?.lyric || lyric.value;
      const parsed = rawLyric ? parseLyric(rawLyric) : [];
      lyricDebug.step4_parseResult(rawLyric, parsed);

      if (rawLyric) {
        lyricLines.value = parsed.map((item: [number, string]) => ({
          time: item[0],
          text: item[1],
        }));
      } else {
        lyricLines.value = [];
      }
      lyricDebug.step5_lyricLines(lyricLines.value);
      currentLine.value = 0;
      lyricOffset.value = (lyricBoxRef.value?.clientHeight || 560) / 2 - lineHeight / 2 - lyricPadTop;
    }

    watch(songId, () => {
      loadLyric();
    });

    let lastDebugTime = 0;
    watch(curTime, (time) => {
      if (!lyricLines.value.length) return;

      let line = currentLine.value;
      const len = lyricLines.value.length;

      while (line < len - 1 && time >= lyricLines.value[line + 1].time) {
        line++;
      }
      while (line > 0 && time < lyricLines.value[line].time) {
        line--;
      }

      if (line !== currentLine.value) {
        currentLine.value = line;
      }

      updateOffset(line);

      if (time - lastDebugTime > 2) {
        lastDebugTime = time;
        lyricDebug.step6_curTime(time, store.getters.duration, currentLine.value);
      }
    });

    function updateOffset(line: number) {
      const containerH = lyricBoxRef.value?.clientHeight || 560;
      // 让第 line 行的中心对齐容器中心
      lyricOffset.value = containerH / 2 - lineHeight / 2 - lyricPadTop - line * lineHeight;
    }

    function onResize() {
      updateOffset(currentLine.value);
    }

    onMounted(() => {
      window.addEventListener('resize', onResize);
    });

    watch(routeSongId, async (id) => {
      if (!id) return;
      if (songId.value === id) {
        loadLyric();
        return;
      }
      try {
        const result = (await HttpManager.getSongOfId(id)) as ResponseBody;
        const song = result.data?.[0] || result.data;
        if (song) {
          store.dispatch("playMusic", {
            id: song.id,
            url: song.url,
            pic: song.pic,
            index: -1,
            songTitle: song.name || song.title,
            singerName: song.singerName || '',
            lyric: song.lyric,
            currentSongList: [song],
          });
          lyricLines.value = song.lyric ? parseLyric(song.lyric).map((item: [number, string]) => ({
            time: item[0],
            text: item[1],
          })) : [];
          currentLine.value = 0;
          lyricOffset.value = (lyricBoxRef.value?.clientHeight || 560) / 2 - lineHeight / 2 - lyricPadTop;
        }
      } catch (e) {
        console.error("获取歌曲信息失败", e);
      }
    }, { immediate: true });

    return {
      songPic,
      singerName,
      songTitle,
      lyricLines,
      currentLine,
      lyricOffset,
      lyricBoxRef,
      songId,
      attachImageUrl: HttpManager.attachImageUrl,
    };
  },
});
</script>

<style lang="scss" scoped>
@import "@/assets/css/var.scss";

// ========== 左侧歌曲信息 ==========
.song-container {
  position: fixed;
  top: 120px;
  left: 50px;
  display: flex;
  flex-direction: column;
  z-index: 10;

  .song-pic {
    height: 260px;
    width: 260px;
    border: 4px solid rgba(255, 255, 255, 0.3);
    border-radius: 16px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  }

  .song-info {
    width: 260px;
    margin-top: 16px;
    li {
      width: 100%;
      line-height: 36px;
      font-size: 16px;
      color: #ccc;
      padding-left: 5%;
    }
  }
}

// ========== 右侧歌词区 ==========
.lyric-container {
  font-family: $font-family;
}

// ========== 星空歌词框 ==========
.song-lyric {
  position: relative;
  height: 560px;
  overflow: hidden;
  border-radius: 20px;
  // 深空底色
  background: radial-gradient(ellipse at 30% 20%, #1a1a3e 0%, #0a0a1a 40%, #020210 100%);

  // ===== 星空背景层 =====
  .star-field {
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 0;
    overflow: hidden;
    border-radius: 20px;
  }

  // 三层星星：小/中/大，纯 CSS box-shadow 生成
  .stars {
    position: absolute;
    inset: 0;
    &::before {
      content: '';
      position: absolute;
      width: 2px;
      height: 2px;
      border-radius: 50%;
      background: transparent;
    }
  }

  .stars-sm::before {
    box-shadow:
      // 随机散布 ~80 颗小星
      30px 40px #fff, 120px 80px #fff, 210px 30px #fff, 340px 60px #fff,
      50px 150px #fff, 180px 120px #fff, 290px 180px #fff, 410px 140px #fff,
      70px 260px #fff, 160px 220px #fff, 310px 290px #fff, 440px 250px #fff,
      90px 370px #fff, 220px 340px #fff, 350px 410px #fff, 460px 380px #fff,
      20px 480px #fff, 140px 450px #fff, 270px 500px #fff, 390px 470px #fff,
      60px 80px #fff, 250px 50px #fff, 380px 100px #fff, 150px 190px #fff,
      330px 230px #fff, 80px 320px #fff, 200px 400px #fff, 420px 340px #fff,
      110px 50px #fff, 280px 150px #fff, 370px 300px #fff, 40px 420px #fff,
      170px 300px #fff, 300px 50px #fff, 450px 200px #fff, 100px 220px #fff,
      240px 380px #fff, 360px 120px #fff, 60px 400px #fff, 190px 480px #fff;
    animation: twinkle-sm 4s ease-in-out infinite;
  }

  .stars-md::before {
    width: 3px;
    height: 3px;
    box-shadow:
      80px 30px #c8d6ff, 200px 90px #c8d6ff, 320px 40px #a0b8ff, 420px 110px #c8d6ff,
      40px 170px #a0b8ff, 150px 160px #c8d6ff, 270px 210px #a0b8ff, 400px 180px #c8d6ff,
      100px 280px #a0b8ff, 230px 270px #c8d6ff, 350px 330px #a0b8ff, 450px 300px #c8d6ff,
      60px 400px #c8d6ff, 180px 430px #a0b8ff, 300px 460px #c8d6ff, 420px 430px #a0b8ff,
      25px 100px #c8d6ff, 260px 50px #a0b8ff, 380px 170px #c8d6ff, 140px 350px #a0b8ff;
    animation: twinkle-md 7s ease-in-out infinite;
  }

  .stars-lg::before {
    width: 4px;
    height: 4px;
    box-shadow:
      150px 60px #e8eeff, 300px 130px #d0daff, 50px 230px #e8eeff, 250px 310px #d0daff,
      380px 260px #e8eeff, 100px 380px #d0daff, 330px 430px #e8eeff, 450px 60px #d0daff,
      70px 340px #e8eeff, 210px 70px #d0daff, 360px 390px #e8eeff, 180px 500px #d0daff;
    animation: twinkle-lg 5s ease-in-out infinite;
  }

  // 流星
  .shooting-star {
    position: absolute;
    top: -20px;
    left: 60%;
    width: 2px;
    height: 80px;
    background: linear-gradient(to bottom, rgba(255,255,255,0), rgba(255,255,255,0.8));
    border-radius: 50%;
    transform: rotate(-25deg);
    animation: shoot 6s ease-in infinite;
    animation-delay: 2s;
    opacity: 0;
  }

  // ===== 渐变遮罩：上下边缘渐隐 =====
  .lyric-mask-top,
  .lyric-mask-bottom {
    position: absolute;
    left: 0;
    right: 0;
    height: 120px;
    z-index: 1;
    pointer-events: none;
  }
  .lyric-mask-top {
    top: 0;
    background: linear-gradient(to bottom, rgba(10,10,26,1) 0%, rgba(10,10,26,0) 100%);
  }
  .lyric-mask-bottom {
    bottom: 0;
    background: linear-gradient(to top, rgba(10,10,26,1) 0%, rgba(10,10,26,0) 100%);
  }

  // ===== 歌词滚动层 =====
  .lyric-wrapper {
    position: relative;
    z-index: 2;
    transition: transform 0.6s cubic-bezier(0.25, 0.46, 0.45, 0.94);
    will-change: transform;
    padding: 280px 0;

    p {
      height: 52px;
      line-height: 52px;
      text-align: center;
      font-size: 18px;
      color: rgba(255, 255, 255, 0.35);
      transition: all 0.5s ease;
      cursor: default;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      padding: 0 30px;
      letter-spacing: 2px;
      text-shadow: 0 0 0 transparent;

      // 已唱过的歌词
      &.prev {
        color: rgba(255, 255, 255, 0.2);
      }
      // 即将到来的歌词
      &.next {
        color: rgba(255, 255, 255, 0.45);
      }
      // 当前行 — 发光高亮
      &.active {
        color: #fff;
        font-size: 24px;
        font-weight: 700;
        letter-spacing: 4px;
        text-shadow:
          0 0 10px rgba(180, 210, 255, 0.9),
          0 0 30px rgba(140, 180, 255, 0.6),
          0 0 60px rgba(100, 150, 255, 0.3),
          0 0 100px rgba(80, 130, 255, 0.2);
      }
    }
  }

  // ===== 无歌词 =====
  .no-lyric {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 2;

    span {
      font-size: 20px;
      color: rgba(255, 255, 255, 0.5);
      letter-spacing: 2px;
    }
  }
}

// ===== 动画关键帧 =====
@keyframes twinkle-sm {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.9; }
}
@keyframes twinkle-md {
  0%, 100% { opacity: 0.5; }
  30% { opacity: 1; }
  70% { opacity: 0.3; }
}
@keyframes twinkle-lg {
  0%, 100% { opacity: 0.6; }
  25% { opacity: 1; }
  75% { opacity: 0.2; }
}
@keyframes shoot {
  0% {
    opacity: 0;
    transform: rotate(-25deg) translateX(0) translateY(0);
  }
  5% {
    opacity: 1;
  }
  10% {
    opacity: 0;
    transform: rotate(-25deg) translateX(-200px) translateY(300px);
  }
  100% {
    opacity: 0;
  }
}

// ===== 过渡动画 =====
.lyric-fade-enter-active,
.lyric-fade-leave-active {
  transition: opacity 0.3s ease;
}
.lyric-fade-enter-from,
.lyric-fade-leave-to {
  opacity: 0;
}

// ===== 响应式 =====
@media screen and (min-width: $sm) {
  .container {
    padding-top: 30px;
  }
  .lyric-container {
    margin: 0 120px 0px 380px;
  }
}

@media screen and (max-width: $sm) {
  .container {
    padding: 20px;
  }
  .song-container {
    display: none;
  }
  .lyric-container {
    margin: 0 10px;
  }
}
</style>
