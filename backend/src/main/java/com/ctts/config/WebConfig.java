package com.ctts.config;

import com.ctts.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer{
    @Autowired
    private JwtInterceptor jwtInterceptor;

    //放行的范围
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/users/login",
                        "/error",
                        // 接口文档（Knife4j）。这四条必须走白名单，而不是在 checkRole 里给 GET 放行——
                        // checkRole 只解决"授权"，而认证（验签拿 userId）在它之前执行，
                        // 浏览器打开页面时手里没有 token，永远过不了认证那一关。
                        "/doc.html",       // 文档页面入口
                        "/webjars/**",     // 页面的 js/css：knife4j-openapi3-ui 以 webjar 形式打包
                        "/v3/api-docs/**", // OpenAPI 描述本身，doc.html 靠它渲染出接口清单
                        "/swagger-ui/**"   // springdoc 自带的 swagger-ui 资源
                );
    }
}
