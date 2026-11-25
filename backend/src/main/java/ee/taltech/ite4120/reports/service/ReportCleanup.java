package ee.taltech.ite4120.reports.service;

import ee.taltech.ite4120.reports.repository.ReporterRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/** Deletes what is kept only for a time (REPORTS.01, Identity and limits). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ReportCleanup {

    private final ReporterRepository repository;

    public ReportCleanup(ReporterRepository repository) {
        this.repository = repository;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 600_000)
    @Transactional
    public void clean() {
        repository.clearOldIpHashes();
        repository.forgetOldDevices();
        repository.expireSignings();
    }
}
