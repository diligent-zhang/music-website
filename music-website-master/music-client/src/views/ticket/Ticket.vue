<template>
  <div class="ticket-page">
    <h2 class="page-title">演唱会</h2>

    <!-- 状态筛选标签栏：全部 / 预告中 / 售票中 / 已售罄 -->
    <div class="tab-bar">
        <span
            v-for="tab in tabs"
            :key="tab.value"
            :class="['tab', { active: activeTab === tab.value }]"
            @click="switchTab(tab.value)"
        >
          {{ tab.label }}
        </span>
    </div>

    <!-- 演唱会卡片网格 -->
    <div v-if="!loading && concerts.length > 0" class="card-grid">
      <div
          v-for="item in concerts"
          :key="item.id"
          class="concert-card"
          @click="goDetail(item.id)"
      >
        <el-image :src="attachImageUrl(item.coverPic) || defaultCover" fit="cover" class="card-img">
          <template #error>
            <div class="img-placeholder">暂无封面</div>
          </template>
        </el-image>
        <div class="card-info">
          <h3>{{ item.title }}</h3>
          <p class="singer">{{ item.singerName || '未知歌手' }}</p>
          <p class="venue">{{ item.venue }}</p>
          <p class="time">{{ formatDate(item.showTime) }}</p>
          <div class="card-footer">
            <el-tag :type="statusTag(item.status)" size="small">
              {{ statusText(item.status) }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <el-empty v-if="!loading && concerts.length === 0" description="暂无演唱会" />

    <!-- 骨架屏加载态 -->
    <div v-if="loading" class="loading-wrap">
      <el-skeleton animated :count="4" />
    </div>

    <!-- 分页 -->
    <el-pagination
        v-if="total > pageSize"
        class="pagination"
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        v-model:current-page="currentPage"
        @current-change="fetchList"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { HttpManager } from '@/api/index';

const attachImageUrl = HttpManager.attachImageUrl;

/** 状态码 → 展示文案 */
const STATUS_MAP: Record<number, string> = {
  0: '下架', 1: '预告', 2: '售票中', 3: '售罄', 4: '已结束',
};
/** 状态码 → Element Tag 颜色 */
const STATUS_TAG_MAP: Record<number, string> = {
  0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'info',
};

export default defineComponent({
  name: 'Ticket',
  setup() {
    const router = useRouter();
    const loading = ref(false);
    const concerts = ref<any[]>([]);
    const total = ref(0);
    const currentPage = ref(1);
    const pageSize = ref(12);
    const activeTab = ref<number | ''>(''); // '' 表示"全部"

    const tabs = [
      { label: '全部', value: '' },
      { label: '预告中', value: 1 },
      { label: '售票中', value: 2 },
      { label: '已售罄', value: 3 },
    ];

    // 默认封面图（Element Plus 占位图）
    const defaultCover =
        'https://cube.elemecdn.com/6/94/4d3ea53c084bad6931a56d5158a48jpeg.jpeg';

    onMounted(() => fetchList());

    /** 拉取演唱会列表 */
    function fetchList() {
      loading.value = true;
      const params: any = { page: currentPage.value, size: pageSize.value };
      if (activeTab.value !== '') params.status = activeTab.value;

      HttpManager.getConcertList(params)
          .then((res: any) => {
            concerts.value = res.data?.records || [];
            total.value = res.data?.total || 0;
          })
          .finally(() => (loading.value = false));
    }

    /** 切换筛选标签 */
    function switchTab(val: number | '') {
      activeTab.value = val;
      currentPage.value = 1; // 重置到第一页
      fetchList();
    }

    /** 跳转到演唱会详情页 */
    function goDetail(id: number) {
      router.push(`/ticket/${id}`);
    }

    function formatDate(dateStr: string) {
      if (!dateStr) return '';
      return new Date(dateStr).toLocaleDateString('zh-CN');
    }

    function statusText(status: number) {
      return STATUS_MAP[status] || '未知';
    }

    function statusTag(status: number): 'info' | 'warning' | 'success' | 'danger' {
      return (STATUS_TAG_MAP[status] || 'info') as any;
    }

    return {
      loading, concerts, total, currentPage, pageSize, activeTab, tabs,
      defaultCover, attachImageUrl, switchTab, goDetail, formatDate, statusText, statusTag, fetchList,
    };
  },
});
</script>

<style scoped>
.ticket-page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.page-title {
  font-size: 24px;
  margin-bottom: 16px;
}
.tab-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.tab {
  padding: 6px 16px;
  border-radius: 20px;
  background: #f5f5f5;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}
.tab.active {
  background: #409eff;
  color: #fff;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}
.concert-card {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: transform 0.2s;
  background: #fff;
}
.concert-card:hover {
  transform: translateY(-4px);
}
.card-img {
  width: 100%;
  height: 180px;
}
.img-placeholder {
  width: 100%;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eee;
  color: #999;
  font-size: 14px;
}
.card-info {
  padding: 12px 16px 16px;
}
.card-info h3 {
  font-size: 16px;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.singer {
  color: #666;
  font-size: 13px;
  margin-bottom: 4px;
}
.venue,
.time {
  color: #999;
  font-size: 12px;
  margin-bottom: 2px;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.loading-wrap {
  padding: 40px;
}
.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>