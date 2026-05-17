package com.digitalbank.customerservice.security;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequestEntityConverter;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class FeignM2MOAuth2Config {

    @Value("${auth0.audience}")
    private String audience;

    @Bean("customerAccountM2MAuthorizedClientManager")
    public OAuth2AuthorizedClientManager customerAccountM2MAuthorizedClientManager(
            ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientService clientService) {
        var baseConverter = new OAuth2ClientCredentialsGrantRequestEntityConverter();
        var tokenClient = new DefaultClientCredentialsTokenResponseClient();
        tokenClient.setRequestEntityConverter(grantRequest -> {
            RequestEntity<?> entity = baseConverter.convert(grantRequest);
            @SuppressWarnings("unchecked")
            MultiValueMap<String, String> form =
                    new LinkedMultiValueMap<>((MultiValueMap<String, String>) entity.getBody());
            form.add("audience", audience);
            return new RequestEntity<>(form, entity.getHeaders(), entity.getMethod(), entity.getUrl());
        });
        var provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
        provider.setAccessTokenResponseClient(tokenClient);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    public RequestInterceptor customerM2mFeignInterceptor(
            @Qualifier("customerAccountM2MAuthorizedClientManager") OAuth2AuthorizedClientManager manager) {
        return template -> {
            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId("account-m2m")
                    .principal("customer-service")
                    .build();
            OAuth2AuthorizedClient client = manager.authorize(authorizeRequest);
            if (client == null || client.getAccessToken() == null) {
                throw new IllegalStateException("Failed to obtain M2M access token for account-m2m");
            }
            template.header("Authorization", "Bearer " + client.getAccessToken().getTokenValue());
        };
    }
}
