package vn.cloud.restclientretrydemo.configuration.annotation;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import vn.cloud.restclientretrydemo.exception.RequestTimeoutHttpClientErrorException;
import vn.cloud.restclientretrydemo.exception.TooEarlyHttpClientErrorException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
