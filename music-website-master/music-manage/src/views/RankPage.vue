<template>
  <div class="rank-page">
    <h3>排行榜管理</h3>
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ==================== Tab 1: 排行榜 ==================== -->
      <el-tab-pane label="排行榜" name="rankList">
        <div class="rank-tabs">
          <el-radio-group v-model="rankType" @change="fetchRankList">
            <el-radio-button value="day">今日榜</el-radio-button>
            <el-radio-button value="week">周榜</el-radio-button>
            <el-radio-button value="month">月榜</el-radio-button>
          </el-radio-group>
        </div>
        <el-table :data="rankList" stripe v-loading="rankLoading" @row-click="showRankDetail">
          <el-table-column label="排名" width="80" align="center">
            <template #default="{ $index }">
              <span :class="{ 'top3': $index < 3 }">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="歌曲名" min-width="200" />
          <el-table-column prop="singerName" label="歌手" min-width="120" />
          <el-table-column prop="play_count" label="播放次数" width="120" align="center" />
        </el-table>
        <el-dialog v-model="detailVisible" title="排名详情" width="400px">
          <div v-if="rankDetail">
            <p>歌曲ID: {{ rankDetail.songId }}</p>
            <p>日榜排名: {{ rankDetail.dailyRank ?? '未上榜' }}</p>
            <p>周榜排名: {{ rankDetail.weeklyRank ?? '未上榜' }}</p>
            <p>月榜排名: {{ rankDetail.monthlyRank ?? '未上榜' }}</p>
            <p>总播放量: {{ rankDetail.totalPlays }}</p>
          </div>
        </el-dialog>
      </el-tab-pane>

      <!-- ==================== Tab 2: 播放记录 ==================== -->
      <el-tab-pane label="播放记录" name="playLogs">
        <div class="search-bar">
          <el-input v-model="searchSongName" placeholder="歌曲名" style="width:180px" clearable />
          <el-input v-model="searchUserId" placeholder="用户ID" style="width:140px;margin-left:10px" clearable />
          <el-button type="primary" @click="fetchPlayLogs" style="margin-left:10px">搜索</el-button>
        </div>
        <el-table :data="playLogs" stripe v-loading="logLoading" style="margin-top:10px">
          <el-table-column prop="playTime" label="播放时间" width="180" />
          <el-table-column prop="songName" label="歌曲名" min-width="180" />
          <el-table-column prop="userId" label="用户ID" width="100" align="center" />
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <el-popconfirm title="确定删除？" @confirm="deleteLog(row.id)">
                <template #reference>
                  <el-button type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="logPage"
          :page-size="logSize"
          :total="logTotal"
          layout="total, prev, pager, next"
          @current-change="fetchPlayLogs"
          style="margin-top:15px;justify-content:flex-end"
        />
      </el-tab-pane>

      <!-- ==================== Tab 3: 操作管理 ==================== -->
      <el-tab-pane label="操作管理" name="operations">
        <el-card header="手动调整播放次数">
          <el-form :inline="true">
            <el-form-item label="歌曲ID">
              <el-input-number v-model="adjustSongId" :min="1" />
            </el-form-item>
            <el-form-item label="时段">
              <el-select v-model="adjustType" style="width:120px">
                <el-option label="日榜" value="day" />
                <el-option label="周榜" value="week" />
                <el-option label="月榜" value="month" />
              </el-select>
            </el-form-item>
            <el-form-item label="播放次数">
              <el-input-number v-model="adjustCount" :min="0" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="doAdjust">确认修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-divider />

        <el-card header="重置榜单">
          <el-form :inline="true">
            <el-form-item label="选择时段">
              <el-select v-model="resetType" style="width:120px">
                <el-option label="日榜" value="day" />
                <el-option label="周榜" value="week" />
                <el-option label="月榜" value="month" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-popconfirm
                title="确定要重置该时段榜单吗？此操作不可恢复！"
                @confirm="doReset"
              >
                <template #reference>
                  <el-button type="danger">重置榜单</el-button>
                </template>
              </el-popconfirm>
            </el-form-item>
          </el-form>
        </el-card>

        <el-divider />

        <el-card header="数据导出">
          <el-form :inline="true">
            <el-form-item label="选择时段">
              <el-select v-model="exportType" style="width:120px">
                <el-option label="日榜" value="day" />
                <el-option label="周榜" value="week" />
                <el-option label="月榜" value="month" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="success" @click="doExport">导出 CSV</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { HttpManager } from '@/api'

