package com.weiver.global.speech.config;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * TTS 전용 스레드풀(bulkhead). RabbitMQ 리스너 스레드가 TTS를 기다리지 않도록 별도 풀에서 실행한다.
 *
 * <p>일부러 {@link java.util.concurrent.Executor}를 구현하지 않는다. Spring Boot는 {@code Executor} 빈이 하나라도 있으면
 * 기본 {@code applicationTaskExecutor}를 만들지 않아, 이후 {@code @Async}가 이 풀을 대신 쓰게 되기 때문이다.
 * 크기·대기열은 {@code weiver.speech.tts.executor.*} 설정이며, 가득 차면 {@link RejectedExecutionException}을 던진다.
 */
@Component
public class SpeechTtsExecutor {

    private static final int SHUTDOWN_AWAIT_SECONDS = 10;

    private final ThreadPoolTaskExecutor delegate;

    public SpeechTtsExecutor(SpeechProperties properties) {
        SpeechProperties.Executor config = properties.tts().executor();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("speech-tts-");
        executor.setCorePoolSize(config.coreSize());
        executor.setMaxPoolSize(config.maxSize());
        executor.setQueueCapacity(config.queueCapacity());
        // 포화 시 호출 스레드에서 실행하지 않고 거절한다(CallerRuns는 리스너 스레드를 붙잡는다).
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_SECONDS);
        executor.initialize();
        this.delegate = executor;
    }

    /**
     * @throws RejectedExecutionException 스레드와 대기열이 모두 가득 찬 경우
     */
    public void execute(Runnable task) {
        delegate.execute(task);
    }

    @PreDestroy
    public void shutdown() {
        delegate.shutdown();
    }
}
