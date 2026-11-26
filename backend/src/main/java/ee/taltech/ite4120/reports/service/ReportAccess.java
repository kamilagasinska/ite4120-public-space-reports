package ee.taltech.ite4120.reports.service;

import ee.taltech.ite4120.reports.model.Report;
import java.util.Set;
import java.util.regex.Pattern;
import org.helex.commons.exception.ForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is a coordinator, and which unit a user is. Coordinators are configuration, and empty
 * means nobody. A unit signs in, in mock auth, as its inbox address.
 */
@Component
public class ReportAccess {

    private static final Pattern UNIT = Pattern.compile("[a-z0-9-]+@[a-z0-9-]+\\.example");

    private final Set<String> coordinators;

    public ReportAccess(@Value("${reports.coordinators:}") Set<String> coordinators) {
        this.coordinators = coordinators;
    }

    public boolean isCoordinator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && coordinators.contains(auth.getName());
    }

    /** The inbox address of the signed-in unit, or null. */
    public String unit() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && UNIT.matcher(auth.getName()).matches() ? auth.getName() : null;
    }

    /** The report was handed to the signed-in unit and not returned. */
    public boolean isUnitOf(Report report) {
        String unit = unit();
        return unit != null && unit.equals(report.getHandoverInbox())
                && ("SENT".equals(report.getHandoverState()) || "OPENED".equals(report.getHandoverState()));
    }

    /** A coordinator, or the unit the report was handed to. */
    public boolean isStaffOf(Report report) {
        return isCoordinator() || isUnitOf(report);
    }

    public void requireCoordinator() {
        if (!isCoordinator()) {
            throw new ForbiddenException("only a coordinator may do this");
        }
    }
}
