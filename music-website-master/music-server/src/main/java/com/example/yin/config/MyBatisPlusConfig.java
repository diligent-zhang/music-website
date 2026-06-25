package com.example.yin.config;


import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // DbType.MYSQL 告诉插件生成 MySQL 方言的 LIMIT 语句
        // 如果单个页超过 500 条，插件会限制为 500，防止恶意请求打爆 DB
        PaginationInnerInterceptor pageInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        pageInterceptor.setMaxLimit(500L);  //这里不确定是不是500数据一页
        interceptor.addInnerInterceptor(pageInterceptor);
        return interceptor;

    }


}
