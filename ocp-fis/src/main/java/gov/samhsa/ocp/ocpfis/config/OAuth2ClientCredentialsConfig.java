package gov.samhsa.ocp.ocpfis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;

@Configuration
@EnableOAuth2Client
public class OAuth2ClientCredentialsConfig {

    @Value("${security.oauth2.client.access-token-uri}")
    private String accessTokenUri;

    @Value("${security.oauth2.client.clientId}")
    private String clientId;

    @Value("${security.oauth2.client.clientSecret}")
    private String clientSecret;

    @Bean
    public ClientCredentialsResourceDetails clientCredentialsResourceDetails() {
        ClientCredentialsResourceDetails clientCredentialsResourceDetails = new ClientCredentialsResourceDetails();
        clientCredentialsResourceDetails.setAccessTokenUri(accessTokenUri);
        clientCredentialsResourceDetails.setClientId(clientId);
        clientCredentialsResourceDetails.setClientSecret(clientSecret);
        return clientCredentialsResourceDetails;
    }

//    @Bean
//    public OAuth2RestTemplate createRestTemplate(OAuth2ClientContext oAuth2ClientContext) {
//        return new OAuth2RestTemplate(clientCredentialsResourceDetails(), oAuth2ClientContext);
//    }
}
