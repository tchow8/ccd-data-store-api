package uk.gov.hmcts.ccd.domain.service.caseaccess;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.data.caseaccess.CachedCaseRoleRepository;
import uk.gov.hmcts.ccd.data.caseaccess.CaseRoleRepository;
import uk.gov.hmcts.ccd.data.caseaccess.CaseUserRepository;
import uk.gov.hmcts.ccd.data.casedetails.CachedCaseDetailsRepository;
import uk.gov.hmcts.ccd.data.casedetails.CaseDetailsRepository;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.service.getcase.CaseNotFoundException;
import uk.gov.hmcts.ccd.endpoint.exceptions.InvalidCaseRoleException;
import uk.gov.hmcts.ccd.v2.external.domain.CaseUser;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CaseAccessOperation {

    private final CaseUserRepository caseUserRepository;
    private final CaseDetailsRepository caseDetailsRepository;
    private final CaseRoleRepository caseRoleRepository;

    public CaseAccessOperation(final CaseUserRepository caseUserRepository,
                               @Qualifier(CachedCaseDetailsRepository.QUALIFIER) final CaseDetailsRepository caseDetailsRepository,
                               @Qualifier(CachedCaseRoleRepository.QUALIFIER) CaseRoleRepository caseRoleRepository) {
        this.caseUserRepository = caseUserRepository;
        this.caseDetailsRepository = caseDetailsRepository;
        this.caseRoleRepository = caseRoleRepository;
    }

    @Transactional
    public void grantAccess(final String jurisdictionId, final String caseReference, final String userId) {
        final Optional<CaseDetails> maybeCase = caseDetailsRepository.findByReference(jurisdictionId,
                                                                                      Long.valueOf(caseReference));

        final CaseDetails caseDetails = maybeCase.orElseThrow(() -> new CaseNotFoundException(caseReference));
        caseUserRepository.grantAccess(Long.valueOf(caseDetails.getId()), userId);
    }

    @Transactional
    public void revokeAccess(final String jurisdictionId, final String caseReference, final String userId) {
        final Optional<CaseDetails> maybeCase = caseDetailsRepository.findByReference(jurisdictionId,
                                                                                      Long.valueOf(caseReference));
        final CaseDetails caseDetails = maybeCase.orElseThrow(() -> new CaseNotFoundException(caseReference));
        caseUserRepository.revokeAccess(Long.valueOf(caseDetails.getId()), userId);
    }

    public List<String> findCasesUserIdHasAccessTo(final String userId) {
        return caseUserRepository.findCasesUserIdHasAccessTo(userId)
                                 .stream()
                                 .map(databaseId -> caseDetailsRepository.findById(databaseId).getReference() + "")
                                 .collect(Collectors.toList());
    }

    @Transactional
    public void grant(CaseDetails caseDetails, CaseUser caseUser) {
        final Set<String> caseRoles = caseRoleRepository.getCaseRoles(caseDetails.getCaseTypeId());

        caseUser.getCaseRoles()
                .stream()
                .filter((role) -> !caseRoles.contains(role))
                .findFirst()
                .ifPresent(role -> {
                    throw new InvalidCaseRoleException(role);
                });

        final Long caseId = new Long(caseDetails.getId());
        caseRoles.forEach(role -> caseUserRepository.grantAccess(caseId, caseUser.getUserId(), role));
    }
}
