package com.bank.migration.load;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MigrationJobConfig {
    @Bean
    Job migrationJob(JobRepository jobRepository, Step migrationStep) {
        return new JobBuilder("oracleToGaussDbMigrationJob", jobRepository)
            .start(migrationStep)
            .build();
    }

    @Bean
    Step migrationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("migrationOrchestrationStep", jobRepository)
            .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
            .build();
    }
}
