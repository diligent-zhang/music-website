<template>
  <!--轮播图-->
  <el-carousel v-if="swiperList.length" class="swiper-container" type="card" height="20vw" :interval="4000">
    <el-carousel-item v-for="(item, index) in swiperList" :key="index">
      <img :src="HttpManager.attachImageUrl(item.pic)" />
    </el-carousel-item>
  </el-carousel>
  <!--热门歌单-->
  <play-list class="play-list-container" title="歌单" path="song-sheet-detail" :playList="songList"></play-list>
  <!--热门歌手-->
  <play-list class="play-list-container" title="歌手" path="singer-detail" :playList="singerList"></play-list>
</template>

<script lang="ts" setup>
import { ref, onMounted } from "vue";

import PlayList from "@/components/PlayList.vue";
import { NavName } from "@/enums";
import { HttpManager } from "@/api";
import mixin from "@/mixins/mixin";

// 1. 定义接口，解决 ResponseBody 未定义和隐式 any 问题
interface BannerItem {
  id: number;
  pic: string;
  // 其他轮播图字段...
}

interface SongSheetItem {
  id: number;
  name: string;
  // 其他歌单字段...
}

interface SingerItem {
  id: number;
  name: string;
  // 其他歌手字段...
}

interface ResponseBody<T = any> {
  code: number;
  msg: string;
  data: T;
}

// 2. 给响应式数据添加明确的类型注解
const songList = ref<SongSheetItem[]>([]); // 歌单列表
const singerList = ref<SingerItem[]>([]); // 歌手列表
const swiperList = ref<BannerItem[]>([]); // 轮播图

const { changeIndex } = mixin();

// 3. 将异步请求移到 onMounted 内，使用 async/await 捕获错误
onMounted(async () => {
  changeIndex(NavName.Home);

  try {
    // 轮播图请求
    const bannerRes = await HttpManager.getBannerList() as ResponseBody<BannerItem[]>;
    swiperList.value = bannerRes.data.sort((a, b) => a.id - b.id);

    // 歌单请求
    const songRes = await HttpManager.getSongList() as ResponseBody<SongSheetItem[]>;
    songList.value = songRes.data.sort((a, b) => a.id - b.id).slice(0, 10);

    // 歌手请求
    const singerRes = await HttpManager.getAllSinger() as ResponseBody<SingerItem[]>;
    singerList.value = singerRes.data.sort((a, b) => a.id - b.id).slice(0, 10);
  } catch (error) {
    console.error("数据请求失败：", error);
  }
});
</script>

<style lang="scss" scoped>
@import "@/assets/css/var.scss";

/*轮播图*/
.swiper-container {
  width: 90%;
  margin: auto;
  padding-top: 20px;
  img {
    width: 100%;
  }
}

.swiper-container:deep(.el-carousel__indicators.el-carousel__indicators--outside) {
  display: inline-block;
  transform: translateX(30vw);
}
</style>