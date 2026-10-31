package com.mganji.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class AsyncConfig {

    /**
     * Runs the (slow) work of answering a customer, so the app gets an answer at once.
     * When every thread is busy and the queue is full, submit() throws and the inbound endpoint answers 503,
     * and the app is asked to send the message again a little later.
     */
    @Bean(destroyMethod = "shutdown")
    ThreadPoolExecutor inboundExecutor() {
        return new ThreadPoolExecutor(8, 32, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200),
                runnable -> {
                    Thread t = new Thread(runnable);
                    t.setName("inbound-" + t.threadId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
