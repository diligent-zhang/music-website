<template>
  <div class="my-favorite">
    <div class="favorite-header">
      <h2>我的喜爱</h2>
    </div>
    <div class="favorite-body">
      <song-list :songList="collectSongList" :show="true" @changeData="changeData"></song-list>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, nextTick, ref, computed } from "vue";
import { useStore } from "vuex";
import SongList from "@/components/SongList.vue";
import { HttpManager } from "@/api";

export default defineComponent({
  components: {
    SongList,
  },
  setup() {
    const store = useStore();
    const collectSongList = ref([]);
    const userId = computed(() => store.getters.userId);

    async function getCollection(uid) {
      collectSongList.value = [];
      const result = (await HttpManager.getCollectionOfUser(uid)) as ResponseBody;
      const collectIDList = result.data || [];
      const ids = collectIDList.filter((item: any) => item.songId).map((item: any) => item.songId);
      if (ids.length > 0) {
        const batchResult = (await HttpManager.getSongsByIds(ids)) as ResponseBody;
        collectSongList.value = batchResult.data || [];
      }
    }

    function changeData() {
      getCollection(userId.value);
    }

    nextTick(() => {
      getCollection(userId.value);
    });

    return {
      collectSongList,
      changeData,
    };
  },
});
</script>

<style lang="scss" scoped>
@import "@/assets/css/var.scss";

.my-favorite {
  padding: 100px 10vw 0;
}

.favorite-header {
  text-align: center;
  margin-bottom: 30px;

  h2 {
    font-size: 28px;
    font-weight: 600;
  }
}

.favorite-body {
  background-color: $color-white;
  border-radius: 12px;
  padding: 20px;
}
</style>
