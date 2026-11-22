package com.weiver.auth.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.auth.dto.KakaoProfile;
import com.weiver.global.config.RedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataRedisTest
@Import({RedisConfig.class, KakaoOAuthTicketRepository.class, ObjectMapper.class})
class KakaoOAuthTicketRepositoryTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private KakaoOAuthTicketRepository repository;

    @Test
    @DisplayName("발급 목적과 브라우저가 일치하는 요청만 티켓을 소비하며 재사용을 거절한다")
    void consumeRequiresMatchingPurposeAndBrowser() {
        KakaoProfile profile = new KakaoProfile(93021L, "kakao@example.com");
        repository.save("signup", "issuing-browser", "signup-ticket", profile, Duration.ofMinutes(1));

        assertThat(repository.consume("login", "issuing-browser", "signup-ticket", KakaoProfile.class)).isEmpty();
        assertThat(repository.consume("signup", "other-browser", "signup-ticket", KakaoProfile.class)).isEmpty();
        assertThat(repository.consume("signup", "issuing-browser", "signup-ticket", KakaoProfile.class)).contains(profile);
        assertThat(repository.consume("signup", "issuing-browser", "signup-ticket", KakaoProfile.class)).isEmpty();
    }

    @Test
    @DisplayName("같은 티켓을 동시에 소비하면 정확히 한 요청만 값을 받는다")
    void consumeOnlyOneConcurrentRequestReceivesTicket() throws Exception {
        KakaoProfile profile = new KakaoProfile(93022L, "concurrent@example.com");
        repository.save("signup", "issuing-browser", "concurrent-ticket", profile, Duration.ofMinutes(1));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Optional<KakaoProfile>> consume = () -> {
            ready.countDown();
            assertThat(start.await(5, SECONDS)).isTrue();
            return repository.consume("signup", "issuing-browser", "concurrent-ticket", KakaoProfile.class);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<KakaoProfile>> first = executor.submit(consume);
            Future<Optional<KakaoProfile>> second = executor.submit(consume);
            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();

            assertThat(Stream.of(first.get(5, SECONDS), second.get(5, SECONDS)).flatMap(Optional::stream).toList())
                    .containsExactly(profile);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("저장할 때 지정한 TTL이 지나면 티켓을 소비할 수 없다")
    void expiredTicketCannotBeConsumed() throws InterruptedException {
        Duration ttl = Duration.ofMillis(250);
        repository.save("signup", "issuing-browser", "expiring-ticket", new KakaoProfile(93023L, null), ttl);

        Thread.sleep(ttl.plusMillis(50));

        assertThat(repository.consume("signup", "issuing-browser", "expiring-ticket", KakaoProfile.class)).isEmpty();
    }
}
