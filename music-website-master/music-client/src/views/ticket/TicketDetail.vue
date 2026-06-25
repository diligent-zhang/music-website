<template>
  <div class="ticket-detail" v-loading="loading">
    <div class="back-btn" @click="$router.back()">&larr; 返回</div>

    <div class="detail-layout" v-if="concert">
      <!-- 左侧：演唱会详细信息 -->
      <div class="left-panel">
        <el-image :src="attachImageUrl(concert.coverPic) || defaultCover" fit="cover" class="cover-img">
          <template #error>
            <div class="img-placeholder">暂无封面</div>
          </template>
        </el-image>
        <h1>{{ concert.title }}</h1>
        <p class="info-row"><strong>歌手：</strong>{{ concert.singerName || '未知' }}</p>
        <p class="info-row"><strong>场馆：</strong>{{ concert.venue }}</p>
        <p class="info-row"><strong>时间：</strong>{{ formatDateTime(concert.showTime) }}</p>
        <p class="info-row" v-if="concert.saleStartTime">
          <strong>开售：</strong>{{ formatDateTime(concert.saleStartTime) }}
        </p>
        <p class="info-row" v-if="concert.saleEndTime">
          <strong>售票截止:</strong>{{formatDateTime(concert.saleEndTime)
          }}
        </p>
        <p class="intro" v-if="concert.introduction">{{ concert.introduction }}</p>
      </div>

      <!-- 右侧：票档选择 + 购票入口 -->
      <div class="right-panel">
        <h2>选择票档</h2>

        <!-- 遍历票档列表 -->
        <div v-for="tier in tiers" :key="tier.id" class="tier-card">
          <div class="tier-info">
            <span class="tier-name">{{ tier.tierName }}</span>
            <span class="tier-price">&yen;{{ tier.price }}</span>
          </div>
          <div class="tier-stock" :class="{ low: tier.remainingStock <= 10 }">
            剩余 {{ tier.remainingStock }} 张
          </div>

          <!-- 未到开售时间 → 倒计时按钮（不可点击） -->
          <el-button v-if="isBeforeSale" type="warning" disabled class="tier-btn">
            距开售 {{ countdown }}
          </el-button>
          <!-- 有库存且售票中 → 可购买 -->
          <el-button
              v-else-if="tier.remainingStock > 0 && concert.status === 2"
              type="primary"
              class="tier-btn"
              @click="showBuyDialog(tier)"
          >
            立即购买
          </el-button>
          <!-- 无库存或非售票状态 → 置灰 -->
          <el-button v-else disabled class="tier-btn">已售罄</el-button>
        </div>

        <el-empty v-if="tiers.length === 0" description="暂无票档" />
      </div>
    </div>

    <!-- 购票弹窗：填写身份证、手机号 + 选择支付方式 -->
    <el-dialog title="确认购票" v-model="buyDialogVisible" width="460px">
      <div class="order-summary">
        <p><strong>{{ concert?.title }}</strong></p>
        <p>{{ selectedTier?.tierName }} — &yen;{{ selectedTier?.price }}</p>
      </div>
      <el-form :model="buyForm" label-width="80px" style="margin-top: 16px">
        <el-form-item label="身份证号">
          <el-input v-model="buyForm.idCard" placeholder="请输入身份证号" maxlength="18" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="buyForm.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
      </el-form>

      <!-- 支付方式选择 -->
      <div class="pay-section">
        <p class="pay-title">支付方式</p>
        <el-radio-group v-model="payMethod" class="pay-group">
          <el-radio label="yinbi" border class="pay-option">
            <span class="pay-label">音符支付</span>
            <span class="pay-desc">余额：{{ yinbi }} 音符</span>
            <span v-if="selectedTier && yinbi < selectedTier.price" class="pay-warn">（余额不足）</span>
          </el-radio>
          <el-radio label="qrcode" border class="pay-option">
            <span class="pay-label">扫码支付</span>
          </el-radio>
        </el-radio-group>

        <!-- 扫码支付时展示二维码占位 -->
        <div v-if="payMethod === 'qrcode'" class="qr-pay-box">
          <!-- TODO: 替换 src 为实际收款二维码图片 -->
          <el-image class="pay-qr-img" src="/img/pay-qr-placeholder.png" fit="contain">
            <template #error>
              <div class="pay-qr-placeholder">
                <span>收款二维码</span>
                <span class="qr-sub">（请替换为实际二维码）</span>
              </div>
            </template>
          </el-image>
          <p>请使用支付 App 扫码付款</p>
        </div>
      </div>

      <template #footer>
        <el-button @click="buyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="buying" @click="doBuy">
          {{ payMethod === 'yinbi' ? '音符支付' : '确认购买' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 购票结果弹窗 -->
    <el-dialog title="购票结果" v-model="resultVisible" width="420px"
               :close-on-click-modal="false">
      <div v-if="result.success" class="result-success">
        <p style="font-size: 18px; color: #67c23a">购票成功</p>
        <p>订单号：{{ result.orderNo }}</p>
        <p>金额：&yen;{{ result.price }}</p>
        <el-button type="primary" @click="goOrder">查看订单 &amp; 二维码</el-button>
      </div>
      <div v-else class="result-fail">
        <p style="font-size: 18px; color: #f56c6c">购票失败</p>
        <p>{{ result.message }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, reactive, onMounted, onUnmounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { HttpManager } from '@/api/index';

const attachImageUrl = HttpManager.attachImageUrl;
import { ElMessage } from 'element-plus';

export default defineComponent({
  name: 'TicketDetail',
  setup() {
    const route = useRoute();
    const router = useRouter();
    const store = useStore();

    const loading = ref(false);
    const concert = ref<any>(null);
    const tiers = ref<any[]>([]);

    // 购票相关状态
    const buyDialogVisible = ref(false);
    const selectedTier = ref<any>(null);
    const buyForm = reactive({ idCard: '', phone: '' });
    const payMethod = ref('yinbi'); // yinbi | qrcode
    const buying = ref(false);
    const yinbi = computed(() => store.getters.yinbi);

    // 结果
    const resultVisible = ref(false);
    const result = reactive({ success: false, orderNo: '', price: 0, message: '' });

    // 开售倒计时
    const countdown = ref('');
    let timer: ReturnType<typeof setInterval> | null = null;

    const defaultCover =
        'https://cube.elemecdn.com/6/94/4d3ea53c084bad6931a56d5158a48jpeg.jpeg';

    onMounted(() => fetchDetail());
    onUnmounted(() => {
      if (timer) clearInterval(timer);
    });

    /** 获取演唱会详情（含票档 + Redis 实时库存） */
    function fetchDetail() {
      loading.value = true;
      HttpManager.getConcertDetail(route.params.id as string)
          .then((res: any) => {
            concert.value = res.data?.concert;
            tiers.value = res.data?.tiers || [];
            startCountdown();
          })
          .finally(() => (loading.value = false));
    }

    /** 是否未到开售时间 */
    const isBeforeSale = computed(() => {
      if (!concert.value?.saleStartTime) return false;
      return new Date(concert.value.saleStartTime).getTime() > Date.now();
    });

    /** 启动开售倒计时（每秒刷新） */
    function startCountdown() {
      if (!isBeforeSale.value || timer) return;
      timer = setInterval(() => {
        const now = Date.now();
        const target = new Date(concert.value.saleStartTime).getTime();
        const diff = target - now;
        if (diff <= 0) {
          countdown.value = '';
          if (timer) clearInterval(timer);
          return;
        }
        const h = Math.floor(diff / 3600000);
        const m = Math.floor((diff % 3600000) / 60000);
        const s = Math.floor((diff % 60000) / 1000);
        countdown.value = `${h}时${String(m).padStart(2, '0')}分${String(s).padStart(2, '0')}秒`;
      }, 1000);
    }

    function showBuyDialog(tier: any) {
      selectedTier.value = tier;
      buyForm.idCard = '';
      buyForm.phone = '';
      payMethod.value = 'yinbi';
      buyDialogVisible.value = true;
    }

    /** 提交购票请求 */
    function doBuy() {
      if (!buyForm.idCard || !buyForm.phone) {
        ElMessage.warning('请填写身份证号和手机号');
        return;
      }
      const userId = store.getters.userId;
      if (!userId) {
        ElMessage.warning('请先登录');
        return;
      }
      if (payMethod.value === 'yinbi' && yinbi.value < (selectedTier.value?.price || 0)) {
        ElMessage.warning('音符余额不足，请充值或切换支付方式');
        return;
      }

      buying.value = true;
      HttpManager.buyTicket({
        concertId: concert.value.id,
        tierId: selectedTier.value.id,
        userId,
        idCard: buyForm.idCard,
        phone: buyForm.phone,
        payMethod: payMethod.value,
      })
          .then((res: any) => {
            buyDialogVisible.value = false;
            if (res.success) {
              result.success = true;
              result.orderNo = res.data.orderNo;
              result.price = res.data.price;
            } else {
              result.success = false;
              result.message = res.message;
            }
            resultVisible.value = true;
          })
          .finally(() => (buying.value = false));
    }

    function goOrder() {
      resultVisible.value = false;
      router.push(`/ticket/order/${result.orderNo}`);
    }

    function formatDateTime(dateStr: string) {
      if (!dateStr) return '';
      return new Date(dateStr).toLocaleString('zh-CN');
    }

    return {
      loading, concert, tiers, defaultCover, attachImageUrl,
      isBeforeSale, countdown,
      yinbi, payMethod,
      buyDialogVisible, selectedTier, buyForm, buying,
      showBuyDialog, doBuy,
      resultVisible, result, goOrder,
      formatDateTime,
    };
  },
});
</script>

<style scoped>
.ticket-detail {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}
.back-btn {
  cursor: pointer;
  color: #409eff;
  margin-bottom: 16px;
  font-size: 14px;
}
.detail-layout {
  display: flex;
  gap: 30px;
}
.left-panel {
  flex: 1;
}
.left-panel h1 {
  font-size: 24px;
  margin: 16px 0 12px;
}
.cover-img {
  width: 400px;
  height: 250px;
  border-radius: 8px;
}
.img-placeholder {
  width: 400px;
  height: 250px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eee;
  color: #999;
  border-radius: 8px;
}
.info-row {
  margin-bottom: 6px;
  color: #555;
}
.intro {
  margin-top: 16px;
  color: #888;
  line-height: 1.6;
}
.right-panel {
  width: 360px;
}
.right-panel h2 {
  font-size: 18px;
  margin-bottom: 16px;
}
.tier-card {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #eee;
  border-radius: 8px;
  margin-bottom: 10px;
  transition: border-color 0.2s;
}
.tier-card:hover {
  border-color: #409eff;
}
.tier-info {
  flex: 1;
}
.tier-name {
  font-size: 15px;
  font-weight: 500;
  display: block;
}
.tier-price {
  font-size: 20px;
  font-weight: bold;
  color: #f56c6c;
}
.tier-stock {
  font-size: 12px;
  color: #999;
  min-width: 80px;
  text-align: center;
}
.tier-stock.low {
  color: #f56c6c;            /* 库存 <= 10 时高亮红色提醒 */
}
.tier-btn {
  min-width: 100px;
}
.order-summary {
  background: #f8f8f8;
  padding: 12px;
  border-radius: 6px;
}
.order-summary p {
  margin-bottom: 4px;
}
.result-success,
.result-fail {
  text-align: center;
}
.result-success p,
.result-fail p {
  margin-bottom: 8px;
}

/* 支付方式 */
.pay-section {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #eee;
}
.pay-title {
  font-weight: 500;
  margin-bottom: 10px;
}
.pay-group {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pay-option {
  width: 100%;
  margin-right: 0 !important;
  padding: 10px 14px;
  height: auto;
}
.pay-label {
  font-weight: 600;
  margin-right: 10px;
}
.pay-desc {
  color: #999;
  font-size: 13px;
}
.pay-warn {
  color: #f56c6c;
  font-size: 13px;
}
.qr-pay-box {
  text-align: center;
  margin-top: 12px;
  p {
    color: #999;
    font-size: 13px;
    margin-top: 6px;
  }
}
.pay-qr-img {
  width: 160px;
  height: 160px;
  border: 1px solid #eee;
  border-radius: 8px;
}
.pay-qr-placeholder {
  width: 160px;
  height: 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  color: #999;
  border-radius: 8px;
  .qr-sub {
    font-size: 11px;
    color: #bbb;
    margin-top: 6px;
  }
}
</style>