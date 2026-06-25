<template>
  <div class="ticket-page">
    <!-- 顶部操作栏 -->
    <div class="page-header">
      <h2>演唱会管理</h2>
      <el-button type="primary" @click="showAddDialog">发布演唱会</el-button>
    </div>

    <!-- 演唱会列表 -->
    <el-table :data="concerts" border stripe v-loading="loading">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" width="200" />
      <el-table-column prop="singerName" label="歌手" width="120" />
      <el-table-column prop="venue" label="场馆" width="150" />
      <el-table-column prop="showTime" label="演出时间" width="160" />
      <el-table-column prop="saleStartTime" label="开售时间" width="160" />
      <el-table-column prop="saleEndTime" label="售票截止" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
          <el-button size="small" @click="showOrders(row)">订单</el-button>
          <el-button size="small" type="warning" @click="showStats(row)">统计</el-button>
          <!-- 状态切换按钮：下架 / 上架 -->
          <el-button size="small" type="danger" v-if="row.status !== 0"
                     @click="toggleStatus(row, 0)">下架</el-button>
          <el-button size="small" type="success" v-if="row.status === 0"
                     @click="toggleStatus(row, 1)">上架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
        style="margin-top: 20px"
        background layout="prev, pager, next, total"
        :total="total" :page-size="pageSize"
        v-model:current-page="currentPage"
        @current-change="fetchList" />

    <!-- 添加/编辑演唱会对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="650px"
               @close="resetForm">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="歌手">
          <el-input v-model="form.singerName" />
        </el-form-item>
        <el-form-item label="场馆">
          <el-input v-model="form.venue" />
        </el-form-item>
        <el-form-item label="封面图片">
          <div style="display:flex; align-items:center; gap:12px;">
            <el-upload
                :action="HttpManager.uploadConcertCoverOnly"
                :before-upload="beforeImgUpload"
                :show-file-list="false"
                :on-success="onCoverSuccess"
                accept="image/*"
                style="flex-shrink:0;">
              <el-button type="primary" size="small">选择封面</el-button>
            </el-upload>
            <img v-if="form.coverPic"
                 :src="attachPicUrl(form.coverPic)"
                 style="width:120px; height:68px; object-fit:cover; border-radius:4px; border:1px solid #ddd;" />
            <span v-else style="color:#999; font-size:12px;">未选择封面</span>
          </div>
        </el-form-item>
        <el-form-item label="演出时间">
          <el-date-picker v-model="form.showTime" type="datetime"
                          format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="开售时间">
          <el-date-picker v-model="form.saleStartTime" type="datetime"
                          format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="售票截止">
          <el-date-picker
              v-model="form.saleEndTime"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
          />
        </el-form-item>1
        <el-form-item label="介绍">
          <el-input v-model="form.introduction" type="textarea" :rows="3" />
        </el-form-item>

        <!-- 票档设置：动态增删 -->
        <el-form-item label="票档设置">
          <div v-for="(tier, idx) in form.tiers" :key="idx"
               style="display:flex; gap:8px; margin-bottom:6px;">
            <el-input v-model="tier.tierName" placeholder="名称（如VIP区）" style="width:100px" />
            <el-input-number v-model="tier.price" :min="0" :precision="2"
                             placeholder="价格" style="width:120px" />
            <el-input-number v-model="tier.totalStock" :min="1"
                             placeholder="库存" style="width:100px" />
            <el-button type="danger" :icon="Delete" circle size="small"
                       @click="form.tiers.splice(idx, 1)" />
          </div>
          <el-button type="primary" size="small" @click="addTier">+ 添加票档</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- 订单查看对话框 -->
    <el-dialog title="订单列表" v-model="orderDialogVisible" width="950px">
      <el-table :data="orders" border stripe v-loading="orderLoading" max-height="500">
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column prop="tierName" label="票档" width="80" />
        <el-table-column prop="price" label="价格" width="80" />
        <el-table-column prop="idCard" label="身份证" width="170" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="支付" width="70">
          <template #default="{ row }">
            <el-tag :type="row.payStatus === 1 ? 'success' : 'info'">
              {{ row.payStatus === 1 ? '已支付' : '未支付' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="核验" width="70">
          <template #default="{ row }">
            <el-tag :type="row.verifyStatus === 1 ? 'success' : 'warning'">
              {{ row.verifyStatus === 1 ? '已核验' : '未核验' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 售票统计对话框 -->
    <el-dialog title="售票统计" v-model="statsDialogVisible" width="500px">
      <el-table :data="stats" border stripe v-loading="statsLoading">
        <el-table-column prop="tierName" label="票档" />
        <el-table-column prop="totalStock" label="总票数" />
        <el-table-column prop="sold" label="已售" />
        <el-table-column prop="remaining" label="剩余" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Delete } from '@element-plus/icons-vue';
import { HttpManager } from '@/api/index';
import mixin from '@/mixins/mixin';

const { beforeImgUpload } = mixin();

// ---- 列表 ----
const loading = ref(false);
const concerts = ref<any[]>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);

// ---- 添加/编辑 ----
const submitting = ref(false);
const dialogVisible = ref(false);
const dialogTitle = ref('');
const form = reactive<any>({ tiers: [] });
const editId = ref<number | null>(null);

// ---- 订单 ----
const orderDialogVisible = ref(false);
const orderLoading = ref(false);
const orders = ref<any[]>([]);

// ---- 统计 ----
const statsDialogVisible = ref(false);
const statsLoading = ref(false);
const stats = ref<any[]>([]);

/** 封面上传成功回调 */
function onCoverSuccess(response: any) {
  const url = response?.data || response;
  form.coverPic = url;
  ElMessage.success('封面上传成功');
}

/** 将相对路径转完整 URL（用于图片预览） */
function attachPicUrl(path: string) {
  if (!path) return '';
  if (path.startsWith('http')) return path;
  return HttpManager.attachImageUrl(path);
}

onMounted(() => fetchList());

/** 拉取演唱会列表 */
function fetchList() {
  loading.value = true;
  HttpManager.getAdminConcertList({ page: currentPage.value, size: pageSize.value })
      .then((res: any) => {
        concerts.value = res.data?.records || [];
        total.value = res.data?.total || 0;
      })
      .finally(() => (loading.value = false));
}

/** 状态码 → 文本 */
function statusText(status: number) {
  const map: Record<number, string> = {
    0: '下架', 1: '预告', 2: '售票中', 3: '售罄', 4: '结束',
  };
  return map[status] || '未知';
}

/** 状态码 → Element Tag 颜色类型 */
function statusTag(status: number): 'info' | 'warning' | 'success' | 'danger' {
  const map: Record<number, string> = {
    0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'info',
  };
  return (map[status] || 'info') as any;
}

/** 打开发布对话框 */
function showAddDialog() {
  dialogTitle.value = '发布演唱会';
  editId.value = null;
  Object.assign(form, {
    title: '', singerName: '', venue: '', coverPic: '',
    showTime: '', saleStartTime: '', introduction: '', tiers: [],
  });
  dialogVisible.value = true;
}

/** 打开编辑对话框 */
function showEditDialog(row: any) {
  dialogTitle.value = '编辑演唱会';
  editId.value = row.id;
  // 浅拷贝行数据；票档需要重新拉取，这里简化处理（编辑不处理票档变更）
  Object.assign(form, { ...row, tiers: [] });
  dialogVisible.value = true;
}

/** 添加一行票档 */
function addTier() {
  form.tiers.push({ tierName: '', price: 0, totalStock: 1 });
}

/** 关闭对话框时清空票档临时数据 */
function resetForm() {
  form.tiers = [];
}

/** 提交表单：创建 / 更新 */
function submitForm() {
  submitting.value = true;
  const api = editId.value
      ? HttpManager.updateConcert({ ...form, id: editId.value })
      : HttpManager.addConcert({ ...form });
  api
      .then((res: any) => {
        ElMessage.success(editId.value ? '修改成功' : '发布成功');
        dialogVisible.value = false;
        fetchList();
      })
      .catch((err: any) => {
        const msg = err?.data?.message || err?.message || '请求失败';
        ElMessage.error('操作失败: ' + msg);
      })
      .finally(() => (submitting.value = false));
}

/** 切换上下架状态（弹窗确认） */
function toggleStatus(row: any, status: number) {
  const action = status === 0 ? '下架' : '上架';
  ElMessageBox.confirm(`确定${action}「${row.title}」？`)
      .then(() => HttpManager.updateConcertStatus({ concertId: row.id, status }))
      .then(() => {
        ElMessage.success('状态已更新');
        fetchList();
      });
}

/** 查看某演唱会的订单列表 */
function showOrders(row: any) {
  orderDialogVisible.value = true;
  orderLoading.value = true;
  HttpManager.getTicketOrders({ concertId: row.id, page: 1, size: 100 })
      .then((res: any) => (orders.value = res.data?.records || []))
      .finally(() => (orderLoading.value = false));
}

/** 查看售票统计 */
function showStats(row: any) {
  statsDialogVisible.value = true;
  statsLoading.value = true;
  HttpManager.getTicketStats(row.id)
      .then((res: any) => (stats.value = res.data || []))
      .finally(() => (statsLoading.value = false));
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
}
</style>