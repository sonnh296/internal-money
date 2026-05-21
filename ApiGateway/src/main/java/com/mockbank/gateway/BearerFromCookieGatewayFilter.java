package com.mockbank.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Đọc JWT từ HttpOnly cookie (do AuthUser phát) và gắn Authorization cho downstream.
 */
@Component
public class BearerFromCookieGatewayFilter implements GlobalFilter, Ordered {

  private static final String PORTAL_HEADER = "X-Portal";
  private static final String COOKIE_USER_ACCESS = "bp_user_access";
  private static final String COOKIE_ADMIN_ACCESS = "bp_admin_access";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (StringUtils.hasText(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
      return chain.filter(exchange);
    }
    String portal = exchange.getRequest().getHeaders().getFirst(PORTAL_HEADER);
    String cookieName = "admin".equalsIgnoreCase(portal) ? COOKIE_ADMIN_ACCESS : COOKIE_USER_ACCESS;
    HttpCookie cookie = exchange.getRequest().getCookies().getFirst(cookieName);
    if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
      return chain.filter(exchange);
    }
    ServerWebExchange mutated = exchange.mutate()
        .request(builder -> builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue()))
        .build();
    return chain.filter(mutated);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }
}
