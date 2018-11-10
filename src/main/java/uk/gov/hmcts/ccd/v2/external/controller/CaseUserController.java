package uk.gov.hmcts.ccd.v2.external.controller;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.ccd.v2.V2;
import uk.gov.hmcts.ccd.v2.external.domain.CaseUser;

@RestController
@RequestMapping(path = "/cases/{caseReference}/users")
public class CaseUserController {

    @PutMapping(
        path = "/{userId}",
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ApiOperation(
        value = "Grant user access to a case with a case role"
    )
    @ApiResponses({
        @ApiResponse(
            code = 204,
            message = "Access granted"
        ),
        @ApiResponse(
            code = 400,
            message = V2.Error.CASE_ID_INVALID
        ),
        @ApiResponse(
            code = 400,
            message = V2.Error.CASE_ROLE_REQUIRED
        ),
        @ApiResponse(
            code = 400,
            message = V2.Error.CASE_ROLE_INVALID
        ),
        @ApiResponse(
            code = 404,
            message = V2.Error.CASE_NOT_FOUND
        )
    })
    public ResponseEntity<Void> putUser(
        @PathVariable("caseReference") String caseReference,
        @PathVariable("userId") String userId,
        @RequestBody CaseUser caseUser
    ) {

        caseUser.setUserId(userId);

        // Validate case ID
        // Find case
        // Validate case roles
        // Save

        return ResponseEntity.noContent()
                             .build();
    }

}
