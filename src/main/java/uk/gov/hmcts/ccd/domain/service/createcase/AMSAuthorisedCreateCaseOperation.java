package uk.gov.hmcts.ccd.domain.service.createcase;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.data.definition.CachedCaseDefinitionRepository;
import uk.gov.hmcts.ccd.data.definition.CaseDefinitionRepository;
import uk.gov.hmcts.ccd.data.user.CachedUserRepository;
import uk.gov.hmcts.ccd.data.user.UserRepository;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.std.CaseDataContent;
import uk.gov.hmcts.ccd.domain.model.std.Event;
import uk.gov.hmcts.ccd.domain.service.accesscontrol.AccessManagementProvider;
import uk.gov.hmcts.ccd.domain.service.common.AMSAccessControlService;
import uk.gov.hmcts.ccd.endpoint.exceptions.ResourceNotFoundException;
import uk.gov.hmcts.ccd.endpoint.exceptions.ValidationException;
import uk.gov.hmcts.reform.amlib.AccessManagementService;
import uk.gov.hmcts.reform.amlib.enums.Permission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static uk.gov.hmcts.ccd.domain.service.common.AccessControlService.NO_CASE_TYPE_FOUND;
import static uk.gov.hmcts.ccd.domain.service.common.AccessControlService.NO_EVENT_FOUND;
import static uk.gov.hmcts.ccd.domain.service.common.AccessControlService.NO_FIELD_FOUND;
import static uk.gov.hmcts.reform.amlib.models.ResourceDefinition.builder;

@Service
@Qualifier("ams-authorised")
public class AMSAuthorisedCreateCaseOperation implements CreateCaseOperation {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference STRING_JSON_MAP = new TypeReference<HashMap<String, JsonNode>>() {
    };

    private final CreateCaseOperation createCaseOperation;
    private final CaseDefinitionRepository caseDefinitionRepository;
    private final AMSAccessControlService amsAccessControlService;
    private final UserRepository userRepository;
    private final AccessManagementProvider accessManagementProvider;
    private final CreateCaseOperation ccdCreateCaseOperation;
    private final AccessManagementService accessManagementService;

    public AMSAuthorisedCreateCaseOperation(@Qualifier("classified") final CreateCaseOperation createCaseOperation,
                                            @Qualifier(CachedCaseDefinitionRepository.QUALIFIER) final CaseDefinitionRepository caseDefinitionRepository,
                                            final AMSAccessControlService amsAccessControlService,
                                            @Qualifier(CachedUserRepository.QUALIFIER) final UserRepository userRepository,
                                            final AccessManagementProvider accessManagementProvider,
                                            @Qualifier("authorised") final CreateCaseOperation ccdCreateCaseOperation,
                                            @Autowired final AccessManagementService accessManagementService) {

        this.createCaseOperation = createCaseOperation;
        this.caseDefinitionRepository = caseDefinitionRepository;
        this.amsAccessControlService = amsAccessControlService;
        this.userRepository = userRepository;
        this.accessManagementProvider = accessManagementProvider;
        this.ccdCreateCaseOperation = ccdCreateCaseOperation;
        this.accessManagementService = accessManagementService;
    }

    @Override
    public CaseDetails createCaseDetails(final String uid,
                                         String jurisdictionId,
                                         String caseTypeId,
                                         CaseDataContent caseDataContent,
                                         Boolean ignoreWarning) {
        if (accessManagementProvider.isAuthorisedManagedByAMS(caseTypeId)) {
            if (caseDataContent == null) {
                throw new ValidationException("No data provided");
            }

            Set<String> userRoles = userRepository.getUserRoles();
            if (userRoles == null) {
                throw new ValidationException("Cannot find user roles for the user");
            }

            Event event = caseDataContent.getEvent();
            Map<JsonPointer, Set<Permission>> rolePermissions = accessManagementService.getRolePermissions(
                builder()
                    .serviceName(jurisdictionId)
                    .resourceType("CASE")
                    .resourceName(caseTypeId)
                    .build(),
                userRoles);
            verifyCreateAccess(rolePermissions, event);

            final CaseDetails caseDetails = createCaseOperation.createCaseDetails(uid,
                jurisdictionId,
                caseTypeId,
                caseDataContent,
                ignoreWarning);
            return verifyReadAccess(rolePermissions, caseDetails);
        } else {
            return ccdCreateCaseOperation.createCaseDetails(uid, jurisdictionId, caseTypeId, caseDataContent, ignoreWarning);
        }
    }

    private CaseDetails verifyReadAccess(Map<JsonPointer, Set<Permission>> rolePermissions, CaseDetails caseDetails) {

        Optional<JsonPointer> optionalRoot = rolePermissions
            .entrySet()
            .stream()
            .filter(jsonPointerSetEntry -> amsAccessControlService.isRoot(jsonPointerSetEntry)
                && amsAccessControlService.isReadable(jsonPointerSetEntry))
            .map(jsonPointerSetEntry -> jsonPointerSetEntry.getKey())
            .findAny();

        if (caseDetails != null) {
            if (!optionalRoot.isPresent()) {
                return null;
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
        return caseDetails;
    }

    private void verifyCreateAccess(Map<JsonPointer, Set<Permission>> rolePermissions, Event event) {

        Optional<JsonPointer> optionalRoot = rolePermissions
            .entrySet()
            .stream()
            .filter(jsonPointerSetEntry -> amsAccessControlService.isRoot(jsonPointerSetEntry)
                && amsAccessControlService.isCreatable(jsonPointerSetEntry))
            .map(jsonPointerSetEntry -> jsonPointerSetEntry.getKey())
            .findAny();

        // challenge on case type
        if (!optionalRoot.isPresent()) {
            throw new ResourceNotFoundException(NO_CASE_TYPE_FOUND);
        }

        // challenge on event
        if (event == null || rolePermissions
            .entrySet()
            .stream()
            .noneMatch(jsonPointerSetEntry -> amsAccessControlService.pointsToEvent(jsonPointerSetEntry, event.getEventId())
                && amsAccessControlService.isCreatable(jsonPointerSetEntry))) {
            throw new ResourceNotFoundException(NO_EVENT_FOUND);
        }

        // challenge on fields
        if (!rolePermissions
            .entrySet()
            .stream()
            .allMatch(jsonPointerSetEntry -> amsAccessControlService.pointsToAField(jsonPointerSetEntry)
                && amsAccessControlService.isCreatable(jsonPointerSetEntry))) {
            throw new ResourceNotFoundException(NO_FIELD_FOUND);
        }
    }

}
