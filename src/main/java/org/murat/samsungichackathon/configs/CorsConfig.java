package org.murat.samsungichackathon.configs;



import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS konfigurasyonu.
 * Spring Security KULLANILMIYOR, bu yuzden CORS izinleri WebMvcConfigurer
 * uzerinden veriliyor. Bu sinif olmadan farkli origin'den (frontend'in
 * calistigi adres) gelen istekler tarayici tarafindan engellenir.
 *
 * NOT: Hackathon/demo amacli TUM origin'lere izin veriyoruz. Production'da
 * allowedOriginPatterns'i gercek frontend adresiyle sinirlamak gerekir.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin",
                        "X-Requested-With", "Cache-Control")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600L);
    }
}
