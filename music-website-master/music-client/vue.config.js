// vue.config.js - 修复安全沙箱 + CSP 跨域连接问题的配置
module.exports = {
  transpileDependencies: [],
  chainWebpack: config => {
    // 注入环境变量 + Vue feature flags
    config.plugin('define').tap(definitions => {
      Object.assign(definitions[0]['process.env'], {
        NODE_HOST: '"http://localhost:8888"',
      });
      // 修复 Vue feature flag 警告
      Object.assign(definitions[0], {
        __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: JSON.stringify(false),
        __VUE_OPTIONS_API__: JSON.stringify(true),
      });
      return definitions;
    });

    // 新增：让babel-loader处理vue-router源码，转译ES6+新语法（解决?.解析失败）
    config.module
        .rule('js')
        .include.add(/node_modules\/vue-router/) // 仅处理vue-router包，不影响其他依赖
        .end()
        .use('babel-loader')
        .loader('babel-loader')
        .options({
          presets: ['@vue/cli-plugin-babel/preset'] // 复用项目原有babel预设，无需额外配置
        });

    // 解决安全沙箱问题
    config.module
        .rule('js')
        .include.add(/node_modules\/lockdown/)
        .end()
        .use('babel-loader')
        .loader('babel-loader')
        .options({
          presets: ['@vue/cli-plugin-babel/preset'],
          compact: false
        });
  },

  // 修改：更新CSP头，允许连接192.168.1.13:8080 + 显式配置connect-src
  devServer: {
    // 可选：配置代理（推荐），避免跨域和CSP双重问题
    proxy: {
      '/sockjs-node': {
        target: 'http://192.168.1.13:8080',
        ws: true, // 支持WebSocket（sockjs依赖）
        changeOrigin: true // 跨域请求时修改Origin头
      },
      '/banner': { // 解决/banner/getAllBanner接口500/跨域问题
        target: 'http://192.168.1.13:8080',
        changeOrigin: true
      }
    },
    // CSP策略：允许所有必需的资源源
    headers: {
      'Content-Security-Policy':
          "default-src 'self' 'unsafe-inline' 'unsafe-eval' http://localhost:* http://192.168.1.13:* http://10.55.146.153:* http://localhost:9005 https://cube.elemecdn.com;" +
          "connect-src 'self' http://localhost:* http://192.168.1.13:* http://10.55.146.153:* ws://localhost:* ws://192.168.1.13:* ws://10.55.146.153:*;" +
          "img-src 'self' data: blob: http://localhost:* http://192.168.1.13:* http://10.55.146.153:* http://localhost:9005 https://cube.elemecdn.com;" +
          "media-src 'self' blob: data: http://localhost:* http://192.168.1.13:* http://10.55.146.153:* http://localhost:9005 https://cube.elemecdn.com;"
    }
  }
}