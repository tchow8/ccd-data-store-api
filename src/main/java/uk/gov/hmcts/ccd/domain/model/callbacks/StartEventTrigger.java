package uk.gov.hmcts.ccd.domain.model.callbacks;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;

import java.util.Map;

public class StartEventTrigger {
    @JsonProperty("case_details")
    private CaseDetails caseDetails;
    @JsonProperty("event_id")
    private String eventId;
    private String token;

    public CaseDetails getCaseDetails() {
        return caseDetails;
    }

    public void setCaseDetails(CaseDetails caseDetails) {
        this.caseDetails = caseDetails;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @JsonIgnore
    public String getJurisdictionId() {
        return caseDetails.getJurisdiction();
    }

    @JsonIgnore
    public String getCaseTypeId() {
        return caseDetails.getCaseTypeId();
    }

    @JsonIgnore
    public String getCaseReference() {
        return caseDetails.getReferenceAsString();
    }

    @JsonIgnore
    public Map<String, JsonNode> getData() {
        return caseDetails.getData();
    }
}
