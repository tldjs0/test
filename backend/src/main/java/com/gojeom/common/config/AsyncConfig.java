package com.gojeom.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 분석 파이프라인용 스레드 풀. (ARCHITECTURE.md §5.3)
 *
 * <p>OpenAI 대기가 대부분인 IO 바운드 작업이라 CPU 코어 수보다 크게 잡는다.
 * 이미지 생성은 더 느리고 실패율이 높아 별도 풀로 분리해, 텍스트 파이프라인이
 * 이미지 작업에 막히지 않게 한다.
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    public static final String ANALYSIS_EXECUTOR = "analysisExecutor";
    public static final String IMAGE_EXECUTOR = "imageExecutor";

    @Bean(ANALYSIS_EXECUTOR)
    public Executor analysisExecutor() {
        return build("analysis-", 8, 16, 100);
    }

    @Bean(IMAGE_EXECUTOR)
    public Executor imageExecutor() {
        return build("image-", 4, 8, 50);
    }

    private ThreadPoolTaskExecutor build(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        // 큐가 가득 차면 버리지 않고 호출 스레드에서 실행한다. 분석 요청을 잃는 것보다 낫다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 반환값이 void인 @Async 메서드의 예외는 호출자에게 전파되지 않는다.
     * 여기서 잡지 않으면 조용히 사라진다.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("async task failed: {}", method.getName(), ex);
            new SimpleAsyncUncaughtExceptionHandler().handleUncaughtException(ex, method, params);
        };
    }
}