interface ResponseBody {
  code: number
  message: string
  data: any
}

const activeTab = ref('rankList')

// ========== Tab 1: 排行榜 ==========
const rankType = ref('day')
const rankList = ref<any[]>([])
const rankLoading = ref(false)
const detailVisible = ref(false)
const rankDetail = ref<any>(null)

const fetchRankList = async () => {
  rankLoading.value = true
  try {
    const res = (await HttpManager.getRankList(rankType.value)) as ResponseBody
    if (res.code === 200) rankList.value = res.data || []
    else ElMessage.error(res.message)
  } finally {
    rankLoading.value = false
  }
}

const showRankDetail = async (row: any) => {
  try {
    const res = (await HttpManager.getRankDetail(row.id)) as ResponseBody
    if (res.code === 200) {
      rankDetail.value = res.data
      detailVisible.value = true
    }
  } catch { ElMessage.error('获取详情失败') }
}

// ========== Tab 2: 播放记录 ==========
const playLogs = ref<any[]>([])
const logLoading = ref(false)
const logPage = ref(1)
const logSize = ref(20)
const logTotal = ref(0)
const searchSongName = ref('')
const searchUserId = ref('')

const fetchPlayLogs = async () => {
  logLoading.value = true
  try {
    const res = (await HttpManager.getPlayLogs({
      page: logPage.value,
      size: logSize.value,
      songName: searchSongName.value || undefined,
      userId: searchUserId.value ? Number(searchUserId.value) : undefined,
    })) as ResponseBody
    if (res.code === 200) {
      playLogs.value = res.data.list || []
      logTotal.value = res.data.total || 0
    } else ElMessage.error(res.message)
  } finally {
    logLoading.value = false
  }
}

const deleteLog = async (id: number) => {
  try {
    const res = (await HttpManager.deletePlayLog(id)) as ResponseBody
    if (res.code === 200) {
      ElMessage.success('删除成功')
      fetchPlayLogs()
    } else ElMessage.error(res.message)
  } catch { ElMessage.error('删除失败') }
}

// ========== Tab 3: 操作管理 ==========
const adjustSongId = ref<number>(1)
const adjustType = ref('day')
const adjustCount = ref<number>(0)

const doAdjust = async () => {
  try {
    const res = (await HttpManager.updateRankPlayCount({
      songId: adjustSongId.value,
      type: adjustType.value,
      playCount: adjustCount.value,
    })) as ResponseBody
    if (res.code === 200) ElMessage.success('修改成功')
    else ElMessage.error(res.message)
  } catch { ElMessage.error('修改失败') }
}

const resetType = ref('day')

const doReset = async () => {
  try {
    const res = (await HttpManager.resetRank(resetType.value)) as ResponseBody
    if (res.code === 200) ElMessage.success('重置成功')
    else ElMessage.error(res.message)
  } catch { ElMessage.error('重置失败') }
}

const exportType = ref('day')

const doExport = async () => {
  try {
    const res = (await HttpManager.exportRank(exportType.value)) as ResponseBody
    if (res.code === 200 && res.data) {
      const data = res.data as any[]
      const header = '排名,歌曲名,歌手,播放次数\n'
      const rows = data.map((item: any, i: number) =>
        `${i + 1},"${item.name || ''}","${item.singerName || ''}",${item.play_count || 0}`
      ).join('\n')
      const blob = new Blob(['﻿' + header + rows], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `rank-${exportType.value}-${new Date().toISOString().slice(0, 10)}.csv`
      a.click()
      URL.revokeObjectURL(url)
      ElMessage.success('导出成功')
    } else ElMessage.error('导出失败')
  } catch { ElMessage.error('导出失败') }
}

// 初始加载
fetchRankList()
fetchPlayLogs()
</script>

<style scoped>
.rank-page {
  padding: 0 10px;
}
.rank-page h3 {
  margin-bottom: 16px;
}
.rank-tabs {
  margin-bottom: 16px;
}
.search-bar {
  display: flex;
  align-items: center;
}
.top3 {
  color: #e6a23c;
  font-weight: bold;
  font-size: 18px;
}
</style>
