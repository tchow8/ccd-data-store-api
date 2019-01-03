package uk.gov.hmcts.ccd.v2.internal.controller;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.gov.hmcts.ccd.data.draft.CachedDraftGateway;
import uk.gov.hmcts.ccd.data.draft.DraftGateway;
import uk.gov.hmcts.ccd.domain.model.aggregated.CaseView;
import uk.gov.hmcts.ccd.domain.model.draft.DraftResponse;
import uk.gov.hmcts.ccd.domain.model.std.CaseDataContent;
import uk.gov.hmcts.ccd.domain.service.aggregated.DefaultGetCaseViewFromDraftOperation;
import uk.gov.hmcts.ccd.domain.service.aggregated.GetCaseViewOperation;
import uk.gov.hmcts.ccd.domain.service.upsertdraft.UpsertDraftOperation;
import uk.gov.hmcts.ccd.v2.V2;

import javax.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;

import static org.springframework.http.ResponseEntity.status;

@RestController
@RequestMapping(path = "/internal")
public class UIDraftsController {
    private static final Logger LOG = LoggerFactory.getLogger(UIDraftsController.class);

    private final UpsertDraftOperation upsertDraftOperation;
    private final GetCaseViewOperation getDraftViewOperation;
    private final DraftGateway draftGateway;

    @Autowired
    public UIDraftsController(
        @Qualifier("default") final UpsertDraftOperation upsertDraftOperation,
        @Qualifier(DefaultGetCaseViewFromDraftOperation.QUALIFIER) GetCaseViewOperation getDraftViewOperation,
        @Qualifier(CachedDraftGateway.QUALIFIER) DraftGateway draftGateway
    ) {
        this.upsertDraftOperation = upsertDraftOperation;
        this.getDraftViewOperation = getDraftViewOperation;
        this.draftGateway = draftGateway;
    }

    @PostMapping(
        path = "/case-types/{ctid}/drafts",
        headers = {
            V2.EXPERIMENTAL_HEADER
        },
        produces = {
            V2.MediaType.UI_DRAFT_CREATE
        }
    )
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation(
        value = "Save draft as a caseworker."
    )
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Draft created"),
        @ApiResponse(code = 422, message = "Bad request")
    })
    public ResponseEntity<DraftResponse> saveDraft(
        @ApiParam(value = "Case type ID", required = true)
        @PathVariable("ctid") final String caseTypeId,
        @RequestBody final CaseDataContent caseDataContent) {

        ResponseEntity.BodyBuilder builder = status(HttpStatus.CREATED);
        return builder.body(upsertDraftOperation.executeSave(caseTypeId, caseDataContent));
    }

    @PutMapping(
        path = "/case-types/{ctid}/drafts/{did}",
        headers = {
            V2.EXPERIMENTAL_HEADER
        },
        produces = {
            V2.MediaType.UI_DRAFT_UPDATE
        }
    )
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(
        value = "Update draft as a caseworker."
    )
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Draft updated"),
        @ApiResponse(code = 400, message = "Bad request")
    })
    public ResponseEntity<DraftResponse> updateDraft(
        @PathVariable("ctid") final String caseTypeId,
        @PathVariable("did") final String draftId,
        @RequestBody final CaseDataContent caseDataContent) {

        return ResponseEntity.ok(upsertDraftOperation.executeUpdate(caseTypeId, draftId, caseDataContent));
    }

    @Transactional
    @GetMapping(
        path = "/drafts/{did}",
        headers = {
            V2.EXPERIMENTAL_HEADER
        },
        produces = {
            V2.MediaType.UI_DRAFT_READ
        })
    @ApiOperation(value = "Fetch a draft for display")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "A displayable draft")
    })
    public ResponseEntity<CaseView> findDraft(@PathVariable("did") final String did) {
        Instant start = Instant.now();
        CaseView caseView = getDraftViewOperation.execute(did);
        final Duration between = Duration.between(start, Instant.now());
        LOG.info("findDraft has been completed in {} millisecs...", between.toMillis());
        return ResponseEntity.ok(caseView);
    }

    @Transactional
    @DeleteMapping(path = "/drafts/{did}",
        headers = {
            V2.EXPERIMENTAL_HEADER
        },
        produces = {
            V2.MediaType.UI_DRAFT_DELETE
        })
    @ApiOperation(value = "Delete a given draft")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "A draft deleted successfully")
    })
    public ResponseEntity<Void> deleteDraft(@PathVariable("did") final String did) {
        Instant start = Instant.now();
        draftGateway.delete(did);
        final Duration between = Duration.between(start, Instant.now());
        LOG.info("deleteDraft has been completed in {} millisecs...", between.toMillis());
        return ResponseEntity.ok().build();
    }
}
