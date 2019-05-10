package uk.gov.hmcts.ccd.domain.service.common;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.amlib.enums.Permission;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static uk.gov.hmcts.reform.amlib.enums.Permission.CREATE;
import static uk.gov.hmcts.reform.amlib.enums.Permission.READ;

@Service
public class AMSAccessControlService {

    private static final JsonNodeFactory JSON_NODE_FACTORY = new JsonNodeFactory(false);
    private static final String FIELD_PREFIX = "/__field/";
    private static final String EVENT_PREFIX = "/__event/";
    private static final String STATE_PREFIX = "/__state/";
    private static final String ROOT = "";


    public String trimFieldPath(final Map.Entry<JsonPointer, Set<Permission>> jsonPointerSetEntry) {
        return jsonPointerSetEntry.getKey().toString().replace(FIELD_PREFIX, "");
    }

    public boolean isRoot(final Map.Entry<JsonPointer, Set<Permission>> jsonPointerSetEntry) {
        return jsonPointerSetEntry.getKey().toString().equals(ROOT);
    }

    public boolean pointsToAField(final Map.Entry<JsonPointer, Set<Permission>> jsonPointerSetEntry) {
        return jsonPointerSetEntry.getKey().toString().contains(FIELD_PREFIX);
    }

    public boolean pointsToState(final Map.Entry<JsonPointer, Set<Permission>> jsonPointerSetEntry, String stateId) {
        return jsonPointerSetEntry.getKey().toString().equals(STATE_PREFIX + stateId);
    }

    public boolean isReadable(final Map.Entry<JsonPointer, Set<Permission>> jsonPointerSetEntry) {
        return jsonPointerSetEntry.getValue().contains(READ);
    }

    public boolean isCreatable(final Map.Entry<JsonPointer, Set<Permission>> jsonPointerSetEntry) {
        return jsonPointerSetEntry.getValue().contains(CREATE);
    }

    public boolean pointsToEvent(final Map.Entry<JsonPointer, Set<Permission>> jsonPointerSetEntry, String eventId) {
        return jsonPointerSetEntry.getKey().toString().equals(EVENT_PREFIX + eventId);
    }

    public JsonNode filterCaseData(final JsonNode caseFields, final List<String> authorisedFieldKeys) {
        JsonNode filteredCaseFields = JSON_NODE_FACTORY.objectNode();
        if (caseFields != null) {
            final Iterator<String> fieldNames = caseFields.fieldNames();
            while (fieldNames.hasNext()) {
                final String fieldName = fieldNames.next();
                for (String caseFieldKey : authorisedFieldKeys) {
                    if (caseFieldKey.equals(fieldName)) {
                        ((ObjectNode) filteredCaseFields).set(fieldName, caseFields.get(fieldName));
                    }
                }
            }
        }
        return filteredCaseFields;
    }

}
