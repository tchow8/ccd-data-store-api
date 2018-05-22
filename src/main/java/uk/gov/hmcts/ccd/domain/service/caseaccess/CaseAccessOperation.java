package uk.gov.hmcts.ccd.domain.service.caseaccess;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.data.caseaccess.CaseUserRepository;
import uk.gov.hmcts.ccd.data.casedetails.CachedCaseDetailsRepository;
import uk.gov.hmcts.ccd.data.casedetails.CaseDetailsRepository;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.std.CaseAccess;
import uk.gov.hmcts.ccd.domain.service.getcase.CaseNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.transaction.Transactional;

import static java.util.Collections.emptyList;

@Service
public class CaseAccessOperation {

    private final CaseUserRepository caseUserRepository;
    private final CaseDetailsRepository caseDetailsRepository;

    public CaseAccessOperation(final CaseUserRepository caseUserRepository, @Qualifier(CachedCaseDetailsRepository.QUALIFIER) final CaseDetailsRepository caseDetailsRepository) {
        this.caseUserRepository = caseUserRepository;
        this.caseDetailsRepository = caseDetailsRepository;
    }

    @Transactional
    public void grantAccess(
        final String jurisdictionId,
        final String caseReference,
        final CaseAccess caseAccess
    ) {
        final CaseDetails caseDetails = caseDetailsRepository.findByReference(
            jurisdictionId,
            Long.valueOf(caseReference)
        ).orElseThrow(() -> new CaseNotFoundException(caseReference));

        caseUserRepository.grantAccess(
            caseDetails.getId(),
            caseAccess.getId(),
            caseAccess.getReasonForAccess()
        );
    }

    @Transactional
    public void revokeAccess(final String jurisdictionId, final String caseReference, final String userId) {
        final CaseDetails caseDetails = caseDetailsRepository.findByReference(
            jurisdictionId,
            Long.valueOf(caseReference)
        ).orElseThrow(() -> new CaseNotFoundException(caseReference));

        caseUserRepository.revokeAccess(caseDetails.getId(), userId);
    }

    public List<String> findCasesUserIdHasAccessTo(final String jurisdiction, final String userId) {
        return caseUserRepository.findCasesUserIdHasAccessTo(userId)
            .stream()
            .map(databaseId -> caseDetailsRepository.findById(jurisdiction, databaseId))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(caseDetails -> caseDetails.getReference().toString())
            .collect(Collectors.toList());
    }

    public List<CaseAccess> findUsersWhoHaveAccessToCase(final String jurisdiction, final String caseId) {
        return caseDetailsRepository.findByReference(jurisdiction, caseId)
            .map(CaseDetails::getId)
            .map(caseUserRepository::findUsersWhoHaveAccessToCase)
            .orElse(emptyList());
    }

}
