package uk.gov.justice.digital.hmpps.communitysupportapi.service

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanStatusDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.CheckDraftReferralDetailsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ConfirmPersonDetailsBffDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.PersonDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralAppointmentHistoryDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralCreationResult
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralDetailsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralInformationDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ReferralProgressDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ServiceDaysPageDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ServiceEndDatePageDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SubmitReferralResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.OffenderProfileDto
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActorType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.CommunityServiceProvider
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Person
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.PersonAdditionalDetails
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Referral
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralEvent
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralEventType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralWithdrawalDetails
import uk.gov.justice.digital.hmpps.communitysupportapi.exception.ConflictException
import uk.gov.justice.digital.hmpps.communitysupportapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.communitysupportapi.mapper.toEntity
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.PersonAggregate
import uk.gov.justice.digital.hmpps.communitysupportapi.model.PersonIdentifier
import uk.gov.justice.digital.hmpps.communitysupportapi.model.WithdrawReferralRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentIcsFeedbackRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentIcsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.AppointmentStatusHistoryRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PersonRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralProviderAssignmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralUserAssignmentRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralWithdrawalDetailsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.util.parseDateOfBirth
import uk.gov.justice.digital.hmpps.communitysupportapi.validation.PersonIdentifierValidator
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ReferralService(
  private val referralRepository: ReferralRepository,
  private val personRepository: PersonRepository,
  private val appointmentRepository: AppointmentRepository,
  private val appointmentIcsRepository: AppointmentIcsRepository,
  private val appointmentStatusHistoryRepository: AppointmentStatusHistoryRepository,
  private val referralProviderAssignmentRepository: ReferralProviderAssignmentRepository,
  private val referralUserAssignmentRepository: ReferralUserAssignmentRepository,
  private val referralWithdrawalDetailsRepository: ReferralWithdrawalDetailsRepository,
  private val referenceGenerator: ReferralReferenceGenerator,
  private val appointmentIcsFeedbackRepository: AppointmentIcsFeedbackRepository,
  private val referralLookupService: ReferralLookupService,
  private val cprProbationService: CprProbationService,
  private val identifierValidator: PersonIdentifierValidator,
  private val personService: PersonService,
  private val actionPlanService: ActionPlanService,
) {
  companion object {
    private val logger = LoggerFactory.getLogger(ReferralService::class.java)
    private const val MAX_REFERENCE_NUMBER_TRIES = 10
  }

  fun getReferral(referralId: UUID) = referralRepository.findById(referralId)

  fun getReferralDetailsPage(caseIdentifier: String?): ReferralDetailsBffResponseDto {
    val foundReferral = referralLookupService.findByCaseIdentifier(caseIdentifier)
    val personDetails = personService.getPerson(foundReferral.personIdentifier)
    val person = upsertPerson(personDetails)
    val referralAssignments = referralUserAssignmentRepository.findAllByReferralIdAndNotDeleted(foundReferral.id)

    return ReferralDetailsBffResponseDto.from(foundReferral, person, referralAssignments)
  }

  fun getCheckDraftReferralDetailsPage(referralId: UUID): CheckDraftReferralDetailsBffResponseDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }
    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralId") }

    return CheckDraftReferralDetailsBffResponseDto.from(referral, person)
  }

  fun getServiceEndDatePage(referralId: UUID): ServiceEndDatePageDto = ServiceEndDatePageDto.from(
    referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") },
  )

  fun getServiceDaysPage(referralId: UUID): ServiceDaysPageDto = ServiceDaysPageDto.from(
    referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") },
  )

  @Transactional
  fun updateReferralServiceEndDate(
    referralId: UUID,
    request: ServiceEndDatePageDto,
  ): ServiceEndDatePageDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }
    referral.targetServiceCompletionDate = request.targetServiceCompletionDate
    referral.targetServiceCompletionDateReason = request.targetServiceCompletionReason

    return ServiceEndDatePageDto.from(referralRepository.save(referral))
  }

  @Transactional
  fun updateReferralServiceDays(
    referralId: UUID,
    request: ServiceDaysPageDto,
  ): ServiceDaysPageDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }
    referral.serviceDays = request.serviceDays

    return ServiceDaysPageDto.from(referralRepository.save(referral))
  }

  @Transactional
  fun createReferral(userId: UUID, createReferralRequest: CreateReferralRequest): ReferralCreationResult {
    val personDetails = fetchPersonDetails(createReferralRequest.personIdentifier)
    val person = upsertPerson(personDetails)

    val referralId = UUID.randomUUID()
    val now = OffsetDateTime.now()

    val referral = Referral(
      id = referralId,
      personIdentifier = createReferralRequest.personIdentifier,
      personId = person.id,
      createdAt = now,
      createdBy = userId,
      updatedAt = now,
      urgency = createReferralRequest.urgency,
    )

    val referralEvent = ReferralEvent(
      id = UUID.randomUUID(),
      eventType = ReferralEventType.CREATED,
      createdAt = now,
      actorType = ActorType.AUTH,
      actorId = userId,
      referral = referral,
    )

    referral.addEvent(referralEvent)
    val savedReferral = referralRepository.save(referral)

    return ReferralCreationResult(
      referral = savedReferral,
      person = person,
    )
  }

  private fun fetchPersonDetails(personIdentifier: String): PersonDto = try {
    personService.getPerson(personIdentifier)
  } catch (e: ValidationException) {
    throw e
  } catch (e: Exception) {
    logger.error(
      "Failed to retrieve person details from Core Person Record for identifier {} while creating referral",
      personIdentifier,
      e,
    )
    throw RuntimeException("Unable to retrieve person details from Core Person Record for identifier $personIdentifier", e)
  }

  @Transactional
  fun submitReferral(
    referralId: UUID,
    userId: UUID,
  ): SubmitReferralResponseDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    if (referral.submittedEvent != null) {
      throw ConflictException("Referral $referralId has already been submitted")
    }

    val providerAssignment = referralProviderAssignmentRepository.findByReferralId(referralId)
      .firstOrNull() ?: throw NotFoundException("Provider assignment not found for referral id $referralId")

    val communityServiceProvider = providerAssignment.communityServiceProvider

    val referralEvent = ReferralEvent(
      id = UUID.randomUUID(),
      eventType = ReferralEventType.SUBMITTED,
      createdAt = OffsetDateTime.now(),
      actorType = ActorType.AUTH,
      actorId = userId,
      referral = referral,
    )

    referral.addEvent(referralEvent)
    referral.referenceNumber = generateReferenceNumber(communityServiceProvider, referralId)

    actionPlanService.findOrCreateByReferralId(referralId)

    val savedReferral = referralRepository.save(referral)
    return SubmitReferralResponseDto(
      referralId = savedReferral.id,
      personId = savedReferral.personId,
      referenceNumber = savedReferral.referenceNumber,
    )
  }

  @Transactional
  fun withdrawReferral(
    referralReference: String,
    userId: UUID,
    request: WithdrawReferralRequest,
  ) {
    val foundReferral = referralLookupService.findByCaseIdentifier(referralReference)
    val validatedRequest = request.validateAndNormalise()

    if (referralWithdrawalDetailsRepository.findByReferralId(foundReferral.id) != null) {
      throw ConflictException("Referral $referralReference has already been withdrawn")
    }

    val now = OffsetDateTime.now()
    referralWithdrawalDetailsRepository.save(
      ReferralWithdrawalDetails(
        id = UUID.randomUUID(),
        referralId = foundReferral.id,
        reasonCode = validatedRequest.reasonCode.name,
        reasonDetails = validatedRequest.additionalDetails,
        createdAt = now,
        createdBy = userId,
      ),
    )

    foundReferral.addEvent(
      ReferralEvent(
        id = UUID.randomUUID(),
        referral = foundReferral,
        eventType = ReferralEventType.WITHDRAWN,
        createdAt = now,
        actorType = ActorType.AUTH,
        actorId = userId,
      ),
    )
    referralRepository.save(foundReferral)
  }

  fun getReferralProgress(referralIdentifier: String): ReferralProgressDto {
    val referral = referralLookupService.findByCaseIdentifier(referralIdentifier)
    val personName = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralIdentifier") }
      .let { "${it.firstName} ${it.lastName}" }

    val appointments = appointmentRepository.findAllByReferralId(referral.id).orEmpty()

    val actionPlan = actionPlanService.findOrCreateByReferralId(referral.id)

    if (appointments.isEmpty()) {
      return ReferralProgressDto(referralId = referral.id, fullName = personName, appointments = emptyList(), actionPlanStatus = ActionPlanStatusDto.fromActionPlan(actionPlan))
    }

    val appointmentIds = appointments.map { it.id }

    val statusHistoryByAppointment = appointmentStatusHistoryRepository
      .findAllByAppointmentIdIn(appointmentIds)
      .groupBy { it.appointment.id }

    val icsByAppointments = appointmentIcsRepository
      .findAllByAppointmentIdInOrderByCreatedAtDesc(appointmentIds)
      .associateBy { it.appointment.id }

    check(appointmentIds.all { it in icsByAppointments }) {
      "Missing ICS for appointments: ${appointmentIds - icsByAppointments.keys}"
    }

    val feedbackByIcsIds = appointmentIcsFeedbackRepository
      .findAllByAppointmentIcsIdIn(icsByAppointments.values.map { it.id })
      .associateBy { it.appointmentIcs.id }

    val appointmentHistory = icsByAppointments.map { (appointmentId, ics) ->
      val latestStatus = statusHistoryByAppointment[appointmentId]
        ?.maxByOrNull { it.createdAt }
        ?.status
        ?: error("No status history for appointment $appointmentId")

      val icsFeedbackId = feedbackByIcsIds[ics.id]?.id

      ReferralAppointmentHistoryDto(
        appointmentIcsId = ics.id,
        type = ics.appointment.type,
        dateTime = ics.appointmentDateTime,
        status = latestStatus,
        icsFeedbackId = icsFeedbackId,
      )
    }

    return ReferralProgressDto(
      referralId = referral.id,
      fullName = personName,
      appointments = appointmentHistory,
      ActionPlanStatusDto.fromActionPlan(actionPlan),
    )
  }

  fun getReferralInformation(caseIdentifier: String?): ReferralInformationDto {
    val foundReferral = referralLookupService.findByCaseIdentifier(caseIdentifier)
    val person = personRepository.findById(foundReferral.personId)
      .orElseThrow { NotFoundException("Person not found for referral ${foundReferral.personId}") }

    val providerAssignment = referralProviderAssignmentRepository.findByReferralId(foundReferral.id)
      .firstOrNull() ?: throw NotFoundException("Provider assignment not found for referral id ${foundReferral.id}")

    return ReferralInformationDto(
      personId = person.id,
      referralId = foundReferral.id,
      referralDate = foundReferral.createdAt.toLocalDate(),
      firstName = person.firstName,
      lastName = person.lastName,
      sex = person.gender,
      personIdentifier = foundReferral.personIdentifier,
      communityServiceProviderId = providerAssignment.communityServiceProvider.id,
      communityServiceProviderName = providerAssignment.communityServiceProvider.name,
      region = providerAssignment.communityServiceProvider.contractArea.region.name,
      referenceNumber = foundReferral.referenceNumber,
      deliveryPartner = providerAssignment.communityServiceProvider.serviceProvider.name,
    )
  }

  fun getPersonAggregateOffenderProfile(personIdentifier: String): Triple<Person, PersonAggregate, OffenderProfileDto> {
    val person = personRepository.findByIdentifier(personIdentifier)
      ?: throw NotFoundException("Person not found for identifier $personIdentifier")

    // TODO: get offenderProfile from DeliusPersonDto
    val offenderProfile = OffenderProfileDto()

    val identifier = identifierValidator.validate(personIdentifier)

    val personAggregate = requireNotNull(
      when (identifier) {
        is PersonIdentifier.Crn -> cprProbationService.getPersonDetailsByCrn(identifier.value)
        is PersonIdentifier.PrisonerNumber -> cprProbationService.getPersonDetailsByPrisonNumber(identifier.value)
      },
    )

    return Triple(person, personAggregate, offenderProfile)
  }

  fun getConfirmPersonDetailsBffDto(personIdentifier: String): ConfirmPersonDetailsBffDto {
    val (person, personAggregate, offenderProfile) = this.getPersonAggregateOffenderProfile(personIdentifier)

    return ConfirmPersonDetailsBffDto.from(person.id, personAggregate, offenderProfile)
  }

  private fun generateReferenceNumber(communityServiceProvider: CommunityServiceProvider, referralId: UUID): String {
    val type = communityServiceProvider.serviceProvider.name

    for (i in 1..MAX_REFERENCE_NUMBER_TRIES) {
      val candidate = referenceGenerator.generate(type)
      if (!referralRepository.existsByReferenceNumber(candidate)) {
        return candidate
      } else {
        logger.warn("Clash found for referral number attempt {} for referral {}", i, referralId)
      }
    }

    logger.error("Unable to generate a unique referral number for referral : {}", referralId)
    throw IllegalStateException("Unable to generate a unique referral reference for referral $referralId")
  }

  private fun upsertPerson(personDetails: PersonDto): Person {
    val existing = personRepository.findByIdentifier(personDetails.personIdentifier!!) ?: return personRepository.save(
      personDetails.toEntity(),
    )
    val desiredGender = personDetails.sex ?: existing.gender
    val desiredPrisonNumbers = personDetails.prisonNumbers.joinToString(",").ifEmpty { null }
    val additionalDetailsChanged = !additionalDetailsEqual(existing.additionalDetails, personDetails.additionalDetails)
    val basicFieldsChanged = existing.firstName != personDetails.firstName ||
      existing.lastName != personDetails.lastName ||
      !existing.dateOfBirth.isEqual(personDetails.dateOfBirth.parseDateOfBirth()) ||
      existing.gender != desiredGender ||
      existing.prisonNumbers != desiredPrisonNumbers

    if (!basicFieldsChanged && !additionalDetailsChanged) return existing

    val updatedPerson = Person(
      id = existing.id,
      identifier = existing.identifier,
      firstName = personDetails.firstName,
      lastName = personDetails.lastName,
      dateOfBirth = personDetails.dateOfBirth.parseDateOfBirth(),
      gender = desiredGender,
      createdAt = existing.createdAt,
      updatedAt = OffsetDateTime.now(),
      prisonNumbers = desiredPrisonNumbers,
    )

    if (personDetails.additionalDetails != null) {
      updatedPerson.additionalDetails = PersonAdditionalDetails(
        id = existing.additionalDetails?.id ?: UUID.randomUUID(),
        person = updatedPerson,
        ethnicity = personDetails.additionalDetails.ethnicity,
        preferredLanguage = personDetails.additionalDetails.preferredLanguage,
        neurodiverseConditions = personDetails.additionalDetails.neurodiverseConditions,
        religionOrBelief = personDetails.additionalDetails.religionOrBelief,
        address = personDetails.additionalDetails.address,
        phoneNumber = personDetails.additionalDetails.phoneNumber,
        emailAddress = personDetails.additionalDetails.emailAddress,
      )
    } else {
      updatedPerson.additionalDetails = existing.additionalDetails
    }

    return personRepository.save(updatedPerson)
  }

  private fun additionalDetailsEqual(existing: PersonAdditionalDetails?, incoming: uk.gov.justice.digital.hmpps.communitysupportapi.model.PersonAdditionalDetails?): Boolean {
    if (incoming == null) return existing == null
    if (existing == null) return false
    return existing.ethnicity == incoming.ethnicity &&
      existing.preferredLanguage == incoming.preferredLanguage &&
      existing.neurodiverseConditions == incoming.neurodiverseConditions &&
      existing.religionOrBelief == incoming.religionOrBelief &&
      existing.address == incoming.address &&
      existing.phoneNumber == incoming.phoneNumber &&
      existing.emailAddress == incoming.emailAddress
  }
}
