<template>
  <div class="order-page" v-loading="loading">
    <div class="back-btn" @click="$router.push('/ticket')">&larr; 返回演唱会列表</div>

    <div class="order-card" v-if="order">
      <h2>订单详情</h2>

      <div class="order-info">
        <div class="info-item">
          <label>订单号</label><span>{{ order.orderNo }}</span>
        </div>
        <div class="info-item">
          <label>演唱会</label><span>{{ order.concertTitle || '未知' }}</span>
        </div>
        <div class="info-item">
          <label>票档</label><span>{{ order.tierName }}</span>
        </div>
        <div class="info-item">
          <label>金额</label><span class="price">&yen;{{ order.price }}</span>
        </div>
        <div class="info-item">
          <label>身份证</label><span>{{ maskIdCard(order.idCard) }}</span>
        </div>
        <div class="info-item">
          <label>手机号</label><span>{{ maskPhone(order.phone) }}</span>
        </div>
        <div class="info-item">
          <label>状态</label>
          <el-tag :type="order.verifyStatus === 1 ? 'success' : 'warning'">
            {{ order.verifyStatus === 1 ? '已入场' : '未核验' }}
          </el-tag>
        </div>
      </div>

      <!-- 入场二维码区域 -->
      <div class="qrcode-section">
        <h3>入场二维码</h3>
        <div class="qr-wrapper">
          <canvas ref="qrCanvas"></canvas>
        </div>
        <p class="qr-hint">入场时请出示此二维码供工作人员扫码核验</p>
      </div>
    </div>

    <el-empty v-if="!loading && !order" description="订单不存在" />
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { HttpManager } from '@/api/index';
import QRCode from 'qrcode';

export default defineComponent({
  name: 'TicketOrder',
  setup() {
    const route = useRoute();
    const loading = ref(false);
    const order = ref<any>(null);
    const qrCanvas = ref<HTMLCanvasElement | null>(null);

    onMounted(() => fetchOrder());

    /** 获取订单详情 */
    function fetchOrder() {
      loading.value = true;
      HttpManager.getTicketOrder(route.params.orderNo as string)
          .then((res: any) => {
            if (res.success) {
              order.value = res.data;
              // DOM 更新后再生成二维码
              nextTick(() => generateQR(res.data?.qrCodeToken));
            }
          })
          .finally(() => (loading.value = false));
    }

    /**
     * 使用 qrcode 库生成二维码到 canvas
     * 二维码内容为核验 URL：{origin}/api/verify/{qrCodeToken}
     */
    function generateQR(token: string) {
      if (!token || !qrCanvas.value) return;
      const verifyUrl = `${window.location.origin}/api/verify/${token}`;
      QRCode.toCanvas(qrCanvas.value, verifyUrl, {
        width: 220,
        margin: 2,
        color: { dark: '#000000', light: '#ffffff' },
        errorCorrectionLevel: 'M', // 中等容错，平衡可识别性
      });
    }

    function maskIdCard(idCard: string) {
      if (!idCard || idCard.length < 8) return '***';
      return idCard.slice(0, 4) + '**********' + idCard.slice(-4);
    }

    function maskPhone(phone: string) {
      if (!phone || phone.length < 7) return '***';
      return phone.slice(0, 3) + '****' + phone.slice(-4);
    }

    return { loading, order, qrCanvas, maskIdCard, maskPhone };
  },
});
</script>

<style scoped>
.order-page {
  padding: 20px;
  max-width: 700px;
  margin: 0 auto;
}
.back-btn {
  cursor: pointer;
  color: #409eff;
  margin-bottom: 16px;
  font-size: 14px;
}
.order-card {
  background: #fff;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.08);
}
.order-card h2 {
  font-size: 22px;
  margin-bottom: 24px;
}
.order-info {
  display: grid;
  gap: 12px;
}
.info-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.info-item label {
  color: #888;
}
.info-item span {
  font-weight: 500;
}
.price {
  color: #f56c6c;
  font-size: 18px;
  font-weight: bold !important;
}
.qrcode-section {
  margin-top: 30px;
  text-align: center;
}
.qrcode-section h3 {
  margin-bottom: 16px;
}
.qr-wrapper {
  display: inline-block;
  padding: 12px;
  border: 2px solid #eee;
  border-radius: 8px;
}
.qr-hint {
  margin-top: 12px;
  font-size: 13px;
  color: #999;
}
</style>