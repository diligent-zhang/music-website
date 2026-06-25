<template>
  <div class="ai-message" :class="role">
    <div class="avatar">
      <el-icon v-if="role === 'assistant'" :size="20"><Service /></el-icon>
      <el-icon v-else :size="20"><UserFilled /></el-icon>
    </div>
    <div class="bubble" :class="role">
      <div class="content" v-html="renderedContent"></div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { Service, UserFilled } from '@element-plus/icons-vue';

const props = defineProps<{
  role: 'user' | 'assistant';
  content: string;
}>();

const renderedContent = computed(() => {
  return props.content.replace(/\n/g, '<br>');
});
</script>

<style lang="scss" scoped>
@import '@/assets/css/var.scss';

.ai-message {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;

  &.assistant {
    flex-direction: row;
  }
  &.user {
    flex-direction: row-reverse;
  }
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

  .user & {
    background: $color-blue-active;
  }
}

.bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;

  &.assistant {
    background: $color-light-grey;
    color: #333;
    border-bottom-left-radius: 4px;
  }
  &.user {
    background: $theme-color;
    color: $color-white;
    border-bottom-right-radius: 4px;
  }
}
</style>
