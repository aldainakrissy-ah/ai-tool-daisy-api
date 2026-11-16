package com.example.ai.tool.analysis.ai_tool_daisy_api.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} jobs, such as the daily clean-up of old Prompt 1 run files. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
