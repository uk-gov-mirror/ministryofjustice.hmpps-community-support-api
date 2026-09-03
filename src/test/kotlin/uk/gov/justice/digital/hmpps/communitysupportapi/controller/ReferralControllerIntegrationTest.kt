package uk.gov.justice.digital.hmpps.communitysupportapi.controller

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpMethod.GET
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.communitysupportapi.authorization.UserMapper
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.AppointmentIcsResponse
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.CheckReferralInformationDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ConfirmPersonDetailsBffDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralDetailsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralInformationDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralProgressDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ServiceDaysPageDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ServiceEndDatePageDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SubmitReferralResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.VirtualAppointment
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.AppointmentDeliveryMethod
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.AppointmentStatusHistoryType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.AppointmentType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Person
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralEventType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralUser
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.AppointmentTestSupport
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.ReferralTestSupport
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.ReferralWithdrawalReasonCode
import uk.gov.justice.digital.hmpps.communitysupportapi.model.WithdrawReferralRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentDeliveryRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentIcsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentStatusHistoryRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.CommunityServiceProviderRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PersonRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralProviderAssignmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralUserRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralWithdrawalDetailsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.CRN
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.cprProbationPersonJson
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.cprProbationPersonNoFixAbodeJson
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.createCprProbationPersonDto
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.factory.PersonAdditionalDetailsFactory
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.factory.PersonFactory
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.factory.ReferralProviderAssignmentFactory
import uk.gov.justice.digital.hmpps.communitysupportapi.util.toFormattedDateOfBirth
import uk.gov.justice.digital.hmpps.communitysupportapi.util.toJson
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class ReferralControllerIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var referralRepository: ReferralRepository

  @Autowired
  private lateinit var personRepository: PersonRepository

  @Autowired
  private lateinit var communityServiceProviderRepository: CommunityServiceProviderRepository

  @Autowired
  private lateinit var referralProviderAssignmentRepository: ReferralProviderAssignmentRepository

  @Autowired
  private lateinit var referralUserRepository: ReferralUserRepository

  @Autowired
  private lateinit var referralWithdrawalDetailsRepository: ReferralWithdrawalDetailsRepository

  @Autowired
  private lateinit var appointmentRepository: AppointmentRepository

  @Autowired
  private lateinit var appointmentIcsRepository: AppointmentIcsRepository

  @Autowired
  private lateinit var appointmentDeliveryRepository: AppointmentDeliveryRepository

  @Autowired
  private lateinit var appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository

  @Autowired
  private lateinit var appointmentHelper: AppointmentTestSupport

  @Autowired
  private lateinit var referralHelper: ReferralTestSupport

  @MockitoBean
  private lateinit var userMapper: UserMapper

  private lateinit var testUser: ReferralUser

  @Nested
  @DisplayName("GET /bff/referral-details/{referralId}")
  inner class ReferralEndPoint {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/referral-details/${referralHelper.communityServiceProviderId}")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(GET, "/bff/referral-details/${referralHelper.communityServiceProviderId}")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(GET, "/bff/referral-details/${referralHelper.communityServiceProviderId}")
    }

    @Test
    fun `should return OK with valid referral information`() {
      val person = referralHelper.createPerson()
      val savedReferral = referralHelper.createReferral(
        person,
        referenceNumber = "REF123456",
        submittedBy = testUser,
      )

      val referralDto = ReferralDto(
        id = savedReferral.id,
        crn = savedReferral.personIdentifier,
        referenceNumber = savedReferral.referenceNumber,
        createdDate = savedReferral.createdAt,
        serviceDays = savedReferral.serviceDays,
      )

      webTestClient.get()
        .uri("/bff/referral-details/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ReferralDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          // compare fields individually and allow a tiny tolerance for createdDate
          body.id shouldBe referralDto.id
          body.crn shouldBe referralDto.crn
          body.referenceNumber shouldBe referralDto.referenceNumber
          body.serviceDays shouldBe referralDto.serviceDays

          val nanosDiff =
            Duration.between(referralDto.createdDate, body.createdDate).abs().toNanos()
          // allow up to 1-millisecond difference to avoid nanosecond serialization jitter
          assertThat(nanosDiff).isLessThanOrEqualTo(1_000_000L)
        }
    }

    @Test
    fun `should return Not Found with invalid referral identifier`() {
      assertNotFound(GET, "/bff/referrals/${referralHelper.communityServiceProviderId}")
    }
  }

  @Nested
  @DisplayName("POST /referral")
  inner class CreateReferrals {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(HttpMethod.POST, "/referral")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(HttpMethod.POST, "/referral", setUpData())
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(HttpMethod.POST, "/referral", setUpData())
    }

    @Test
    fun `should return OK with valid referral information`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      webTestClient.post()
        .uri("/referral")
        .headers(setAuthorisation())
        .bodyValue(setUpData())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ReferralInformationDto>()
        .consumeWith { response ->
          run {
            val referral = referralRepository.findAll().firstOrNull()!!
            val person = personRepository.findById(referral.personId).get()

            val referralInfo = ReferralInformationDto(
              referralId = referral.id,
              referralDate = referral.createdAt.toLocalDate(),
              personId = referral.personId,
              firstName = person.firstName,
              lastName = person.lastName,
              sex = person.gender,
              personIdentifier = referral.personIdentifier,
              communityServiceProviderId = null,
              communityServiceProviderName = null,
              region = null,
              deliveryPartner = null,
            )
            response.responseBody shouldBe referralInfo
          }
        }
    }

    @Test
    fun `should return a server error, not Not Found, when CPR probation lookup returns 404`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val crn = "X654321"

      stubFor(
        get(urlEqualTo("/person/probation/$crn"))
          .willReturn(aResponse().withStatus(404)),
      )

      webTestClient.post()
        .uri("/referral")
        .headers(setAuthorisation())
        .bodyValue(
          CreateReferralRequest(personIdentifier = crn),
        )
        .exchange()
        .expectStatus().is5xxServerError

      assertThat(referralRepository.findAll()).isEmpty()
    }

    @Test
    fun `should return a server error and persist nothing when CPR probation lookup fails`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val crn = "X654322"

      stubFor(
        get(urlEqualTo("/person/probation/$crn"))
          .willReturn(aResponse().withStatus(500)),
      )

      webTestClient.post()
        .uri("/referral")
        .headers(setAuthorisation())
        .bodyValue(
          CreateReferralRequest(personIdentifier = crn),
        )
        .exchange()
        .expectStatus().is5xxServerError

      assertThat(referralRepository.findAll()).isEmpty()
      assertThat(personRepository.findByIdentifier(crn)).isNull()
    }

    private fun setUpData(): CreateReferralRequest {
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()

      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprProbationPersonJson(CRN)),
          ),
      )

      return CreateReferralRequest(personIdentifier = CRN)
    }
  }

  @Nested
  @DisplayName("POST /{referralId}/submit-a-referral")
  inner class SubmitReferral {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(HttpMethod.POST, "/${UUID.randomUUID()}/submit-a-referral")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(HttpMethod.POST, "/${UUID.randomUUID()}/submit-a-referral")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(HttpMethod.POST, "/${UUID.randomUUID()}/submit-a-referral")
    }

    @Test
    fun `should return OK and create submitted event for valid referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val savedReferral = referralHelper.createDraftReferral(
        person = person,
        createdBy = testUser.id,
      )
      referralHelper.createProviderAssignment(savedReferral, communityServiceProvider)

      webTestClient.post()
        .uri("/${savedReferral.id}/submit-a-referral")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<SubmitReferralResponseDto>()
        .consumeWith { response ->
          run {
            val updatedReferral = referralRepository.findById(savedReferral.id).get()
            val submitReferralResponseDto = SubmitReferralResponseDto(
              referralId = updatedReferral.id,
              personId = updatedReferral.personId,
              referenceNumber = updatedReferral.referenceNumber,
            )
            response.responseBody shouldBe submitReferralResponseDto
            response.responseBody?.referralId shouldBe savedReferral.id
            response.responseBody?.personId shouldBe person.id
            response.responseBody?.referenceNumber shouldNotBe null
          }

          val updated = referralRepository.findById(savedReferral.id).get()
          assertThat(updated.submittedEvent?.eventType).isEqualTo(ReferralEventType.SUBMITTED)
        }
    }

    @Test
    fun `should return 409 conflict when referral has already been submitted`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = personRepository.save(
        PersonFactory()
          .withFirstName("Jane")
          .withLastName("Doe")
          .withIdentifier("X654321")
          .create(),
      )
      val alreadySubmittedReferral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
      )

      webTestClient.post()
        .uri("/${alreadySubmittedReferral.id}/submit-a-referral")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .is4xxClientError
        .expectStatus()
        .isEqualTo(409)
    }
  }

  @Nested
  @DisplayName("POST /referral/{referralReference}/withdraw")
  inner class WithdrawReferral {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(HttpMethod.POST, "/referral/AB1234CD/withdraw")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(
        HttpMethod.POST,
        "/referral/AB1234CD/withdraw",
        WithdrawReferralRequest(
          reasonCode = ReferralWithdrawalReasonCode.SENTENCE_EXPIRED,
          additionalDetails = "Some details",
        ),
      )
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(
        HttpMethod.POST,
        "/referral/AB1234CD/withdraw",
        WithdrawReferralRequest(
          reasonCode = ReferralWithdrawalReasonCode.SENTENCE_EXPIRED,
          additionalDetails = "Some details",
        ),
      )
    }

    @Test
    fun `should return OK and save withdrawal details and event`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val referral = referralHelper.createReferral(submittedBy = testUser)

      webTestClient.post()
        .uri("/referral/${referral.referenceNumber}/withdraw")
        .headers(setAuthorisation())
        .bodyValue(
          WithdrawReferralRequest(
            reasonCode = ReferralWithdrawalReasonCode.SENTENCE_EXPIRED,
            additionalDetails = "  Sentence expired  ",
          ),
        )
        .exchange()
        .expectStatus()
        .isOk

      val savedWithdrawalDetails = referralWithdrawalDetailsRepository.findByReferralId(referral.id)
      assertThat(savedWithdrawalDetails).isNotNull()
      assertThat(savedWithdrawalDetails?.reasonCode).isEqualTo("SENTENCE_EXPIRED")
      assertThat(savedWithdrawalDetails?.reasonDetails).isEqualTo("Sentence expired")

      val updatedReferral = referralRepository.findById(referral.id).get()
      assertThat(updatedReferral.referralEvents.any { it.eventType == ReferralEventType.WITHDRAWN }).isTrue()
    }

    @Test
    fun `should return Not Found when referral does not exist`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      webTestClient.post()
        .uri("/referral/ZZ9999ZZ/withdraw")
        .headers(setAuthorisation())
        .bodyValue(
          WithdrawReferralRequest(
            reasonCode = ReferralWithdrawalReasonCode.SENTENCE_EXPIRED,
            additionalDetails = "Some details",
          ),
        )
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `should return conflict when referral has already been withdrawn`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val referral = referralHelper.createReferral(submittedBy = testUser)
      val request = WithdrawReferralRequest(
        reasonCode = ReferralWithdrawalReasonCode.SENTENCE_EXPIRED,
        additionalDetails = "Some details",
      )

      webTestClient.post()
        .uri("/referral/${referral.referenceNumber}/withdraw")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk

      webTestClient.post()
        .uri("/referral/${referral.referenceNumber}/withdraw")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isEqualTo(409)
    }
  }

  @Nested
  @DisplayName("GET /bff/referral-details-page/{referralId}")
  inner class ReferralViewPageEndPoint {

    fun createReferralDetailsBffResponseDto(person: Person, testUser: ReferralUser): ReferralDetailsBffResponseDto {
      val savedReferral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
        targetServiceCompletionDate = null,
        targetServiceCompletionDateReason = null,
      )

      val personDetailsTable = ReferralDetailsBffResponseDto.PersonDetailsTableDataDto(
        name = "${person.firstName} ${person.lastName}",
        crn = savedReferral.personIdentifier,
        dateOfBirth = person.dateOfBirth.toString(),
        preferredLanguage = person.additionalDetails?.preferredLanguage.toString(),
        disabilities = "",
      )

      val equalityDetailsTable = ReferralDetailsBffResponseDto.EqualityDetailsTableDataDto(
        ethnicity = person.additionalDetails?.ethnicity.toString(),
        religionOrBelief = person.additionalDetails?.religionOrBelief.toString(),
        sex = "",
      )

      val contactDetailsTable = ReferralDetailsBffResponseDto.ContactDetailsTableDataDto(
        phoneNumber = person.additionalDetails?.phoneNumber,
        mobileNumber = null,
        email = person.additionalDetails?.emailAddress,
        address = person.additionalDetails?.address,
      )

      val referralDetailsTable = ReferralDetailsBffResponseDto.ReferralDetailsTableDataDto(
        referralDate = savedReferral.createdAt.toString(),
        assignedTo = emptyList(),
      )

      return ReferralDetailsBffResponseDto(
        id = savedReferral.id,
        referenceNumber = savedReferral.referenceNumber,
        createdDate = savedReferral.createdAt,
        serviceDays = savedReferral.serviceDays,
        targetServiceCompletionDate = savedReferral.targetServiceCompletionDate,
        targetServiceCompletionDateReason = savedReferral.targetServiceCompletionDateReason,
        personDetailsTableData = personDetailsTable,
        equalityDetailsTableData = equalityDetailsTable,
        contactDetailsTableData = contactDetailsTable,
        referralDetailsTableData = referralDetailsTable,
      )
    }

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/referral-details-page/${referralHelper.communityServiceProviderId}")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(GET, "/bff/referral-details-page/${referralHelper.communityServiceProviderId}")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(GET, "/bff/referral-details-page/${referralHelper.communityServiceProviderId}")
    }

    @Test
    fun `should return OK with valid referral details page information`() {
      val cprPersonDTO = createCprProbationPersonDto(CRN)
      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprPersonDTO.toJson()),
          ),
      )

      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPersonFromCprPersonDTO(cprPersonDTO)

      personRepository.save(person)

      val referralDetailsDto = createReferralDetailsBffResponseDto(person, testUser)

      webTestClient.get()
        .uri("/bff/referral-details-page/${referralDetailsDto.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ReferralDetailsBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          // compare fields individually and allow a tiny tolerance for createdDate
          body.id shouldBe referralDetailsDto.id
          body.referenceNumber shouldBe referralDetailsDto.referenceNumber
          body.targetServiceCompletionDate shouldBe null
          body.targetServiceCompletionDateReason shouldBe null
          body.personDetailsTableData shouldBe referralDetailsDto.personDetailsTableData
          body.equalityDetailsTableData shouldBe referralDetailsDto.equalityDetailsTableData
          body.contactDetailsTableData shouldBe referralDetailsDto.contactDetailsTableData

          val nanosDiff =
            Duration.between(referralDetailsDto.createdDate, body.createdDate).abs().toNanos()
          // allow up to 1-millisecond difference to avoid nanosecond serialization jitter
          assertThat(nanosDiff).isLessThanOrEqualTo(1_000_000L)
        }
    }

    @Test
    fun `should return targetServiceCompletionDate and reason when set on referral`() {
      val cprPersonDTO = createCprProbationPersonDto(CRN)
      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprPersonDTO.toJson()),
          ),
      )

      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPersonFromCprPersonDTO(cprPersonDTO)
      personRepository.save(person)

      val completionDate = OffsetDateTime.now().plusMonths(3).withNano(0)
      val savedReferral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
        targetServiceCompletionDate = completionDate,
        targetServiceCompletionDateReason = "Extended due to complexity",
      )

      webTestClient.get()
        .uri("/bff/referral-details-page/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ReferralDetailsBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.targetServiceCompletionDate?.toInstant() shouldBe completionDate.toInstant()
          body.targetServiceCompletionDateReason shouldBe "Extended due to complexity"
        }
    }

    @Test
    fun `should return OK with valid referral details page and change in person details`() {
      val cprPersonDTO = createCprProbationPersonDto(CRN)
      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprPersonDTO.toJson()),
          ),
      )

      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPersonFromCprPersonDTO(cprPersonDTO)

      // Change personDetails in db - the get call should overwright this with the details returned from the stub
      val additionalDetails = PersonAdditionalDetailsFactory()
        .withPerson(person)
        .withEthnicity(cprPersonDTO.ethnicity?.description)
        .withPreferredLanguage("")
        .withNeurodiverseConditions("None")
        .withReligionOrBelief(cprPersonDTO.religion?.description)
        .withPhoneNumber("09876543210")
        .withEmailAddress("changed.email@example.com")
        .create()
      person.additionalDetails = additionalDetails
      personRepository.save(person)

      val referralDetailsDto = createReferralDetailsBffResponseDto(person, testUser)

      webTestClient.get()
        .uri("/bff/referral-details-page/${referralDetailsDto.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ReferralDetailsBffResponseDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          // compare fields individually and allow a tiny tolerance for createdDate
          body.id shouldBe referralDetailsDto.id
          body.referenceNumber shouldBe referralDetailsDto.referenceNumber
          body.personDetailsTableData shouldBe referralDetailsDto.personDetailsTableData
          body.equalityDetailsTableData shouldBe referralDetailsDto.equalityDetailsTableData
          body.contactDetailsTableData shouldNotBe referralDetailsDto.contactDetailsTableData

          body.contactDetailsTableData.phoneNumber shouldBe cprPersonDTO.addresses.first().contacts.first { it.type?.code == "TELEPHONE" }.value
          body.contactDetailsTableData.email shouldBe cprPersonDTO.addresses.first().contacts.first { it.type?.code == "EMAIL" }.value

          val nanosDiff =
            Duration.between(referralDetailsDto.createdDate, body.createdDate).abs().toNanos()
          // allow up to 1-millisecond difference to avoid nanosecond serialization jitter
          assertThat(nanosDiff).isLessThanOrEqualTo(1_000_000L)
        }
    }

    @Test
    fun `should return Not Found with invalid referral identifier`() {
      assertNotFound(GET, "/bff/referral-details-page/${referralHelper.communityServiceProviderId}")
    }
  }

  @Nested
  @DisplayName("GET /bff/service-end-date-page/{referralId}")
  inner class ServiceEndDatePageEndPoint {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/service-end-date-page/${UUID.randomUUID()}")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(GET, "/bff/service-end-date-page/${UUID.randomUUID()}")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(GET, "/bff/service-end-date-page/${UUID.randomUUID()}")
    }

    @Test
    fun `should return OK with service end date data when set on referral`() {
      val cprPersonDTO = createCprProbationPersonDto(CRN)
      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprPersonDTO.toJson()),
          ),
      )

      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPersonFromCprPersonDTO(cprPersonDTO)
      personRepository.save(person)

      val completionDate = OffsetDateTime.now().plusMonths(3).withNano(0)
      val savedReferral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
        targetServiceCompletionDate = completionDate,
        targetServiceCompletionDateReason = "Extended due to complexity",
      )

      webTestClient.get()
        .uri("/bff/service-end-date-page/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ServiceEndDatePageDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.targetServiceCompletionDate?.toInstant() shouldBe completionDate.toInstant()
          body.targetServiceCompletionReason shouldBe "Extended due to complexity"
        }
    }

    @Test
    fun `should return OK with null service end date data when unset on referral`() {
      val cprPersonDTO = createCprProbationPersonDto(CRN)
      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprPersonDTO.toJson()),
          ),
      )

      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPersonFromCprPersonDTO(cprPersonDTO)
      personRepository.save(person)

      val savedReferral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
        targetServiceCompletionDate = null,
        targetServiceCompletionDateReason = null,
      )

      webTestClient.get()
        .uri("/bff/service-end-date-page/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ServiceEndDatePageDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.targetServiceCompletionDate shouldBe null
          body.targetServiceCompletionReason shouldBe null
        }
    }

    @Test
    fun `should return Not Found with invalid referral identifier`() {
      assertNotFound(GET, "/bff/service-end-date-page/${UUID.randomUUID()}")
    }
  }

  @Nested
  @DisplayName("GET /bff/service-days-page/{referralId}")
  inner class ServiceDaysPageEndPoint {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/service-days-page/${UUID.randomUUID()}")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(GET, "/bff/service-days-page/${UUID.randomUUID()}")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(GET, "/bff/service-days-page/${UUID.randomUUID()}")
    }

    @Test
    fun `should return OK with service days when set on referral`() {
      val cprPersonDTO = createCprProbationPersonDto(CRN)
      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprPersonDTO.toJson()),
          ),
      )

      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPersonFromCprPersonDTO(cprPersonDTO)
      personRepository.save(person)

      val savedReferral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
        serviceDays = 60,
      )

      webTestClient.get()
        .uri("/bff/service-days-page/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ServiceDaysPageDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.serviceDays shouldBe 60
        }
    }

    @Test
    fun `should return OK with null service days when unset on referral`() {
      val cprPersonDTO = createCprProbationPersonDto(CRN)
      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprPersonDTO.toJson()),
          ),
      )

      val testUser = referralHelper.createTestUser()
      val person = referralHelper.createPersonFromCprPersonDTO(cprPersonDTO)
      personRepository.save(person)

      val savedReferral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
        serviceDays = null,
      )

      webTestClient.get()
        .uri("/bff/service-days-page/${savedReferral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ServiceDaysPageDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.serviceDays shouldBe null
        }
    }

    @Test
    fun `should return Not Found with invalid referral identifier`() {
      assertNotFound(GET, "/bff/service-days-page/${UUID.randomUUID()}")
    }
  }

  @Nested
  @DisplayName("GET /bff/referral-details/{referralId}/progress")
  inner class ReferralProgressPageEndPoint {
    val referralId = UUID.randomUUID().toString()
    val referralCaseRef = "AA1234DD"

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/referral-details/$referralId/progress")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(GET, "/bff/referral-details/$referralId/progress")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(GET, "/bff/referral-details/$referralId/progress")
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      assertNotFound(GET, "/bff/referral-details/$referralId/progress")
    }

    @Test
    fun `should return an Referral Progress object with an empty appointments list when no appointments exist for referral`() {
      val person = referralHelper.createPerson()
      val referralUser = referralHelper.ensureReferralUser()
      val referral = referralHelper.createReferral(person, submittedBy = referralUser)

      webTestClient.get()
        .uri("/bff/referral-details/${referral.id}/progress")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<ReferralProgressDto>()
        .consumeWith { response ->
          val referralProgressDto = response.responseBody!!

          referralProgressDto.referralId shouldBe referral.id
          referralProgressDto.fullName shouldBe person.firstName + " " + person.lastName
          referralProgressDto.appointments.size shouldBe 0
          referralProgressDto.actionPlanStatus.actionPlanId shouldNotBe null
          referralProgressDto.actionPlanStatus.status.submitted shouldBe false
          referralProgressDto.actionPlanStatus.status.statusText shouldBe "Not Submitted"
          referralProgressDto.actionPlanStatus.status.tag shouldBe "govuk-tag--blue"
        }
    }

    @Test
    fun `should return a referral Progress object when given a case reference`() {
      val person = referralHelper.createPerson()
      val referralUser = referralHelper.ensureReferralUser()
      val referral = referralHelper.createReferral(person, submittedBy = referralUser, referenceNumber = referralCaseRef)

      webTestClient.get()
        .uri("/bff/referral-details/$referralCaseRef/progress")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<ReferralProgressDto>()
        .consumeWith { response ->
          val referralProgressDto = response.responseBody!!

          referralProgressDto.referralId shouldBe referral.id
          referralProgressDto.fullName shouldBe person.firstName + " " + person.lastName
          referralProgressDto.appointments.size shouldBe 0
          referralProgressDto.actionPlanStatus.actionPlanId shouldNotBe null
          referralProgressDto.actionPlanStatus.status.submitted shouldBe false
          referralProgressDto.actionPlanStatus.status.statusText shouldBe "Not Submitted"
          referralProgressDto.actionPlanStatus.status.tag shouldBe "govuk-tag--blue"
        }
    }

    @Test
    fun `should returns 500 when an ICS is missing from referral progress`() {
      val person = referralHelper.createPerson()
      val referralUser = referralHelper.ensureReferralUser()
      val referral = referralHelper.createReferral(person, submittedBy = referralUser)

      appointmentHelper.createAppointment(referral)

      webTestClient.get()
        .uri("/bff/referral-details/${referral.id}/progress")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().is5xxServerError
    }

    @Test
    fun `should return list containing referral progress Dto`() {
      val appointmentDateTime = LocalDateTime.of(2026, 3, 4, 15, 30)
      val oneWeekAgo = appointmentDateTime.minusWeeks(1)

      val person = referralHelper.createPerson()
      val referralUser = referralHelper.ensureReferralUser()
      val referral = referralHelper.createReferral(person, submittedBy = referralUser)
      val appointment = appointmentHelper.createAppointment(referral)

      appointmentHelper.createAppointmentStatusHistory(
        appointment,
        AppointmentStatusHistoryType.SCHEDULED,
        oneWeekAgo,
      )
      appointmentHelper.createAppointmentStatusHistory(
        appointment,
        AppointmentStatusHistoryType.NEEDS_FEEDBACK,
        appointmentDateTime,
      )

      val ics = appointmentHelper.createAppointmentIcs(
        appointment,
        delivery = appointmentHelper.createAppointmentDelivery(),
        user = referralUser,
        appointmentDateTime = appointmentDateTime,
        createdAt = oneWeekAgo,
        communications = listOf("EMAIL", "SMS", "LETTER"),
      )

      webTestClient.get()
        .uri("/bff/referral-details/${referral.id}/progress")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<ReferralProgressDto>()
        .consumeWith { response ->
          val referralProgressDto = response.responseBody!!

          referralProgressDto.referralId shouldBe referral.id
          referralProgressDto.fullName shouldBe person.firstName + " " + person.lastName
          referralProgressDto.appointments.size shouldBe 1
          referralProgressDto.appointments[0].appointmentIcsId shouldBe ics.id
          referralProgressDto.appointments[0].dateTime shouldBe appointmentDateTime
          referralProgressDto.appointments[0].status shouldBe AppointmentStatusHistoryType.NEEDS_FEEDBACK
          referralProgressDto.actionPlanStatus.actionPlanId shouldNotBe null
          referralProgressDto.actionPlanStatus.status.submitted shouldBe false
          referralProgressDto.actionPlanStatus.status.statusText shouldBe "Not Submitted"
          referralProgressDto.actionPlanStatus.status.tag shouldBe "govuk-tag--blue"
        }
    }
  }

  @Nested
  @DisplayName("PATCH /referral/{referralId}/service-end-date")
  inner class ServiceEndDatePageUpdateEndPoint {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      webTestClient.patch()
        .uri("/referral/${UUID.randomUUID()}/service-end-date")
        .bodyValue(ServiceEndDatePageDto(null, null))
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `should return forbidden if no role`() {
      webTestClient.patch()
        .uri("/referral/${UUID.randomUUID()}/service-end-date")
        .headers(setAuthorisation("AUTH_ADM", listOf(), listOf("read")))
        .bodyValue(ServiceEndDatePageDto(null, null))
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `should return forbidden if wrong role`() {
      webTestClient.patch()
        .uri("/referral/${UUID.randomUUID()}/service-end-date")
        .headers(setAuthorisation(roles = listOf("ROLE_WRONG")))
        .bodyValue(ServiceEndDatePageDto(null, null))
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `should update the service end date details for a referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val referral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
        targetServiceCompletionDate = null,
        targetServiceCompletionDateReason = null,
      )

      val completionDate = OffsetDateTime.now().plusMonths(2).withNano(0)
      val request = ServiceEndDatePageDto(
        targetServiceCompletionDate = completionDate,
        targetServiceCompletionReason = "Needs more time",
      )

      webTestClient.patch()
        .uri("/referral/${referral.id}/service-end-date")
        .headers(setAuthorisation())
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ServiceEndDatePageDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          body.targetServiceCompletionDate?.toInstant() shouldBe completionDate.toInstant()
          body.targetServiceCompletionReason shouldBe "Needs more time"
        }

      val updatedReferral = referralRepository.findById(referral.id).get()
      updatedReferral.targetServiceCompletionDate?.toInstant() shouldBe completionDate.toInstant()
      updatedReferral.targetServiceCompletionDateReason shouldBe "Needs more time"
      updatedReferral.referralEvents.any { it.eventType == ReferralEventType.UPDATED } shouldBe false
    }

    @Test
    fun `should return Not Found with invalid referral identifier`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      webTestClient.patch()
        .uri("/referral/${UUID.randomUUID()}/service-end-date")
        .headers(setAuthorisation())
        .bodyValue(ServiceEndDatePageDto(null, null))
        .exchange()
        .expectStatus()
        .isNotFound
    }
  }

  @Nested
  @DisplayName("PATCH /draft-referral/{referralId}/service-days")
  inner class ServiceDaysPageUpdateEndPoint {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      webTestClient.patch()
        .uri("/draft-referral/${UUID.randomUUID()}/service-days")
        .bodyValue(ServiceDaysPageDto(null))
        .exchange()
        .expectStatus()
        .isUnauthorized
    }

    @Test
    fun `should return forbidden if no role`() {
      webTestClient.patch()
        .uri("/draft-referral/${UUID.randomUUID()}/service-days")
        .headers(setAuthorisation("AUTH_ADM", listOf(), listOf("read")))
        .bodyValue(ServiceDaysPageDto(null))
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `should return forbidden if wrong role`() {
      webTestClient.patch()
        .uri("/draft-referral/${UUID.randomUUID()}/service-days")
        .headers(setAuthorisation(roles = listOf("ROLE_WRONG")))
        .bodyValue(ServiceDaysPageDto(null))
        .exchange()
        .expectStatus()
        .isForbidden
    }

    @Test
    fun `should update service days for a referral`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      val person = referralHelper.createPerson()
      val referral = referralHelper.createReferral(
        person = person,
        submittedBy = testUser,
        serviceDays = null,
      )

      webTestClient.patch()
        .uri("/draft-referral/${referral.id}/service-days")
        .headers(setAuthorisation())
        .bodyValue(ServiceDaysPageDto(35))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ServiceDaysPageDto>()
        .consumeWith { response ->
          val body = response.responseBody!!
          body.serviceDays shouldBe 35
        }

      val updatedReferral = referralRepository.findById(referral.id).get()
      updatedReferral.serviceDays shouldBe 35
      updatedReferral.referralEvents.any { it.eventType == ReferralEventType.UPDATED } shouldBe false
    }

    @Test
    fun `should return Not Found with invalid referral identifier`() {
      whenever(userMapper.fromToken(any<HmppsAuthenticationHolder>())).thenReturn(testUser)

      webTestClient.patch()
        .uri("/draft-referral/${UUID.randomUUID()}/service-days")
        .headers(setAuthorisation())
        .bodyValue(ServiceDaysPageDto(null))
        .exchange()
        .expectStatus()
        .isNotFound
    }
  }

  @Nested
  @DisplayName("GET /bff/referral-details/{caseReference}/ics")
  inner class GetICSDetailsEndPoint {
    val caseReference = "AA1234DD"

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/referral-details/$caseReference/ics")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(GET, "/bff/referral-details/$caseReference/ics")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(GET, "/bff/referral-details/$caseReference/ics")
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      assertNotFound(GET, "/bff/referral-details/$caseReference/ics")
    }

    @Test
    fun `should return 404 when referral has no appointments`() {
      val person = referralHelper.createPerson()
      val referralUser = referralHelper.ensureReferralUser()
      val referral = referralHelper.createReferral(person, submittedBy = referralUser, referenceNumber = caseReference)

      assertNotFound(GET, "/bff/referral-details/${referral.referenceNumber}/ics")
    }

    @Test
    fun `should return 200 when ICS details exists`() {
      val person = referralHelper.createPerson()
      val referralUser = referralHelper.ensureReferralUser()
      val referral = referralHelper.createReferral(person, submittedBy = referralUser, referenceNumber = caseReference)
      val appointment = appointmentHelper.createAppointment(referral)
      val appointmentDateTime = LocalDateTime.of(2026, 3, 27, 15, 0)
      val createdAt = appointmentDateTime.minusDays(1)
      val delivery = appointmentHelper.createAppointmentDelivery(
        AppointmentDeliveryMethod.VIDEO_CALL,
        "Zoom link",
      )
      val savedIcs = appointmentHelper.createAppointmentIcs(
        appointment,
        delivery,
        testUser,
        appointmentDateTime,
        createdAt,
        listOf("Email", "Phone call"),
      )

      appointmentHelper.createAppointmentStatusHistory(appointment)

      webTestClient.get()
        .uri("/bff/referral-details/$caseReference/ics")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<AppointmentIcsResponse>()
        .consumeWith { result ->
          val body = result.responseBody!!
          body.appointmentIcsId shouldBe savedIcs.id
          body.appointmentType shouldBe AppointmentType.ICS
          body.appointmentDate shouldBe LocalDate.of(2026, 3, 27)
          body.appointmentTime.hour shouldBe 3
          body.appointmentTime.amPm shouldBe "pm"
          body.appointmentTime.minute shouldBe 0
          assertThat(body.sessionMethod).isInstanceOf(VirtualAppointment::class.java)
          with(body.sessionMethod as VirtualAppointment) {
            type shouldBe "VIDEO"
            whyNotInPersonReason shouldBe "Zoom link"
          }
          body.sessionCommunications shouldBe listOf("Email", "Phone call")
        }
    }
  }

  @Nested
  @DisplayName("GET /bff/confirm-person-details/{referralId}")
  inner class ConfirmPersonDetailsEndPoint {

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return 200 with person details for a valid referralID`() {
      val referralUser = referralHelper.ensureReferralUser()
      val person = referralHelper.createPerson(identifier = CRN)
      val referral = referralHelper.createReferral(person, submittedBy = referralUser)

      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprProbationPersonJson(CRN)),
          ),
      )

      webTestClient.get()
        .uri("/bff/confirm-person-details/${referral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<ConfirmPersonDetailsBffDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          assertCommonPersonDetails(body, person)

          body.contactDetails.address.value shouldBe "1, Test Street, Testville, TE1 1ST"
          body.contactDetails.address.type shouldBe "Friends/Family (settled) (verified)"
          body.contactDetails.address.startAt shouldBe "2005-12-01"
          body.contactDetails.address.notes shouldBe "No notes"
          body.contactDetails.address.noFixedAbode shouldBe false
        }
    }

    @Test
    fun `should return noFixedAbode as true when the downstream address is marked as no fixed abode`() {
      val referralUser = referralHelper.ensureReferralUser()
      val person = referralHelper.createPerson(identifier = CRN)
      val referral = referralHelper.createReferral(person, submittedBy = referralUser)

      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprProbationPersonNoFixAbodeJson(CRN)),
          ),
      )

      webTestClient.get()
        .uri("/bff/confirm-person-details/${referral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<ConfirmPersonDetailsBffDto>()
        .consumeWith { response ->
          val body = response.responseBody!!

          assertCommonPersonDetails(body, person)

          body.contactDetails.address.noFixedAbode shouldBe true
        }
    }

    private fun assertCommonPersonDetails(body: ConfirmPersonDetailsBffDto, person: Person) {
      body.id shouldBe person.id

      body.personalDetails.firstName shouldBe "John"
      body.personalDetails.middleNames shouldBe "David"
      body.personalDetails.lastName shouldBe "Smith"
      body.personalDetails.crn shouldBe CRN
      body.personalDetails.dateOfBirth shouldBe LocalDate.of(1985, 1, 1)

      body.personalDetails.disabilities.allDisabilities shouldBe "None"

      body.equalityMonitoring.sex shouldBe "Male"
      body.equalityMonitoring.ethnicity shouldBe "White"
      body.equalityMonitoring.religionOrBelief shouldBe "Christian"
      body.equalityMonitoring.nationalities shouldBe listOf("Argentine", "Brazilian")

      body.contactDetails.phoneNumber shouldBe "01234567890"
      body.contactDetails.mobileNumber shouldBe "07700900002"
      body.contactDetails.emailAddress shouldBe "john.smith@example.com"
    }
  }

  @Nested
  @DisplayName("GET /bff/referral-information/{caseReference}")
  inner class ReferralInformationEndPoint {
    val caseReference = "AA1234DD"

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, "/bff/referral-information/$caseReference")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(GET, "/bff/referral-information/$caseReference")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(GET, "/bff/referral-information/$caseReference")
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      assertNotFound(GET, "/bff/referral-information/$caseReference")
    }

    @Test
    fun `should return 200 when referral information exists`() {
      val person = referralHelper.createPerson()
      val referralUser = referralHelper.ensureReferralUser()
      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createReferral(person, submittedBy = referralUser)

      val providerAssignment = ReferralProviderAssignmentFactory()
        .withReferral(referral)
        .withCommunityServiceProvider(communityServiceProvider)
        .create()
      referralProviderAssignmentRepository.save(providerAssignment)

      webTestClient.get()
        .uri("/bff/referral-information/${referral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<ReferralInformationDto>()
        .consumeWith { result ->
          val body = result.responseBody!!
          body.referralId shouldBe referral.id
          body.personIdentifier shouldBe referral.personIdentifier
          body.firstName shouldBe person.firstName
          body.lastName shouldBe person.lastName
        }
    }
  }

  @Nested
  @DisplayName("GET /bff/referral/check-referral-information/{caseIdentifier}")
  inner class CheckReferralInformationEndPoint {
    val caseReference = "AA1234DD"
    private val url = "/bff/referral/check-referral-information/$caseReference"

    @BeforeEach
    fun setup() {
      testDataCleaner.cleanAllTables()
      testUser = referralHelper.ensureReferralUser()
    }

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(GET, url)
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(GET, url)
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(GET, url)
    }

    @Test
    fun `should return 404 when referral does not exist`() {
      assertNotFound(GET, url)
    }

    @Test
    fun `should return 200 when referral information exists`() {
      val cprPersonDTO = createCprProbationPersonDto(CRN)
      stubFor(
        get(urlEqualTo("/person/probation/$CRN"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(cprPersonDTO.toJson()),
          ),
      )
      val referralUser = referralHelper.ensureReferralUser()
      val person = referralHelper.createPersonFromCprPersonDTO(cprPersonDTO)

      val communityServiceProvider = referralHelper.getCommunityServiceProvider()
      val referral = referralHelper.createReferral(person, submittedBy = referralUser)
      referralHelper.createProviderAssignment(referral, communityServiceProvider)

      val providerAssignment = ReferralProviderAssignmentFactory()
        .withReferral(referral)
        .withCommunityServiceProvider(communityServiceProvider)
        .create()
      referralProviderAssignmentRepository.save(providerAssignment)

      webTestClient.get()
        .uri("/bff/referral/check-referral-information/${referral.id}")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody<CheckReferralInformationDto>()
        .consumeWith { result ->
          val body = result.responseBody!!

          body.referralId shouldBe referral.id
          body.communityServiceProviderName shouldBe communityServiceProvider.name
          body.region shouldBe communityServiceProvider.contractArea.region.name
          body.deliveryPartner shouldBe communityServiceProvider.serviceProvider.name
          body.personIdentifier shouldBe referral.personIdentifier
          body.prisonNumbers shouldBe if (person.prisonNumbers != null) person.prisonNumbers.toString().split(", ") else emptyList<String>()
          body.fullName shouldBe "${person.firstName} ${person.lastName}"
          body.dateOfBirth shouldBe person.dateOfBirth.toFormattedDateOfBirth()
          body.sex shouldBe person.gender
        }
    }
  }
}
