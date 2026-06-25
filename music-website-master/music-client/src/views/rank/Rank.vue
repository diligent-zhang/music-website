<template>
  <div class="rank-container">
    <h2 class="rank-title">🔥 热门歌曲排行榜</h2>
    
    <div class="rank-tabs">
      <button 
        v-for="tab in tabs" 
        :key="tab.key" 
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ tab.name }}
      </button>
    </div>

    <div v-if="rankList.length > 0" class="rank-list">
      <div 
        v-for="(song, index) in rankList" 
        :key="song.id" 
        class="rank-item"
        @click="playSong(song)"
      >
        <div class="rank-number" :class="{ top3: index < 3 }">
          {{ index + 1 }}
        </div>
        <div class="song-info">
          <div class="song-name">{{ song.title || song.name }}</div>
          <div class="song-singer">{{ song.singerName || song.singer }}</div>
        </div>
        <div class="play-count">
          {{ formatPlayCount(song.playCount || song.play_count) }} 播放
        </div>
        <div class="play-btn">
          <span class="iconfont icon-bofang"></span>
        </div>
      </div>
    </div>

    <div v-else-if="!loading" class="empty-state">
      <span class="empty-icon">📊</span>
      <span>暂无排行榜数据</span>
    </div>

    <div v-if="loading" class="loading">
      <span class="loading-icon">🎵</span>
      <span>加载中...</span>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted } from "vue";
import { useStore } from "vuex";
import { HttpManager } from "@/api";

interface ResponseBody {
  code: number;
  message: string;
  data: any;
}

export default defineComponent({
  name: "Rank",
  setup() {
    const store = useStore();
    const activeTab = ref("day");
    const rankList = ref<any[]>([]);
    const loading = ref(true);

    const tabs = [
      { key: "day", name: "今日榜" },
      { key: "week", name: "周榜" },
      { key: "month", name: "月榜" },
    ];

    const fetchRankList = async (type: string) => {
      loading.value = true;
      try {
        const result = (await HttpManager.getRankList(type)) as ResponseBody;
        if (result.code === 200) {
          rankList.value = result.data || [];
        } else {
          console.error("获取排行榜失败:", result.message);
          rankList.value = [];
        }
      } catch (error) {
        console.error("获取排行榜失败:", error);
        rankList.value = [];
      } finally {
        loading.value = false;
      }
    };

    const switchTab = (tabKey: string) => {
      if (activeTab.value !== tabKey) {
        activeTab.value = tabKey;
        fetchRankList(tabKey);
      }
    };

    const playSong = (song: any) => {
      try {
        // 通过 store action 统一处理播放（含播放次数上报）
        store.dispatch("playMusic", {
          id: song.id,
          url: song.url,
          pic: song.pic,
          index: 0,
          songTitle: song.title || song.name,
          singerName: song.singerName || song.singer,
          lyric: song.lyric || [],
          currentSongList: rankList.value,
        });
        store.commit("setIsPlay", true);
        store.commit("setPlayBtnIcon", "icon-zanting");
      } catch (error) {
        console.error("播放失败:", error);
      }
    };

    const formatPlayCount = (count: number | undefined | null): string => {
      if (!count) return "0";
      if (count >= 100000000) {
        return (count / 100000000).toFixed(1) + "亿";
      }
      if (count >= 10000) {
        return (count / 10000).toFixed(1) + "万";
      }
      return count.toString();
    };

    onMounted(() => {
      fetchRankList("day");
    });

    return {
      activeTab,
      rankList,
      loading,
      tabs,
      switchTab,
      playSong,
      formatPlayCount,
    };
  },
});
</script>

<style lang="scss" scoped>
@import "@/assets/css/var.scss";

.rank-container {
  padding: 1rem;
  max-width: 800px;
  margin: 0 auto;
}

.rank-title {
  font-size: 1.5rem;
  font-weight: bold;
  color: $color-black;
  margin-bottom: 1rem;
  text-align: center;
}

.rank-tabs {
  display: flex;
  justify-content: center;
  margin-bottom: 1rem;
  
  button {
    padding: 0.5rem 1.5rem;
    margin: 0 0.5rem;
    border: none;
    border-radius: 20px;
    background: $color-light-grey;
    cursor: pointer;
    transition: all 0.3s;
    font-size: 0.9rem;
    
    &.active {
      background: $theme-color;
      color: white;
    }
    
    &:hover:not(.active) {
      background: darken($color-light-grey, 5%);
    }
  }
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.rank-item {
  display: flex;
  align-items: center;
  padding: 0.8rem;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.2s;
  
  &:hover {
    transform: translateX(5px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }
}

.rank-number {
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  font-weight: bold;
  color: $color-grey;
  margin-right: 1rem;
  
  &.top3 {
    color: $theme-color;
    font-size: 1.5rem;
  }
}

.song-info {
  flex: 1;
  min-width: 0;
  
  .song-name {
    font-weight: 600;
    color: $color-black;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  
  .song-singer {
    font-size: 0.8rem;
    color: $color-grey;
    margin-top: 0.2rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

.play-count {
  font-size: 0.8rem;
  color: $color-grey;
  margin-right: 1rem;
  white-space: nowrap;
}

.play-btn {
  color: $theme-color;
  font-size: 1.2rem;
  opacity: 0;
  transition: opacity 0.2s;
  
  .rank-item:hover & {
    opacity: 1;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 3rem 1rem;
  color: $color-grey;
  
  .empty-icon {
    font-size: 3rem;
    margin-bottom: 1rem;
  }
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
  color: $color-grey;
  
  .loading-icon {
    font-size: 2rem;
    margin-bottom: 0.5rem;
    animation: bounce 1s infinite;
  }
}

@keyframes bounce {
  0%, 100% { 
    transform: translateY(0); 
  }
  50% { 
    transform: translateY(-10px); 
  }
}

@media screen and (max-width: $sm) {
  .rank-container {
    padding: 0.5rem;
  }
  
  .rank-title {
    font-size: 1.2rem;
  }
  
  .rank-tabs button {
    padding: 0.4rem 1rem;
    font-size: 0.8rem;
  }
  
  .rank-item {
    padding: 0.6rem;
  }
  
  .rank-number {
    width: 1.5rem;
    height: 1.5rem;
    font-size: 1rem;
    margin-right: 0.5rem;
    
    &.top3 {
      font-size: 1.2rem;
    }
  }
  
  .play-count {
    display: none;
  }
}
</style>