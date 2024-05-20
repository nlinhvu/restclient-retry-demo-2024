package vn.cloud.restclientretrydemo.hello;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import vn.cloud.restclientretrydemo.configuration.annotation.RestClientRetryable;
import vn.cloud.restclientretrydemo.exception.RequestTimeoutHttpClientErrorException;
import vn.cloud.restclientretrydemo.exception.TooEarlyHttpClientErrorException;

import java.time.Duration;

@Service
public class HelloServiceClient {

    private final RestClient restClient;

    public HelloServiceClient(RestClient.Builder builder) {
//        ClientHttpRequestFactorySettings requestFactorySettings = ClientHttpRequestFactorySettings.DEFAULTS
//                .withConnectTimeout(Duration.ofSeconds(1L))
//                .withReadTimeout(Duration.ofSeconds(5L));
//
//        HttpComponentsClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(HttpComponentsClientHttpRequestFactory.class, requestFactorySettings);

        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create().useSystemProperties().build();
        CloseableHttpClient httpClient =
                HttpClientBuilder.create()
                        .useSystemProperties()
                        .setConnectionManager(connectionManager)
                        .disableAutomaticRetries()  // <-- disable internal retry in Apache HttpClient
                        .build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);


        this.restClient = builder
                .baseUrl("http://localhost:8080")
                .requestFactory(requestFactory)
                .requestInterceptor(
                        (request, body, execution) -> {
                            ClientHttpResponse response = execution.execute(request, body);
                            if (response.getStatusCode() == HttpStatus.REQUEST_TIMEOUT) throw new RequestTimeoutHttpClientErrorException();
                            if (response.getStatusCode() == HttpStatus.TOO_EARLY) throw new TooEarlyHttpClientErrorException();
                            return response;
                        })
                .build();
    }

    @RestClientRetryable
    public String getHello() {
        return this.restClient.get()
                .uri("/hello")
                .retrieve()
                .body(String.class);
    }
}
