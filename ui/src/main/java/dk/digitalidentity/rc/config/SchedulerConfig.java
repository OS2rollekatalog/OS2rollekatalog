package dk.digitalidentity.rc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

// this ensures scheduled tasks runs in a single thread, with no overlapping jobs. Due to virtualthreds being enabled,
// this is needed, unless someone goes through all tasks, and ensures they are either okay being run in parallel (the same task
// can be started multiple times if they are long-running and runs often), or some blocker is implemented in them
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

	@Bean(destroyMethod = "shutdown")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduled-");
        scheduler.setRemoveOnCancelPolicy(true);

        // explicitly do NOT use virtual threads
        scheduler.setVirtualThreads(false);
        scheduler.initialize();

        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
    }
}
