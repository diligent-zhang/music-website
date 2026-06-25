<template>
  <!-- 浮动按钮 -->
  <div class="ai-fab" @click="togglePanel" v-show="!visible">
    <el-icon :size="24"><Headset /></el-icon>
  </div>

  <!-- 聊天面板 -->
  <transition name="panel-slide">
    <div class="ai-panel" v-show="visible">
      <!-- 头部 -->
      <div class="panel-header">
        <span class="title">🎵 音乐小助手</span>
        <el-icon class="close-btn" :size="18" @click="visible = false"><Close /></el-icon>
      </div>

      <!-- 消息列表 -->
      <div class="panel-body" ref="bodyRef">
        <yin-ai-message
          v-for="(msg, i) in messages"
          :key="i"
          :role="msg.role"
          :content="msg.content"
        />
        <yin-ai-typing v-if="loading" />
      </div>

      <!-- 输入区 -->
      <div class="panel-footer">
        <el-input
          v-model="inputText"
          placeholder="问我音乐相关的问题..."
          @keyup.enter="send"
          :disabled="loading"
          size="small"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          circle
          size="small"
          @click="send"
          :disabled="loading || !inputText.trim()"
        />
      </div>
    </div>
  </transition>
</template>

<script lang="ts" setup>
import { ref, nextTick, watch } from 'vue';
import { useStore } from 'vuex';
import { Headset, Close, Promotion } from '@element-plus/icons-vue';
import { HttpManager } from '@/api';
import YinAiMessage from './YinAiMessage.vue';
import YinAiTyping from './YinAiTyping.vue';

interface Message {
  role: 'user' | 'assistant';
  content: string;
}

// 持久化用户ID（localStorage），保证同一用户记忆不丢失
function getUserId(): string {
  const key = 'ai_user_id';
  let id = localStorage.getItem(key);
  if (!id) {
    id = 'u-' + crypto.randomUUID();
    localStorage.setItem(key, id);
  }
  return id;
}
const userId = getUserId();

const store = useStore();
const visible = ref(false);
const messages = ref<Message[]>([]);
const inputText = ref('');
const loading = ref(false);
const bodyRef = ref<HTMLElement | null>(null);

function togglePanel() {
  visible.value = !visible.value;
}

// 自动滚动到底部
watch(messages, async () => {
  await nextTick();
  if (bodyRef.value) {
    bodyRef.value.scrollTop = bodyRef.value.scrollHeight;
  }
}, { deep: true });

// 处理结构化播放动作（新格式），直接驱动播放，无需二次 API 请求
function processActions(actions: any[]) {
  for (const action of actions) {
    switch (action.type) {
      case 'play': {
        if (action.url) {
          store.dispatch('playMusic', {
            id: action.songId,
            url: action.url,
            pic: action.pic,
            index: 0,
            songTitle: action.name,
            singerName: action.singerName,
            lyric: action.lyric,
            currentSongList: [{
              id: action.songId,
              name: action.name,
              url: action.url,
              pic: action.pic,
              singerName: action.singerName,
              lyric: action.lyric,
            }],
          });
        }
        break;
      }
      case 'pause':
        store.commit('setIsPlay', false);
        break;
      case 'resume':
        store.commit('setIsPlay', true);
        break;
      case 'next':
        store.commit('setAutoNext', true);
        break;
      case 'volume':
        if (action.volume != null) {
          store.commit('setVolume', action.volume / 100);
        }
        break;
    }
  }
}

// 解析控制标记并执行操作（旧格式兼容）
function parseControlMarkers(text: string): string {
  const playMatch = text.match(/\[PLAY:(\d+)\]/);
  if (playMatch) {
    const songId = parseInt(playMatch[1]);
    HttpManager.getSongOfId(songId).then((res: any) => {
      const list = (res.data || res) as any[];
      const song = Array.isArray(list) ? list[0] : list;
      if (song && song.url) {
        store.dispatch('playMusic', {
          id: song.id,
          url: song.url,
          pic: song.pic,
          index: 0,
          songTitle: song.name || song.title,
          singerName: song.singerName,
          lyric: song.lyric,
          currentSongList: Array.isArray(list) ? list : [song],
        });
      }
    }).catch((err) => { console.error('AI播放失败:', err); });
    return text.replace(/\[PLAY:\d+\]/g, '');
  }

  if (/\[PAUSE\]/.test(text)) {
    store.commit('setIsPlay', false);
    return text.replace(/\[PAUSE\]/g, '');
  }
  if (/\[RESUME\]/.test(text)) {
    store.commit('setIsPlay', true);
    return text.replace(/\[RESUME\]/g, '');
  }
  if (/\[NEXT\]/.test(text)) {
    store.commit('setAutoNext', true);
    return text.replace(/\[NEXT\]/g, '');
  }
  const volMatch = text.match(/\[VOLUME:(\d+)\]/);
  if (volMatch) {
    const vol = parseInt(volMatch[1]) / 100;
    store.commit('setVolume', vol);
    return text.replace(/\[VOLUME:\d+\]/g, '');
  }

  return text;
}

async function send() {
  const text = inputText.value.trim();
  if (!text || loading.value) return;

  messages.value.push({ role: 'user', content: text });
  inputText.value = '';
  loading.value = true;

  try {
    const apiMessages = messages.value.map(m => ({ role: m.role, content: m.content }));
    const res: any = await HttpManager.sendAiMessage({ userId, messages: apiMessages });
    const data = res.data || res;
    const reply = data.reply || '抱歉，我暂时无法回复。';

    const hasActions = data.actions && Array.isArray(data.actions) && data.actions.length > 0;

    if (hasActions) {
      // 新格式：结构化动作直接驱动播放，无需二次 API 请求
      processActions(data.actions);
    }

    // 向后兼容：仅当没有结构化 actions 时，才用文本标记解析兜底
    const cleanReply = hasActions ? reply.replace(/\[(PLAY|PAUSE|RESUME|NEXT|VOLUME)[^\]]*\]/g, '') : parseControlMarkers(reply);
    messages.value.push({ role: 'assistant', content: cleanReply });
  } catch {
    messages.value.push({ role: 'assistant', content: '抱歉，网络出错了，请稍后再试。' });
  } finally {
    loading.value = false;
  }
}
</script>

<style lang="scss" scoped>
@import '@/assets/css/var.scss';

.ai-fab {
  position: fixed;
  z-index: 200;
  right: 10px;
  bottom: 130px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: $theme-color;
  color: $color-white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  animation: breathe 2s ease-in-out infinite;
  transition: transform 0.2s;

  &:hover {
    transform: scale(1.1);
  }
}

@keyframes breathe {
  0%, 100% { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3); }
  50% { box-shadow: 0 2px 20px rgba(48, 164, 252, 0.6); }
}

.ai-panel {
  position: fixed;
  z-index: 200;
  right: 10px;
  bottom: 180px;
  width: 380px;
  height: 520px;
  background: $color-white;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: $theme-color;
  color: $color-white;

  .title {
    font-size: 15px;
    font-weight: 600;
  }
  .close-btn {
    cursor: pointer;
    &:hover { opacity: 0.8; }
  }
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f9fafb;
}

.panel-footer {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #eee;
}

.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: all 0.3s ease;
}
.panel-slide-enter-from,
.panel-slide-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
