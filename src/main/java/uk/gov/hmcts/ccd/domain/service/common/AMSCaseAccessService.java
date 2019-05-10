package uk.gov.hmcts.ccd.domain.service.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.data.caseaccess.CaseUserRepository;
import uk.gov.hmcts.ccd.data.user.CachedUserRepository;
import uk.gov.hmcts.ccd.data.user.UserRepository;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.endpoint.exceptions.ValidationException;
import uk.gov.hmcts.ccd.infrastructure.user.UserAuthorisation.AccessLevel;
import uk.gov.hmcts.reform.amlib.AccessManagementService;
import uk.gov.hmcts.reform.amlib.models.FilteredResourceEnvelope;
import uk.gov.hmcts.reform.amlib.models.Resource;
import uk.gov.hmcts.reform.auth.checker.spring.serviceanduser.ServiceAndUserDetails;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static uk.gov.hmcts.reform.amlib.models.ResourceDefinition.builder;

/**
 * Check access to a case for the current user.
 * <p>
 * User with the following roles should only be given access to the cases explicitly granted:
 * <ul>
 * <li>caseworker-*-solicitor: Solicitors</li>
 * <li>citizen(-loa[0-3]): Citizens</li>
 * <li>letter-holder: Citizen with temporary user account, as per CMC journey</li>
 * </ul>
 */
@Service
public class AMSCaseAccessService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final UserRepository userRepository;
    private final CaseUserRepository caseUserRepository;
    private final AccessManagementService accessManagementService;

    private static final Pattern RESTRICT_GRANTED_ROLES_PATTERN
        = Pattern.compile(".+-solicitor$|.+-panelmember$|^citizen(-.*)?$|^letter-holder$|^caseworker-.+-localAuthority$");

    public AMSCaseAccessService(@Qualifier(CachedUserRepository.QUALIFIER) final UserRepository userRepository,
                                final CaseUserRepository caseUserRepository,
                                final AccessManagementService accessManagementService) {
        this.userRepository = userRepository;
        this.caseUserRepository = caseUserRepository;
        this.accessManagementService = accessManagementService;
    }

    public Boolean canUserAccess(CaseDetails caseDetails) {
        return !canOnlyViewGrantedCases() || accessGranted(caseDetails);
    }

    public AccessLevel getAccessLevel(ServiceAndUserDetails serviceAndUserDetails) {
        return serviceAndUserDetails.getAuthorities()
                                    .stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .filter(role -> RESTRICT_GRANTED_ROLES_PATTERN.matcher(role).matches())
                                    .findFirst()
                                    .map(role -> AccessLevel.GRANTED)
                                    .orElse(AccessLevel.ALL);
    }

    public Optional<List<Long>> getGrantedCaseIdsForRestrictedRoles() {
        if (canOnlyViewGrantedCases()) {
            return Optional.of(caseUserRepository.findCasesUserIdHasAccessTo(userRepository.getUserId()));
        }

        return Optional.empty();
    }

    public Set<String> getCaseRoles(String caseId) {
        return new HashSet<>(caseUserRepository.findCaseRoles(Long.valueOf(caseId), userRepository.getUserId()));
    }

    public Set<String> getUserRoles() {
        Set<String> userRoles = userRepository.getUserRoles();
        if (userRoles == null) {
            throw new ValidationException("Cannot find user roles for the user");
        }
        return userRoles;
    }

    private Boolean accessGranted(CaseDetails caseDetails) {
        // TODO: should get a populated envelope if earlier access was given or empty envelope if no explicit access
        // (API missing that will case roles assigned to user)
        FilteredResourceEnvelope resourceEnvelope = accessManagementService.filterResource(
            userRepository.getUserId(),
            getUserRoles(),
            Resource.builder()
                .id(caseDetails.getReferenceAsString())
                .definition(builder()
                    .serviceName(caseDetails.getJurisdiction())
                    .resourceType("CASE")
                    .resourceName(caseDetails.getCaseTypeId())
                    .build())
                .data(MAPPER.convertValue(caseDetails.getData(), JsonNode.class))
                .build());

        final List<Long> grantedCases = caseUserRepository.findCasesUserIdHasAccessTo(userRepository.getUserId());

        if (null != grantedCases && grantedCases.contains(Long.valueOf(caseDetails.getId()))) {
            return Boolean.TRUE;
        }

        return Boolean.FALSE;
    }

    private Boolean canOnlyViewGrantedCases() {
        return userRepository.getUserRoles()
            .stream()
            .anyMatch(role -> RESTRICT_GRANTED_ROLES_PATTERN.matcher(role).matches());
    }

    public String getUserId() {
        return userRepository.getUserId();
    }
}
