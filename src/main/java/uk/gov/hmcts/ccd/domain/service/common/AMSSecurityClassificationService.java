package uk.gov.hmcts.ccd.domain.service.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.data.casedetails.SecurityClassification;
import uk.gov.hmcts.ccd.data.user.CachedUserRepository;
import uk.gov.hmcts.ccd.data.user.UserRepository;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.definition.CaseEvent;
import uk.gov.hmcts.ccd.domain.model.definition.CaseType;
import uk.gov.hmcts.ccd.domain.model.draft.Draft;
import uk.gov.hmcts.ccd.domain.model.std.AuditEvent;
import uk.gov.hmcts.reform.amlib.AccessManagementService;
import uk.gov.hmcts.reform.amlib.models.FilteredResourceEnvelope;
import uk.gov.hmcts.reform.amlib.models.Resource;
import uk.gov.hmcts.reform.amlib.models.ResourceDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Comparator.comparingInt;
import static uk.gov.hmcts.ccd.domain.service.common.SecurityClassificationUtils.caseHasClassificationEqualOrLowerThan;
import static uk.gov.hmcts.ccd.domain.service.common.SecurityClassificationUtils.getDataClassificationForData;
import static uk.gov.hmcts.ccd.domain.service.common.SecurityClassificationUtils.getSecurityClassification;
import static uk.gov.hmcts.reform.amlib.models.ResourceDefinition.builder;

@Service
public class AMSSecurityClassificationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference STRING_JSON_MAP = new TypeReference<HashMap<String, JsonNode>>() {
    };
    private static final JsonNodeFactory JSON_NODE_FACTORY = new JsonNodeFactory(false);

    private static final Logger LOG = LoggerFactory.getLogger(AMSSecurityClassificationService.class);

    private final UserRepository userRepository;
    private final AccessManagementService accessManagementService;
    private final CaseAccessService caseAccessService;

    @Autowired
    public AMSSecurityClassificationService(@Qualifier(CachedUserRepository.QUALIFIER) UserRepository userRepository,
                                            final AccessManagementService accessManagementService,
                                            final CaseAccessService caseAccessService) {
        this.userRepository = userRepository;
        this.accessManagementService = accessManagementService;
        this.caseAccessService = caseAccessService;
    }

    public Optional<SecurityClassification> getUserClassification(String jurisdictionId) {
        return userRepository.getUserClassifications(jurisdictionId)
            .stream()
            .max(comparingInt(SecurityClassification::getRank));
    }

    public Optional<CaseDetails> applyClassification(CaseDetails caseDetails) {
        Optional<SecurityClassification> userClassificationOpt = getUserClassification(caseDetails.getJurisdiction());
        Optional<CaseDetails> result = Optional.of(caseDetails);

        Set<String> userRoles = Sets.union(caseAccessService.getUserRoles(), getCaseRoles(caseDetails));

        // TODO: the data in resourceEnvelope should have the case fields filtered on security classification
        // (or completely removed if case type classification is higher)
        FilteredResourceEnvelope resourceEnvelope = accessManagementService.filterResource(
            userRepository.getUserId(),
            userRoles,
            Resource.builder()
                .id(caseDetails.getReferenceAsString())
                .definition(builder()
                    .serviceName(caseDetails.getJurisdiction())
                    .resourceType("CASE")
                    .resourceName(caseDetails.getCaseTypeId())
                    .build())
                .data(MAPPER.convertValue(caseDetails.getData(), JsonNode.class))
                .build());

        caseDetails.setData(
            MAPPER.convertValue(
                resourceEnvelope.getResource().getData(),
                STRING_JSON_MAP));

        return Optional.of(caseDetails);
    }

    public List<AuditEvent> applyClassification(String jurisdictionId, List<AuditEvent> events) {
        final Optional<SecurityClassification> userClassification = getUserClassification(jurisdictionId);

        if (null == events || !userClassification.isPresent()) {
            return newArrayList();
        }

        final ArrayList<AuditEvent> classifiedEvents = newArrayList();

        for (AuditEvent event : events) {
            if (userClassification.get().higherOrEqualTo(event.getSecurityClassification())) {
                classifiedEvents.add(event);
            }
        }

        return classifiedEvents;
    }

    private Set<String> getCaseRoles(CaseDetails caseDetails) {
        if (caseDetails == null || caseDetails.getId() == null || Draft.isDraft(caseDetails.getId())) {
            return Collections.emptySet();
        } else {
            return caseAccessService.getCaseRoles(caseDetails.getId());
        }
    }

}
