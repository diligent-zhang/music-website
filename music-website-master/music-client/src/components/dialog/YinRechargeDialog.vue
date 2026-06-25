<template>
  <el-dialog title="音符充值" v-model="visible" width="400px" :close-on-click-modal="false" @close="handleClose">
    <div class="recharge-body">
      <!-- 当前余额 -->
      <div class="balance-info">
        当前余额：<span class="balance-num">{{ balance }} 音符</span>
      </div>

      <!-- 收款码占位（你后续替换为实际收款码图片） -->
      <div class="qr-section">
        <!-- TODO: 替换 src 为实际收款码图片路径 -->
        <el-image class="qr-img" :src="qrCodeUrl" fit="contain">
          <template #error>
            <div class="qr-placeholder">
              <span>收款码</span>
              <span class="qr-hint">（请替换为实际收款码）</span>
            </div>
          </template>
        </el-image>
        <p class="qr-label">请扫码支付（模拟）</p>
      </div>

      <!-- 金额输入 -->
      <div class="amount-section">
        <p class="amount-label">充值金额（1元 = 1音符）</p>
        <div class="quick-amounts">
          <el-button v-for="n in [50, 100, 200, 500]" :key="n"
                     :type="amount === n ? 'primary' : ''" size="small"
                     @click="amount = n">{{ n }}元</el-button>
        </div>
        <el-input-number v-model="amount" :min="1" :max="99999" :step="1" class="amount-input" placeholder="输入充值金额" />
      </div>

      <!-- 交易流水号 -->
      <div class="txn-section">
        <p class="txn-label">支付交易流水号</p>
        <el-input v-model="transactionNo" placeholder="请输入微信/支付宝的交易流水号" maxlength="32" clearable />
        <p class="txn-hint">请扫码支付后，填写支付平台返回的交易流水号</p>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="doRecharge">确认充值</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts">
import { defineComponent, ref, computed, watch } from 'vue';
import { useStore } from 'vuex';
import { HttpManager } from '@/api';
import { ElMessage } from 'element-plus';

export default defineComponent({
  name: 'YinRechargeDialog',
  props: {
    modelValue: { type: Boolean, default: false },
  },
  emits: ['update:modelValue', 'recharged'],
  setup(props, { emit }) {
    const store = useStore();
    const submitting = ref(false);
    const amount = ref(100);
    const transactionNo = ref('');

    const userId = computed(() => store.getters.userId);
    const balance = computed(() => store.getters.yinbi);

    const visible = computed({
      get: () => props.modelValue,
      set: (v) => emit('update:modelValue', v),
    });

    const qrCodeUrl = ref('/img/pay-qr-placeholder.jpg');

    async function doRecharge() {
      if (!amount.value || amount.value <= 0) {
        ElMessage.warning('请输入充值金额');
        return;
      }
      if (!transactionNo.value.trim()) {
        ElMessage.warning('请输入交易流水号');
        return;
      }
      submitting.value = true;
      try {
        const res = (await HttpManager.rechargeYinbi({
          userId: userId.value,
          amount: amount.value,
          transactionNo: transactionNo.value.trim(),
        })) as ResponseBody;
        if (res.success) {
          store.commit('setYinbi', res.data?.balance ?? balance.value + amount.value);
          ElMessage.success(`充值成功！当前余额 ${store.getters.yinbi} 音符`);
          emit('recharged');
          visible.value = false;
        } else {
          ElMessage.error(res.message || '充值失败');
        }
      } catch {
        ElMessage.error('充值请求失败');
      } finally {
        submitting.value = false;
      }
    }

    function handleClose() {
      amount.value = 100;
      transactionNo.value = '';
    }

    return { visible, balance, amount, transactionNo, submitting, qrCodeUrl, doRecharge, handleClose };
  },
});
</script>

<style scoped>
.recharge-body {
  text-align: center;
}
.balance-info {
  font-size: 16px;
  margin-bottom: 20px;
}
.balance-num {
  color: #e6a23c;
  font-weight: bold;
  font-size: 20px;
}
.qr-section {
  margin-bottom: 20px;
}
.qr-img {
  width: 200px;
  height: 200px;
  border: 1px solid #eee;
  border-radius: 8px;
}
.qr-placeholder {
  width: 200px;
  height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  color: #999;
  border-radius: 8px;
}
.qr-hint {
  font-size: 12px;
  color: #bbb;
  margin-top: 8px;
}
.qr-label {
  color: #999;
  font-size: 13px;
  margin-top: 8px;
}
.amount-section {
  text-align: left;
}
.amount-label {
  margin-bottom: 10px;
  font-weight: 500;
}
.quick-amounts {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.amount-input {
  width: 100%;
}
.txn-section {
  text-align: left;
  margin-top: 16px;
}
.txn-label {
  margin-bottom: 8px;
  font-weight: 500;
}
.txn-hint {
  color: #bbb;
  font-size: 12px;
  margin-top: 6px;
}
</style>
