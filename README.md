# RestClient & Spring Retry in Spring Boot 3 - Retryable Exceptions, Error Handlers, JDK HttpClient, Apache HttpClient

> This repo is used in this Youtube video: https://youtu.be/pezBoiaBvus

> **Noted:** We won't cover the basics of RestClient and Spring Retry here, for those, you can refer to previous videos:

> [RestClient in Spring Boot 3 - Builder, Timeout, Interceptor, RequestFactory](https://youtu.be/iNWVlF8o0A4)

> [Spring Retry & RestClient (Part 1): Getting Started with Spring Retry - A Stateless Declarative way](https://youtu.be/pGN6tp3Hij8)

## 1. Test calling RestClient

Create our playground `hello.HelloServiceClient`:
```java
@Service
public class HelloServiceClient {

    private final RestClient restClient;

    public String getHello() {
        return this.restClient.get()
                .uri("/hello")
                .retrieve()
                .body(String.class);
    }
}
```

Initialize `RestClient` from `Builder`
```java
public HelloServiceClient(RestClient.Builder builder) {
    ClientHttpRequestFactorySettings requestFactorySettings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofSeconds(1L))
            .withReadTimeout(Duration.ofSeconds(5L));

    JdkClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(JdkClientHttpRequestFactory.class, requestFactorySettings);

    this.restClient = builder
            .baseUrl("http://localhost:8080")
            .requestFactory(requestFactory)
            .build();
}
```

Update `port`
```properties
server.port=8081
```

Test calling it
```java
private static final Logger log = LoggerFactory.getLogger(RestclientRetryDemoApplication.class);

@Bean
ApplicationRunner applicationRunner(HelloServiceClient helloServiceClient) {
    return args -> {
        String hello = helloServiceClient.getHello();
        log.info("Result: %s".formatted(hello));
    };
}
```

## 2. Enable Retry
Add retry dependencies in `build.gradle`
```groovy
implementation 'org.springframework.retry:spring-retry'
runtimeOnly 'org.springframework.boot:spring-boot-starter-aop'
```
Add `@EnableRetry` in any `@Configuration` files
```java
@SpringBootApplication
@EnableRetry
public class RestclientRetryDemoApplication {
}
```
Logging(optional)
```properties
logging.level.org.springframework.retry=debug
```

## 3. Apply Retry
```java
@Retryable(
        maxAttempts=5,
        backoff=@Backoff(delay=1000, multiplier=1.2)
)
public String getHello() {
    return this.restClient.get()
            .uri("/hello")
            .retrieve()
            .body(String.class);
}
```

## 4. HTTP Retryable Exceptions
> **Noted:** By default, `RestClient` throws a subclass of `RestClientException` when retrieving a response with a `4xx` or `5xx` status code
> (https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#_error_handling).

**Retryable Exceptions** for REST Calls
```text
408 Request Timeout         ->  HttpClientErrorException.class
425 Too Early               ->  HttpClientErrorException.class
429 Too Many Requests       ->  HttpClientErrorException.TooManyRequests.class
502 Bad Gateway             ->  HttpServerErrorException.BadGateway.class
503 Service Unavailable     ->  HttpServerErrorException.ServiceUnavailable.class
504 Gateway Timeout         ->  HttpServerErrorException.GatewayTimeout.class
Cannot connect              ->  ResourceAccessException.class
```

We don't have corresponding classes for `408 Request Timeout`, `425 Too Early`. They will be mapped to `HttpClientErrorException` class.
But if we use this `HttpClientErrorException` class for `retryFor`, all of its subclasses like `HttpClientErrorException.TooManyRequests`(retryable) here and
`HttpClientErrorException.Unauthorized`(non-retryable) will trigger a retry.
```java
@Retryable(
    maxAttempts = 5,
    backoff= @Backoff(delay=1000, multiplier=1.2),
    retryFor = {
        HttpServerErrorException.class, // Don't use, because all non-retryable and retryable subclasses will be retried
        HttpServerErrorException.BadGateway.class,
        HttpServerErrorException.GatewayTimeout.class,
        HttpServerErrorException.ServiceUnavailable.class,
        HttpClientErrorException.TooManyRequests.class,
        ResourceAccessException.class
    }
)
```

## 5. Custom HTTP Exceptions
> **For cases like:** `408 Request Timeout` and `425 Too Early`, we can override the exception that thrown by `RestClient`
> https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#_error_handling

Create custom exceptions for `408 Request Timeout` and `425 Too Early`:
```java
public final class RequestTimeoutHttpClientErrorException extends HttpClientErrorException {

    public RequestTimeoutHttpClientErrorException() {
        super(HttpStatus.REQUEST_TIMEOUT);
    }
}
```

```java
public final class TooEarlyHttpClientErrorException extends HttpClientErrorException {

    public TooEarlyHttpClientErrorException() {
        super(HttpStatus.TOO_EARLY);
    }
}
```

## 5. RestClient Error Handlers
Add `ErrorHandler` on `RestClient.onStatus` method, for example when receiving a `408 Request Timeout`, throws `RequestTimeoutHttpClientErrorException` instead
```java
public String getHello() {
    return this.restClient.get()
            .uri("/hello")
            .retrieve()
            .onStatus(
                    statusCode -> HttpStatus.valueOf(statusCode.value()) == HttpStatus.REQUEST_TIMEOUT,
                    (request, response) -> {
                        throw new RequestTimeoutHttpClientErrorException();
                    }
            )
            .body(String.class);
}
```

Update `retryFor` with our `custom exceptions`:
```java
@Retryable(
        maxAttempts=5,
        backoff=@Backoff(delay=1000, multiplier=1.2),
        retryFor = {
                RequestTimeoutHttpClientErrorException.class,
                TooEarlyHttpClientErrorException.class,
                HttpServerErrorException.BadGateway.class,
                HttpServerErrorException.GatewayTimeout.class,
                HttpServerErrorException.ServiceUnavailable.class,
                HttpClientErrorException.TooManyRequests.class,
                ResourceAccessException.class
        }
)
```

If we don't want to add `ErrorHandler`s at each `RestClient` call. We can create `interceptor`s instead
```java
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
```

## 6. Custom Retry Annotation
Create `configuration.annotation.RestClientRetryable`
```java
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        maxAttempts=5,
        backoff=@Backoff(delay=1000, multiplier=1.2),
        retryFor = {
                RequestTimeoutHttpClientErrorException.class,
                TooEarlyHttpClientErrorException.class,
                HttpServerErrorException.BadGateway.class,
                HttpServerErrorException.GatewayTimeout.class,
                HttpServerErrorException.ServiceUnavailable.class,
                HttpClientErrorException.TooManyRequests.class,
                ResourceAccessException.class
        }
)
public @interface RestClientRetryable {
}
```
Apply the new annotation `@RestClientRetryable`
```java
@RestClientRetryable
public String getHello() {
    return this.restClient.get()
            .uri("/hello")
            .retrieve()
            .body(String.class);
}
```

## 7. Be aware of `Apache HttpClient`
`Apache HttpClient` has internal retry on itself, by default, it will retry 1 time (2 attempts) for `429 Too Many Requests` and `503 Service Unavailable`

```groovy
implementation 'org.apache.httpcomponents.client5:httpclient5'
```

```java
HttpComponentsClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(HttpComponentsClientHttpRequestFactory.class, requestFactorySettings);
```

Disable retry strategy:
```java
PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create().useSystemProperties().build();
CloseableHttpClient httpClient =
        HttpClientBuilder.create()
                .useSystemProperties()
                .setConnectionManager(connectionManager)
                .disableAutomaticRetries()  // <-- disable internal retry in Apache HttpClient
                .build();
HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
```

## References
- `1.` **Spring Retry** in [**Spring Retry Github Repository**](https://github.com/spring-projects/spring-retry).
- `2.` **RestClient** in [**Spring Framework 6**](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#_creating_a_restclient).
- `3.` **RestClient Error Handler** in [**Spring Framework 6**](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#_error_handling) .
- `4.` **RestClient** in [**Spring Boot 3**](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#io.rest-client.restclient) .
- `4.` **RetryStrategy** in [**Apache HttpClient 5**](https://hc.apache.org/httpcomponents-client-5.2.x/current/httpclient5/apidocs/org/apache/hc/client5/http/impl/DefaultHttpRequestRetryStrategy.html) .
