package ee.taltech.ite4120.reports.service;

/** The reporter is over the limit. Answers 429 with Retry-After. */
public class LimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public LimitExceededException(long retryAfterSeconds) {
        super("too many reports, try again in " + Math.ceilDiv(retryAfterSeconds, 60)
                + " minutes or sign with Smart-ID for a higher limit");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
