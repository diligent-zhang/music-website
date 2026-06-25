<template>
  <el-container>
    <el-aside class="album-slide">
      <el-image class="album-img" fit="contain" :src="attachImageUrl(songDetails.pic)" />
      <h3 class="album-info">{{ songDetails.title }}</h3>
    </el-aside>
    <el-main class="album-main">
      <h1>简介</h1>
      <p>{{ songDetails.introduction }}</p>
      <!--评分-->
      <div class="album-score">
        <div>
          <h3>歌单评分</h3>
          <el-rate v-model="rank" allow-half disabled></el-rate>
        </div>
        <span>{{ rank * 2 }}</span>
        <div>
          <h3>{{ assistText }} {{ score * 2 }}</h3>
          <el-rate allow-half v-model="score" :disabled="disabledRank" @click="pushValue()"></el-rate>
        </div>
      </div>
      <!--歌曲-->
      <song-list class="album-body" :songList="currentSongList"></song-list>
      <!-- 分页器：总条数大于每页条数时才显示 -->
      <div class="pagination-wrapper" v-if="total > pageSize">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          v-model:current-page="currentPage"
          @current-change="handlePageChange"
        />
      </div>
      <comment :playId="songListId" :type="1"></comment>
    </el-main>
  </el-container>
</template>

<script lang="ts">
import { defineComponent, ref, computed, getCurrentInstance } from "vue";
import { useStore } from "vuex";
import mixin from "@/mixins/mixin";
import SongList from "@/components/SongList.vue";
import Comment from "@/components/Comment.vue";
import { HttpManager } from "@/api";

export default defineComponent({
  components: {
    SongList,
    Comment,
  },
  setup() {
    const { proxy } = getCurrentInstance();
    const store = useStore();
    const { checkStatus } = mixin();

    const currentSongList = ref([]); // 当前页歌曲
    const nowSongListId = ref<number>(0);   // 歌单 ID
    const currentPage = ref(1);      // 当前页码
    const pageSize = ref(10);        // 每页 10 条
    const total = ref(0);            // 总条数
    const nowScore = ref(0);
    const nowRank = ref(0);
    const disabledRank = ref(false);
    const assistText = ref("评价");
    const songDetails = computed(() => store.getters.songDetails);
    const nowUserId = computed(() => store.getters.userId);
    let allSongs: any[] = [];        // 播放器用：累积全部歌曲

    nowSongListId.value = Number(songDetails.value.id);

    // 分页获取歌单歌曲
    async function fetchSongs(page: number) {
      try {
        const result = (await HttpManager.getSongsBySongListId(
          nowSongListId.value, page, pageSize.value
        )) as ResponseBody;
        const data = result.data;
        currentSongList.value = data.records || [];
        total.value = data.total || 0;
        if (page === 1 && data.records) {
          allSongs = [...data.records];
          store.commit("setCurrentPlayList", allSongs);
        }
      } catch (e) {
        console.error("获取歌单歌曲失败", e);
      }
    }

    // 翻页
    function handlePageChange(page: number) {
      currentPage.value = page;
      fetchSongs(page);
    }

    // 点击歌曲播放时补全完整列表（后台请求全量，确保切歌不中断）
    async function ensureFullPlaylist() {
      if (total.value > allSongs.length) {
        try {
          const result = (await HttpManager.getSongsBySongListId(
            nowSongListId.value, 1, 999
          )) as ResponseBody;
          allSongs = result.data?.records || [];
          store.commit("setCurrentPlayList", allSongs);
        } catch (e) {
          console.error("补全播放列表失败", e);
        }
      }
    }
    // 获取评分
    async function getRank(id) {
      try {
        const result = (await HttpManager.getRankOfSongListId(id)) as ResponseBody;
        nowRank.value = (result.data || 0) / 2;
      } catch (e) {
        console.error("获取歌单评分失败", e);
      }
    }
    async function getUserRank(userId, songListId) {
      try {
        const result = (await HttpManager.getUserRank(userId, songListId)) as ResponseBody;
        nowScore.value = (result.data || 0) / 2;
        disabledRank.value = true;
        assistText.value = "已评价";
      } catch (e) {
        console.error("获取用户评分失败", e);
      }
    }
    // 提交评分
    async function pushValue() {
      if (disabledRank.value || !checkStatus()) return;

      const songListId = nowSongListId.value;
      var consumerId = nowUserId.value;
      const score = nowScore.value*2;
      try {
        const result = (await HttpManager.setRank({songListId,consumerId,score})) as ResponseBody;
        (proxy as any).$message({
          message: result.message,
          type: result.type,
        });

        if (result.success) {
          getRank(nowSongListId.value);
          disabledRank.value = true;
          assistText.value = "已评价";
        }
      } catch (error) {
        console.error(error);
      }
    }

    getUserRank(nowUserId.value, nowSongListId.value);
    getRank(nowSongListId.value);
    fetchSongs(1); // 初次加载第 1 页

    return {
      songDetails,
      rank: nowRank,
      score: nowScore,
      disabledRank,
      assistText,
      currentSongList,
      songListId: nowSongListId,
      total,
      pageSize,
      currentPage,
      attachImageUrl: HttpManager.attachImageUrl,
      pushValue,
      handlePageChange,
      ensureFullPlaylist,
    };
  },
});
</script>

<style lang="scss" scoped>
@import "@/assets/css/var.scss";

.album-slide {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 20px;

  .album-img {
    height: 250px;
    width: 250px;
    border-radius: 10%;
  }

  .album-info {
    width: 70%;
    padding-top: 2rem;
  }
}

.album-main {
  h1 {
    font-size: 22px;
  }

  p {
    color: rgba(0, 0, 0, 0.5);
    margin: 10px 0 20px 0px;
  }
  /*歌单打分*/
  .album-score {
    display: flex;
    align-items: center;
    margin: 1vw;

    h3 {
      margin: 10px 0;
    }
    span {
      font-size: 60px;
    }
    & > div:last-child {
      margin-left: 10%;
    }
  }

  .album-body {
    margin: 20px 0 20px 0px;
  }
}

@media screen and (min-width: $sm) {
  .album-slide {
    position: fixed;
    width: 400px;
  }
  .album-main {
    min-width: 600px;
    padding-right: 10vw;
    margin-left: 400px;
  }
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin: 20px 0;
}

@media screen and (max-width: $sm) {
  .album-slide {
    display: none;
  }
}
</style>
