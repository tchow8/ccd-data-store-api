package uk.gov.hmcts.ccd.domain.service.getcase;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.service.accesscontrol.AccessManagementProvider;
import uk.gov.hmcts.ccd.domain.service.common.AMSCaseAccessService;
import uk.gov.hmcts.ccd.domain.service.common.CaseAccessService;

import java.util.Optional;

@Service
@Qualifier(CreatorGetCaseOperation.QUALIFIER)
public class CreatorGetCaseOperation implements GetCaseOperation {

    public static final String QUALIFIER = "creator";

    private GetCaseOperation getCaseOperation;

    private final CaseAccessService caseAccessService;
    private final AMSCaseAccessService amsCaseAccessService;
    private final AccessManagementProvider accessManagementProvider;

    public CreatorGetCaseOperation(@Qualifier("authorised")final GetCaseOperation getCaseOperation,
                                   final CaseAccessService caseAccessService,
                                   final AMSCaseAccessService amsCaseAccessService,
                                   final AccessManagementProvider accessManagementProvider) {
        this.getCaseOperation = getCaseOperation;
        this.caseAccessService = caseAccessService;
        this.accessManagementProvider = accessManagementProvider;
        this.amsCaseAccessService = amsCaseAccessService;
    }

    @Override
    public Optional<CaseDetails> execute(String jurisdictionId, String caseTypeId, String caseReference) {
        return this.getCaseOperation.execute(jurisdictionId, caseTypeId, caseReference)
            .flatMap(caseDetails -> {
                if (accessManagementProvider.isAuthorisedManagedByAMS(caseDetails.getCaseTypeId())) {
                    return amsCheckVisibility(caseDetails);
                } else {
                    return checkVisibility(caseDetails);
                }
            });
    }

    @Override
    public Optional<CaseDetails> execute(String caseReference) {
        return this.getCaseOperation.execute(caseReference)
            .flatMap(caseDetails -> {
                if (accessManagementProvider.isAuthorisedManagedByAMS(caseDetails.getCaseTypeId())) {
                    return amsCheckVisibility(caseDetails);
                } else {
                    return checkVisibility(caseDetails);
                }
            });
    }

    private Optional<CaseDetails> amsCheckVisibility(CaseDetails caseDetails) {
        return this.amsCaseAccessService.canUserAccess(caseDetails)
            ? Optional.of(caseDetails) : Optional.empty();
    }

    private Optional<CaseDetails> checkVisibility(CaseDetails caseDetails) {
        return this.caseAccessService.canUserAccess(caseDetails)
            ? Optional.of(caseDetails) : Optional.empty();
    }

}
