package com.weiver.global.speech.config;

import com.weiver.global.speech.SpeechTestProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeechTtsExecutorTest {

    private SpeechTtsExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    private SpeechTtsExecutor executor(int core, int max, int queue) {
        SpeechProperties base = SpeechTestProperties.of("http://localhost", "key", "", Duration.ofSeconds(8));
        SpeechProperties.Tts tts = base.tts();
        SpeechProperties.Tts custom = new SpeechProperties.Tts(
                tts.baseUrl(), tts.apiKey(), tts.model(), tts.voice(), tts.style(), tts.timeout(), tts.s3Dir(),
                new SpeechProperties.Executor(core, max, queue, Duration.ofSeconds(5)));
        return new SpeechTtsExecutor(new SpeechProperties(base.stt(), custom));
    }

    @Test
    @DisplayName("작업을 호출한 스레드가 아닌 전용 스레드에서 실행한다")
    void execute_runsOnDedicatedThread() throws InterruptedException {
        executor = executor(1, 1, 1);
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        executor.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            done.countDown();
        });

        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).startsWith("speech-tts-").isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("스레드와 대기열이 모두 가득 차면 호출 스레드에서 실행하지 않고 RejectedExecutionException을 던진다")
    void execute_rejectsWhenSaturated() throws InterruptedException {
        // given: 스레드 1개, 대기열 1개 → 세 번째 작업부터 거절된다
        executor = executor(1, 1, 1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        Runnable blocking = () -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        executor.execute(blocking);                 // 실행 중
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        executor.execute(() -> { });                // 대기열

        // when & then
        AtomicReference<Boolean> ranOnCaller = new AtomicReference<>(false);
        Thread caller = Thread.currentThread();
        assertThatThrownBy(() -> executor.execute(() -> ranOnCaller.set(Thread.currentThread() == caller)))
                .isInstanceOf(RejectedExecutionException.class);
        assertThat(ranOnCaller.get()).isFalse();

        release.countDown();
    }

    @Test
    @DisplayName("SpeechTtsExecutor는 Spring Boot 기본 스레드풀을 없애는 Executor 타입이 아니다")
    void isNotAnExecutorType() {
        assertThat(Executor.class.isAssignableFrom(SpeechTtsExecutor.class)).isFalse();
    }
}
