package uk.gov.justice.digital.hmpps.communitysupportapi.service

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import jakarta.validation.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.cpr.CprCodeDescriptionDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.cpr.CprIdentifiersDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.cpr.CprPersonDto
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlan
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanEvent
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanEventType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActorType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.AppointmentStatusHistoryType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralEventType
import uk.gov.justice.digital.hmpps.communitysupportapi.exception.ConflictException
import uk.gov.justice.digital.hmpps.communitysupportapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.AppointmentTestSupport
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.PersonTestSupport
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.ReferralTestSupport
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.ReferralWithdrawalReasonCode
import uk.gov.justice.digital.hmpps.communitysupportapi.model.WithdrawReferralRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanEventRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanTemplateRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentDeliveryRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentIcsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentStatusHistoryRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PersonAdditionalSupportNeedsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PersonRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralProviderAssignmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralUserRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralWithdrawalDetailsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.createCprPrisonPersonDto
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.createCprProbationPersonDto
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.createHomeOfficeInterest
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.createPersonDetailsAndCircumstances
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.factory.ReferralProviderAssignmentFactory
import uk.gov.justice.digital.hmpps.communitysupportapi.util.toJson
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ReferralServiceIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var referralProviderAssignmentRepository: ReferralProviderAssignmentRepository

  @Autowired
  private lateinit var personRepository: PersonRepository

  @Autowired
  private lateinit var referralService: ReferralService

  @Autowired
  private lateinit var referralUserRepository: ReferralUserRepository

  @Autowired
  private lateinit var referralRepository: ReferralRepository

  @Autowired
  private lateinit var referralWithdrawalDetailsRepository: ReferralWithdrawalDetailsRepository

  @Autowired
  private lateinit var actionPlanRepository: ActionPlanRepository

  @Autowired
  private lateinit var actionPlanEventRepository: ActionPlanEventRepository

  @Autowired
  private lateinit var actionPlanTemplateRepository: ActionPlanTemplateRepository

  @Autowired
  private lateinit var appointmentRepository: AppointmentRepository

  @Autowired
  private lateinit var appointmentIcsRepository: AppointmentIcsRepository

  @Autowired
  private lateinit var appointmentDeliveryRepository: AppointmentDeliveryRepository

  @Autowired
  private lateinit var appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository

  @Autowired
  private lateinit var referralAssignmentService: ReferralAssignmentService

  @Autowired
  private lateinit var appointmentHelper: AppointmentTestSupport

  @Autowired
  private lateinit var referralHelper: ReferralTestSupport

  @Autowired
  private lateinit var personHelper: PersonTestSupport

  @Autowired
  private lateinit var personAdditionSupportNeedsRepository: PersonAdditionalSupportNeedsRepository

  private companion object {
    const val NON_EXISTENT_REFERRAL_REFERENCE = "ZZ9999YY"
    const val NON_EXISTENT_CRN = "X888888"
    const val NON_EXISTENT_PRISON = "Z1234YY"
    const val EXISTING_CRN = "X666666"
  }

  @Test
  fun `createReferral should save referral and referral events`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral

    assertThat(savedReferral.personId).isEqualTo(result.person.id)
    assertThat(savedReferral.personIdentifier).isEqualTo(createReferralRequest.personIdentifier)
    assertThat(savedReferral.referralEvents.size).isEqualTo(1)
    assertThat(savedReferral.referenceNumber).isNull()
    assertThat(savedReferral.createdBy).isEqualTo(referralUser.id)

    val createdEvent = savedReferral.referralEvents.first { it.eventType == ReferralEventType.CREATED }
    assertThat(createdEvent).isNotNull
    assertThat(createdEvent.actorType).isEqualTo(ActorType.AUTH)
    assertThat(createdEvent.actorId).isEqualTo(referralUser.id)
  }

  @Test
  fun `createReferral should update existing person when identifier matches`() {
    val referralUser = referralHelper.ensureReferralUser()
    val crn = "X123456"

    val existingPerson = referralHelper.createPerson(
      firstName = "Old",
      lastName = "Name",
      identifier = crn,
      dateOfBirth = LocalDate.of(1970, 1, 1),
    )

    stubCprProbationPerson(crn, createCprProbationPersonDto(crn).copy(dateOfBirth = "1980-01-01"))

    val request = CreateReferralRequest(personIdentifier = crn)

    val result = referralService.createReferral(referralUser.id, request)

    val persistedPerson = personRepository.findById(existingPerson.id).get()

    assertThat(result.referral.personId).isEqualTo(existingPerson.id)
    assertThat(persistedPerson.firstName).isEqualTo("John")
    assertThat(persistedPerson.lastName).isEqualTo("Smith")
    assertThat(persistedPerson.dateOfBirth).isEqualTo(LocalDate.of(1980, 1, 1))
  }

  @Test
  fun `createReferral should update existing person additional details when identifier matches`() {
    val referralUser = referralHelper.ensureReferralUser()
    val crn = "X999999"

    val existingPerson = referralHelper.createPerson(
      firstName = "Old",
      lastName = "Name",
      identifier = crn,
      dateOfBirth = LocalDate.of(1975, 5, 5),
    )

    val existingDetails = referralHelper.createPersonAdditionalDetails(existingPerson)

    existingPerson.additionalDetails = existingDetails

    personRepository.save(existingPerson)

    stubCprProbationPerson(
      crn,
      createCprProbationPersonDto(crn).copy(
        firstName = "NewFirst",
        lastName = "NewLast",
        dateOfBirth = "1985-06-06",
        ethnicity = CprCodeDescriptionDto(code = "NE", description = "NewEthnicity"),
      ),
    )
    setupNDeliusStubs(crn)

    val request = CreateReferralRequest(personIdentifier = crn)

    referralService.createReferral(referralUser.id, request)

    val persistedPerson = personRepository.findById(existingPerson.id).get()

    assertThat(persistedPerson.firstName).isEqualTo("NewFirst")
    assertThat(persistedPerson.lastName).isEqualTo("NewLast")
    assertThat(persistedPerson.dateOfBirth).isEqualTo(LocalDate.of(1985, 6, 6))
    assertThat(persistedPerson.additionalDetails?.ethnicity).isEqualTo("NewEthnicity")
    assertThat(persistedPerson.additionalDetails?.preferredLanguage).isNull()
    assertThat(persistedPerson.additionalDetails?.id).isEqualTo(existingDetails.id)
  }

  @Test
  fun `createReferral should persist prison numbers on person when provided`() {
    val referralUser = referralHelper.ensureReferralUser()
    val prisonNumber = "A1234BC"

    stubCprPrisonPerson(
      prisonNumber,
      createCprPrisonPersonDto(prisonNumber).copy(
        identifiers = CprIdentifiersDto(
          crns = emptyList(),
          prisonNumbers = listOf("A1234BC", "B5678DE"),
          pncs = listOf("12/394773H"),
          cros = listOf("29906/12J"),
        ),
      ),
    )
    setupNDeliusStubs(prisonNumber)

    val request = CreateReferralRequest(personIdentifier = prisonNumber)

    val result = referralService.createReferral(referralUser.id, request)
    val persistedPerson = personRepository.findById(result.person.id).get()

    assertThat(persistedPerson.prisonNumbers).isEqualTo("A1234BC,B5678DE")
  }

  @Test
  fun `createReferral should update prison numbers on existing person`() {
    val referralUser = referralHelper.ensureReferralUser()
    val prisonNumber = "A9999ZZ"

    val existingPerson = referralHelper.createPerson(
      firstName = "John",
      lastName = "Smith",
      identifier = prisonNumber,
      dateOfBirth = LocalDate.of(1980, 1, 1),
    )

    stubCprPrisonPerson(
      prisonNumber,
      createCprPrisonPersonDto(prisonNumber).copy(
        firstName = "John",
        lastName = "Smith",
        dateOfBirth = "1980-01-01",
        identifiers = CprIdentifiersDto(
          crns = emptyList(),
          prisonNumbers = listOf(prisonNumber),
          pncs = listOf("12/394773H"),
          cros = listOf("29906/12J"),
        ),
      ),
    )
    setupNDeliusStubs(prisonNumber)

    val request = CreateReferralRequest(personIdentifier = prisonNumber)

    referralService.createReferral(referralUser.id, request)

    val persistedPerson = personRepository.findById(existingPerson.id).get()
    assertThat(persistedPerson.prisonNumbers).isEqualTo(prisonNumber)
  }

  @Test
  fun `createReferral should convert a CPR probation 404 into a non-404 failure and persist nothing`() {
    val referralUser = referralHelper.ensureReferralUser()

    stubFor(
      get(urlPathEqualTo("/person/probation/$NON_EXISTENT_CRN"))
        .willReturn(aResponse().withStatus(404)),
    )

    val request = CreateReferralRequest(personIdentifier = NON_EXISTENT_CRN)

    val exception = assertThrows(RuntimeException::class.java) {
      referralService.createReferral(referralUser.id, request)
    }

    assertThat(exception).isNotInstanceOf(NotFoundException::class.java)
    assertThat(personRepository.findByIdentifier(NON_EXISTENT_CRN)).isNull()
    assertThat(referralRepository.findAll()).isEmpty()
  }

  @Test
  fun `createReferral should throw and persist nothing when CPR probation lookup returns a server error`() {
    val referralUser = referralHelper.ensureReferralUser()
    val crn = "X777777"

    stubFor(
      get(urlPathEqualTo("/person/probation/$crn"))
        .willReturn(aResponse().withStatus(500)),
    )

    val request = CreateReferralRequest(personIdentifier = crn)

    assertThrows(RuntimeException::class.java) {
      referralService.createReferral(referralUser.id, request)
    }

    assertThat(personRepository.findByIdentifier(crn)).isNull()
    assertThat(referralRepository.findAll()).isEmpty()
  }

  @Test
  fun `createReferral should convert a CPR prison 404 into a non-404 failure and persist nothing`() {
    val referralUser = referralHelper.ensureReferralUser()

    stubFor(
      get(urlPathEqualTo("/person/prison/$NON_EXISTENT_PRISON"))
        .willReturn(aResponse().withStatus(404)),
    )

    val request = CreateReferralRequest(personIdentifier = NON_EXISTENT_PRISON)

    val exception = assertThrows(RuntimeException::class.java) {
      referralService.createReferral(referralUser.id, request)
    }

    assertThat(exception).isNotInstanceOf(NotFoundException::class.java)
    assertThat(personRepository.findByIdentifier(NON_EXISTENT_PRISON)).isNull()
    assertThat(referralRepository.findAll()).isEmpty()
  }

  @Test
  fun `createReferral should not update an existing person when CPR lookup fails`() {
    val referralUser = referralHelper.ensureReferralUser()

    val existingPerson = referralHelper.createPerson(
      firstName = "Original",
      lastName = "Person",
      identifier = EXISTING_CRN,
      dateOfBirth = LocalDate.of(1970, 1, 1),
    )

    stubFor(
      get(urlPathEqualTo("/person/probation/$EXISTING_CRN"))
        .willReturn(aResponse().withStatus(500)),
    )

    val request = CreateReferralRequest(personIdentifier = EXISTING_CRN)

    assertThrows(RuntimeException::class.java) {
      referralService.createReferral(referralUser.id, request)
    }

    val persistedPerson = personRepository.findById(existingPerson.id).get()
    assertThat(persistedPerson.firstName).isEqualTo("Original")
    assertThat(persistedPerson.lastName).isEqualTo("Person")
    assertThat(referralRepository.findAll()).isEmpty()
  }

  @Test
  fun `createReferral should still throw ValidationException for a malformed person identifier without calling CPR`() {
    val referralUser = referralHelper.ensureReferralUser()

    val request = CreateReferralRequest(personIdentifier = "NOT-VALID")

    assertThrows(ValidationException::class.java) {
      referralService.createReferral(referralUser.id, request)
    }

    assertThat(referralRepository.findAll()).isEmpty()
  }

  @Test
  fun `submitReferral should create a referral and referral number`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral
    val provider = referralHelper.getCommunityServiceProvider()
    referralProviderAssignmentRepository.save(ReferralProviderAssignmentFactory.anAssignment(savedReferral, provider))

    val submissionResult = referralService.submitReferral(savedReferral.id, referralUser.id)

    assertThat(submissionResult).isNotNull()
    assertThat(savedReferral.id).isEqualTo(submissionResult.referralId)
    assertThat(submissionResult.referenceNumber).isNotNull()
  }

  @Test
  fun `submitReferral should create an action plan pointing to the referral`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral

    assertThat(actionPlanRepository.findByReferralId(savedReferral.id)).isNull()

    val provider = referralHelper.getCommunityServiceProvider()
    referralProviderAssignmentRepository.save(ReferralProviderAssignmentFactory.anAssignment(savedReferral, provider))

    referralService.submitReferral(savedReferral.id, referralUser.id)

    val actionPlan = actionPlanRepository.findByReferralId(savedReferral.id)
    assertThat(actionPlan).isNotNull()
    assertThat(actionPlan?.referralId).isEqualTo(savedReferral.id)
    assertThat(actionPlan!!.actionPlanTemplateId).isEqualTo(globalTemplateId())

    val actionPlanEvents = actionPlanEventRepository.findByActionPlanId(actionPlan.id)
    assertThat(actionPlanEvents).isNotNull()
    assertThat(actionPlanEvents).hasSize(1)
    assertThat(actionPlanEvents.first().eventType).isEqualTo(ActionPlanEventType.CREATED)
  }

  @Test
  fun `submitReferral should not create a new action plan when referral has already been submitted`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral
    val communityServiceProvider = referralHelper.getCommunityServiceProvider()
    referralHelper.createProviderAssignment(savedReferral, communityServiceProvider)
    referralService.submitReferral(savedReferral.id, referralUser.id)

    assertThat(actionPlanRepository.findByReferralId(savedReferral.id)).isNotNull()

    assertThrows(ConflictException::class.java) {
      referralService.submitReferral(savedReferral.id, referralUser.id)
    }

    assertThat(actionPlanRepository.findByReferralId(savedReferral.id)).isNotNull()
  }

  @Test
  fun `submitReferral should not create a duplicate action plan when one already exists`() {
    val referralUser = referralHelper.ensureReferralUser()
    val createReferralRequest = setUpData()

    val result = referralService.createReferral(referralUser.id, createReferralRequest)
    val savedReferral = result.referral

    val actionPlanTemplateId = globalTemplateId()
    actionPlanRepository.save(ActionPlan.forReferral(actionPlanTemplateId, savedReferral.id))

    assertThat(actionPlanRepository.findByReferralId(savedReferral.id)).isNotNull()

    val provider = referralHelper.getCommunityServiceProvider()
    referralProviderAssignmentRepository.save(ReferralProviderAssignmentFactory.anAssignment(savedReferral, provider))

    referralService.submitReferral(savedReferral.id, referralUser.id)

    assertThat(actionPlanRepository.findByReferralId(savedReferral.id)).isNotNull()
    assertThat(referralRepository.findById(savedReferral.id).get().submittedEvent).isNotNull()
  }

  @Test
  fun `withdrawReferral should save withdrawal details and withdrawal event`() {
    val referralUser = referralHelper.ensureReferralUser()
    val referral = referralHelper.createReferral(submittedBy = referralUser)

    referralService.withdrawReferral(
      referral.referenceNumber!!,
      referralUser.id,
      WithdrawReferralRequest(
        reasonCode = ReferralWithdrawalReasonCode.NOT_ENGAGED,
        additionalDetails = "User is not actively engaged.",
      ),
    )

    val savedWithdrawalDetails = referralWithdrawalDetailsRepository.findByReferralId(referral.id)
    assertThat(savedWithdrawalDetails).isNotNull()
    assertThat(savedWithdrawalDetails?.reasonCode).isEqualTo("NOT_ENGAGED")
    assertThat(savedWithdrawalDetails?.reasonDetails).isEqualTo("User is not actively engaged.")
    assertThat(savedWithdrawalDetails?.createdBy).isEqualTo(referralUser.id)

    val latestReferral = referralRepository.findById(referral.id).get()

    val withdrawnEvent = latestReferral.referralEvents.last { it.eventType == ReferralEventType.WITHDRAWN }
    assertThat(withdrawnEvent).isNotNull
    assertThat(withdrawnEvent.actorId).isEqualTo(referralUser.id)
  }

  @Test
  fun `withdrawReferral should throw NotFoundException when referral does not exist`() {
    val referralUser = referralHelper.ensureReferralUser()

    assertThrows(NotFoundException::class.java) {
      referralService.withdrawReferral(
        NON_EXISTENT_REFERRAL_REFERENCE,
        referralUser.id,
        WithdrawReferralRequest(
          reasonCode = ReferralWithdrawalReasonCode.SENTENCE_EXPIRED,
          additionalDetails = "Some details",
        ),
      )
    }
  }

  @Test
  fun `withdrawReferral should throw ConflictException when referral already withdrawn`() {
    val referralUser = referralHelper.ensureReferralUser()
    val referral = referralHelper.createReferral(submittedBy = referralUser)
    val request = WithdrawReferralRequest(
      reasonCode = ReferralWithdrawalReasonCode.SENTENCE_EXPIRED,
      additionalDetails = "Some details",
    )

    referralService.withdrawReferral(referral.referenceNumber!!, referralUser.id, request)
    assertThat(referralWithdrawalDetailsRepository.findAll()).hasSize(1)

    assertThrows(ConflictException::class.java) {
      referralService.withdrawReferral(referral.referenceNumber!!, referralUser.id, request)
    }
    assertThat(referralWithdrawalDetailsRepository.findAll()).hasSize(1)
  }

  @Test
  fun `getReferralProgress should throw NotFoundException when referral does not exist`() {
    val nonExistentReferralId = UUID.randomUUID()

    assertThrows(NotFoundException::class.java) {
      referralService.getReferralProgress(nonExistentReferralId.toString())
    }
  }

  @Test
  fun `getReferralProgress should return dto with an empty appointments list when referral has no appointments`() {
    val person = referralHelper.createPerson()
    val referralUser = referralHelper.ensureReferralUser()
    val referral = referralHelper.createReferral(person, submittedBy = referralUser)

    val result = referralService.getReferralProgress(referral.id.toString())

    assertEquals(referral.id, result.referralId)
    assertEquals(person.firstName + " " + person.lastName, result.fullName)
    assertTrue(result.appointments.isEmpty())
    assertNotNull(result.actionPlanStatus.actionPlanId)
    assertFalse(result.actionPlanStatus.status.submitted)
    assertEquals("Not Submitted", result.actionPlanStatus.status.statusText)
    assertEquals("govuk-tag--blue", result.actionPlanStatus.status.tag)
  }

  @Test
  fun `getReferralProgress should return submitted action plan status when submitted event exists`() {
    val person = referralHelper.createPerson()
    val referralUser = referralHelper.ensureReferralUser()
    val referral = referralHelper.createReferral(person, submittedBy = referralUser)

    val actionPlan = actionPlanRepository.save(ActionPlan.forReferral(globalTemplateId(), referral.id))
    actionPlanEventRepository.save(
      ActionPlanEvent(
        id = UUID.randomUUID(),
        actionPlanId = actionPlan.id,
        eventType = ActionPlanEventType.SUBMITTED,
      ),
    )

    val result = referralService.getReferralProgress(referral.id.toString())

    assertEquals(actionPlan.id, result.actionPlanStatus.actionPlanId)
    assertTrue(result.actionPlanStatus.status.submitted)
    assertEquals("Submitted", result.actionPlanStatus.status.statusText)
    assertEquals("govuk-tag--teal", result.actionPlanStatus.status.tag)
  }

  @Test
  fun `getReferralProgress should throw an IllegalStateException when ICS missing from appointments`() {
    val person = referralHelper.createPerson()
    val referralUser = referralHelper.ensureReferralUser()
    val referral = referralHelper.createReferral(person, submittedBy = referralUser)

    appointmentHelper.createAppointment(referral)

    assertThrows(IllegalStateException::class.java) {
      referralService.getReferralProgress(referral.id.toString())
    }
  }

  @Test
  fun `view referral detail page bff should return referral details with uuid`() {
    val referralUser = referralHelper.ensureReferralUser()
    val person = referralHelper.createPerson()
    val referral = referralHelper.createReferral(person, submittedBy = referralUser)

    referralHelper.assignCaseWorkers(referral, listOf(referralUser))
    stubCprProbationPerson(person.identifier, createCprProbationPersonDto(person.identifier))

    val result = referralService.getReferralDetailsPage(referral.id.toString())

    assertEquals(referral.id, result.id)
    assertEquals(referral.personIdentifier, result.personDetailsTableData.crn)
    assertEquals(referralUser.fullName, result.referralDetailsTableData.assignedTo.first().fullName)
    assertEquals(referralUser.hmppsAuthUsername, result.referralDetailsTableData.assignedTo.first().emailAddress)
  }

  @Test
  fun `view referral detail page bff should return referral details with case id`() {
    val referralUser = referralHelper.ensureReferralUser()
    val person = referralHelper.createPerson()
    val referral = referralHelper.createReferral(person, submittedBy = referralUser, referenceNumber = "AA1234BB")

    referralHelper.assignCaseWorkers(referral, listOf(referralUser))
    stubCprProbationPerson(person.identifier, createCprProbationPersonDto(person.identifier))

    val result = referralService.getReferralDetailsPage(referral.referenceNumber)

    assertEquals(referral.id, result.id)
    assertEquals(referral.personIdentifier, result.personDetailsTableData.crn)
    assertEquals(referralUser.fullName, result.referralDetailsTableData.assignedTo.first().fullName)
    assertEquals(referralUser.hmppsAuthUsername, result.referralDetailsTableData.assignedTo.first().emailAddress)
  }

  @Test
  fun `getReferralProgress should return a list containing a referral progress dto`() {
    val appointmentDateTime = LocalDateTime.of(2026, 3, 4, 15, 30)
    val yesterday = appointmentDateTime.minusDays(1)

    val person = referralHelper.createPerson()
    val referralUser = referralHelper.ensureReferralUser()
    val referral = referralHelper.createReferral(person, submittedBy = referralUser)
    val appointment = appointmentHelper.createAppointment(referral)

    appointmentHelper.createAppointmentStatusHistory(appointment, AppointmentStatusHistoryType.SCHEDULED, yesterday)
    appointmentHelper.createAppointmentStatusHistory(appointment, AppointmentStatusHistoryType.NEEDS_FEEDBACK, appointmentDateTime)
    appointmentHelper.createAppointmentStatusHistory(appointment, AppointmentStatusHistoryType.COMPLETED, appointmentDateTime.plusDays(1))

    val ics = appointmentHelper.createAppointmentIcs(
      appointment = appointment,
      delivery = appointmentHelper.createAppointmentDelivery(),
      user = referralUser,
      createdAt = yesterday,
      appointmentDateTime = appointmentDateTime,
      communications = listOf("EMAIL", "SMS", "LETTER"),
    )

    val icsFeedback = appointmentHelper.createIcsFeedback(ics = ics, createdBy = referralUser)

    val result = referralService.getReferralProgress(referral.id.toString())

    assertEquals(referral.id, result.referralId)
    assertEquals(person.firstName + " " + person.lastName, result.fullName)

    assertEquals(1, result.appointments.size)
    assertEquals(ics.id, result.appointments[0].appointmentIcsId)
    assertEquals(appointmentDateTime, result.appointments[0].dateTime)
    assertEquals(AppointmentStatusHistoryType.COMPLETED, result.appointments[0].status)
    assertEquals(icsFeedback.id, result.appointments[0].icsFeedbackId)
    assertNotNull(result.actionPlanStatus.actionPlanId)
    assertFalse(result.actionPlanStatus.status.submitted)
    assertEquals("Not Submitted", result.actionPlanStatus.status.statusText)
    assertEquals("govuk-tag--blue", result.actionPlanStatus.status.tag)
  }

  @Test
  fun `referral information bff should return referral information`() {
    val referralUser = referralHelper.ensureReferralUser()
    val person = referralHelper.createPerson()
    val communityServiceProvider = referralHelper.getCommunityServiceProvider()
    val referral = referralHelper.createReferral(person, submittedBy = referralUser)

    val providerAssignment = ReferralProviderAssignmentFactory()
      .withReferral(referral)
      .withCommunityServiceProvider(communityServiceProvider)
      .create()
    referralProviderAssignmentRepository.save(providerAssignment)

    val referralInformation = referralService.getReferralInformation(referral.id.toString())

    assertEquals(referral.id, referralInformation.referralId)
    assertEquals(referral.personIdentifier, referralInformation.personIdentifier)
    assertEquals(person.firstName, referralInformation.firstName)
    assertEquals(person.lastName, referralInformation.lastName)
    assertEquals(communityServiceProvider.id, referralInformation.communityServiceProviderId)
  }

  @Nested
  @DisplayName("getConfirmPersonDetailsBffDto")
  inner class GetConfirmPersonDetailsBffDtoo {
    @Test
    fun `Finding a person with both a CRN and a Prison Number`() {
      val theCrn = personHelper.generateCrn()
      val person = referralHelper.createPerson(identifier = theCrn)

      stubCprProbationPerson(
        theCrn,
        createCprProbationPersonDto(theCrn).copy(
          identifiers = CprIdentifiersDto(
            crns = listOf(theCrn),
            prisonNumbers = listOf("THE_PRISON_NUMBER"),
            pncs = listOf("2012/0052494Q"),
            cros = listOf("123456/24A"),
            nationalInsuranceNumbers = listOf("AA123456A"),
          ),
        ),
      )

      val result = referralService.getConfirmPersonDetailsBffDto(theCrn)

      assertEquals(person.id, result.id)
      assertEquals(theCrn, result.personalDetails.crn)
      assertEquals(listOf("THE_PRISON_NUMBER"), result.personalDetails.prisonNumbers)
      assertEquals("John", result.personalDetails.firstName)
      assertEquals("Smith", result.personalDetails.lastName)
    }

    @Test
    fun `should throw NotFoundException when person does not exist in the database`() {
      val unknownCrn = "DEFINITELYDOESNOTEXIST"

      assertThrows(NotFoundException::class.java) {
        referralService.getConfirmPersonDetailsBffDto(unknownCrn)
      }
    }
  }

  private fun setUpData(): CreateReferralRequest {
    val crn = "X123456"

    stubCprProbationPerson(crn, createCprProbationPersonDto(crn))

    return CreateReferralRequest(personIdentifier = crn)
  }

  private fun globalTemplateId(): UUID = actionPlanTemplateRepository.findFirstByActiveGlobalTrueOrderByIdAsc()?.id
    ?: throw NotFoundException("No active global action plan template found")

  private fun stubCprProbationPerson(crn: String, cprPersonDto: CprPersonDto) {
    stubFor(
      get(urlPathEqualTo("/person/probation/$crn"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(cprPersonDto.toJson()),
        ),
    )
  }

  private fun stubCprPrisonPerson(prisonNumber: String, cprPersonDto: CprPersonDto) {
    stubFor(
      get(urlPathEqualTo("/person/prison/$prisonNumber"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(cprPersonDto.toJson()),
        ),
    )
  }

  private fun setupNDeliusStubs(identifier: String) {
    stubFor(
      get(urlEqualTo("/case/$identifier"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(createPersonDetailsAndCircumstances()),
        ),
    )
    stubFor(
      get(urlEqualTo(("/case/$identifier/home-office-interest")))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(createHomeOfficeInterest()),
        ),
    )
  }
}
