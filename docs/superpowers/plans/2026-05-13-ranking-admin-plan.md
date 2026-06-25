# 管理员后台排行榜功能 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 music-manage 管理后台新增排行榜管理页面，支持排行榜查看、播放记录管理、操作管理（调整播放次数/重置榜单/导出CSV）。

**Architecture:** 前端新增 RankPage.vue（3个Tab），后端在 AdminController 新增5个管理接口，RankService 新增3个方法，PlayLogMapper 新增2条查询SQL。

**Tech Stack:** Vue 3 + TypeScript + Element Plus / Spring Boot + MyBatis-Plus + Redis + MySQL

---

### Task 1: 后端 — PlayLogMapper 新增分页查询

**Files:**
- Modify: `music-server/src/main/java/com/example/yin/mapper/PlayLogMapper.java`
- Modify: `music-server/src/main/resources/mapper/PlayLogMapper.xml`

- [ ] **Step 1: PlayLogMapper 接口新增方法**

```java
// PlayLogMapper.java — 在现有方法下方添加
List<Map<String, Object>> selectPlayLogs(
    @Param("offset") int offset,
    @Param("size") int size,
    @Param("songName") String songName,
    @Param("userId") Integer userId
);

long countPlayLogs(
    @Param("songName") String songName,
    @Param("userId") Integer userId
);
```

- [ ] **Step 2: PlayLogMapper.xml 新增 SQL**

```xml
<!-- PlayLogMapper.xml — 在 </mapper> 标签之前添加 -->
<select id="selectPlayLogs" resultType="java.util.HashMap">
  SELECT
    pl.id,
    pl.song_id     AS songId,
    pl.user_id     AS userId,
    pl.play_time   AS playTime,
    s.name         AS songName,
    s.pic          AS songPic
  FROM play_log pl
  LEFT JOIN song s ON pl.song_id = s.id
  WHERE 1=1
  <if test="songName != null and songName != ''">
    AND s.name LIKE CONCAT('%', #{songName}, '%')
  </if>
  <if test="userId != null">
    AND pl.user_id = #{userId}
  </if>
  ORDER BY pl.play_time DESC
  LIMIT #{offset}, #{size}
</select>

<select id="countPlayLogs" resultType="long">
  SELECT COUNT(*)
  FROM play_log pl
  LEFT JOIN song s ON pl.song_id = s.id
  WHERE 1=1
  <if test="songName != null and songName != ''">
    AND s.name LIKE CONCAT('%', #{songName}, '%')
  </if>
  <if test="userId != null">
    AND pl.user_id = #{userId}
  </if>
</select>
```

- [ ] **Step 3: 验证编译**

```bash
cd music-server && ./mvnw compile -q
```

---

### Task 2: 后端 — RankService 新增管理操作方法

**Files:**
- Modify: `music-server/src/main/java/com/example/yin/service/RankService.java`
- Modify: `music-server/src/main/java/com/example/yin/service/impl/RankServiceImpl.java`

- [ ] **Step 1: RankService 接口新增方法签名**

```java
// RankService.java — 在现有方法下方添加
void updatePlayCount(Integer songId, String type, Long playCount);

void resetRank(String type);

List<Map<String, Object>> exportRank(String type);
```

- [ ] **Step 2: RankServiceImpl 实现 updatePlayCount**

```java
// RankServiceImpl.java — 在类末尾、最后一个 "}" 之前添加
@Override
public void updatePlayCount(Integer songId, String type, Long playCount) {
    String zsetKey = getZSetKey(type);
    // ZADD key score member — 覆盖式设置分数
    redisTemplate.opsForZSet().add(zsetKey, songId, playCount.doubleValue());
    // 失效对应缓存
    redisTemplate.delete(getCacheKey(type));
    // 同步更新 MySQL play_count（取三个时段最大值）
    updateSongPlayCount(songId);
}

private void updateSongPlayCount(Integer songId) {
    Double dailyScore = redisTemplate.opsForZSet()
        .score(RankRedisKey.DAILY_RANK_KEY, songId);
    Double weeklyScore = redisTemplate.opsForZSet()
        .score(RankRedisKey.WEEKLY_RANK_KEY, songId);
    Double monthlyScore = redisTemplate.opsForZSet()
        .score(RankRedisKey.MONTHLY_RANK_KEY, songId);
    long max = Math.max(
        Math.max(dailyScore != null ? dailyScore.longValue() : 0,
                 weeklyScore != null ? weeklyScore.longValue() : 0),
        monthlyScore != null ? monthlyScore.longValue() : 0
    );
    songMapper.updatePlayCountByValue(songId, (int) max);
}
```

- [ ] **Step 3: RankServiceImpl 实现 resetRank**

