package uk.gov.hmcts.ccd.domain.model.std;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

@ToString
public class CaseAccess {

    private String id;
    private String reasonForAccess;

    public CaseAccess(
        @JsonProperty("id") String id,
        @JsonProperty("reason_for_access") String reasonForAccess
    ) {
        this.id = id;
        this.reasonForAccess = reasonForAccess;
    }

    public String getId() {
        return id;
    }

    public String getReasonForAccess() {
        return reasonForAccess;
    }
}
