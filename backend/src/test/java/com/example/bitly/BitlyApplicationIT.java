package com.example.bitly;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/** End to end against real DynamoDB (LocalStack) and Redis containers. Run with {@code mvn verify}. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.base-url=http://sho.rt", "app.counter.batch-size=5"})
@ActiveProfiles("test")
class BitlyApplicationIT {

    @ServiceConnection(name = "redis")
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @ServiceConnection
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.14")).withServices("dynamodb");

    static {
        Startables.deepStart(redisContainer, localstack).join();
    }

    @LocalServerPort int port;
    @Autowired StringRedisTemplate redis;

    RestClient http;

    @BeforeEach
    void setUp() {
        // JDK client does not follow redirects by default, so we can assert on the 302 itself.
        http = RestClient.builder().baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {})
                .build();
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> shorten(Map<String, Object> body) {
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) http.post().uri("/urls")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(Map.class);
    }

    private ResponseEntity<Void> follow(String code) {
        return http.get().uri("/" + code).retrieve().toBodilessEntity();
    }

    @Test
    void shortenThenRedirect_andSecondRedirectIsServedFromCache() {
        ResponseEntity<Map<String, Object>> created = shorten(Map.of("long_url", "https://example.com/it"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String code = (String) created.getBody().get("short_code");
        assertThat(code).matches("[0-9a-zA-Z]{7}");
        assertThat(created.getBody().get("short_url")).isEqualTo("http://sho.rt/" + code);

        ResponseEntity<Void> redirect = follow(code);
        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation()).hasToString("https://example.com/it");

        assertThat(redis.opsForValue().get("bitly:url:" + code)).isEqualTo("https://example.com/it");
        assertThat(follow(code).getStatusCode()).isEqualTo(HttpStatus.FOUND);
    }

    @Test
    void customAlias_isUniqueAcrossRequests() {
        String alias = "it-" + System.nanoTime();
        assertThat(shorten(Map.of("long_url", "https://a.example", "custom_alias", alias)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(shorten(Map.of("long_url", "https://b.example", "custom_alias", alias)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(follow(alias).getHeaders().getLocation()).hasToString("https://a.example");
    }

    @Test
    void expiredLink_is410() throws Exception {
        String alias = "exp-" + System.nanoTime();
        shorten(Map.of("long_url", "https://example.com", "custom_alias", alias,
                "expiration_date", Instant.now().plusSeconds(2).toString()));
        assertThat(follow(alias).getStatusCode()).isEqualTo(HttpStatus.FOUND); // cached with a ~2s TTL

        Thread.sleep(3_000);

        assertThat(follow(alias).getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void generatedCodesAreUniqueAcrossManyCounterBatches() {
        Set<Object> codes = new HashSet<>();
        for (int i = 0; i < 40; i++) { // batch size 5 -> 8 Redis round trips
            codes.add(shorten(Map.of("long_url", "https://example.com/" + i)).getBody().get("short_code"));
        }
        assertThat(codes).hasSize(40);
    }

    @Test
    void unknownCode_is404() {
        assertThat(follow("doesNotExist").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
