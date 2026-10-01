package com.expence.expenceApi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The browser calls this API directly (see {@code server.port}) — there is no Next.js proxy layer — so
 * every UI request is cross-origin and needs CORS.
 *
 * <p>The origins come from {@code app.cors.allowed-origins} rather than being hardcoded
 * so a later deployment profile can supply its own without touching this class.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Value("${app.cors.allowed-origins}")
	private String[] allowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
	}

}
