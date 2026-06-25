<template>
  <div class="my-tickets">
    <div class="page-header">
      <h2>我的票夹</h2>
      <span class="ticket-count" v-if="orders.length">共 {{ orders.length }} 张票</span>
    </div>

    <!-- 加载态 -->
    <div v-if="loading" class="loading-wrap">
      <el-skeleton animated :count="3" />
    </div>

    <!-- 未登录 -->
    <div v-else-if="!userId" class="empty-wrap">
      <el-empty description="请先登录查看票夹">
        <el-button type="primary" @click="$router.push('/sign-in')">去登录</el-button>
      </el-empty>
    </div>

    <!-- 空票夹 -->
    <div v-else-if="!orders.length" class="empty-wrap">
      <el-empty description="暂无购票记录">
        <el-button type="primary" @click="$router.push('/ticket')">去购票</el-button>
      </el-empty>
    </div>

    <!-- 订单列表 -->
    <div v-else class="order-list">
      <div v-for="item in orders" :key="item.orderNo" class="order-card">
        <div class="card-top">
          <div class="concert-info">
            <h3>{{ item.concertTitle || '未知演唱会' }}</h3>
            <p class="meta">
              <span class="venue">📍 {{ item.venue || '待定场馆' }}</span>
              <span class="time">🕐 {{ formatDateTime(item.showTime) }}</span>
            </p>
          </div>
          <el-tag
            :type="item.verifyStatus === 1 ? 'success' : 'warning'"
            size="small"
            class="status-tag"
          >
            {{ item.verifyStatus === 1 ? '已入场' : '未核验' }}
          </el-tag>
        </div>

        <div class="card-bottom">
          <div class="ticket-detail">
            <span class="tier">{{ item.tierName }}</span>
            <span class="price">&yen;{{ item.price }}</span>
            <span class="order-no">订单号：{{ item.orderNo }}</span>
          </div>
          <el-button type="primary" size="small" @click="goDetail(item.orderNo)">
            查看详情
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { HttpManager } from '@/api/index';

export default defineComponent({
  name: 'MyTickets',
  setup() {
    const router = useRouter();
    const store = useStore();

    const loading = ref(false);
    const orders = ref<any[]>([]);

    const userId = computed(() => store.getters.userId);

    onMounted(() => {
      if (userId.value) {
        fetchOrders();
      }
    });

    function fetchOrders() {
      loading.value = true;
      HttpManager.getMyTicketOrders(userId.value)
        .then((res: any) => {
          if (res.success) {
            orders.value = res.data || [];
          }
        })
        .finally(() => (loading.value = false));
    }

    function goDetail(orderNo: string) {
      router.push(`/ticket/order/${orderNo}`);
    }

    function formatDateTime(dateStr: string) {
      if (!dateStr) return '';
      return new Date(dateStr).toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    }

    return { loading, orders, userId, goDetail, formatDateTime };
  },
});
</script>

<style scoped>
.my-tickets {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}
.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}
.page-header h2 {
  font-size: 22px;
}
.ticket-count {
  color: #999;
  font-size: 14px;
}
.loading-wrap {
  padding: 20px 0;
}
.empty-wrap {
  padding: 60px 0;
  text-align: center;
}
.order-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.order-card {
  background: #fff;
  border-radius: 10px;
  padding: 18px 22px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.2s;
}
.order-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.10);
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}
.concert-info h3 {
  font-size: 16px;
  margin-bottom: 6px;
}
.meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #888;
}
.status-tag {
  flex-shrink: 0;
}
.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f5f5f5;
}
.ticket-detail {
  display: flex;
  align-items: center;
  gap: 16px;
}
.tier {
  font-weight: 500;
  font-size: 14px;
}
.price {
  font-size: 18px;
  font-weight: bold;
  color: #f56c6c;
}
.order-no {
  font-size: 12px;
  color: #bbb;
}
</style>
