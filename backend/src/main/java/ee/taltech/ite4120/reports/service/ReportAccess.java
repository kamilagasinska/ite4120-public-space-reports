package ee.taltech.ite4120.reports.service;

import java.util.Set;
import org.helex.commons.exception.ForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Who is a coordinator. The list is configuration, and empty means nobody. */
@Component
public class ReportAccess {

    private final Set<String> coordinators;

    public ReportAccess(@Value("${reports.coordinators:}") Set<String> coordinators) {
        this.coordinators = coordinators;
    }

    public boolean isCoordinator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && coordinators.contains(auth.getName());
    }

    public void requireCoordinator() {
        if (!isCoordinator()) {
            throw new ForbiddenException("only a coordinator may do this");
        }
    }
}
