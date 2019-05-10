package uk.gov.hmcts.ccd.domain.service.getcase;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.ccd.data.caseaccess.CaseUserRepository;
import uk.gov.hmcts.ccd.data.definition.CachedCaseDefinitionRepository;
import uk.gov.hmcts.ccd.data.definition.CaseDefinitionRepository;
import uk.gov.hmcts.ccd.data.user.CachedUserRepository;
import uk.gov.hmcts.ccd.data.user.UserRepository;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.definition.CaseType;
import uk.gov.hmcts.ccd.domain.service.accesscontrol.AccessManagementProvider;
import uk.gov.hmcts.ccd.domain.service.common.AMSAccessControlService;
import uk.gov.hmcts.ccd.domain.service.common.AccessControlService;
import uk.gov.hmcts.reform.amlib.AccessManagementService;
import uk.gov.hmcts.reform.amlib.enums.Permission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static uk.gov.hmcts.ccd.domain.service.common.AccessControlService.CAN_READ;
import static uk.gov.hmcts.reform.amlib.models.ResourceDefinition.builder;

@Service
@Qualifier("authorised")
public class AuthorisedGetCaseOperation implements GetCaseOperation {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference STRING_JSON_MAP = new TypeReference<HashMap<String, JsonNode>>() {
    };

    private final GetCaseOperation getCaseOperation;
    private final CaseDefinitionRepository caseDefinitionRepository;
    private final AccessControlService accessControlService;
    private final UserRepository userRepository;
    private final CaseUserRepository caseUserRepository;
    private final AccessManagementProvider accessManagementProvider;
    private final AccessManagementService accessManagementService;
    private final AMSAccessControlService amsAccessControlService;


    public AuthorisedGetCaseOperation(@Qualifier("classified") final GetCaseOperation getCaseOperation,
                                      @Qualifier(CachedCaseDefinitionRepository.QUALIFIER) final CaseDefinitionRepository caseDefinitionRepository,
                                      final AccessControlService accessControlService,
                                      final AMSAccessControlService amsAccessControlService,
                                      @Qualifier(CachedUserRepository.QUALIFIER) final UserRepository userRepository,
                                      CaseUserRepository caseUserRepository,
                                      final AccessManagementProvider accessManagementProvider,
                                      @Autowired final AccessManagementService accessManagementService) {
        this.getCaseOperation = getCaseOperation;
        this.caseDefinitionRepository = caseDefinitionRepository;
        this.accessControlService = accessControlService;
        this.amsAccessControlService = amsAccessControlService;
        this.userRepository = userRepository;
        this.caseUserRepository = caseUserRepository;
        this.accessManagementProvider = accessManagementProvider;
        this.accessManagementService = accessManagementService;
    }

    @Override
    public Optional<CaseDetails> execute(String jurisdictionId, String caseTypeId, String caseReference) {
        return this.execute(caseReference);
    }

    @Override
    public Optional<CaseDetails> execute(String caseReference) {
        return getCaseOperation.execute(caseReference)
            .flatMap(caseDetails -> {
                Set<String> userRoles = getUserRoles(caseDetails.getId());
                if (accessManagementProvider.isAuthorisedManagedByAMS(caseDetails.getCaseTypeId())) {
                    Map<JsonPointer, Set<Permission>> rolePermissions = accessManagementService.getRolePermissions(
                        builder()
                            .serviceName(caseDetails.getJurisdiction())
                            .resourceType("CASE")
                            .resourceName(caseDetails.getCaseTypeId())
                            .build(),
                        userRoles);

                    verifyReadAccessAMS(rolePermissions,
                        userRoles,
                        caseDetails);
                } else {
                    verifyReadAccess(getCaseType(caseDetails.getCaseTypeId()),
                        userRoles,
                        caseDetails);
                }
                return Optional.of(caseDetails);
            });
    }

    private CaseType getCaseType(String caseTypeId) {
        return caseDefinitionRepository.getCaseType(caseTypeId);
    }


    private Set<String> getUserRoles(String caseId) {
        return Sets.union(userRepository.getUserRoles(),
            caseUserRepository
                .findCaseRoles(Long.valueOf(caseId), userRepository.getUserId())
                .stream()
                .collect(Collectors.toSet()));
    }

    private Optional<CaseDetails> verifyReadAccess(CaseType caseType, Set<String> userRoles, CaseDetails caseDetails) {

        if (caseType == null || caseDetails == null || CollectionUtils.isEmpty(userRoles)) {
            return Optional.empty();
        }

        if (!accessControlService.canAccessCaseTypeWithCriteria(caseType, userRoles, CAN_READ) ||
            !accessControlService.canAccessCaseStateWithCriteria(caseDetails.getState(), caseType, userRoles, CAN_READ)) {
            return Optional.empty();
        }

        caseDetails.setData(MAPPER.convertValue(
            accessControlService.filterCaseFieldsByAccess(
                MAPPER.convertValue(caseDetails.getData(), JsonNode.class),
                caseType.getCaseFields(),
                userRoles,
                CAN_READ),
            STRING_JSON_MAP));
        caseDetails.setDataClassification(MAPPER.convertValue(
            accessControlService.filterCaseFieldsByAccess(
                MAPPER.convertValue(caseDetails.getDataClassification(), JsonNode.class),
                caseType.getCaseFields(),
                userRoles,
                CAN_READ),
            STRING_JSON_MAP));

        return Optional.of(caseDetails);
    }

    private Optional<CaseDetails> verifyReadAccessAMS(Map<JsonPointer, Set<Permission>> rolePermissions, Set<String> userRoles, CaseDetails caseDetails) {

        if (caseDetails == null || CollectionUtils.isEmpty(userRoles)) {
            return Optional.empty();
        }

        Optional<JsonPointer> optionalRoot = rolePermissions
            .entrySet()
            .stream()
            .filter(jsonPointerSetEntry -> amsAccessControlService.isRoot(jsonPointerSetEntry)
                && amsAccessControlService.isReadable(jsonPointerSetEntry))
            .map(jsonPointerSetEntry -> jsonPointerSetEntry.getKey())
            .findAny();

        Optional<JsonPointer> optionalState = rolePermissions
            .entrySet()
            .stream()
            .filter(jsonPointerSetEntry -> amsAccessControlService.pointsToState(jsonPointerSetEntry, caseDetails.getState())
                && amsAccessControlService.isReadable(jsonPointerSetEntry))
            .map(jsonPointerSetEntry -> jsonPointerSetEntry.getKey())
            .findAny();

        if (caseDetails != null) {
            if (!optionalRoot.isPresent() || !optionalState.isPresent()) {
                return Optional.empty();
            }

            List<String> authorisedFields = rolePermissions
                .entrySet()
                .stream()
                .filter(jsonPointerSetEntry -> amsAccessControlService.pointsToAField(jsonPointerSetEntry)
                    && amsAccessControlService.isReadable(jsonPointerSetEntry))
                .map(jsonPointerSetEntry -> amsAccessControlService.trimFieldPath(jsonPointerSetEntry))
                .collect(Collectors.toList());

            caseDetails.setData(MAPPER.convertValue(
                amsAccessControlService.filterCaseData(
                    MAPPER.convertValue(
                        caseDetails.getData(),
                        JsonNode.class),
                    authorisedFields),
                STRING_JSON_MAP));
            caseDetails.setDataClassification(MAPPER.convertValue(
                amsAccessControlService.filterCaseData(
                    MAPPER.convertValue(
                        caseDetails.getDataClassification(),
                        JsonNode.class),
                    authorisedFields),
                STRING_JSON_MAP));
        }
        return Optional.of(caseDetails);
    }

}
