<template>
  <div class="ai-message assistant">
    <div class="avatar">
      <el-icon :size="20"><Service /></el-icon>
    </div>
    <div class="bubble typing">
      <span class="dot"></span>
      <span class="dot"></span>
      <span class="dot"></span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { Service } from '@element-plus/icons-vue';
</script>

<style lang="scss" scoped>
@import '@/assets/css/var.scss';

.ai-message {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: $theme-color;
  color: $color-white;
}

.bubble {
  padding: 10px 14px;
  border-radius: 12px;
  background: $color-light-grey;
  border-bottom-left-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #999;
  animation: bounce 1.4s infinite ease-in-out both;

  &:nth-child(1) { animation-delay: -0.32s; }
  &:nth-child(2) { animation-delay: -0.16s; }
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}
</style>
