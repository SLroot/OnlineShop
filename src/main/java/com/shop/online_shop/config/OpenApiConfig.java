package com.shop.online_shop.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Online Shop API")
                        .version("v1")
                        .description("""
                                API فروشگاه اینترنتی

                                برای استفاده از endpoint های محافظت‌شده:
                                ۱. با /auth/login توکن بگیرید
                                ۲. روی دکمه Authorize کلیک کنید
                                ۳. فقط خود توکن را وارد کنید (بدون کلمه Bearer)
                                """)
                        .contact(new Contact().name("Saeid Lotfi")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
