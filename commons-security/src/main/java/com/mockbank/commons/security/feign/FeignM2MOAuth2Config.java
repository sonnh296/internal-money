package com.mockbank.commons.security.feign;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequestEntityConverter;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Configuration
@EnableConfigurationProperties(FeignM2MProperties.class)
public class FeignM2MOAuth2Config {

    @Value("${auth.jwt.audience}")
    private String audience;

    @Bean
    public OAuth2AuthorizedClientManager m2mAuthorizedClientManager(
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
    public RequestInterceptor m2mFeignInterceptor(
            OAuth2AuthorizedClientManager m2mAuthorizedClientManager,
            FeignM2MProperties properties) {
        return template -> {
            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(properties.getClientRegistrationId())
                    .principal(properties.getPrincipal())
                    .build();
            OAuth2AuthorizedClient client = m2mAuthorizedClientManager.authorize(authorizeRequest);
            if (client == null || client.getAccessToken() == null) {
                throw new IllegalStateException(
                        "Failed to obtain M2M access token for " + properties.getClientRegistrationId());
            }
            template.header("Authorization", "Bearer " + client.getAccessToken().getTokenValue());
        };
    }
}