```java
// RankServiceImpl.java
@Override
public void resetRank(String type) {
    String zsetKey = getZSetKey(type);
    redisTemplate.delete(zsetKey);
    redisTemplate.delete(getCacheKey(type));
    // 更新重置时间戳防重
    String tsKey;
    switch (type.toLowerCase()) {
        case "week":  tsKey = RankRedisKey.WEEKLY_RESET_TS; break;
        case "month": tsKey = RankRedisKey.MONTHLY_RESET_TS; break;
        default:      tsKey = RankRedisKey.DAILY_RESET_TS; break;
    }
    redisTemplate.opsForValue().set(tsKey,
        String.valueOf(System.currentTimeMillis()));
}
```

- [ ] **Step 4: RankServiceImpl 实现 exportRank**

```java
// RankServiceImpl.java
@Override
public List<Map<String, Object>> exportRank(String type) {
    return getRankList(type, 200); // 最多导出200条
}
```

- [ ] **Step 5: SongMapper 新增 updatePlayCountByValue 方法**

```java
// SongMapper.java — 在现有方法下方添加
void updatePlayCountByValue(@Param("songId") Integer songId,
                            @Param("playCount") Integer playCount);
```

```xml
<!-- SongMapper.xml — 在 </mapper> 标签之前添加 -->
<update id="updatePlayCountByValue">
  UPDATE song SET play_count = #{playCount} WHERE id = #{songId}
</update>
```

- [ ] **Step 6: 验证编译**

```bash
cd music-server && ./mvnw compile -q
```

---

### Task 3: 后端 — AdminController 新增管理接口

**Files:**
- Modify: `music-server/src/main/java/com/example/yin/controller/AdminController.java`

- [ ] **Step 1: 重写 AdminController.java**

将整个文件替换为以下内容（保持原有 login 接口不变，新增5个接口）:

```java
package com.example.yin.controller;

import com.example.yin.common.R;
import com.example.yin.mapper.PlayLogMapper;
import com.example.yin.model.request.AdminRequest;
import com.example.yin.service.AdminService;
import com.example.yin.service.RankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class AdminController {
    @Autowired
    private AdminService adminService;

    @Autowired
    private RankService rankService;

    @Autowired
    private PlayLogMapper playLogMapper;

    // 判断是否登录成功
    @PostMapping("/admin/login/status")
    public R loginStatus(@RequestBody AdminRequest adminRequest, HttpSession session) {
        return adminService.verityPasswd(adminRequest, session);
    }

    // ==================== 排行榜管理 ====================

    // 分页查询播放记录
    @GetMapping("/admin/playLogs")
    public R getPlayLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String songName,
            @RequestParam(required = false) Integer userId) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = playLogMapper
                .selectPlayLogs(offset, size, songName, userId);
        long total = playLogMapper.countPlayLogs(songName, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return R.success(null, result);
    }

    // 删除单条播放记录
    @DeleteMapping("/admin/playLog/{id}")
    public R deletePlayLog(@PathVariable Long id) {
        playLogMapper.deleteById(id);
        return R.success("删除成功");
    }

    // 手动修改播放次数
    @PutMapping("/admin/rank/playCount")
    public R updatePlayCount(@RequestBody Map<String, Object> body) {
        Integer songId = (Integer) body.get("songId");
        String type = (String) body.get("type");
        Long playCount = ((Number) body.get("playCount")).longValue();
        rankService.updatePlayCount(songId, type, playCount);
        return R.success("修改成功");
    }

    // 手动重置榜单
    @PostMapping("/admin/rank/reset")
    public R resetRank(@RequestBody Map<String, String> body) {
        String type = body.get("type");
        rankService.resetRank(type);
        return R.success("重置成功");
    }

    // 导出榜单 CSV
    @GetMapping("/admin/rank/export")
    public R exportRank(@RequestParam(defaultValue = "day") String type) {
        List<Map<String, Object>> data = rankService.exportRank(type);
        return R.success(null, data);
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd music-server && ./mvnw compile -q
```

---

### Task 4: 前端 — 路由和枚举

**Files:**
- Modify: `music-manage/src/enums/router-name.ts`
- Modify: `music-manage/src/router/index.ts`

- [ ] **Step 1: router-name.ts 新增 Rank 枚举值**

```ts
// 在现有枚举中添加 Rank 项
export const enum RouterName {
  Home = "/home",
  Info = "/info",
  Song = "/song",
  Singer = "/singer",
  SongList = "/songList",
  ListSong = "/listSong",
  Comment = "/Comment",
  Consumer = "/consumer",
  Collect = "/collect",
  Rank = "/rank",      // ← 新增
  Error = "/404",
  SignIn = "/",
  SignOut = "0",
}
```

- [ ] **Step 2: router/index.ts 新增 /Rank 路由**

```ts
// 在 children 数组中、Collect 路由之后添加
{
  path: '/rank',
  component: () => import('@/views/RankPage.vue'),
  meta: { title: 'Rank' }
}
```

- [ ] **Step 3: 验证前端编译**

```bash
cd music-manage && npm run build 2>&1 | tail -5
```

