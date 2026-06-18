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
import java.util.Map;

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
            log.info("RUTA PÚBLICA SIN VALIDACIÓN: {}", path);
            return chain.filter(exchange);
        }

        // Buscar el header Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("PETICIÓN SIN TOKEN: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Validar el token con el Auth service y extraer el rol
        return webClient.get()
                .uri("/v1/auth/validar")
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(body -> {
                    String rol = (String) body.get("rol");
                    log.info("TOKEN VÁLIDO, ROL: {} PARA RUTA: {}", rol, path);
                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(r -> r.header("X-User-Rol", rol != null ? rol : ""))
                            .build();
                    return chain.filter(mutatedExchange);
                })
                .onErrorResume(e -> {
                    log.warn("TOKEN INVÁLIDO PARA {}: {}", path, e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }
}