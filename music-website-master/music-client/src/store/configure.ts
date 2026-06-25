export default {
  state: {
    token: false, // 用户是否登录
    showAside: false, // 是否显示侧边栏
    searchWord: "", // 搜索关键词
    activeNavName: "", // 导航栏名称
    userId: null, // 用户 ID
    username: "", // 用户名
    userPic: "", // 用户头像
    yinbi: 0, // 音符余额
  },
  getters: {
    token: (state) => state.token,
    activeNavName: (state) => state.activeNavName,
    showAside: (state) => state.showAside,
    searchWord: (state) => state.searchWord,
    userId: (state) => state.userId,
    username: (state) => state.username,
    userPic: (state) => state.userPic,
    yinbi: (state) => state.yinbi,
  },
  mutations: {
    setToken: (state, token) => {
      state.token = token;
    },
    setActiveNavName: (state, activeNavName) => {
      state.activeNavName = activeNavName;
    },
    setShowAside: (state, showAside) => {
      state.showAside = showAside;
    },
    setSearchWord: (state, searchWord) => {
      state.searchWord = searchWord;
    },
    setUserId: (state, userId) => {
      state.userId = userId;
    },
    setUsername: (state, username) => {
      state.username = username;
    },
    setUserPic: (state, userPic) => {
      state.userPic = userPic;
    },
    setYinbi: (state, yinbi) => {
      state.yinbi = yinbi;
    },
  },
};