---

### Task 5: 前端 — 侧边栏菜单

**Files:**
- Modify: `music-manage/src/components/layouts/YinAside.vue`

- [ ] **Step 1: 添加菜单项和图标引入**

```html
<!-- 在 <script> 中 import 添加 DataAnalysis 图标 -->
<script lang="ts" setup>
import { ref } from "vue";
import { PieChart, Mic, Document, User, DataAnalysis } from "@element-plus/icons-vue";
// ... 其余代码不变
</script>
```

```html
<!-- 在歌单管理 el-menu-item 之后添加 -->
<el-menu-item index="rank">
  <el-icon><DataAnalysis /></el-icon>
  <span>排行榜管理</span>
</el-menu-item>
```

完整的 YinAside.vue 模板部分修改后：

```html
<template>
  <div class="sidebar">
    <el-menu
      class="sidebar-el-menu"
      background-color="#ffffff"
      active-text-color="#30a4fc"
      default-active="2"
      router
      :collapse="collapse"
    >
      <el-menu-item index="info">
        <el-icon><pie-chart /></el-icon>
        <span>系统首页</span>
      </el-menu-item>
      <el-menu-item index="consumer">
        <el-icon><User /></el-icon>
        <span>用户管理</span>
      </el-menu-item>
      <el-menu-item index="singer">
        <el-icon><mic /></el-icon>
        <span>歌手管理</span>
      </el-menu-item>
      <el-menu-item index="songList">
        <el-icon><Document /></el-icon>
        <span>歌单管理</span>
      </el-menu-item>
      <el-menu-item index="rank">
        <el-icon><DataAnalysis /></el-icon>
        <span>排行榜管理</span>
      </el-menu-item>
    </el-menu>
  </div>
</template>
```

- [ ] **Step 2: 验证编译**

```bash
cd music-manage && npx vue-tsc --noEmit 2>&1 | head -10
```

---

### Task 6: 前端 — API 新增

**Files:**
- Modify: `music-manage/src/api/index.ts`

- [ ] **Step 1: api/index.ts 添加排行榜相关 API**

在 `HttpManager` 对象末尾（`deleteListSong` 之后）添加：

```ts
// =======================> 排行榜 API
// 获取排行榜列表
getRankList: (type) => get(`rank/list?type=${type}&limit=50`),
// 获取歌曲排名详情
getRankDetail: (songId) => get(`rank/detail/${songId}`),
// 分页查询播放记录
getPlayLogs: (params) => get(`admin/playLogs`, { params }),
// 删除播放记录
deletePlayLog: (id) => deletes(`admin/playLog/${id}`),
// 手动修改播放次数
updateRankPlayCount: (data) => put(`admin/rank/playCount`, data),
// 手动重置榜单
resetRank: (type) => post(`admin/rank/reset`, { type }),
// 导出榜单
exportRank: (type) => get(`admin/rank/export?type=${type}`),
```

同时修改顶部的 import 行，将 `put` 加入：

```ts
import { deletes, get, getBaseURL, post, put } from './request'
```

- [ ] **Step 2: 验证编译**

```bash
cd music-manage && npx vue-tsc --noEmit 2>&1 | head -10
```

---

### Task 7: 前端 — RankPage.vue 页面

**Files:**
- Create: `music-manage/src/views/RankPage.vue`

- [ ] **Step 1: 创建 RankPage.vue — 脚本部分**

```vue
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
        <!-- 排名详情弹窗 -->
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
        <!-- 手动调整播放次数 -->
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

        <!-- 重置榜单 -->
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

        <!-- 数据导出 -->
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
```

- [ ] **Step 2: 创建 RankPage.vue — script 部分**

```ts
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
```

- [ ] **Step 3: 创建 RankPage.vue — style 部分**

```css
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
```

- [ ] **Step 4: 验证编译**

```bash
cd music-manage && npx vue-tsc --noEmit 2>&1 | head -15
```

---

### Task 8: 后端 — 确认 SongMapper 方法

**Files:**
- Check: `music-server/src/main/java/com/example/yin/mapper/SongMapper.java`

- [ ] **Step 1: 检查 SongMapper 是否有 updatePlayCount 方法**

```bash
grep -n "updatePlayCount" music-server/src/main/java/com/example/yin/mapper/SongMapper.java
```

如果不存在，添加：

```java
// SongMapper.java
void updatePlayCount(@Param("songId") Integer songId);
void updatePlayCountByValue(@Param("songId") Integer songId,
                            @Param("playCount") Integer playCount);
```

并在 `SongMapper.xml` 的 `resources/mapper/SongMapper.xml` 中添加对应 SQL（若不存在）:

```xml
<update id="updatePlayCountByValue">
  UPDATE song SET play_count = #{playCount} WHERE id = #{songId}
</update>
```

- [ ] **Step 2: 最终验证后端编译**

```bash
cd music-server && ./mvnw compile -q
```
