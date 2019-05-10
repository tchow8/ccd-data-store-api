package uk.gov.hmcts.ccd.domain.service.startevent;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.data.casedetails.CachedCaseDetailsRepository;
import uk.gov.hmcts.ccd.data.casedetails.CaseDetailsRepository;
import uk.gov.hmcts.ccd.data.definition.CachedCaseDefinitionRepository;
import uk.gov.hmcts.ccd.data.definition.CaseDefinitionRepository;
import uk.gov.hmcts.ccd.data.draft.CachedDraftGateway;
import uk.gov.hmcts.ccd.data.draft.DraftGateway;
import uk.gov.hmcts.ccd.domain.model.callbacks.StartEventTrigger;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.definition.CaseType;
import uk.gov.hmcts.ccd.domain.model.draft.Draft;
import uk.gov.hmcts.ccd.domain.service.accesscontrol.AccessManagementProvider;
import uk.gov.hmcts.ccd.domain.service.common.AMSAccessControlService;
import uk.gov.hmcts.ccd.domain.service.common.CaseAccessService;
import uk.gov.hmcts.ccd.domain.service.common.UIDService;
import uk.gov.hmcts.ccd.domain.service.getcase.CaseNotFoundException;
import uk.gov.hmcts.ccd.domain.service.startevent.StartEventOperation;
import uk.gov.hmcts.ccd.endpoint.exceptions.BadRequestException;
import uk.gov.hmcts.ccd.endpoint.exceptions.ValidationException;
import uk.gov.hmcts.reform.amlib.AccessManagementService;
import uk.gov.hmcts.reform.amlib.enums.Permission;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;
import static uk.gov.hmcts.reform.amlib.models.ResourceDefinition.builder;

@Service
@Qualifier("ams-authorised")
public class AMSAuthorisedStartEventOperation implements StartEventOperation {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference STRING_JSON_MAP = new TypeReference<HashMap<String, JsonNode>>() {
    };

    private final StartEventOperation startEventOperation;
    private final CaseDefinitionRepository caseDefinitionRepository;
    private final CaseDetailsRepository caseDetailsRepository;
    private final AMSAccessControlService amsAccessControlService;
    private final UIDService uidService;
    private final CaseAccessService caseAccessService;
    private final DraftGateway draftGateway;
    private final AccessManagementProvider accessManagementProvider;
    private final StartEventOperation ccdAuthorisedStartEventOperation;
    private final AccessManagementService accessManagementService;

    public AMSAuthorisedStartEventOperation(@Qualifier("classified") final StartEventOperation startEventOperation,
                                            @Qualifier(CachedCaseDefinitionRepository.QUALIFIER) final CaseDefinitionRepository caseDefinitionRepository,
                                            @Qualifier(CachedCaseDetailsRepository.QUALIFIER) final CaseDetailsRepository caseDetailsRepository,
                                            final AMSAccessControlService amsAccessControlService,
                                            final UIDService uidService,
                                            @Qualifier(CachedDraftGateway.QUALIFIER) final DraftGateway draftGateway,
                                            CaseAccessService caseAccessService,
                                            final AccessManagementProvider accessManagementProvider,
                                            @Qualifier("authorised") final StartEventOperation ccdAuthorisedStartEventOperation,
                                            @Autowired final AccessManagementService accessManagementService) {

        this.startEventOperation = startEventOperation;
        this.caseDefinitionRepository = caseDefinitionRepository;
        this.caseDetailsRepository = caseDetailsRepository;
        this.amsAccessControlService = amsAccessControlService;
        this.uidService = uidService;
        this.caseAccessService = caseAccessService;
        this.draftGateway = draftGateway;
        this.accessManagementProvider = accessManagementProvider;
        this.ccdAuthorisedStartEventOperation = ccdAuthorisedStartEventOperation;
        this.accessManagementService = accessManagementService;
    }

    @Override
    public StartEventTrigger triggerStartForCaseType(String caseTypeId, String eventTriggerId, Boolean ignoreWarning) {
        if (accessManagementProvider.isAuthorisedManagedByAMS(caseTypeId)) {
            return verifyReadAccess(caseTypeId, startEventOperation.triggerStartForCaseType(caseTypeId,
                eventTriggerId,
                ignoreWarning));
        } else {
            return ccdAuthorisedStartEventOperation.triggerStartForCaseType(caseTypeId, eventTriggerId, ignoreWarning);
        }
    }

    @Override
    public StartEventTrigger triggerStartForCase(String caseReference, String eventTriggerId, Boolean ignoreWarning) {

        if (!uidService.validateUID(caseReference)) {
            throw new BadRequestException("Case reference is not valid");
        }

        return caseDetailsRepository.findByReference(caseReference)
            .map(caseDetails -> verifyReadAccess(caseDetails.getCaseTypeId(), startEventOperation.triggerStartForCase(caseReference,
                eventTriggerId,
                ignoreWarning)))
            .orElseThrow(() -> new CaseNotFoundException(caseReference));
    }

    @Override
    public StartEventTrigger triggerStartForDraft(String draftReference,
                                                  Boolean ignoreWarning) {

        final CaseDetails caseDetails = draftGateway.getCaseDetails(Draft.stripId(draftReference));
        return verifyReadAccess(caseDetails.getCaseTypeId(), startEventOperation.triggerStartForDraft(draftReference,
            ignoreWarning));
    }

    private Set<String> getCaseRoles(CaseDetails caseDetails) {
        if (caseDetails == null || caseDetails.getId() == null || Draft.isDraft(caseDetails.getId())) {
            return Collections.emptySet();
        } else {
            return caseAccessService.getCaseRoles(caseDetails.getId());
        }
    }

    private StartEventTrigger verifyReadAccess(final String caseTypeId, final StartEventTrigger startEventTrigger) {

        Set<String> userRoles = Sets.union(caseAccessService.getUserRoles(), getCaseRoles(startEventTrigger.getCaseDetails()));

        Map<JsonPointer, Set<Permission>> rolePermissions = accessManagementService.getRolePermissions(
            builder()
                .serviceName(startEventTrigger.getJurisdictionId())
                .resourceType("CASE")
                .resourceName(caseTypeId)
                .build(),
            userRoles);

        Optional<JsonPointer> optionalRoot = rolePermissions
            .entrySet()
            .stream()
            .filter(jsonPointerSetEntry -> amsAccessControlService.isRoot(jsonPointerSetEntry)
                && amsAccessControlService.isReadable(jsonPointerSetEntry))
            .map(jsonPointerSetEntry -> jsonPointerSetEntry.getKey())
            .findAny();

        CaseDetails caseDetails = startEventTrigger.getCaseDetails();
        // filter on case type
        if (!optionalRoot.isPresent()) {
            caseDetails.setData(newHashMap());
            caseDetails.setDataClassification(newHashMap());
            return startEventTrigger;
        }

        // filter on case fields
        if (caseDetails != null) {
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

        return startEventTrigger;
    }
}
