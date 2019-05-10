package uk.gov.hmcts.ccd.domain.service.getcase;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.service.accesscontrol.AccessManagementProvider;
import uk.gov.hmcts.ccd.domain.service.common.AMSSecurityClassificationService;
import uk.gov.hmcts.ccd.domain.service.common.SecurityClassificationService;
import uk.gov.hmcts.reform.amlib.AccessManagementService;

import java.util.Optional;

@Service
@Qualifier("classified")
public class ClassifiedGetCaseOperation implements GetCaseOperation {


    private final GetCaseOperation getCaseOperation;
    private final SecurityClassificationService classificationService;
    private final AMSSecurityClassificationService amsClassificationService;
    private final AccessManagementProvider accessManagementProvider;

    public ClassifiedGetCaseOperation(@Qualifier("default") GetCaseOperation getCaseOperation,
                                      SecurityClassificationService classificationService,
                                      AMSSecurityClassificationService amsClassificationService,
                                      final AccessManagementProvider accessManagementProvider) {
        this.getCaseOperation = getCaseOperation;
        this.classificationService = classificationService;
        this.amsClassificationService = amsClassificationService;
        this.accessManagementProvider = accessManagementProvider;
    }

    @Override
    public Optional<CaseDetails> execute(String jurisdictionId, String caseTypeId, String caseReference) {
        return getCaseOperation.execute(jurisdictionId, caseTypeId, caseReference)
            .flatMap(caseDetails -> {
                if (accessManagementProvider.isAuthorisedManagedByAMS(caseDetails.getCaseTypeId())) {
                    amsClassificationService.applyClassification(caseDetails);
                } else {
                    classificationService.applyClassification(caseDetails);
                }
                return Optional.of(caseDetails);
            });
    }

    @Override
    public Optional<CaseDetails> execute(String caseReference) {
        return getCaseOperation.execute(caseReference)
            .flatMap(caseDetails -> {
                if (accessManagementProvider.isAuthorisedManagedByAMS(caseDetails.getCaseTypeId())) {
                    amsClassificationService.applyClassification(caseDetails);
                } else {
                    classificationService.applyClassification(caseDetails);
                }
                return Optional.of(caseDetails);
            });
    }
}
