package com.ruoyi.system.shop;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
@EnableScheduling
public class ShopConfiguration implements WebMvcConfigurer {
    private final ShopPdfGuard guard;
    public ShopConfiguration(ShopPdfGuard guard){this.guard=guard;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(guard).addPathPatterns("/profile/**","/common/download","/common/download/resource").order(-100);}
}
