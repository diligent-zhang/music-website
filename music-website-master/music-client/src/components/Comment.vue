<template>
  <div class="comment">
    <h2 class="comment-title">
      <span>评论</span>
      <span class="comment-desc">共 {{ commentList.length }} 条评论</span>
    </h2>
    <el-input
        class="comment-input"
        type="textarea"
        placeholder="期待您的精彩评论..."
        :rows="2"
        v-model="textarea"
    ></el-input>
    <el-button class="sub-btn" type="primary" @click="submitComment()">发表评论</el-button>
  </div>

  <ul class="popular">
    <li v-for="(item, index) in commentList" :key="index">
      <!-- 修复：el-image标签完整闭合 + 属性格式规范 -->
      <el-image
          class="popular-img"
          fit="contain"
          :src="attachImageUrl(item.avator)"
      ></el-image>
      <div class="popular-msg">
        <ul>
          <li class="name">{{ item.username }}</li>
          <li class="time">{{ formatDate(item.createTime) }}</li>
          <li class="content">{{ item.content }}</li>
        </ul>
      </div>

      <div ref="up" class="comment-ctr" @click="setSupport(item.id, item.up, userId)">
        <div><yin-icon :icon="iconList.Support"></yin-icon> {{ item.up }}</div>
        <el-icon
            v-if="item.userId === userId"
            @click.stop="deleteComment(item.id, index)"
            class="delete-icon"
        >
          <Delete />
        </el-icon>
      </div>
    </li>
  </ul>
</template>

<script lang="ts" setup>
import {
  defineProps,
  getCurrentInstance,
  ref,
  toRefs,
  computed,
  watch,
  reactive,
  onMounted
} from "vue";
import { useStore } from "vuex";
import { Delete } from "@element-plus/icons-vue";

import YinIcon from "@/components/layouts/YinIcon.vue";
import mixin from "@/mixins/mixin";
import { HttpManager } from "@/api";
import { Icon } from "@/enums";
import { formatDate } from "@/utils";

// 类型定义
interface ResponseBody {
  success: boolean;
  message: string;
  type: "success" | "warning" | "error";
  data: any;
}

const { proxy } = getCurrentInstance();
const store = useStore();
const { checkStatus } = mixin();

// Props定义（修复类型语法）
const props = defineProps({
  playId: {
    type: [Number, String],
    required: true
  },
  type: {
    type: Number,
    required: true,
    validator: (val: number) => [0, 1].includes(val)
  }
});

const { playId, type } = toRefs(props);
const textarea = ref("");
const commentList = ref([]);
const iconList = reactive({
  Support: Icon.Support
});

const userId = computed(() => store.getters.userId);
const songId = computed(() => store.getters.songId);

// 监听歌曲ID变化
watch(songId, (newVal) => {
  if (newVal) getComment(newVal);
});

// 初始化评论列表
onMounted(() => {
  if (playId.value) getComment(playId.value);
});

// 获取评论列表
async function getComment(id: number | string) {
  if (!id) return;
  try {
    const result = (await HttpManager.getAllComment(type.value, id)) as ResponseBody;
    if (result.success && result.data) {
      commentList.value = result.data;
      for (const item of commentList.value) {
        if (item.userId) {
          const userResult = (await HttpManager.getUserOfId(item.userId)) as ResponseBody;
          if (userResult.success && userResult.data?.length) {
            item.avator = userResult.data[0].avator;
            item.username = userResult.data[0].username;
          }
        }
      }
    }
  } catch (error) {
    console.error("[获取所有评论失败]===>", error);
    proxy && (proxy as any).$message({
      message: "获取评论失败，请稍后重试",
      type: "error"
    });
  }
}

// 提交评论
async function submitComment() {
  if (!checkStatus()) return;
  if (!textarea.value.trim()) {
    proxy && (proxy as any).$message({
      message: "评论内容不能为空",
      type: "warning"
    });
    return;
  }

  try {
    const params = {
      userId: userId.value,
      content: textarea.value.trim(),
      songId: type.value === 0 ? `${playId.value}` : null,
      songListId: type.value === 1 ? `${playId.value}` : null,
      nowType: type.value
    };

    const result = (await HttpManager.setComment(params)) as ResponseBody;
    proxy && (proxy as any).$message({
      message: result.message,
      type: result.type
    });

    if (result.success) {
      textarea.value = "";
      await getComment(playId.value);
    }
  } catch (error) {
    console.error("[提交评论失败]===>", error);
    proxy && (proxy as any).$message({
      message: "提交评论失败，请稍后重试",
      type: "error"
    });
  }
}

