package uk.gov.justice.digital.hmpps.communitysupportapi.controller

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.PATCH
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.communitysupportapi.authorization.UserMapper
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.AdditionalSupportNeedsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.AreaConfirmationBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.CheckDraftReferralDetailsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.CommunityServiceProviderBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.NeedsInterpreterBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.OffenceSentenceInfoBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ProbationPractitionerDetailsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralCriminogenicNeedsDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SelectionDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.TaskListStatusItem
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.TaskListStatusResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ProbationPractitionerDetails
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralCriminogenicNeeds
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralUser
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.ReferralTestSupport
import uk.gov.justice.digital.hmpps.communitysupportapi.model.AdditionalSupportNeedsRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CommunityServiceProviderRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CriminogenicNeedsRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.NeedsInterpreterRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.UpdateOffenceSentenceRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.UpdateProbationPractitionerDetailsRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.CommunityServiceProviderRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PduRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PersonAdditionalSupportNeedsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PersonRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ProbationPractitionerDetailsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralCriminogenicNeedsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralOffenceSentenceRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralProviderAssignmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.RiskInformationRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.CRN
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.createCommunityManager
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.factory.PersonAdditionalDetailsFactory
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.factory.PersonAdditionalSupportNeedsFactory
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.factory.RiskInformationFactory
import uk.gov.justice.digital.hmpps.communitysupportapi.util.toFormattedDateOfBirthLong
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.OffsetDateTime
import java.util.UUID

class DraftReferralControllerIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var personRepository: PersonRepository

  @Autowired
  private lateinit var referralRepository: ReferralRepository

  @Autowired
  private lateinit var personAdditionalSupportNeedsRepository: PersonAdditionalSupportNeedsRepository

  @Autowired
  private lateinit var riskInformationRepository: RiskInformationRepository

  @Autowired
  private lateinit var pduRepository: PduRepository

  @Autowired
  private lateinit var referralCriminogenicNeedsRepository: ReferralCriminogenicNeedsRepository

  @Autowired
  private lateinit var referralHelper: ReferralTestSupport

  @Autowired
  private lateinit var communityServiceProviderRepository: CommunityServiceProviderRepository

  @Autowired
  private lateinit var referralProviderAssignmentRepository: ReferralProviderAssignmentRepository

  @Autowired
  private lateinit var referralOffenceSentenceRepository: ReferralOffenceSentenceRepository

  @Autowired
  private lateinit var probationPractitionerDetailsRepository: ProbationPractitionerDetailsRepository

  @MockitoBean
  private lateinit var userMapper: UserMapper

  private lateinit var testUser: ReferralUser

  @Nested
  @DisplayName("GET /bff/draft-referral/check-draft-referral-details/{referralId}")
  inner class CheckDraftReferralDetailsEndpoint {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/draft-referral/check-draft-referral-details/${UUID.randomUUID()}")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(GET, "/bff/draft-referral/check-draft-referral-details/${UUID.randomUUID()}")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(GET, "/bff/draft-referral/check-draft-referral-details/${UUID.randomUUID()}")
    }

    @Test
    fun `should return OK with draft referral and person details`() {
      val person = referralHelper.createPerson(identifier = CRN)
      person.additionalDetails = PersonAdditionalDetailsFactory()
        .withPerson(person)
        .withEthnicity("White")
        .withPreferredLanguage("")
        .withNeurodiverseConditions("None")
        .withReligionOrBelief("Christianity")
        .withAddress("1 Test Street, Test Town, TEST 1AB")
        .withPhoneNumber("01234567890")
        .withEmailAddress("test@example.com")
        .create()
      personRepository.save(person)
      val referral = referralHelper.createDraftReferral(person, createdBy = testUser.id)

      webTestClient.get()
        .uri("/bff/draft-referral/check-draft-referral-details/${referral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<CheckDraftReferralDetailsBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.id shouldBe referral.id
          body.referenceNumber shouldBe referral.referenceNumber
          body.personDetailsTableData.name.firstName shouldBe person.firstName
          body.personDetailsTableData.name.lastName shouldBe person.lastName
          body.personDetailsTableData.crn shouldBe CRN
          body.personDetailsTableData.dateOfBirth shouldBe person.dateOfBirth.toString()
          body.personDetailsTableData.prisonNumbers shouldBe person.prisonNumbers
          body.personDetailsTableData.preferredLanguage shouldBe ""
          body.equalityDetailsTableData.ethnicity shouldBe person.additionalDetails?.ethnicity
          body.equalityDetailsTableData.religionOrBelief shouldBe person.additionalDetails?.religionOrBelief
          body.equalityDetailsTableData.sex shouldBe person.gender
          body.contactDetailsTableData.phoneNumber shouldBe person.additionalDetails?.phoneNumber
          body.contactDetailsTableData.email shouldBe person.additionalDetails?.emailAddress
          body.contactDetailsTableData.address shouldBe person.additionalDetails?.address
          body.riskInformationDetailsTableData shouldBe CheckDraftReferralDetailsBffResponseDto.DraftRiskInformationDetailsTableDataDto()
          body.additionalSupportNeedsDetailsTableData shouldBe CheckDraftReferralDetailsBffResponseDto.DraftAdditionalSupportNeedsDetailsTableDataDto()
          body.personNeedsDetailsTableData shouldBe CheckDraftReferralDetailsBffResponseDto.DraftPersonNeedsDetailsTableDataDto()
          body.referralAreaTableData shouldBe CheckDraftReferralDetailsBffResponseDto.DraftReferralAreaTableDataDto()
          body.mainPocDetailsTableData shouldBe CheckDraftReferralDetailsBffResponseDto.DraftMainPOCDetailsTableDataDto()
        }
    }

    @Test
    fun `should return Not Found with invalid referral identifier`() {
      assertNotFound(GET, "/bff/draft-referral/check-draft-referral-details/${UUID.randomUUID()}")
    }
  }

  @Nested
  @DisplayName("PATCH /draft-referral/addition-support-needs/:referralId")
  inner class AdditionalSupportNeedsTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(PATCH, "/draft-referral/addition-support-needs/${UUID.randomUUID()}")
    }

    @Test
    fun `should return OK and updated additional information for a draft referral - partial support needs`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(
        person = person,
        createdBy = testUser.id,
      )
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = AdditionalSupportNeedsRequest(
        needsAdditionalSupport = true,
        physicalHealth = "Requires wheelchair access",
      )

      webTestClient.patch()
        .uri("/draft-referral/additional-support-needs/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<AdditionalSupportNeedsBffResponseDto>()

      val supportNeeds = personAdditionalSupportNeedsRepository.findByReferralId(referral.id)
      supportNeeds shouldNotBe null
      supportNeeds!!.physicalHealthDetails shouldBe "Requires wheelchair access"
    }

    @Test
    fun `should return OK and updated additional information for a draft referral - full support needs`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(
        person = person,
        createdBy = testUser.id,
      )
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = AdditionalSupportNeedsRequest(
        needsAdditionalSupport = true,
        physicalHealth = "Wheelchair access required",
        mentalEmotionalHealth = "Anxiety support needed",
        neurodiversity = "ADHD diagnosis",
        locationTravel = "Cannot use public transport",
        caringResponsibilities = "Caring for elderly parent",
        employmentResponsibilities = "Part-time work",
        diversity = "Requires cultural sensitivity",
        anythingElse = "Additional notes here",
      )

      webTestClient.patch()
        .uri("/draft-referral/additional-support-needs/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<AdditionalSupportNeedsBffResponseDto>()

      val supportNeeds = personAdditionalSupportNeedsRepository.findByReferralId(referral.id)!!

      supportNeeds.additionalSupportNeeded shouldBe true
      supportNeeds.physicalHealthDetails shouldBe "Wheelchair access required"
      supportNeeds.mentalEmotionalHealthDetails shouldBe "Anxiety support needed"
      supportNeeds.neurodiversityDetails shouldBe "ADHD diagnosis"
      supportNeeds.locationTravelDetails shouldBe "Cannot use public transport"
      supportNeeds.caringResponsibilitiesDetails shouldBe "Caring for elderly parent"
      supportNeeds.employmentResponsibilitiesDetails shouldBe "Part-time work"
      supportNeeds.diversityDetails shouldBe "Requires cultural sensitivity"
      supportNeeds.anythingElseDetails shouldBe "Additional notes here"
    }

    @Test
    fun `should return OK and updated additional information for a draft referral - no additional needs`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(
        person = person,
        createdBy = testUser.id,
      )
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = AdditionalSupportNeedsRequest(
        needsAdditionalSupport = true,
        physicalHealth = "Wheelchair access required",
        mentalEmotionalHealth = "Anxiety support needed",
        neurodiversity = "ADHD diagnosis",
        locationTravel = "Cannot use public transport",
        caringResponsibilities = "Caring for elderly parent",
        employmentResponsibilities = "Part-time work",
        diversity = "Requires cultural sensitivity",
        anythingElse = "Additional notes here",
      )

      webTestClient.patch()
        .uri("/draft-referral/additional-support-needs/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<AdditionalSupportNeedsBffResponseDto>()

      val supportNeeds = personAdditionalSupportNeedsRepository.findByReferralId(referral.id)!!

      supportNeeds.additionalSupportNeeded shouldBe true
      supportNeeds.physicalHealthDetails shouldBe "Wheelchair access required"
      supportNeeds.mentalEmotionalHealthDetails shouldBe "Anxiety support needed"
      supportNeeds.neurodiversityDetails shouldBe "ADHD diagnosis"
      supportNeeds.locationTravelDetails shouldBe "Cannot use public transport"
      supportNeeds.caringResponsibilitiesDetails shouldBe "Caring for elderly parent"
      supportNeeds.employmentResponsibilitiesDetails shouldBe "Part-time work"
      supportNeeds.diversityDetails shouldBe "Requires cultural sensitivity"
      supportNeeds.anythingElseDetails shouldBe "Additional notes here"

      val updateRequest = AdditionalSupportNeedsRequest(
        needsAdditionalSupport = false,
      )

      webTestClient.patch()
        .uri("/draft-referral/additional-support-needs/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(updateRequest)
        .exchange()
        .expectStatus().isOk
        .expectBody<AdditionalSupportNeedsBffResponseDto>()

      val updatedSupportNeeds = personAdditionalSupportNeedsRepository.findByReferralId(referral.id)!!

      updatedSupportNeeds.additionalSupportNeeded shouldBe false
      updatedSupportNeeds.physicalHealthDetails shouldBe null
      updatedSupportNeeds.mentalEmotionalHealthDetails shouldBe null
      updatedSupportNeeds.neurodiversityDetails shouldBe null
      updatedSupportNeeds.locationTravelDetails shouldBe null
      updatedSupportNeeds.caringResponsibilitiesDetails shouldBe null
      updatedSupportNeeds.employmentResponsibilitiesDetails shouldBe null
      updatedSupportNeeds.diversityDetails shouldBe null
      updatedSupportNeeds.anythingElseDetails shouldBe null
    }
  }

  @Nested
  @DisplayName("GET /bff/draft-referral/addition-support-needs/:referralId")
  inner class AdditionalSupportNeedsPageTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/draft-referral/addition-support-needs/${UUID.randomUUID()}")
    }

    @Test
    fun `should return additional support needs for a draft referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(
        person = person,
        createdBy = testUser.id,
      )
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val supportNeeds = PersonAdditionalSupportNeedsFactory()
        .withReferral(referral)
        .withPerson(person)
        .withAdditionalSupportNeeded(true)
        .withPhysicalHealthDetails("Wheelchair access required")
        .withCreatedBy(testUser.id)
        .create()

      personAdditionalSupportNeedsRepository.save(supportNeeds)

      webTestClient.get()
        .uri("/bff/draft-referral/additional-support-needs/${referral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<AdditionalSupportNeedsBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.refereeName.firstName shouldBe person.firstName
          body.refereeName.lastName shouldBe person.lastName
          body.needsAdditionalSupport shouldBe true
          body.physicalHealth shouldBe SelectionDto.Yes("Wheelchair access required")
        }
    }
  }

  @Nested
  @DisplayName("PATCH /draft-referral/needs-interpreter/:referralId")
  inner class NeedsInterpreterTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(PATCH, "/draft-referral/needs-interpreter/${UUID.randomUUID()}")
    }

    @Test
    fun `should return OK and updated needs-interpreter for a draft referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(
        person = person,
        createdBy = testUser.id,
      )
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = NeedsInterpreterRequest(
        needsInterpreter = true,
        language = "Italian",
      )

      webTestClient.patch()
        .uri("/draft-referral/needs-interpreter/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<NeedsInterpreterBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.refereeName.firstName shouldBe person.firstName
          body.refereeName.lastName shouldBe person.lastName
          body.language shouldBe SelectionDto.Yes("Italian")
        }
    }

    @Test
    fun `should return OK and interpreter needs for a draft referral - no interpreter needed`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(
        person = person,
        createdBy = testUser.id,
      )
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = NeedsInterpreterRequest(
        needsInterpreter = true,
        language = "German",
      )

      webTestClient.patch()
        .uri("/draft-referral/needs-interpreter/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<NeedsInterpreterBffResponseDto>()

      val needs = personAdditionalSupportNeedsRepository.findByReferralId(referral.id)!!
      needs.interpreterLanguage shouldBe "German"

      val updateRequest = NeedsInterpreterRequest(
        needsInterpreter = false,
      )

      webTestClient.patch()
        .uri("/draft-referral/needs-interpreter/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(updateRequest)
        .exchange()
        .expectStatus().isOk
        .expectBody<NeedsInterpreterBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.refereeName.firstName shouldBe person.firstName
          body.refereeName.lastName shouldBe person.lastName
          body.language shouldBe SelectionDto.No
        }
    }
  }

  @Nested
  @DisplayName("GET /bff/draft-referral/:referralId/community-service-provider/:providerId")
  inner class AreaConfirmationTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/draft-referral/${UUID.randomUUID()}/community-service-provider/${UUID.randomUUID()}")
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val communityServiceProvider = referralHelper.getCommunityServiceProvider()

      webTestClient.get()
        .uri("/bff/draft-referral/${UUID.randomUUID()}/community-service-provider/${communityServiceProvider.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return 404 when community service provider does not exist`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)

      webTestClient.get()
        .uri("/bff/draft-referral/${referral.id}/community-service-provider/${UUID.randomUUID()}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return community service provider details with crn and dateOfBirth`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)

      val expectedAssociatedPdus = pduRepository.findByContractAreaId(communityServiceProvider.contractArea.id)
        .map { it.name }
        .sorted()

      webTestClient.get()
        .uri("/bff/draft-referral/${referral.id}/community-service-provider/${communityServiceProvider.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<AreaConfirmationBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.contractArea shouldBe communityServiceProvider.contractArea.area
          body.deliveryPartner shouldBe communityServiceProvider.serviceProvider.name
          body.associatedPdus shouldBe expectedAssociatedPdus
          body.crn shouldBe person.identifier
          body.dateOfBirth shouldBe person.dateOfBirth.toFormattedDateOfBirthLong()
        }
    }
  }

  @Nested
  @DisplayName("GET /bff/draft-referral/:referralId/probation-practitioner-details")
  inner class ProbationPractitionerDetailsTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/draft-referral/${UUID.randomUUID()}/probation-practitioner-details")
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      webTestClient.get()
        .uri("/bff/draft-referral/${UUID.randomUUID()}/probation-practitioner-details")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return probation practitioner details for a draft referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)

      stubFor(
        get(urlEqualTo("/case/${person.identifier}/community-manager"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(createCommunityManager()),
          ),
      )

      webTestClient.get()
        .uri("/bff/draft-referral/${referral.id}/probation-practitioner-details")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<ProbationPractitionerDetailsBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.name shouldBe "TestForename TestSurname"
          body.jobRole shouldBe "Probation practitioner"
          body.emailAddress shouldBe "testForename.testSurname@digital.justice.gov.uk"
          body.pdu shouldBe "Northumberland"
        }
    }

    @Test
    fun `should return 400 when person is identified by prison number`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson(identifier = "A1234BC")
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)

      webTestClient.get()
        .uri("/bff/draft-referral/${referral.id}/probation-practitioner-details")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isBadRequest
    }
  }

  @Nested
  @DisplayName("PATCH /draft-referral/{referralId}/probation-practitioner-details")
  inner class ProbationPractitionerDetailsPatchTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(PATCH, "/draft-referral/${UUID.randomUUID()}/probation-practitioner-details")
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val request = UpdateProbationPractitionerDetailsRequest(name = "Jane Doe")

      assertNotFound(PATCH, "/draft-referral/${UUID.randomUUID()}/probation-practitioner-details", request)
    }

    @Test
    fun `should return 200 and save probation practitioner details for a known referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)

      val request = UpdateProbationPractitionerDetailsRequest(
        name = "Jane Doe",
        jobRole = "Probation practitioner",
        emailAddress = "jane.doe@example.com",
        pdu = "Northumberland",
        probationOffice = "Newcastle Office",
        teamPhoneNumber = "0123456789",
        ppDetailsFoundAndCorrect = false,
      )

      webTestClient.patch()
        .uri("/draft-referral/${referral.id}/probation-practitioner-details")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<ProbationPractitionerDetailsBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.name shouldBe "Jane Doe"
          body.jobRole shouldBe "Probation practitioner"
          body.emailAddress shouldBe "jane.doe@example.com"
          body.pdu shouldBe "Northumberland"
          body.probationOffice shouldBe "Newcastle Office"
          body.teamPhoneNumber shouldBe "0123456789"
          body.ppDetailsFoundAndCorrect shouldBe false
        }

      val persistedRecord = probationPractitionerDetailsRepository.findByReferralId(referral.id)
      persistedRecord shouldNotBe null
      persistedRecord!!.name shouldBe "Jane Doe"
      persistedRecord.jobRole shouldBe "Probation practitioner"
      persistedRecord.emailAddress shouldBe "jane.doe@example.com"
      persistedRecord.pdu shouldBe "Northumberland"
      persistedRecord.probationOffice shouldBe "Newcastle Office"
      persistedRecord.teamPhoneNumber shouldBe "0123456789"
      persistedRecord.ppDetailsFoundAndCorrect shouldBe false
      persistedRecord.updatedBy shouldBe testUser.id
    }

    @Test
    fun `should update existing probation practitioner details when record already exists`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)

      val firstRequest = UpdateProbationPractitionerDetailsRequest(
        name = "Jane Doe",
        pdu = "Northumberland",
      )

      webTestClient.patch()
        .uri("/draft-referral/${referral.id}/probation-practitioner-details")
        .headers(setAuthorisation())
        .bodyValue(firstRequest)
        .exchange()
        .expectStatus().isOk

      val initialRecord = probationPractitionerDetailsRepository.findByReferralId(referral.id)
      initialRecord shouldNotBe null
      initialRecord!!.name shouldBe "Jane Doe"
      initialRecord.pdu shouldBe "Northumberland"
      val existingRecordId = initialRecord.id

      val secondRequest = UpdateProbationPractitionerDetailsRequest(
        name = "John Smith",
        jobRole = "Senior Probation practitioner",
        pdu = "Yorkshire",
      )

      webTestClient.patch()
        .uri("/draft-referral/${referral.id}/probation-practitioner-details")
        .headers(setAuthorisation())
        .bodyValue(secondRequest)
        .exchange()
        .expectStatus().isOk
        .expectBody<ProbationPractitionerDetailsBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.name shouldBe "John Smith"
          body.jobRole shouldBe "Senior Probation practitioner"
          body.pdu shouldBe "Yorkshire"
        }

      val updatedRecord = probationPractitionerDetailsRepository.findByReferralId(referral.id)
      updatedRecord shouldNotBe null
      updatedRecord!!.id shouldBe existingRecordId
      updatedRecord.name shouldBe "John Smith"
      updatedRecord.jobRole shouldBe "Senior Probation practitioner"
      updatedRecord.pdu shouldBe "Yorkshire"
      updatedRecord.updatedBy shouldBe testUser.id
      updatedRecord.updatedAt shouldNotBe null
    }
  }

  @Nested
  @DisplayName("PATCH /draft-referral/community-service-provider/:referralId")
  inner class CommunityServiceProviderTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(PATCH, "/draft-referral/community-service-provider/${UUID.randomUUID()}")
    }

    @Test
    fun `should return OK and update the community service provider for a draft referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(
        person = person,
        createdBy = testUser.id,
      )
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val newCommunityServiceProvider = communityServiceProviderRepository.findAll()
        .first { it.id != communityServiceProvider.id }

      val request = CommunityServiceProviderRequest(
        communityServiceProviderId = newCommunityServiceProvider.id,
      )

      webTestClient.patch()
        .uri("/draft-referral/community-service-provider/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<CommunityServiceProviderBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.referralId shouldBe referral.id
          body.communityServiceProviderId shouldBe newCommunityServiceProvider.id
          body.communityServiceProviderName shouldBe newCommunityServiceProvider.name
        }

      val assignments = referralProviderAssignmentRepository.findByReferralId(referral.id)
      assignments.size shouldBe 1
      assignments.first().communityServiceProvider.id shouldBe newCommunityServiceProvider.id
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val communityServiceProvider = referralHelper.getCommunityServiceProvider()

      val request = CommunityServiceProviderRequest(
        communityServiceProviderId = communityServiceProvider.id,
      )

      webTestClient.patch()
        .uri("/draft-referral/community-service-provider/${UUID.randomUUID()}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return 404 when community service provider does not exist`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(
        person = person,
        createdBy = testUser.id,
      )
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = CommunityServiceProviderRequest(
        communityServiceProviderId = UUID.randomUUID(),
      )

      webTestClient.patch()
        .uri("/draft-referral/community-service-provider/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isNotFound
    }
  }

  @Nested
  @DisplayName("GET /bff/task-list-status/{referralId}")
  inner class TaskListStatusEndPoint {
    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
    }

    @Test
    fun `should return 200 with all task list statuses as false`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "CRN12345")
      val additionalDetails = PersonAdditionalDetailsFactory()
        .withPerson(person)
        .withEthnicity("White")
        .withPreferredLanguage("English")
        .withNeurodiverseConditions("None")
        .withReligionOrBelief("None")
        .withAddress("123 Test Street /n Test Town /n Testshire")
        .withPhoneNumber("0191 234 5678")
        .withEmailAddress("test@test.com")
        .create()

      person.additionalDetails = additionalDetails
      personRepository.save(person)

      val savedReferral = referralHelper.createReferral(person = person, submittedBy = testUser, targetServiceCompletionDate = null, targetServiceCompletionDateReason = null)
      referralRepository.save(savedReferral)

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.fullName shouldBe "John Smith"
          body.confirmPersonalDetailsCompleted shouldBe TaskListStatusItem.notStarted()
          body.checkRiskInformationCompleted shouldBe TaskListStatusItem.notStarted()
          body.selectThePersonsNeedsCompleted shouldBe TaskListStatusItem.notStarted()
          body.addDetailsOfAnyAdditionalSupportNeedsCompleted shouldBe TaskListStatusItem.notStarted()
          body.addDetailsOfMainPointOfContactCompleted shouldBe TaskListStatusItem.notStarted()
          body.selectAnAreaForReferralCompleted shouldBe TaskListStatusItem.notStarted()
          body.addAdditionalInformationCompleted shouldBe TaskListStatusItem.notStarted()
          body.checkProbationPractitionerDetailsCompleted shouldBe null
          body.addMainPointOfContactCompleted shouldBe TaskListStatusItem.notStarted()
        }
    }

    @Test
    fun `should return inProgress for addDetailsOfAnyAdditionalSupportNeedsCompleted when additionalSupportNeeds is partially complete`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "CRN12345")
      val savedReferral = referralHelper.createReferral(person = person, submittedBy = testUser)
      referralRepository.save(savedReferral)

      val supportNeeds = PersonAdditionalSupportNeedsFactory()
        .withReferral(savedReferral)
        .withPerson(person)
        .withAdditionalSupportNeeded(true)
        .withCreatedBy(testUser.id)
        .create()
      personAdditionalSupportNeedsRepository.save(supportNeeds)

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.addDetailsOfAnyAdditionalSupportNeedsCompleted shouldBe TaskListStatusItem.inProgress()
        }
    }

    @Test
    fun `should return completed for addDetailsOfAnyAdditionalSupportNeedsCompleted when additionalSupportNeeds is fully complete`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "CRN12345")
      val savedReferral = referralHelper.createReferral(person = person, submittedBy = testUser)
      referralRepository.save(savedReferral)

      val supportNeeds = PersonAdditionalSupportNeedsFactory()
        .withReferral(savedReferral)
        .withPerson(person)
        .withAdditionalSupportNeeded(true)
        .withInterpreterNeeded(true)
        .withCreatedBy(testUser.id)
        .create()
      personAdditionalSupportNeedsRepository.save(supportNeeds)

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.addDetailsOfAnyAdditionalSupportNeedsCompleted shouldBe TaskListStatusItem.completed()
        }
    }

    @Test
    fun `should return completed for checkRiskInformationCompleted when risk info exists`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "CRN12345")
      val savedReferral = referralHelper.createReferral(person = person, submittedBy = testUser)
      referralRepository.save(savedReferral)

      val riskInfo = RiskInformationFactory()
        .withReferral(savedReferral)
        .withUpdatedBy(testUser.id)
        .create()
      riskInformationRepository.save(riskInfo)

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.checkRiskInformationCompleted shouldBe TaskListStatusItem.completed()
        }
    }

    @Test
    fun `should return completed for selectAnAreaForReferralCompleted when community service provider is assigned`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "CRN12345")
      val savedReferral = referralHelper.createReferral(person = person, submittedBy = testUser)
      referralRepository.save(savedReferral)

      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      referralHelper.createProviderAssignment(savedReferral, communityServiceProvider)

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.selectAnAreaForReferralCompleted shouldBe TaskListStatusItem.completed()
        }
    }

    @Test
    fun `should return in progress for addAdditionalInformationCompleted when target date and reason exist without service days`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "CRN12345")
      val savedReferral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
      )
      referralRepository.save(savedReferral)

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.addAdditionalInformationCompleted shouldBe TaskListStatusItem.inProgress()
        }
    }

    @Test
    fun `should return completed for addAdditionalInformationCompleted when target date reason and service days exist`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "CRN12345")
      val savedReferral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
        serviceDays = 40,
      )
      referralRepository.save(savedReferral)

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.addAdditionalInformationCompleted shouldBe TaskListStatusItem.completed()
        }
    }

    @Test
    fun `should default to main point of contact when no probation practitioner found in nDelius`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "X123456")
      val savedReferral = referralHelper.createReferral(person = person, submittedBy = testUser)
      referralRepository.save(savedReferral)

      stubFor(
        get(urlEqualTo("/case/${person.identifier}/community-manager"))
          .willReturn(aResponse().withStatus(404)),
      )

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.checkProbationPractitionerDetailsCompleted shouldBe null
          body.addMainPointOfContactCompleted shouldBe TaskListStatusItem.notStarted()
        }
    }

    @Test
    fun `should return notStarted for probation practitioner check when found in nDelius but not saved`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "X123456")
      val savedReferral = referralHelper.createReferral(person = person, submittedBy = testUser)
      referralRepository.save(savedReferral)

      stubFor(
        get(urlEqualTo("/case/${person.identifier}/community-manager"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(createCommunityManager()),
          ),
      )

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.checkProbationPractitionerDetailsCompleted shouldBe TaskListStatusItem.notStarted()
          body.addMainPointOfContactCompleted shouldBe null
        }
    }

    @Test
    fun `should return completed for probation practitioner check when found in nDelius and saved`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "X123456")
      val savedReferral = referralHelper.createReferral(person = person, submittedBy = testUser)
      referralRepository.save(savedReferral)

      stubFor(
        get(urlEqualTo("/case/${person.identifier}/community-manager"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(createCommunityManager()),
          ),
      )

      probationPractitionerDetailsRepository.save(
        ProbationPractitionerDetails(
          id = UUID.randomUUID(),
          referralId = savedReferral.id,
          name = "Jane Doe",
          updatedAt = OffsetDateTime.now(),
          updatedBy = testUser.id,
        ),
      )

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.checkProbationPractitionerDetailsCompleted shouldBe TaskListStatusItem.completed()
          body.addMainPointOfContactCompleted shouldBe null
        }
    }

    @Test
    fun `should return null for checkProbationPractitionerDetails and completed for addMainPointOfContact when saved probation practitioner details are not correct`() {
      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPerson(identifier = "X123456")
      val savedReferral = referralHelper.createReferral(person = person, submittedBy = testUser)
      referralRepository.save(savedReferral)

      stubFor(
        get(urlEqualTo("/case/${person.identifier}/community-manager"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(createCommunityManager()),
          ),
      )

      probationPractitionerDetailsRepository.save(
        ProbationPractitionerDetails(
          id = UUID.randomUUID(),
          referralId = savedReferral.id,
          name = "Jane Doe",
          ppDetailsFoundAndCorrect = false,
          updatedAt = OffsetDateTime.now(),
          updatedBy = testUser.id,
        ),
      )

      webTestClient.get()
        .uri("/bff/task-list-status/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<TaskListStatusResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.checkProbationPractitionerDetailsCompleted shouldBe null
          body.addMainPointOfContactCompleted shouldBe TaskListStatusItem.completed()
        }
    }
  }

  @Nested
  @DisplayName("PATCH /draft-referral/person-needs/:referralId")
  inner class CriminogenicNeedsPatchTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(PATCH, "/draft-referral/person-needs/${UUID.randomUUID()}")
    }

    @Test
    fun `should return bad request when selected criminogenic need has no details`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)

      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = CriminogenicNeedsRequest(hasAccommodationNeeds = true)

      webTestClient.patch()
        .uri("/draft-referral/person-needs/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isBadRequest

      referralCriminogenicNeedsRepository.findByReferralId(referral.id) shouldBe null
    }

    @Test
    fun `should return OK and create criminogenic needs for a draft referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)

      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = CriminogenicNeedsRequest(
        hasAccommodationNeeds = true,
        accommodationDetails = "Needs emergency housing",
        hasDrugUseNeeds = false,
      )

      webTestClient.patch()
        .uri("/draft-referral/person-needs/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<ReferralCriminogenicNeedsDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.referralId shouldBe referral.id
          body.refereeName.firstName shouldBe person.firstName
          body.refereeName.lastName shouldBe person.lastName
          body.hasAccommodationNeeds shouldBe true
          body.accommodationDetails shouldBe "Needs emergency housing"
          body.hasDrugUseNeeds shouldBe false
          body.updatedBy shouldBe testUser.id
        }

      val savedCriminogenicNeedsRecord = referralCriminogenicNeedsRepository.findByReferralId(referral.id)
      savedCriminogenicNeedsRecord shouldNotBe null
      savedCriminogenicNeedsRecord!!.hasAccommodationNeeds shouldBe true
      savedCriminogenicNeedsRecord.accommodationDetails shouldBe "Needs emergency housing"
      savedCriminogenicNeedsRecord.hasDrugUseNeeds shouldBe false
      savedCriminogenicNeedsRecord.updatedBy shouldBe testUser.id
    }

    @Test
    fun `should return OK and update existing criminogenic needs for a draft referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val existing = referralCriminogenicNeedsRepository.save(
        ReferralCriminogenicNeeds(
          id = UUID.randomUUID(),
          referral = referral,
          hasAccommodationNeeds = false,
          updatedAt = OffsetDateTime.now().minusDays(1),
          updatedBy = testUser.id,
        ),
      )

      val request = CriminogenicNeedsRequest(hasAccommodationNeeds = true, accommodationDetails = "Updated accommodation details")

      webTestClient.patch()
        .uri("/draft-referral/person-needs/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<ReferralCriminogenicNeedsDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.id shouldBe existing.id
          body.refereeName.firstName shouldBe person.firstName
          body.refereeName.lastName shouldBe person.lastName
          body.hasAccommodationNeeds shouldBe true
          body.accommodationDetails shouldBe "Updated accommodation details"
        }

      val savedCriminogenicNeedsRecord = referralCriminogenicNeedsRepository.findByReferralId(referral.id)
      savedCriminogenicNeedsRecord shouldNotBe null
      savedCriminogenicNeedsRecord!!.id shouldBe existing.id
      savedCriminogenicNeedsRecord.hasAccommodationNeeds shouldBe true
      savedCriminogenicNeedsRecord.accommodationDetails shouldBe "Updated accommodation details"
    }

    @Test
    fun `should clear previously saved needs when patch payload omits those fields`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val existing = referralCriminogenicNeedsRepository.save(
        ReferralCriminogenicNeeds(
          id = UUID.randomUUID(),
          referral = referral,
          hasAccommodationNeeds = true,
          accommodationDetails = "Needs emergency housing",
          hasFinancialNeeds = true,
          financialDetails = "Needs debt support",
          updatedAt = OffsetDateTime.now().minusDays(1),
          updatedBy = testUser.id,
        ),
      )

      val request = CriminogenicNeedsRequest(
        hasFinancialNeeds = true,
        financialDetails = "Updated debt support",
      )

      webTestClient.patch()
        .uri("/draft-referral/person-needs/${referral.id}")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<ReferralCriminogenicNeedsDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.id shouldBe existing.id
          body.refereeName.firstName shouldBe person.firstName
          body.refereeName.lastName shouldBe person.lastName
          body.hasAccommodationNeeds shouldBe null
          body.accommodationDetails shouldBe null
          body.hasFinancialNeeds shouldBe true
          body.financialDetails shouldBe "Updated debt support"
        }

      val savedCriminogenicNeedsRecord = referralCriminogenicNeedsRepository.findByReferralId(referral.id)
      savedCriminogenicNeedsRecord shouldNotBe null
      savedCriminogenicNeedsRecord!!.id shouldBe existing.id
      savedCriminogenicNeedsRecord.hasAccommodationNeeds shouldBe null
      savedCriminogenicNeedsRecord.accommodationDetails shouldBe null
      savedCriminogenicNeedsRecord.hasFinancialNeeds shouldBe true
      savedCriminogenicNeedsRecord.financialDetails shouldBe "Updated debt support"
    }
  }

  @Nested
  @DisplayName("GET /bff/draft-referral/person-needs/:referralId")
  inner class CriminogenicNeedsGetTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/draft-referral/person-needs/${UUID.randomUUID()}")
    }

    @Test
    fun `should return not found when criminogenic needs do not exist for referral`() {
      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      assertNotFound(GET, "/bff/draft-referral/person-needs/${referral.id}")
    }

    @Test
    fun `should return criminogenic needs for a referral`() {
      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      referralCriminogenicNeedsRepository.save(
        ReferralCriminogenicNeeds(
          id = UUID.randomUUID(),
          referral = referral,
          hasFinancialNeeds = true,
          financialDetails = "Needs debt management support",
          updatedAt = OffsetDateTime.now(),
          updatedBy = testUser.id,
        ),
      )

      webTestClient.get()
        .uri("/bff/draft-referral/person-needs/${referral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<ReferralCriminogenicNeedsDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.referralId shouldBe referral.id
          body.refereeName.firstName shouldBe person.firstName
          body.refereeName.lastName shouldBe person.lastName
          body.hasFinancialNeeds shouldBe true
          body.financialDetails shouldBe "Needs debt management support"
          body.updatedBy shouldBe testUser.id
        }
    }
  }

  @Nested
  @DisplayName("GET /bff/draft-referral/{referralId}/offence-sentence")
  inner class OffenceSentenceInfoEndPoint {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/draft-referral/${UUID.randomUUID()}/offence-sentence")
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      assertNotFound(GET, "/bff/draft-referral/${UUID.randomUUID()}/offence-sentence")
    }

    @Test
    fun `should return 200 with offence and sentence info for a known referral`() {
      val person = referralHelper.createPerson(identifier = CRN)
      val referral = referralHelper.createReferral(person, submittedBy = testUser)

      webTestClient.get()
        .uri("/bff/draft-referral/${referral.id}/offence-sentence")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<OffenceSentenceInfoBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.firstName shouldBe person.firstName
          body.lastName shouldBe person.lastName
          body.offenceSentenceInfo.offence shouldBe null
          body.offenceSentenceInfo.offenceSubCategory shouldBe null
          body.offenceSentenceInfo.outcome shouldBe null
          body.offenceSentenceInfo.sentenceEndDate shouldBe null
          body.offenceSentenceInfo.expectedReleaseDate shouldBe null
          body.offenceSentenceInfo.hasLicenceConditionsOrZones shouldBe null
          body.offenceSentenceInfo.licenceConditionsOrZonesDetails shouldBe null
        }
    }

    @Test
    fun `should return stored offence and sentence info when details were previously upserted`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson(identifier = CRN)
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = UpdateOffenceSentenceRequest(
        offence = "Assault",
        offenceSubCategory = "Common assault",
        outcome = "18 month community order",
        sentenceEndDate = java.time.LocalDate.of(2026, 3, 1),
      )

      webTestClient.patch()
        .uri("/draft-referral/${referral.id}/offence-sentence")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk

      webTestClient.get()
        .uri("/bff/draft-referral/${referral.id}/offence-sentence")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<OffenceSentenceInfoBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.offenceSentenceInfo.offence shouldBe null
          body.offenceSentenceInfo.offenceSubCategory shouldBe null
          body.offenceSentenceInfo.outcome shouldBe null
          body.offenceSentenceInfo.sentenceEndDate shouldBe null
          body.offenceSentenceInfo.expectedReleaseDate shouldBe null
          body.offenceSentenceInfo.hasLicenceConditionsOrZones shouldBe null
          body.offenceSentenceInfo.licenceConditionsOrZonesDetails shouldBe null
        }
    }
  }

  @Nested
  @DisplayName("PATCH /draft-referral/{referralId}/offence-sentence")
  inner class OffenceSentencePatchTest {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(PATCH, "/draft-referral/${UUID.randomUUID()}/offence-sentence")
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val request = UpdateOffenceSentenceRequest(offence = "Robbery")

      assertNotFound(PATCH, "/draft-referral/${UUID.randomUUID()}/offence-sentence", request)
    }

    @Test
    fun `should return 400 when licence condition details are missing while question is yes`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson(identifier = "X123456")
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = UpdateOffenceSentenceRequest(
        offence = "Robbery",
        hasLicenceConditionsOrZones = true,
        licenceConditionsOrZonesDetails = null,
      )

      webTestClient.patch()
        .uri("/draft-referral/${referral.id}/offence-sentence")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isBadRequest

      referralOffenceSentenceRepository.findByReferralId(referral.id) shouldBe null
    }

    @Test
    fun `should return 200 and updated offence and sentence info for a known referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson(identifier = "X123456")
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val request = UpdateOffenceSentenceRequest(
        offence = "Robbery",
        offenceSubCategory = "Street robbery",
        outcome = "12 month community order",
        sentenceEndDate = java.time.LocalDate.of(2026, 1, 1),
        expectedReleaseDate = java.time.LocalDate.of(2026, 2, 1),
        hasLicenceConditionsOrZones = true,
        licenceConditionsOrZonesDetails = "Cannot enter City Centre exclusion area",
      )

      webTestClient.patch()
        .uri("/draft-referral/${referral.id}/offence-sentence")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk
        .expectBody<OffenceSentenceInfoBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.firstName shouldBe person.firstName
          body.lastName shouldBe person.lastName
          body.offenceSentenceInfo.offence shouldBe "Robbery"
          body.offenceSentenceInfo.offenceSubCategory shouldBe "Street robbery"
          body.offenceSentenceInfo.outcome shouldBe "12 month community order"
          body.offenceSentenceInfo.sentenceEndDate shouldBe java.time.LocalDate.of(2026, 1, 1)
          body.offenceSentenceInfo.expectedReleaseDate shouldBe java.time.LocalDate.of(2026, 2, 1)
          body.offenceSentenceInfo.hasLicenceConditionsOrZones shouldBe true
          body.offenceSentenceInfo.licenceConditionsOrZonesDetails shouldBe "Cannot enter City Centre exclusion area"
        }

      val persistedRecord = referralOffenceSentenceRepository.findByReferralId(referral.id)
      persistedRecord shouldNotBe null
      persistedRecord!!.offence shouldBe "Robbery"
      persistedRecord.offenceSubCategory shouldBe "Street robbery"
      persistedRecord.outcome shouldBe "12 month community order"
      persistedRecord.sentenceEndDate shouldBe java.time.LocalDate.of(2026, 1, 1)
      persistedRecord.expectedReleaseDate shouldBe java.time.LocalDate.of(2026, 2, 1)
      persistedRecord.hasLicenceConditionsOrZones shouldBe true
      persistedRecord.licenceConditionsOrZonesDetails shouldBe "Cannot enter City Centre exclusion area"
      persistedRecord.createdBy shouldBe testUser.id
      persistedRecord.updatedBy shouldBe null
    }

    @Test
    fun `should update existing offence and sentence details when record already exists`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson(identifier = CRN)
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createDraftReferral(person = person, createdBy = testUser.id)
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val firstRequest = UpdateOffenceSentenceRequest(
        offence = "Robbery",
        outcome = "Community order",
        hasLicenceConditionsOrZones = true,
        licenceConditionsOrZonesDetails = "Initial exclusion zone",
      )

      webTestClient.patch()
        .uri("/draft-referral/${referral.id}/offence-sentence")
        .headers(setAuthorisation())
        .bodyValue(firstRequest)
        .exchange()
        .expectStatus().isOk

      val initialRecord = referralOffenceSentenceRepository.findByReferralId(referral.id)
      initialRecord shouldNotBe null

      val existingRecordId = initialRecord!!.id

      val secondRequest = UpdateOffenceSentenceRequest(
        offence = "Fraud",
        offenceSubCategory = "Benefit fraud",
        outcome = "Suspended sentence",
        hasLicenceConditionsOrZones = false,
        licenceConditionsOrZonesDetails = "Should be removed",
      )

      webTestClient.patch()
        .uri("/draft-referral/${referral.id}/offence-sentence")
        .headers(setAuthorisation())
        .bodyValue(secondRequest)
        .exchange()
        .expectStatus().isOk
        .expectBody<OffenceSentenceInfoBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.offenceSentenceInfo.hasLicenceConditionsOrZones shouldBe false
          body.offenceSentenceInfo.licenceConditionsOrZonesDetails shouldBe null
        }

      val updatedRecord = referralOffenceSentenceRepository.findByReferralId(referral.id)
      updatedRecord shouldNotBe null
      updatedRecord!!.id shouldBe existingRecordId
      updatedRecord.offence shouldBe "Fraud"
      updatedRecord.offenceSubCategory shouldBe "Benefit fraud"
      updatedRecord.outcome shouldBe "Suspended sentence"
      updatedRecord.hasLicenceConditionsOrZones shouldBe false
      updatedRecord.licenceConditionsOrZonesDetails shouldBe null
      updatedRecord.updatedBy shouldBe testUser.id
      updatedRecord.updatedAt shouldNotBe null
    }
  }
}
