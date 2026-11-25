package ee.taltech.ite4120.reports.model;

import java.util.Arrays;

/** The report statuses and the changes allowed between them. */
public enum ReportStatus {
    RECEIVED, ACCEPTED, IN_PROGRESS, RESOLVED, REJECTED;

    public boolean canBecome(ReportStatus next) {
        return switch (this) {
            case RECEIVED -> next == ACCEPTED || next == IN_PROGRESS || next == REJECTED;
            case ACCEPTED -> next == IN_PROGRESS || next == REJECTED;
            case IN_PROGRESS -> next == RESOLVED;
            case RESOLVED, REJECTED -> false;
        };
    }

    public boolean isClosed() {
        return this == RESOLVED || this == REJECTED;
    }

    public static boolean isKnown(String name) {
        return Arrays.stream(values()).anyMatch(s -> s.name().equals(name));
    }
}
