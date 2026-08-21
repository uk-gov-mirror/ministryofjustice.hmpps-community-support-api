package uk.gov.justice.digital.hmpps.communitysupportapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.testcontainers.utility.Base58.randomString
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SelectionDto
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Person
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Referral
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.*
import uk.gov.justice.digital.hmpps.communitysupportapi.validation.PersonIdentifierValidator
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class DraftReferralServiceTest {
  @Mock
  lateinit var referralRepository: ReferralRepository

  @Mock
  lateinit var referralCriminogenicNeedsRepository: ReferralCriminogenicNeedsRepository

  @Mock
  lateinit var personRepository: PersonRepository

  @Mock
  lateinit var personAdditionalSupportNeedsRepository: PersonAdditionalSupportNeedsRepository

  @Mock
  lateinit var riskInformationRepository: RiskInformationRepository

  @Mock
  lateinit var pduRepository: PduRepository

  @Mock
  lateinit var communityServiceProviderRepository: CommunityServiceProviderRepository

  @Mock
  lateinit var referralProviderAssignmentRepository: ReferralProviderAssignmentRepository

  @Mock
  lateinit var referralOffenceSentenceRepository: ReferralOffenceSentenceRepository

  @Mock
  lateinit var identifierValidator: PersonIdentifierValidator

  @Mock
  lateinit var timePort: TimePort

  @Mock
  lateinit var nDeliusService: NDeliusService

  @InjectMocks
  lateinit var draftReferralService: DraftReferralService

  @Nested
  inner class AdditionalInformationForTheDeliveryPartner {
    val referralId: UUID = UUID.randomUUID()
    val personId: UUID = UUID.randomUUID()
    val createdAt: OffsetDateTime = OffsetDateTime.parse("2026-08-13T10:09:02Z")
    val updatedAt: OffsetDateTime = OffsetDateTime.parse("2026-08-14T11:12:14Z")
    val createdBy: UUID = UUID.randomUUID()
    val fixedInstant: OffsetDateTime = OffsetDateTime.parse("2026-08-26T12:00:00Z")

    var referral: Referral = Referral(
      id = referralId,
      personId = personId,
      personIdentifier = "X123456",
      createdAt = createdAt,
      createdBy = createdBy,
    )

    val person = Person(
      id = personId,
      identifier = "X123456",
      firstName = "Robert",
      lastName = "Smith",
      dateOfBirth = OffsetDateTime.parse("1968-08-13T10:09:02Z").toLocalDate(),
      gender = "doesn't matter",
      createdAt = createdAt,
      updatedAt = updatedAt,
      prisonNumbers = "",
      additionalDetails = null,
    )

    @BeforeEach
    fun setup() {
      reset(referralRepository, personRepository, timePort)
      referral = Referral(
        id = referralId,
        personId = personId,
        personIdentifier = "X123456",
        createdAt = createdAt,
        createdBy = createdBy,
      )
      whenever(referralRepository.findById(referralId)).thenReturn(Optional.of(referral))
      whenever(personRepository.findById(referral.personId)).thenReturn(Optional.of(person))
    }

    @Test
    fun shouldGiveUnansweredWhenUnpopulatedInReferral() {
      val result = draftReferralService.getAdditionalInformationForTheDeliveryPartner(referralId)

      assertThat(result.refereeName.firstName).isEqualTo("Robert")
      assertThat(result.refereeName.lastName).isEqualTo("Smith")
      assertThat(result.refereeName.middleName).isNull()

      assertThat(result.details).isEqualTo(SelectionDto.Unanswered)
    }

    @Test
    fun shouldGiveNoWhenHasAdditionalInformationForTheDeliveryPartnerIsFalse() {
      referral.hasAdditionalInformationForTheDeliveryPartner = false

      val result = draftReferralService.getAdditionalInformationForTheDeliveryPartner(referralId)

      assertThat(result.refereeName.firstName).isEqualTo("Robert")
      assertThat(result.refereeName.lastName).isEqualTo("Smith")
      assertThat(result.refereeName.middleName).isNull()

      assertThat(result.details).isEqualTo(SelectionDto.No)
    }

    @Test
    fun shouldGiveYesWhenHasAdditionalInformationForTheDeliveryPartnerIsTrueAndThereIsAValue() {
      val value = randomString(5)
      referral.hasAdditionalInformationForTheDeliveryPartner = true
      referral.additionalInformationForTheDeliveryPartner = value

      val result = draftReferralService.getAdditionalInformationForTheDeliveryPartner(referralId)

      assertThat(result.refereeName.firstName).isEqualTo("Robert")
      assertThat(result.refereeName.lastName).isEqualTo("Smith")
      assertThat(result.refereeName.middleName).isNull()

      assertThat(result.details).isEqualTo(SelectionDto.Yes(value))
    }

    @Test
    fun updateAdditionalInformationForTheDeliveryPartnerWithNoAdditionalInformation() {
      whenever(timePort.now()).thenReturn(fixedInstant)

      val result = draftReferralService.updateAdditionalInformationForTheDeliveryPartner(referralId, SelectionDto.No)

      val captor = argumentCaptor<Referral>()
      verify(referralRepository).save(captor.capture())

      val saved = captor.firstValue
      assertThat(saved.id).isEqualTo(referralId)
      assertThat(saved.personId).isEqualTo(personId)
      assertThat(saved.personIdentifier).isEqualTo("X123456")
      assertThat(saved.createdAt).isEqualTo(createdAt)
      assertThat(saved.createdBy).isEqualTo(createdBy)
      assertThat(saved.updatedAt).isEqualTo(fixedInstant)
      assertThat(saved.hasAdditionalInformationForTheDeliveryPartner).isFalse()
      assertThat(saved.additionalInformationForTheDeliveryPartner).isNull()

      assertThat(result.refereeName.firstName).isEqualTo("Robert")
      assertThat(result.refereeName.lastName).isEqualTo("Smith")
      assertThat(result.refereeName.middleName).isNull()
      assertThat(result.details).isEqualTo(SelectionDto.No)
    }

    @Test
    fun shouldThrowWhenGivenBadData() {
      reset(referralRepository)
      referral = Referral(
        id = referralId,
        personId = personId,
        personIdentifier = "X123456",
        createdAt = createdAt,
        createdBy = createdBy,
        hasAdditionalInformationForTheDeliveryPartner = true,
        additionalInformationForTheDeliveryPartner = null,
      )
      whenever(referralRepository.findById(referralId)).thenReturn(Optional.of(referral))
      val ex = assertThrows<IllegalStateException> {
        draftReferralService.getAdditionalInformationForTheDeliveryPartner(referralId)
      }
      assertThat(ex).hasMessage("Invalid Selection state: selected=true, value=null")
    }
  }
}
