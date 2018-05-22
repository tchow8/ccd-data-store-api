package uk.gov.hmcts.ccd.data.caseaccess;

import uk.gov.hmcts.ccd.domain.model.std.CaseAccess;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

@Named
@Singleton
public class CaseUserRepository {

    private final CaseUserAuditRepository auditRepo;

    @PersistenceContext
    private EntityManager em;

    @Inject
    public CaseUserRepository(CaseUserAuditRepository caseUserAuditRepository) {
        this.auditRepo = caseUserAuditRepository;
    }

    public void grantAccess(final Long caseId, final String userId, final String reasonForAccess) {
        em.merge(new CaseUserEntity(caseId, userId, reasonForAccess));
        auditRepo.auditGrant(caseId, userId);
    }

    public void revokeAccess(final Long caseId, final String userId) {
        CaseUserEntity primaryKey = new CaseUserEntity(caseId, userId);
        CaseUserEntity caseUser = em.find(CaseUserEntity.class, primaryKey.getCasePrimaryKey());

        if (caseUser != null) {
            em.remove(caseUser);
            auditRepo.auditRevoke(caseId, userId);
        }
    }

    public List<Long> findCasesUserIdHasAccessTo(final String userId) {
        TypedQuery<Long> namedQuery = em.createNamedQuery(CaseUserEntity.GET_ALL_CASES_USER_HAS_ACCESS_TO, Long.class);
        namedQuery.setParameter("userId", userId);

        return namedQuery.getResultList();
    }

    public List<CaseAccess> findUsersWhoHaveAccessToCase(final Long caseDbId) {
        TypedQuery<CaseUserEntity> namedQuery = em.createNamedQuery(
            CaseUserEntity.GET_ALL_CASES_USER_HAS_ACCESS_TO,
            CaseUserEntity.class
        );
        namedQuery.setParameter("caseDbId", caseDbId);

        return namedQuery.getResultList()
            .stream()
            .map(dbEntity -> new CaseAccess(dbEntity.getCasePrimaryKey().getUserId(), dbEntity.getReasonForAccess()))
            .collect(Collectors.toList());
    }
}
