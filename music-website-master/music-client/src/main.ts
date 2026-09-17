import { createApp } from "vue";
import ElementPlus from "element-plus";
import App from "./App.vue";
import router from "./router";
import store from "./store";
import { HttpManager } from "./api";
import { getToken, clearToken } from "./api/request";
import "element-plus/dist/index.css";
import "./assets/css/index.scss";
import "./assets/icons/index.js";

import { ComponentCustomProperties } from "vue";
import { Store } from "vuex";
declare module "@vue/runtime-core" {
  interface State {
    count: number;
  }

  interface ComponentCustomProperties {
    $store: Store<State>;
  }
}

createApp(App).use(store).use(router).use(ElementPlus).mount("#app");

// 刷新后自动恢复登录态（localStorage 中的 JWT 仍有效则还原用户信息）
const savedToken = getToken();
if (savedToken && !(store as any).state.configure.token) {
  HttpManager.getUserInfo()
    .then((res: any) => {
      if (res.success && res.data) {
        (store as any).commit("setUserId", res.data.id);
        (store as any).commit("setUsername", res.data.username);
        (store as any).commit("setUserPic", res.data.avator);
        (store as any).commit("setToken", savedToken);
      } else {
        clearToken();
      }
    })
    .catch(() => clearToken());
}
