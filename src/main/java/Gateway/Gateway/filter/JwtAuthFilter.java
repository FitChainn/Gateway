package Gateway.Gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@Order(2)
public class JwtAuthFilter implements GlobalFilter {

    private final WebClient webClient;

    // Rutas que NO necesitan token
    private static final List<String> RUTAS_PUBLICAS = List.of(
            "/v1/auth/login",
            "/v1/auth/register",
            "/v1/auth/validar"
    );

    public JwtAuthFilter(@Value("${auth.service.url}") String authUrl) {
        this.webClient = WebClient.builder().baseUrl(authUrl).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Si es ruta pública, deja pasar sin validar
        if (RUTAS_PUBLICAS.stream().anyMatch(path::startsWith)) {
            log.info("Ruta pública, sin validación: {}", path);
            return chain.filter(exchange);
        }

        // Buscar el header Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Petición sin token: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Validar el token con el Auth service
        return webClient.get()
                .uri("/v1/auth/validar")
                .header("Authorization", authHeader)
                .retrieve()
                .toBodilessEntity()
                .flatMap(response -> {
                    log.info("Token válido para: {}", path);
                    return chain.filter(exchange);
                })
                .onErrorResume(e -> {
                    log.warn("Token inválido para {}: {}", path, e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }
}