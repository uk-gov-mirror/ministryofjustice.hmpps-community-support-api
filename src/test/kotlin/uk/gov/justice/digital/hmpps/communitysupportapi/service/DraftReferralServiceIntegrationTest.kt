package uk.gov.justice.digital.hmpps.communitysupportapi.service

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SelectionDto
import uk.gov.justice.digital.hmpps.communitysupportapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.ReferralTestSupport
import uk.gov.justice.digital.hmpps.communitysupportapi.model.AdditionalSupportNeedsRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CommunityServiceProviderRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.NeedsInterpreterRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.CommunityServiceProviderRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PersonAdditionalSupportNeedsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralProviderAssignmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.createCprProbationPersonDto
import uk.gov.justice.digital.hmpps.communitysupportapi.util.toJson
import java.time.OffsetDateTime
import java.util.*

class DraftReferralServiceIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var referralService: ReferralService

  @Autowired
  private lateinit var draftReferralService: DraftReferralService

  @Autowired
  private lateinit var referralHelper: ReferralTestSupport

  @Autowired
  private lateinit var personAdditionSupportNeedsRepository: PersonAdditionalSupportNeedsRepository

  @Autowired
  private lateinit var communityServiceProviderRepository: CommunityServiceProviderRepository

  @Autowired
  private lateinit var referralProviderAssignmentRepository: ReferralProviderAssignmentRepository

  @Test
  fun `update additional information should be saved`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral

    val supportNeeds = AdditionalSupportNeedsRequest(
      employmentResponsibilities = "Test employment responsibilities",
      caringResponsibilities = "Test caring responsibilities",
      needsAdditionalSupport = true,
    )

    val updatedResult = draftReferralService.upsertAdditionalSupportNeeds(
      savedReferral.id,
      referralUser.id,
      supportNeeds,
    )
    assertThat(updatedResult).isNotNull()

    val savedSupportNeeds = personAdditionSupportNeedsRepository.findByReferralId(savedReferral.id)
    assertThat(savedSupportNeeds).isNotNull()
    assertThat(savedSupportNeeds?.referralId).isEqualTo(savedReferral.id)
    assertThat(savedSupportNeeds?.personId).isEqualTo(savedReferral.personId)
    assertThat(savedSupportNeeds?.caringResponsibilitiesDetails).isEqualTo("Test caring responsibilities")
    assertThat(savedSupportNeeds?.additionalSupportNeeded).isTrue()
    assertThat(savedSupportNeeds?.physicalHealthDetails).isNull()
    assertThat(savedSupportNeeds?.mentalEmotionalHealthDetails).isNull()
    assertThat(savedSupportNeeds?.diversityDetails).isNull()
    assertThat(savedSupportNeeds?.employmentResponsibilitiesDetails).isEqualTo("Test employment responsibilities")
    assertThat(savedSupportNeeds?.locationTravelDetails).isNull()
    assertThat(savedSupportNeeds?.neurodiversityDetails).isNull()
    assertThat(savedSupportNeeds?.anythingElseDetails).isNull()
    assertThat(savedSupportNeeds?.interpreterNeeded).isNull()
    assertThat(savedSupportNeeds?.interpreterLanguage).isNull()
    assertThat(savedSupportNeeds?.createdBy).isEqualTo(referralUser.id)
  }

  @Test
  fun `update interpreter needs should be saved`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral

    val interpreterNeeds = NeedsInterpreterRequest(
      needsInterpreter = true,
      language = "Spanish",
    )

    val updatedResult = draftReferralService.upsertNeedsInterpreter(
      savedReferral.id,
      referralUser.id,
      interpreterNeeds,
    )
    assertThat(updatedResult).isNotNull()

    val savedInterpreterNeeds = personAdditionSupportNeedsRepository.findByReferralId(savedReferral.id)
    assertThat(savedInterpreterNeeds).isNotNull()
    assertThat(savedInterpreterNeeds?.referralId).isEqualTo(savedReferral.id)
    assertThat(savedInterpreterNeeds?.personId).isEqualTo(savedReferral.personId)
    assertThat(savedInterpreterNeeds?.interpreterLanguage).isEqualTo("Spanish")
    assertThat(savedInterpreterNeeds?.interpreterNeeded).isTrue()
    assertThat(savedInterpreterNeeds?.createdBy).isEqualTo(referralUser.id)
  }

  fun `update additional information should be saved without clearing needs interpreter information`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral

    val interpreterNeeds = NeedsInterpreterRequest(
      needsInterpreter = true,
      language = "Spanish",
    )

    val updatedNeedsInterpreterResult = draftReferralService.upsertNeedsInterpreter(
      savedReferral.id,
      referralUser.id,
      interpreterNeeds,
    )
    assertThat(updatedNeedsInterpreterResult).isNotNull()

    val supportNeeds = AdditionalSupportNeedsRequest(
      employmentResponsibilities = "Test employment responsibilities",
      caringResponsibilities = "Test caring responsibilities",
      needsAdditionalSupport = true,
    )

    val updatedResult = draftReferralService.upsertAdditionalSupportNeeds(
      savedReferral.id,
      referralUser.id,
      supportNeeds,
    )
    assertThat(updatedResult).isNotNull()

    val savedResult = personAdditionSupportNeedsRepository.findByReferralId(savedReferral.id)
    assertThat(savedResult).isNotNull()
    assertThat(savedResult?.referralId).isEqualTo(savedReferral.id)
    assertThat(savedResult?.personId).isEqualTo(savedReferral.personId)
    assertThat(savedResult?.caringResponsibilitiesDetails).isEqualTo("Test caring responsibilities")
    assertThat(savedResult?.additionalSupportNeeded).isTrue()
    assertThat(savedResult?.physicalHealthDetails).isNull()
    assertThat(savedResult?.mentalEmotionalHealthDetails).isNull()
    assertThat(savedResult?.diversityDetails).isNull()
    assertThat(savedResult?.employmentResponsibilitiesDetails).isEqualTo("Test employment responsibilities")
    assertThat(savedResult?.locationTravelDetails).isNull()
    assertThat(savedResult?.neurodiversityDetails).isNull()
    assertThat(savedResult?.anythingElseDetails).isNull()
    assertThat(savedResult?.interpreterLanguage).isEqualTo("Spanish")
    assertThat(savedResult?.interpreterNeeded).isTrue()
    assertThat(savedResult?.createdBy).isEqualTo(referralUser.id)
  }

  @Test
  fun `update community service provider should be saved`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()
    val communityServiceProvider = referralHelper.getCommunityServiceProvider()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral

    val newCommunityServiceProvider = communityServiceProviderRepository.findAll()
      .first { it.id != communityServiceProvider.id }

    val request = CommunityServiceProviderRequest(
      communityServiceProviderId = newCommunityServiceProvider.id,
    )

    val updatedResult = draftReferralService.upsertCommunityServiceProvider(savedReferral.id, request)
    assertThat(updatedResult).isNotNull()
    assertThat(updatedResult.communityServiceProviderId).isEqualTo(newCommunityServiceProvider.id)
    assertThat(updatedResult.communityServiceProviderName).isEqualTo(newCommunityServiceProvider.name)

    val assignments = referralProviderAssignmentRepository.findByReferralId(savedReferral.id)
    assertThat(assignments).hasSize(1)
    assertThat(assignments.first().communityServiceProvider.id).isEqualTo(newCommunityServiceProvider.id)
  }

  @Test
  fun `update community service provider should throw not found for unknown referral`() {
    val unknownCommunityServiceProvider = referralHelper.getCommunityServiceProvider()

    val request = CommunityServiceProviderRequest(
      communityServiceProviderId = unknownCommunityServiceProvider.id,
    )

    assertThatThrownBy { draftReferralService.upsertCommunityServiceProvider(UUID.randomUUID(), request) }
      .isInstanceOf(NotFoundException::class.java)
  }

  @Test
  fun `update community service provider should throw not found for unknown community service provider`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral

    val request = CommunityServiceProviderRequest(
      communityServiceProviderId = UUID.randomUUID(),
    )

    assertThatThrownBy { draftReferralService.upsertCommunityServiceProvider(savedReferral.id, request) }
      .isInstanceOf(NotFoundException::class.java)
  }

  @Test
  fun getAdditionalInformationForTheDeliveryPartnerSmokeTest() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral
    // create the selection to the database
    run {
      val request = SelectionDto.Yes("extra information for delivery partner")
      val result = draftReferralService.updateAdditionalInformationForTheDeliveryPartner(
        savedReferral.id,
        request,
        OffsetDateTime.now(),
      )
      assertThat(result.details).isEqualTo(SelectionDto.Yes("extra information for delivery partner"))
    }
    // retrieve the selection from the database
    run {
      val result = draftReferralService.getAdditionalInformationForTheDeliveryPartner(savedReferral.id)
      assertThat(result.details).isEqualTo(SelectionDto.Yes("extra information for delivery partner"))
    }
    // update the selection in the database
    run {
      val request = SelectionDto.No
      val result = draftReferralService.updateAdditionalInformationForTheDeliveryPartner(
        savedReferral.id,
        request,
        OffsetDateTime.now(),
      )
      assertThat(result.details).isEqualTo(SelectionDto.No)
    }
    // check the selection in the database
    run {
      val result = draftReferralService.getAdditionalInformationForTheDeliveryPartner(savedReferral.id)
      assertThat(result.details).isEqualTo(SelectionDto.No)
    }
  }

  private fun setUpData(): CreateReferralRequest {
    val crn = "X123456"
    stubFor(
      get(urlPathEqualTo("/person/probation/$crn"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(createCprProbationPersonDto(crn).toJson()),
        ),
    )
    return CreateReferralRequest(personIdentifier = crn)
  }
}
