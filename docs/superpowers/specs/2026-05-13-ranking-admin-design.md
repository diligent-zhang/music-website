# 管理员后台排行榜功能设计

## 概述

在 music-manage（Vue 3 + TypeScript + Element Plus 管理后台）中新增排行榜管理功能，包括排行榜查看、播放记录管理、操作管理三大能力。

## 架构

```
music-manage (Vue) ── HTTP ──► music-server (Spring Boot)
                                  │
                               ┌──┴──┐
                               │ Redis │ ← ZSet 排序 + 缓存
                               └──┬──┘
                                  │
                               ┌──┴──┐
                               │ MySQL │ ← play_log + rank_snapshot 表
                               └──────┘
```

## 方案：单页面 Tab 式

侧边栏新增一个"排行榜管理"菜单项，进入 RankPage.vue，页面内三个 Tab 切换。

## 前端改动

### 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/views/RankPage.vue` | 新建 | 排行榜管理页面 |
| `src/api/index.ts` | 修改 | 新增排行榜相关 API |
| `src/router/index.ts` | 修改 | 新增 `/Rank` 路由 |
| `src/enums/router-name.ts` | 修改 | 新增 `Rank` 枚举值 |
| `src/components/layouts/YinAside.vue` | 修改 | 侧边栏新增菜单项 |

### 新增 API 调用（api/index.ts）

```ts
// 排行榜
getRankList: (type) => get(`rank/list?type=${type}&limit=50`)
getRankDetail: (songId) => get(`rank/detail/${songId}`)
// 播放记录
getPlayLogs: (params) => get(`admin/playLogs`, params)
deletePlayLog: (id) => deletes(`admin/playLog/${id}`)
// 操作管理
updatePlayCount: (data) => post(`admin/rank/playCount`, data)
resetRank: (type) => post(`admin/rank/reset`, { type })
exportRank: (type) => get(`admin/rank/export?type=${type}`)
```

### 路由

```ts
{
  path: '/Rank',
  component: () => import('@/views/RankPage.vue'),
  meta: { title: 'Rank' }
}
```

在 Home 的 children 数组中添加。

### 侧边栏

在 YinAside.vue 的 el-menu 中添加：

```html
<el-menu-item index="rank">
  <el-icon><DataAnalysis /></el-icon>
  <span>排行榜管理</span>
</el-menu-item>
```

### RankPage.vue 组件结构

页面使用 el-tabs 分为三个 Tab：

**Tab 1 — 排行榜**
- 日榜/周榜/月榜切换按钮组
- el-table 展示：排名、歌曲名、歌手、播放次数
- 点击行弹出 el-dialog 展示该歌曲在三个榜单中的排名详情（调用 `/rank/detail/{songId}`）

**Tab 2 — 播放记录**
- 搜索栏：歌曲名输入框 + 用户ID输入框 + 搜索按钮
- el-table：播放时间、歌曲名、用户ID、操作（删除）
- 删除按钮使用 el-popconfirm 二次确认
- el-pagination 分页组件

**Tab 3 — 操作管理**
- 手动调整播放次数：搜索选择歌曲 → 选择时段 → 输入新数值 → 确认
- 重置榜单：选择时段 → 二次确认弹窗 → 执行重置
- 数据导出：选择时段 → 导出 CSV 文件

## 后端改动

### 新增 API（AdminController.java）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/admin/playLogs` | 分页查询播放记录，支持 songName/userId 筛选 |
| DELETE | `/admin/playLog/{id}` | 删除单条播放记录 |
| PUT | `/admin/rank/playCount` | 修改某歌曲指定时段播放次数（更新 Redis ZSet score） |
| POST | `/admin/rank/reset` | 手动重置指定时段榜单 |
| GET | `/admin/rank/export` | 导出指定时段榜单为 CSV |

### 复用现有接口

- `GET /rank/list?type=day&limit=50` — 排行榜列表
- `GET /rank/detail/{songId}` — 单曲排名详情

### 无需新建表

play_log、rank_snapshot 表已在之前的后端设计中存在。

## 技术栈

- 前端：Vue 3 + TypeScript + Element Plus + Vue Router + Axios
- 后端：Spring Boot + MyBatis-Plus + Redis + MySQL
