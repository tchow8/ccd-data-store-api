package uk.gov.hmcts.ccd.data.caseaccess;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@Entity
@Table(name = "case_users")
@NamedQueries({@NamedQuery(name = CaseUserEntity.GET_ALL_CASES_USER_HAS_ACCESS_TO,
    query = "SELECT casePrimaryKey.caseDataId from CaseUserEntity where casePrimaryKey.userId = :userId"),
    @NamedQuery(name = CaseUserEntity.GET_ALL_USERS_ON_A_CASE,
        query = "SELECT casePrimaryKey.userId, reasonForAccess FROM CaseUserEntity where casePrimaryKey.caseDataId = :caseDbId")
}
)
public class CaseUserEntity implements Serializable {

    static final String GET_ALL_CASES_USER_HAS_ACCESS_TO = "GET_ALL_CASES";
    static final String GET_ALL_USERS_ON_A_CASE = "GET_USERS_ON_CASE";

    public static class CasePrimaryKey implements Serializable {
        @Column(name = "case_data_id")
        private Long caseDataId;
        @Column(name = "user_id")
        private String userId;

        public Long getCaseDataId() {
            return caseDataId;
        }

        public void setCaseDataId(Long caseDataId) {
            this.caseDataId = caseDataId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }

    @EmbeddedId
    private CasePrimaryKey casePrimaryKey;

    @Column(name = "reason_for_access")
    private String reasonForAccess;

    public CaseUserEntity() {
        // needed for hibernate
    }

    CaseUserEntity(Long caseDataId, String userId) {
        this(caseDataId, userId, null);
    }

    CaseUserEntity(Long caseDataId, String userId, String reasonForAccess) {
        CasePrimaryKey casePrimaryKey = new CasePrimaryKey();
        casePrimaryKey.caseDataId = caseDataId;
        casePrimaryKey.userId = userId;

        this.casePrimaryKey = casePrimaryKey;
        this.reasonForAccess = reasonForAccess;
    }

    public CasePrimaryKey getCasePrimaryKey() {
        return casePrimaryKey;
    }

    public void setCasePrimaryKey(CasePrimaryKey casePrimaryKey) {
        this.casePrimaryKey = casePrimaryKey;
    }

    public String getReasonForAccess() {
        return reasonForAccess;
    }

    public void setReasonForAccess(String reasonForAccess) {
        this.reasonForAccess = reasonForAccess;
    }
}
