package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Which URL paths go to which service. The Authorization header is forwarded unchanged. */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder,
                               @Value("${gateway.routes.order-url}") String orderUrl,
                               @Value("${gateway.routes.inventory-url}") String inventoryUrl,
                               @Value("${gateway.routes.payment-url}") String paymentUrl) {
        return builder.routes()
                .route("orders", r -> r.path("/api/orders", "/api/orders/**").uri(orderUrl))
                .route("inventory", r -> r.path("/api/products", "/api/products/**", "/api/inventory/**").uri(inventoryUrl))
                .route("payments", r -> r.path("/api/payments/**").uri(paymentUrl))
                .build();
    }
}
