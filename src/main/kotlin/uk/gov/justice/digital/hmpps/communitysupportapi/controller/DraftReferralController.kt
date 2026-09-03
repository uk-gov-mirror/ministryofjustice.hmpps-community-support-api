package uk.gov.justice.digital.hmpps.communitysupportapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.communitysupportapi.authorization.UserMapper
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.AdditionalInformationForTheDeliveryPartnerBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.AdditionalSupportNeedsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.AreaConfirmationBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.CheckDraftReferralDetailsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.CommunityServiceProviderBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.NeedsInterpreterBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.OffenceSentenceInfoBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ProbationPractitionerDetailsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralCriminogenicNeedsDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SelectionDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.TaskListStatusResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.model.AdditionalSupportNeedsRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CommunityServiceProviderRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CriminogenicNeedsRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.NeedsInterpreterRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.UpdateOffenceSentenceRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.UpdateProbationPractitionerDetailsRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.service.CriminogenicNeedsService
import uk.gov.justice.digital.hmpps.communitysupportapi.service.DraftReferralService
import uk.gov.justice.digital.hmpps.communitysupportapi.service.ReferralService
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.OffsetDateTime
import java.util.UUID

@RestController
@PreAuthorize("hasAnyRole('ROLE_IPB_FRONTEND_RW')")
class DraftReferralController(
  private val draftReferralService: DraftReferralService,
  private val referralService: ReferralService,
  private val criminogenicNeedsService: CriminogenicNeedsService,
  private val userMapper: UserMapper,
  private val authenticationHolder: HmppsAuthenticationHolder,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Operation(summary = "Get check draft referral details page data")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Check draft referral details found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = CheckDraftReferralDetailsBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/bff/draft-referral/check-draft-referral-details/{referralId}")
  fun getCheckDraftReferralDetails(@PathVariable referralId: UUID): ResponseEntity<CheckDraftReferralDetailsBffResponseDto> = ResponseEntity.ok(referralService.getCheckDraftReferralDetailsPage(referralId))

  @Operation(summary = "Get additional support needs page data")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Additional support needs data found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = AdditionalSupportNeedsBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral, or the Referral's Person, not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/bff/draft-referral/additional-support-needs/{referralId}")
  fun getAdditionalSupportNeedsPage(
    @PathVariable referralId: String,
  ): ResponseEntity<AdditionalSupportNeedsBffResponseDto> = ResponseEntity.ok(draftReferralService.getAdditionalSupportNeedsForReferral(referralId))

  @Operation(summary = "Update the Additional Support Needs information for a Draft Referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Additional support needs information updated",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = AdditionalSupportNeedsBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @PatchMapping("/draft-referral/additional-support-needs/{referralId}")
  fun updateAdditionalSupportNeeds(
    @PathVariable referralId: UUID,
    @RequestBody request: AdditionalSupportNeedsRequest,
  ): ResponseEntity<AdditionalSupportNeedsBffResponseDto> {
    val user = userMapper.fromToken(authenticationHolder)

    return ResponseEntity.ok(draftReferralService.upsertAdditionalSupportNeeds(referralId, user.id, request))
  }

  @Operation(summary = "Get interpreter needs page data")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Interpreter needs data found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = NeedsInterpreterBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral, or the Referral's Person, not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/bff/draft-referral/needs-interpreter/{referralId}")
  fun getNeedsInterpreterPage(
    @PathVariable referralId: String,
  ): ResponseEntity<NeedsInterpreterBffResponseDto> = ResponseEntity.ok(draftReferralService.getInterpreterNeedsForReferral(referralId))

  @Operation(summary = "Update the Interpreter Needs information for a Draft Referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Interpreter needs information updated",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = NeedsInterpreterBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @PatchMapping("/draft-referral/needs-interpreter/{referralId}")
  fun updateNeedsInterpreter(
    @PathVariable referralId: UUID,
    @RequestBody request: NeedsInterpreterRequest,
  ): ResponseEntity<NeedsInterpreterBffResponseDto> {
    val user = userMapper.fromToken(authenticationHolder)
    println("updateNeedsInterpreter - request = $request")
    return ResponseEntity.ok(draftReferralService.upsertNeedsInterpreter(referralId, user.id, request))
  }

  @Operation(summary = "Get area confirmation details for a community service provider by ID")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Community service provider found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = AreaConfirmationBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Community service provider not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/bff/draft-referral/{referralId}/community-service-provider/{providerId}")
  fun getAreaConfirmationDetails(
    @PathVariable referralId: UUID,
    @PathVariable providerId: UUID,
  ): ResponseEntity<AreaConfirmationBffResponseDto> = ResponseEntity.ok(
    draftReferralService.getAreaConfirmationDetails(referralId, providerId),
  )

  @Operation(summary = "Update the Community Service Provider for a Draft Referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Community Service Provider updated",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = CommunityServiceProviderBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral, or the Community Service Provider, not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @PatchMapping("/draft-referral/community-service-provider/{referralId}")
  fun updateCommunityServiceProvider(
    @PathVariable referralId: UUID,
    @RequestBody request: CommunityServiceProviderRequest,
  ): ResponseEntity<CommunityServiceProviderBffResponseDto> = ResponseEntity.ok(draftReferralService.upsertCommunityServiceProvider(referralId, request))

  @Operation(summary = "Get task list status")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Task list status found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = TaskListStatusResponseDto::class))],
      ),
    ],
  )
  @GetMapping("/bff/task-list-status/{referralId}")
  fun getTaskListStatus(@PathVariable referralId: UUID): ResponseEntity<TaskListStatusResponseDto> = ResponseEntity.ok(draftReferralService.getTaskListStatus(referralId))

  @Operation(summary = "Update criminogenic needs for a referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Criminogenic needs information updated",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ReferralCriminogenicNeedsDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @PatchMapping("/draft-referral/person-needs/{referralId}")
  fun updateCriminogenicNeeds(
    @PathVariable referralId: UUID,
    @RequestBody request: CriminogenicNeedsRequest,
  ): ResponseEntity<ReferralCriminogenicNeedsDto> {
    log.info("Attempt to update criminogenic needs for referral: {}", referralId)
    val user = userMapper.fromToken(authenticationHolder)

    return ResponseEntity.ok(criminogenicNeedsService.upsertCriminogenicNeeds(referralId, user.id, request))
  }

  @Operation(summary = "Get criminogenic needs for a referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Referral criminogenic needs data found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ReferralCriminogenicNeedsDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral criminogenic needs data not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/bff/draft-referral/person-needs/{referralId}")
  fun getPersonCriminogenicNeeds(
    @PathVariable referralId: UUID,
  ): ResponseEntity<ReferralCriminogenicNeedsDto> {
    log.info("Attempt to get criminogenic needs for referral: {}", referralId)
    return ResponseEntity.ok(criminogenicNeedsService.getCriminogenicNeeds(referralId))
  }

  @Operation(summary = "Get Offence and Sentence information for a draft referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Offence and Sentence information found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = OffenceSentenceInfoBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Offence and Sentence information not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/bff/draft-referral/{referralId}/offence-sentence")
  fun getOffenceSentenceDetails(
    @PathVariable referralId: UUID,
  ): ResponseEntity<OffenceSentenceInfoBffResponseDto> = ResponseEntity.ok(
    draftReferralService.getOffenceSentenceDetails(referralId),
  )

  @Operation(summary = "Get additional information for the delivery partner for a draft referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "additional information found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = AdditionalInformationForTheDeliveryPartnerBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "additional information not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/bff/draft-referral/additional-information-for-the-delivery-partner/{referralId}")
  fun getAdditionalInformationForTheDeliveryPartner(
    @PathVariable referralId: UUID,
  ): ResponseEntity<AdditionalInformationForTheDeliveryPartnerBffResponseDto> = ResponseEntity.ok(
    draftReferralService.getAdditionalInformationForTheDeliveryPartner(referralId),
  )

  @Operation(summary = "Update additional information for the delivery partner for a referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Additional information for the delivery partner updated",
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @PatchMapping("/draft-referral/additional-information-for-the-delivery-partner/{referralId}")
  fun updateAdditionalInformationForTheDeliveryPartner(
    @PathVariable referralId: UUID,
    @RequestBody request: SelectionDto,
  ): ResponseEntity<AdditionalInformationForTheDeliveryPartnerBffResponseDto> = ResponseEntity.ok(
    draftReferralService.updateAdditionalInformationForTheDeliveryPartner(referralId, request, OffsetDateTime.now()),
  )

  @Operation(summary = "Update the Offence and Sentence information for a Draft Referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Offence and Sentence information updated",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = OffenceSentenceInfoBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @PatchMapping("/draft-referral/{referralId}/offence-sentence")
  fun updateOffenceSentenceDetails(
    @PathVariable referralId: UUID,
    @RequestBody request: UpdateOffenceSentenceRequest,
  ): ResponseEntity<OffenceSentenceInfoBffResponseDto> {
    val user = userMapper.fromToken(authenticationHolder)

    return ResponseEntity.ok(draftReferralService.upsertOffenceSentenceDetails(referralId, user.id, request))
  }

  @Operation(summary = "Get Probation Practitioner details for a draft referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Probation Practitioner details found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ProbationPractitionerDetailsBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral, or the Referral's Person, not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/bff/draft-referral/{referralId}/probation-practitioner-details")
  fun getProbationPractitionerDetails(
    @PathVariable referralId: UUID,
  ): ResponseEntity<ProbationPractitionerDetailsBffResponseDto> = ResponseEntity.ok(
    draftReferralService.getProbationPractitionerDetails(referralId),
  )

  @Operation(summary = "Save the Probation Practitioner details for a Draft Referral")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Probation Practitioner details saved",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ProbationPractitionerDetailsBffResponseDto::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Referral not found",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @PatchMapping("/draft-referral/{referralId}/probation-practitioner-details")
  fun updateProbationPractitionerDetails(
    @PathVariable referralId: UUID,
    @RequestBody request: UpdateProbationPractitionerDetailsRequest,
  ): ResponseEntity<ProbationPractitionerDetailsBffResponseDto> {
    val user = userMapper.fromToken(authenticationHolder)

    return ResponseEntity.ok(draftReferralService.upsertProbationPractitionerDetails(referralId, user.id, request))
  }
}
