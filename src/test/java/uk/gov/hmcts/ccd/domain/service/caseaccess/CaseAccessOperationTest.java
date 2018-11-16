package uk.gov.hmcts.ccd.domain.service.caseaccess;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.ccd.data.caseaccess.CaseRoleRepository;
import uk.gov.hmcts.ccd.data.caseaccess.CaseUserRepository;
import uk.gov.hmcts.ccd.data.casedetails.CaseDetailsRepository;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.service.getcase.CaseNotFoundException;
import uk.gov.hmcts.ccd.endpoint.exceptions.InvalidCaseRoleException;
import uk.gov.hmcts.ccd.v2.external.domain.CaseUser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CaseAccessOperationTest {

    private static final String JURISDICTION = "CMC";
    private static final String WRONG_JURISDICTION = "DIVORCE";
    private static final String CASE_TYPE_ID = "Application";
    private static final Long CASE_REFERENCE = 1234123412341236L;
    private static final String USER_ID = "123";
    private static final Long CASE_ID = 456L;
    private static final Long CASE_NOT_FOUND = 9999999999999999L;
    private static final String NOT_CASE_ROLE = "NotACaseRole";
    private static final String CASE_ROLE = "[DEFENDANT]";
    private static final String CASE_ROLE_OTHER = "[OTHER]";

    @Mock
    private CaseDetailsRepository caseDetailsRepository;

    @Mock
    private CaseUserRepository caseUserRepository;

    @Mock
    private CaseRoleRepository caseRoleRepository;

    @InjectMocks
    private CaseAccessOperation caseAccessOperation;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        configureCaseRepository();
        configureCaseRoleRepository();
    }

    @Nested
    @DisplayName("grantAccess()")
    class GrantAccess {

        @Test
        @DisplayName("should grant access to user")
        void shouldGrantAccess() {
            caseAccessOperation.grantAccess(JURISDICTION, CASE_REFERENCE.toString(), USER_ID);

            assertAll(
                () -> verify(caseDetailsRepository).findByReference(JURISDICTION, CASE_REFERENCE),
                () -> verify(caseUserRepository).grantAccess(CASE_ID, USER_ID)
            );
        }

        @Test
        @DisplayName("should throw not found exception when reference not found")
        void shouldThrowNotFound() {
            assertAll(
                () -> assertThrows(CaseNotFoundException.class, () -> {
                    caseAccessOperation.grantAccess(JURISDICTION, CASE_NOT_FOUND.toString(), USER_ID);
                }),
                () -> verify(caseDetailsRepository).findByReference(JURISDICTION, CASE_NOT_FOUND),
                () -> verify(caseUserRepository, never()).grantAccess(CASE_ID, USER_ID)
            );
        }

        @Test
        @DisplayName("should throw not found exception when reference in different jurisdiction")
        void shouldHandleWrongJurisdiction() {
            assertAll(
                () -> assertThrows(CaseNotFoundException.class, () -> {
                    caseAccessOperation.grantAccess(WRONG_JURISDICTION, CASE_REFERENCE.toString(), USER_ID);
                }),
                () -> verify(caseDetailsRepository).findByReference(WRONG_JURISDICTION, CASE_REFERENCE),
                () -> verify(caseUserRepository, never()).grantAccess(CASE_ID, USER_ID)
            );
        }
    }

    @Nested()
    @DisplayName("grantAccess(reference, caseUser)")
    class GrantAccessCaseUser {
        private CaseDetails caseDetails;

        @BeforeEach
        void setUp() {
            caseDetails = new CaseDetails();
            caseDetails.setId(CASE_ID.toString());
            caseDetails.setCaseTypeId(CASE_TYPE_ID);
        }

        @Test
        @DisplayName("should reject granting when case roles doesn't exist")
        void shouldRejectWhenUnknownCaseRoles() {
            final CaseUser caseUser = new CaseUser();
            caseUser.setUserId(USER_ID);
            caseUser.getCaseRoles().add(NOT_CASE_ROLE);

            assertThrows(InvalidCaseRoleException.class, ()-> caseAccessOperation.grant(caseDetails, caseUser));
            verifyZeroInteractions(caseUserRepository);
        }

        @Test
        @DisplayName("should grant access when case role valid")
        void shouldGrantAccessForCaseRole() {
            final CaseUser caseUser = new CaseUser();
            caseUser.setUserId(USER_ID);
            caseUser.getCaseRoles().add(CASE_ROLE);

            caseAccessOperation.grant(caseDetails, caseUser);

            verify(caseUserRepository).grantAccess(CASE_ID.toString(), USER_ID, CASE_ROLE);
        }
    }

    @Nested
    @DisplayName("revokeAccess()")
    class RevokeAccess {

        @Test
        @DisplayName("should revoke access to user")
        void shouldRevokeAccess() {
            caseAccessOperation.revokeAccess(JURISDICTION, CASE_REFERENCE.toString(), USER_ID);

            assertAll(
                () -> verify(caseDetailsRepository).findByReference(JURISDICTION, CASE_REFERENCE),
                () -> verify(caseUserRepository).revokeAccess(CASE_ID, USER_ID)
            );
        }

        @Test
        @DisplayName("should throw not found exception when reference not found")
        void shouldThrowNotFound() {
            assertAll(
                () -> assertThrows(CaseNotFoundException.class, () -> {
                    caseAccessOperation.revokeAccess(JURISDICTION, CASE_NOT_FOUND.toString(), USER_ID);
                }),
                () -> verify(caseDetailsRepository).findByReference(JURISDICTION, CASE_NOT_FOUND),
                () -> verify(caseUserRepository, never()).revokeAccess(CASE_ID, USER_ID)
            );
        }

        @Test
        @DisplayName("should throw not found exception when reference in different jurisdiction")
        void shouldHandleWrongJurisdiction() {
            assertAll(
                () -> assertThrows(CaseNotFoundException.class, () -> {
                    caseAccessOperation.revokeAccess(WRONG_JURISDICTION, CASE_REFERENCE.toString(), USER_ID);
                }),
                () -> verify(caseDetailsRepository).findByReference(WRONG_JURISDICTION, CASE_REFERENCE),
                () -> verify(caseUserRepository, never()).revokeAccess(CASE_ID, USER_ID)
            );
        }
    }

    private void configureCaseRepository() {
        final CaseDetails caseDetails = new CaseDetails();
        caseDetails.setId(String.valueOf(CASE_ID));
        caseDetails.setReference(CASE_REFERENCE);

        doReturn(Optional.of(caseDetails)).when(caseDetailsRepository)
                                          .findByReference(JURISDICTION, CASE_REFERENCE);
        doReturn(Optional.empty()).when(caseDetailsRepository)
                                  .findByReference(JURISDICTION, CASE_NOT_FOUND);
        doReturn(Optional.empty()).when(caseDetailsRepository)
                                  .findByReference(WRONG_JURISDICTION, CASE_REFERENCE);
    }

    private void configureCaseRoleRepository() {
        when(caseRoleRepository.getCaseRoles(CASE_TYPE_ID)).thenReturn(Sets.newHashSet(CASE_ROLE, CASE_ROLE_OTHER));
    }

}
