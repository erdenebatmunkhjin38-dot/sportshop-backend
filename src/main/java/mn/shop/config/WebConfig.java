package mn.shop.config;

import mn.shop.security.AdminInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;
    // Админы оруулсан зургууд хадгалагдах хавтас (next-app/uploads). Хоосон бол үйлчлэхгүй.
    private final String uploadDir;
    // Backend-ийг шууд дуудах эрхтэй frontend хаягууд (таслалаар). Жишээ: https://sportshop.mn
    private final String[] corsOrigins;

    public WebConfig(AdminInterceptor adminInterceptor, @Value("${app.upload-dir:}") String uploadDir,
                     @Value("${app.cors.origins:http://localhost:3000,http://localhost:5173}") String corsOrigins) {
        this.adminInterceptor = adminInterceptor;
        this.uploadDir = uploadDir.trim();
        this.corsOrigins = java.util.Arrays.stream(corsOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    // Mobile апп зургийг backend-ээс шууд татна: GET /uploads/<нэр>
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (uploadDir.isEmpty()) return;
        String location = Path.of(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /api/auth/** (нэвтрэх, бүртгүүлэх) ба QPay callback-аас бусад бүх API шалгагдана
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/qpay/callback");
    }

    // CORS-ыг зөвхөн энд тохируулна (controller дээр @CrossOrigin бүү нэм)
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type");
    }
}
