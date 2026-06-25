/* eslint-disable */
declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<{}, {}, any>;
  export default component;
}

declare module "vue/types/vue" {
  import VueRouter, { Route } from "vue-router";

  interface Vue {
    $router: VueRouter;
    $route: Route;
  }
}


// src/shims-vue-router.d.ts
import type { Router, RouteLocationNormalized } from 'vue-router';

// 扩展 Vue 组件实例的全局类型 ComponentPublicInstance
declare module '@vue/runtime-core' {
  interface ComponentPublicInstance {
    // 声明 $router 为 vue-router 的 Router 类型
    $router: Router;
    // 声明 $route 为 vue-router 的 RouteLocationNormalized 类型
    $route: RouteLocationNormalized;
  }
}

// 必须导出空对象，确保该文件被识别为模块
export {};
