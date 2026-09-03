package uk.gov.justice.digital.hmpps.communitysupportapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ServiceDaysPageDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ServiceEndDatePageDto
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Referral
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentIcsFeedbackRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentIcsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentStatusHistoryRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PersonRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralProviderAssignmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralUserAssignmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralWithdrawalDetailsRepository
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ReferralServiceTest {

  @Mock
  lateinit var referralRepository: ReferralRepository

  @Mock
  lateinit var personRepository: PersonRepository

  @Mock
  lateinit var appointmentRepository: AppointmentRepository

  @Mock
  lateinit var appointmentIcsRepository: AppointmentIcsRepository

  @Mock
  lateinit var appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository

  @Mock
  lateinit var referralProviderAssignmentRepository: ReferralProviderAssignmentRepository

  @Mock
  lateinit var referralUserAssignmentRepository: ReferralUserAssignmentRepository

  @Mock
  lateinit var referralWithdrawalDetailsRepository: ReferralWithdrawalDetailsRepository

  @Mock
  lateinit var referenceGenerator: ReferralReferenceGenerator

  @Mock
  lateinit var appointmentIcsFeedbackRepository: AppointmentIcsFeedbackRepository

  @Mock
  lateinit var cprProbationService: CprProbationService

  @Mock
  lateinit var referralLookupService: ReferralLookupService

  @Mock
  lateinit var identifierValidator: uk.gov.justice.digital.hmpps.communitysupportapi.validation.PersonIdentifierValidator

  @Mock
  lateinit var personService: PersonService

  @Mock
  lateinit var actionPlanService: ActionPlanService

  @InjectMocks
  lateinit var referralService: ReferralService

  @Test
  fun `updateReferralServiceEndDate should update referral without adding an event`() {
    val referralId = UUID.randomUUID()
    val personId = UUID.randomUUID()
    val createdAt = OffsetDateTime.parse("2026-08-13T10:09:02Z")
    val completionDate = OffsetDateTime.parse("2026-11-13T10:09:02Z")
    val referral = Referral(
      id = referralId,
      personId = personId,
      personIdentifier = "X123456",
      createdAt = createdAt,
      createdBy = UUID.randomUUID(),
      targetServiceCompletionDate = null,
      targetServiceCompletionDateReason = null,
    )

    whenever(referralRepository.findById(referralId)).thenReturn(java.util.Optional.of(referral))
    whenever(referralRepository.save(any<Referral>())).thenAnswer { invocation -> invocation.arguments[0] as Referral }

    val request = ServiceEndDatePageDto(
      targetServiceCompletionDate = completionDate,
      targetServiceCompletionReason = "Extended due to complexity",
    )

    val result = referralService.updateReferralServiceEndDate(referralId, request)
    val savedReferralCaptor = argumentCaptor<Referral>()

    assertThat(result.targetServiceCompletionDate).isEqualTo(completionDate)
    assertThat(result.targetServiceCompletionReason).isEqualTo("Extended due to complexity")

    verify(referralRepository).save(savedReferralCaptor.capture())
    val savedReferral = savedReferralCaptor.firstValue

    assertThat(savedReferral.targetServiceCompletionDate).isEqualTo(completionDate)
    assertThat(savedReferral.targetServiceCompletionDateReason).isEqualTo("Extended due to complexity")
    assertThat(savedReferral.referralEvents).isEmpty()
  }

  @Test
  fun `updateReferralServiceEndDate should return not found when referral does not exist`() {
    whenever(referralRepository.findById(any())).thenReturn(java.util.Optional.empty())

    org.junit.jupiter.api.assertThrows<uk.gov.justice.digital.hmpps.communitysupportapi.exception.NotFoundException> {
      referralService.updateReferralServiceEndDate(UUID.randomUUID(), ServiceEndDatePageDto(null, null))
    }
  }

  @Test
  fun `updateReferralServiceDays should update referral without adding an event`() {
    val referralId = UUID.randomUUID()
    val personId = UUID.randomUUID()
    val createdAt = OffsetDateTime.parse("2026-08-13T10:09:02Z")
    val referral = Referral(
      id = referralId,
      personId = personId,
      personIdentifier = "X123456",
      createdAt = createdAt,
      createdBy = UUID.randomUUID(),
      serviceDays = null,
    )

    whenever(referralRepository.findById(referralId)).thenReturn(java.util.Optional.of(referral))
    whenever(referralRepository.save(any<Referral>())).thenAnswer { invocation -> invocation.arguments[0] as Referral }

    val result = referralService.updateReferralServiceDays(referralId, ServiceDaysPageDto(42))
    val savedReferralCaptor = argumentCaptor<Referral>()

    assertThat(result.serviceDays).isEqualTo(42)

    verify(referralRepository).save(savedReferralCaptor.capture())
    val savedReferral = savedReferralCaptor.firstValue

    assertThat(savedReferral.serviceDays).isEqualTo(42)
    assertThat(savedReferral.referralEvents).isEmpty()
  }

  @Test
  fun `updateReferralServiceDays should return not found when referral does not exist`() {
    whenever(referralRepository.findById(any())).thenReturn(java.util.Optional.empty())

    org.junit.jupiter.api.assertThrows<uk.gov.justice.digital.hmpps.communitysupportapi.exception.NotFoundException> {
      referralService.updateReferralServiceDays(UUID.randomUUID(), ServiceDaysPageDto(null))
    }
  }
}