// 删除评论
async function deleteComment(id: number | string, index: number) {
  if (!id) return;
  try {
    const result = (await HttpManager.deleteComment(id)) as ResponseBody;
    proxy && (proxy as any).$message({
      message: result.message,
      type: result.type
    });

    if (result.success) {
      commentList.value.splice(index, 1);
    }
  } catch (error) {
    console.error("[删除评论失败]===>", error);
    proxy && (proxy as any).$message({
      message: "删除评论失败，请稍后重试",
      type: "error"
    });
  }
}

// 点赞/取消点赞
async function setSupport(id: number | string, up: number, userId: number | string) {
  if (!checkStatus()) return;
  if (!id || !userId) return;

  try {
    const checkResult = (await HttpManager.testAlreadySupport({
      commentId: id,
      userId
    })) as ResponseBody;

    proxy && (proxy as any).$message({
      message: checkResult.message,
      type: checkResult.type
    });

    let operatorResult: ResponseBody;
    let updateResult: ResponseBody;

    if (checkResult.data) {
      operatorResult = (await HttpManager.deleteUserSupport({
        commentId: id,
        userId
      })) as ResponseBody;
      updateResult = (await HttpManager.setSupport({ id, up: up - 1 })) as ResponseBody;
    } else {
      operatorResult = (await HttpManager.insertUserSupport({
        commentId: id,
        userId
      })) as ResponseBody;
      updateResult = (await HttpManager.setSupport({ id, up: up + 1 })) as ResponseBody;
    }

    if (operatorResult.success && updateResult.success) {
      await getComment(playId.value);
    }
  } catch (error) {
    console.error("[点赞操作失败]===>", error);
    proxy && (proxy as any).$message({
      message: "点赞操作失败，请稍后重试",
      type: "error"
    });
  }
}

// 暴露变量
const attachImageUrl = HttpManager.attachImageUrl;
</script>

<style lang="scss" scoped>
@import "@/assets/css/var.scss";
@import "@/assets/css/global.scss";

.comment {
  position: relative;
  margin-bottom: 60px;

  .comment-title {
    height: 50px;
    line-height: 50px;
    font-weight: 600;

    .comment-desc {
      font-size: 14px;
      font-weight: 400;
      color: $color-grey;
      margin-left: 10px;
    }
  }

  .comment-input {
    display: flex;
    margin-bottom: 20px;
  }

  .sub-btn {
    position: absolute;
    right: 0;
    bottom: 0;
  }
}

.popular {
  width: 100%;
  list-style: none;
  padding: 0;
  margin: 0;

  > li {
    border-bottom: solid 1px rgba(0, 0, 0, 0.1);
    padding: 15px 0;
    display: flex;
    align-items: flex-start;

    .popular-img {
      width: 50px;
      height: 50px;
      border-radius: 50%;
    }

    .popular-msg {
      padding: 0 20px;
      flex: 1;

      ul {
        list-style: none;
        padding: 0;
        margin: 0;

        li {
          width: 100%;
          line-height: 1.5;
        }

        .time {
          font-size: 0.6rem;
          color: rgba(0, 0, 0, 0.5);
        }

        .name {
          color: rgba(0, 0, 0, 0.7);
          font-weight: 500;
        }

        .content {
          font-size: 1rem;
          margin-top: 5px;
        }
      }
    }

    .comment-ctr {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 80px;
      font-size: 1rem;
      cursor: pointer;
      gap: 10px;

      .delete-icon {
        color: $color-grey;
        &:hover {
          color: #f56c6c;
        }
      }

      &:hover,
      :deep(.icon):hover {
        color: $color-grey;
      }
    }
  }
}

.icon {
  @include icon(1em);
}
</style>