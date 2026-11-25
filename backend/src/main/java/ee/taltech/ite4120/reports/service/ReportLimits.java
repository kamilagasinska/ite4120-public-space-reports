package ee.taltech.ite4120.reports.service;

import ee.taltech.ite4120.reports.model.Reporter;
import ee.taltech.ite4120.reports.model.Standing;
import ee.taltech.ite4120.reports.repository.ReporterRepository;
import ee.taltech.ite4120.reports.repository.ReporterRepository.Usage;
import org.springframework.stereotype.Component;

/** How often someone may report. The numbers are the tables of REPORTS.01. */
@Component
public class ReportLimits {

    private static final long DAY = 24 * 60 * 60;
    private static final int GUEST_PER_DAY = 1;
    private static final int UNSIGNED_PER_IP_PER_DAY = 20;
    private static final int[] POINTS_FOR_LEVEL = {0, 50, 150, 300};
    private static final int[] DEVICE_GAP_SECONDS = {600, 300, 120, 60};
    private static final int[] DEVICE_PER_DAY = {5, 10, 20, 30};
    private static final int SIGNED_GAP_SECONDS = 60;
    private static final int[] SIGNED_PER_DAY = {50, 100, 200, 300};

    private final ReporterRepository repository;

    public ReportLimits(ReporterRepository repository) {
        this.repository = repository;
    }

    /**
     * Takes the locks that make checking and storing one step. Call inside the
     * transaction that stores the report. The order is always IP, then reporter.
     */
    public void lock(Reporter reporter, String ipHash) {
        if (reporter == null || reporter.kind() == Reporter.Kind.DEVICE) {
            repository.lock("ip:" + ipHash);
        }
        if (reporter != null) {
            repository.lock(reporter.kind() + ":" + reporter.keyHash());
        }
    }

    /** Throws when one more report is over the limit. A null reporter is a guest. */
    public void check(Reporter reporter, String ipHash) {
        if (reporter == null) {
            perDay(repository.guestUsage(ipHash), GUEST_PER_DAY);
        }
        if (reporter == null || reporter.kind() == Reporter.Kind.DEVICE) {
            perDay(repository.unsignedUsage(ipHash), UNSIGNED_PER_IP_PER_DAY);
        }
        if (reporter != null) {
            Usage usage = repository.usage(reporter);
            int level = level(repository.points(reporter));
            boolean signed = reporter.kind() == Reporter.Kind.SIGNED;
            int gap = signed ? SIGNED_GAP_SECONDS : DEVICE_GAP_SECONDS[level];
            if (usage.secondsSinceLast() != null && usage.secondsSinceLast() < gap) {
                throw new LimitExceededException(gap - usage.secondsSinceLast());
            }
            perDay(usage, signed ? SIGNED_PER_DAY[level] : DEVICE_PER_DAY[level]);
        }
    }

    public Standing standing(Reporter reporter) {
        int points = repository.points(reporter);
        int level = level(points);
        return new Standing(points, level, level + 1 < POINTS_FOR_LEVEL.length ? POINTS_FOR_LEVEL[level + 1] : null);
    }

    static int level(int points) {
        int level = 0;
        while (level + 1 < POINTS_FOR_LEVEL.length && points >= POINTS_FOR_LEVEL[level + 1]) {
            level++;
        }
        return level;
    }

    private static void perDay(Usage usage, int limit) {
        if (usage.lastDay() >= limit) {
            /* A place is free again when the oldest of the last `limit` reports is a day old. */
            throw new LimitExceededException(Math.max(1, DAY - usage.secondsSinceNth(limit)));
        }
    }
}
