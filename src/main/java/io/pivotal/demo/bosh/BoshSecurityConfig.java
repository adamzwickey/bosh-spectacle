package io.pivotal.demo.bosh;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails;
import org.springframework.security.oauth2.client.token.AccessTokenProvider;
import org.springframework.security.oauth2.client.token.AccessTokenRequest;
import org.springframework.security.oauth2.client.token.DefaultAccessTokenRequest;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by azwickey on 11/22/16.
 */
@Configuration
@EnableOAuth2Client
public class BoshSecurityConfig {

    @Autowired(required = false)
    ClientHttpRequestFactory clientHttpRequestFactory;

    @Value("${bosh.oauth.accessTokenUri}")
    private String _accessTokenUri;

    @Value("${bosh.oauth.clientID}")
    private String _clientID;

    @Value("${bosh.oauth.clientSecret}")
    private String _clientSecret;

    @Bean
    public OAuth2ProtectedResourceDetails bosh() {
        ClientCredentialsResourceDetails details = new ClientCredentialsResourceDetails();
        details.setId("bosh");
        details.setClientId(_clientID);
        details.setClientSecret(_clientSecret);
        details.setAccessTokenUri(_accessTokenUri);
        details.setGrantType("client_credentials");
        details.setScope(Arrays.asList("bosh.admin"));
        return details;
    }

    @Bean
    public OAuth2RestTemplate restTemplate() {
        AccessTokenRequest atr = new DefaultAccessTokenRequest();
        OAuth2RestTemplate template =  new OAuth2RestTemplate(bosh(), new DefaultOAuth2ClientContext(atr));
        template.setRequestFactory(clientHttpRequestFactory());
        template.setAccessTokenProvider(clientAccessTokenProvider());
        template.getInterceptors().add(new ContentTypeClientHttpRequestInterceptor());
        handleTextHtmlResponses(template);
        return template;
    }

    @Bean
    public AccessTokenProvider clientAccessTokenProvider() {
        ClientCredentialsAccessTokenProvider accessTokenProvider = new ClientCredentialsAccessTokenProvider();
        accessTokenProvider.setRequestFactory(clientHttpRequestFactory());
        return accessTokenProvider;
    }

    private ClientHttpRequestFactory clientHttpRequestFactory() {
        if (clientHttpRequestFactory == null) {

            SSLContext sslContext = null;
            try {
                sslContext = SSLContexts.custom()
                        .loadTrustMaterial(null, new TrustSelfSignedStrategy()).useProtocol("TLS").build();
            } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
                throw new RuntimeException("Unable to configure ClientHttpRequestFactory", e);
            }

            SSLConnectionSocketFactory connectionFactory = new SSLConnectionSocketFactory(sslContext,
                    new AllowAllHostnameVerifier());

            HttpClient httpClient = HttpClientBuilder.create()
                    .disableRedirectHandling()
                    .setSSLSocketFactory(connectionFactory)
                    .build();
            clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        }
        return clientHttpRequestFactory;
    }

    private void handleTextHtmlResponses(RestTemplate restTemplate) {
        List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
        messageConverters.add(new StringHttpMessageConverter());
        MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter();
        messageConverter.setSupportedMediaTypes(Arrays.asList(new MediaType("application", "json",
                AbstractJackson2HttpMessageConverter.DEFAULT_CHARSET),
                new MediaType("application", "*+json", AbstractJackson2HttpMessageConverter.DEFAULT_CHARSET),
                new MediaType("text", "html", AbstractJackson2HttpMessageConverter.DEFAULT_CHARSET)));
        messageConverters.add(messageConverter);
        restTemplate.setMessageConverters(messageConverters);
    }

    private static class ContentTypeClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            ClientHttpResponse response = execution.execute(request, body);
            // some BOSH resources return text/plain and this modifies this response
            // so we can use Jackson
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().remove("Transfer-Encoding");
            return response;
        }

    }

}
