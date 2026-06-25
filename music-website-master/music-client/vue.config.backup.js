// vue.config.js - Vue CLI 4.x 完整适配配置
module.exports = {
  transpileDependencies: [],
  chainWebpack: config => {
    // 原有：注入环境变量配置
    config.plugin('define').tap(definitions => {
      Object.assign(definitions[0]['process.env'], {
        NODE_HOST: '"http://localhost:8888"',
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
  }
}