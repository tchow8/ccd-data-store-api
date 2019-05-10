package uk.gov.hmcts.ccd.domain.service.accesscontrol;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.ApplicationParams;

@Service
public class AccessManagementProvider {

    private final ApplicationParams applicationParams;

    public AccessManagementProvider(final ApplicationParams applicationParams) {
        this.applicationParams = applicationParams;
    }

    public boolean isAuthorisedManagedByAMS(final String caseType) {
        return this.applicationParams.getAccessmanagementEnabled() && this.applicationParams.getAmsAuthorisedCaseTypes().contains(caseType);
    }

    public boolean isClassifiedManagedByAMS(final String caseType) {
        return this.applicationParams.getAccessmanagementEnabled() && this.applicationParams.getAmsClassifiedCaseTypes().contains(caseType);
    }

    public boolean isUserAccessManagedByAMS(final String caseType) {
        return this.applicationParams.getAccessmanagementEnabled() && this.applicationParams.getAmsUserAccessCaseTypes().contains(caseType);
    }

}
