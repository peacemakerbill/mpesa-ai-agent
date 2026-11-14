package com.darajaagentx.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on @Scheduled jobs, such as the one that recovers STK Pushes whose callback never arrived. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
