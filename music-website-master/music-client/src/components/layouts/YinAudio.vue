<template>
  <!-- 修复：完整闭合标签 + 规范属性格式 + 错误监听 -->
  <audio
      :src="attachImageUrl(songUrl)"
      ref="audioRef"
      preload="auto"
      @canplay="handleCanplay"
      @timeupdate="handleTimeupdate"
      @ended="handleEnded"
      @error="handleAudioError"
      type="audio/mpeg"
  >
    您的浏览器不支持音频播放
  </audio>
</template>

<script lang="ts">
import { defineComponent, ref, getCurrentInstance, computed, watch, onMounted } from "vue";
import { useStore } from "vuex";
import { HttpManager } from "@/api"; // 确保导入音频路径拼接工具

export default defineComponent({
  name: "YinAudio",
  setup() {
    // 获取Vue实例和Vuex store
    const { proxy } = getCurrentInstance();
    const store = useStore();

    // 音频DOM元素引用（语义化命名 + TS类型）
    const audioRef = ref<HTMLAudioElement | null>(null);

    // 静音状态（初始化静音，解决自动播放限制）
    const muted = ref(true);

    // ===== 核心变量定义（解决TS作用域报错）=====
    // 1. 从Vuex获取音频路径（computed属性）
    const songUrl = computed(() => store.getters.songUrl);
    // 2. 播放状态（Vuex）
    const isPlay = computed(() => store.getters.isPlay);
    // 3. 音量（Vuex）
    const volume = computed(() => store.getters.volume);
    // 4. 指定播放时刻（Vuex）
    const changeTime = computed(() => store.getters.changeTime);
    // 5. 自动播放下一首触发标识（Vuex）
    const autoNext = computed(() => store.getters.autoNext);
    // 6. 音频路径拼接方法（从HttpManager解构，解决TS报错）
    const attachImageUrl: (path: string | number) => string = HttpManager.attachImageUrl;

    // ===== 监听逻辑（增加空值校验）=====
    // 监听播放/暂停状态
    watch(isPlay, () => {
      if (audioRef.value) togglePlay();
    });

    // 监听指定播放时刻
    watch(changeTime, () => {
      if (audioRef.value) audioRef.value.currentTime = changeTime.value;
    });

    // 监听音量变化
    watch(volume, (value) => {
      if (audioRef.value) audioRef.value.volume = value;
    });

    // ===== 核心方法定义 =====
    /**
     * 播放/暂停切换
     */
    function togglePlay() {
      if (!audioRef.value) return;
      // 捕获play()的Promise异常，解决Uncaught (in promise)报错
      isPlay.value
          ? audioRef.value.play().catch(handlePlayError)
          : audioRef.value.pause();
    }

    /**
     * 音频可播放时触发（初始化播放）
     */
    function handleCanplay() {
      if (!audioRef.value) return;
      proxy.$store.commit("setDuration", audioRef.value.duration);
      if (muted.value) {
        audioRef.value.muted = false;
        muted.value = false;
      }
      proxy.$store.commit("setIsPlay", true);
      // 直接播放，不依赖 watch(isPlay)——切歌时 isPlay 可能已为 true，watch 不会触发
      audioRef.value.play().catch(handlePlayError);
    }

    /**
     * 播放进度更新时触发（记录当前播放位置）
     */
    function handleTimeupdate() {
      if (audioRef.value) {
        proxy.$store.commit("setCurTime", audioRef.value.currentTime);
      }
    }

    /**
     * 播放结束时触发（重置状态 + 触发下一首）
     */
    function handleEnded() {
      proxy.$store.commit("setIsPlay", false);
      proxy.$store.commit("setCurTime", 0);
      proxy.$store.commit("setAutoNext", !autoNext.value);
    }

    /**
     * 音频加载/播放错误监听（定位404/格式问题）
     */
    function handleAudioError(e: Event) {
      const audio = e.target as HTMLAudioElement;
      console.error("===== 音频错误详情 =====");
      console.error("错误对象:", audio.error);
      console.error("请求URL:", audio.src);
      
      // 错误类型映射（快速定位原因）
      const errorMap = {
        1: "音频加载被中止（手动取消/网络中断）",
        2: "网络错误（跨域/服务器拒绝）",
        3: "格式不支持/文件损坏（非MP3/AAC）",
        4: "资源不存在（404，URL错误）",
        5: "浏览器不支持该音频资源"
      };
      
      const errorCode = audio.error?.code;
      console.error("错误原因：", errorMap[errorCode] || "未知错误");
      console.error("错误代码：", errorCode);
      
      // 显示用户友好的错误提示
      const message = (proxy as any)?.$message;
      if (message) {
        message({
          message: `音频播放失败: ${errorMap[errorCode] || "未知错误"}`,
          type: "error"
        });
      }
    }

    /**
     * 播放失败异常捕获
     */
    function handlePlayError(error: Error) {
      console.error("播放失败：", error.message);
      proxy.$store.commit("setIsPlay", false);
    }

    // ===== 组件挂载后初始化（排查音频URL）=====
    onMounted(() => {
      // 初始化音频DOM（解决自动播放限制）
      if (audioRef.value) {
        audioRef.value.muted = true;
        audioRef.value.play().catch(error => {
          console.warn("自动播放需用户交互触发（浏览器限制）：", error.message);
        });
      }

      // ===== 关键：打印音频URL用于排查404 =====
      console.log("===== 音频寻址排查 =====");
      console.log("1. Vuex原始songUrl值：", songUrl.value);
      console.log("2. 拼接后的完整音频URL：", attachImageUrl(songUrl.value));
      console.log("3. 请复制上方URL到浏览器地址栏，验证是否能访问！");
    });

    // ===== 返回模板所需变量/方法 =====
    return {
      songUrl,          // 音频原始路径
      audioRef,         // DOM引用
      handleCanplay,    // 可播放事件
      handleTimeupdate, // 进度更新事件
      handleEnded,      // 播放结束事件
      handleAudioError, // 错误监听事件
      muted,            // 静音状态
      attachImageUrl    // 路径拼接方法（必须返回，解决TS报错）
    };
  },
});
</script>

<style scoped>
/* 隐藏原生音频控件（由自定义播放条控制） */
audio {
  display: none;
  width: 0;
  height: 0;
}
</style>