package uk.gov.justice.digital.hmpps.communitysupportapi.service

import jakarta.validation.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.AdditionalInformationForTheDeliveryPartnerBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.AdditionalSupportNeedsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.AreaConfirmationBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.CommunityServiceProviderBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.NeedsInterpreterBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.OffenceSentenceInfoBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ProbationPractitionerDetailsBffResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SelectionDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.TaskListStatusResponseDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.OffenceSentenceDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.toTriState
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.value
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Person
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.PersonAdditionalSupportNeeds
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ProbationPractitionerDetails
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Referral
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralOffenceSentence
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ReferralProviderAssignment
import uk.gov.justice.digital.hmpps.communitysupportapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.communitysupportapi.model.AdditionalSupportNeedsRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.CommunityServiceProviderRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.NeedsInterpreterRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.model.PersonIdentifier
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
import uk.gov.justice.digital.hmpps.communitysupportapi.util.toFormattedDateOfBirthLong
import uk.gov.justice.digital.hmpps.communitysupportapi.validation.PersonIdentifierValidator
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Service
class DraftReferralService(
  private val referralRepository: ReferralRepository,
  private val referralCriminogenicNeedsRepository: ReferralCriminogenicNeedsRepository,
  private val personRepository: PersonRepository,
  private val personAdditionalSupportNeedsRepository: PersonAdditionalSupportNeedsRepository,
  private val riskInformationRepository: RiskInformationRepository,
  private val pduRepository: PduRepository,
  private val communityServiceProviderRepository: CommunityServiceProviderRepository,
  private val referralProviderAssignmentRepository: ReferralProviderAssignmentRepository,
  private val referralOffenceSentenceRepository: ReferralOffenceSentenceRepository,
  private val probationPractitionerDetailsRepository: ProbationPractitionerDetailsRepository,
  private val identifierValidator: PersonIdentifierValidator,
  private val nDeliusService: NDeliusService,
) {
  private data class ReferralSupportNeedsContext(
    val referral: Referral,
    val person: Person,
    val additionalSupportNeeds: PersonAdditionalSupportNeeds?,
  )

  fun getAdditionalSupportNeedsForReferral(
    referralId: String,
  ): AdditionalSupportNeedsBffResponseDto {
    val context = getReferralSupportNeedsContext(UUID.fromString(referralId))

    context.additionalSupportNeeds?.let {
      return AdditionalSupportNeedsBffResponseDto.fromNeeds(context.person, it)
    }
    return AdditionalSupportNeedsBffResponseDto.fromPerson(context.person)
  }

  fun getInterpreterNeedsForReferral(
    referralId: String,
  ): NeedsInterpreterBffResponseDto {
    val context = getReferralSupportNeedsContext(UUID.fromString(referralId))

    return context.additionalSupportNeeds?.let {
      NeedsInterpreterBffResponseDto.from(context.person, it)
    } ?: throw NotFoundException("Interpreter needs not found for referral $referralId")
  }

  fun getAreaConfirmationDetails(referralId: UUID, providerId: UUID): AreaConfirmationBffResponseDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralId") }

    val communityServiceProvider = communityServiceProviderRepository.findById(providerId)
      .orElseThrow { NotFoundException("Community Service Provider not found for id $providerId") }

    val associatedPdus = pduRepository.findByContractAreaId(communityServiceProvider.contractArea.id)
      .map { it.name }
      .sorted()

    val identifier = identifierValidator.validate(person.identifier)
    val crn = if (identifier is PersonIdentifier.Crn) identifier.value else ""
    val dateOfBirth = person.dateOfBirth.toFormattedDateOfBirthLong()

    return AreaConfirmationBffResponseDto.from(communityServiceProvider, associatedPdus, crn, dateOfBirth)
  }

  @Transactional
  fun upsertAdditionalSupportNeeds(
    referralId: UUID,
    userId: UUID,
    request: AdditionalSupportNeedsRequest,
  ): AdditionalSupportNeedsBffResponseDto {
    val context = getReferralSupportNeedsContext(referralId)

    val personAdditionalSupportNeeds = if (context.additionalSupportNeeds == null) {
      createSupportNeeds(referralId, context.person.id, request, userId)
    } else {
      updateSupportNeeds(context.additionalSupportNeeds, request, userId)
    }

    return AdditionalSupportNeedsBffResponseDto.fromNeeds(context.person, personAdditionalSupportNeeds)
  }

  @Transactional
  fun upsertNeedsInterpreter(
    referralId: UUID,
    userId: UUID,
    request: NeedsInterpreterRequest,
  ): NeedsInterpreterBffResponseDto {
    val context = getReferralSupportNeedsContext(referralId)
    println("upsertNeedsInterpreter - context.additionalSupportNeeds = ${context.additionalSupportNeeds}")
    println("upsertNeedsInterpreter - request = $request")
    val personAdditionalSupportNeeds = if (context.additionalSupportNeeds == null) {
      println("createNeedsInterpreter")
      createNeedsInterpreter(referralId, context.person.id, request, userId)
    } else {
      println("updateNeedsInterpreter")
      updateNeedsInterpreter(context.additionalSupportNeeds, request, userId)
    }

    return NeedsInterpreterBffResponseDto.from(context.person, personAdditionalSupportNeeds)
  }

  @Transactional
  fun upsertCommunityServiceProvider(
    referralId: UUID,
    request: CommunityServiceProviderRequest,
  ): CommunityServiceProviderBffResponseDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val communityServiceProvider = communityServiceProviderRepository.findById(request.communityServiceProviderId)
      .orElseThrow { NotFoundException("Community Service Provider not found for id ${request.communityServiceProviderId}") }

    // This is a temporary solution to ensure that only one provider assignment exists for a referral.
    // IPB-2532 is done to remove providing community service provider from the referral entity.
    val existingAssignments = referralProviderAssignmentRepository.findByReferralId(referralId)
    if (existingAssignments.isNotEmpty()) {
      referralProviderAssignmentRepository.deleteAll(existingAssignments)
    }

    val providerAssignment = ReferralProviderAssignment(
      id = UUID.randomUUID(),
      referral = referral,
      communityServiceProvider = communityServiceProvider,
      createdAt = LocalDateTime.now(),
    )
    referralProviderAssignmentRepository.save(providerAssignment)

    return CommunityServiceProviderBffResponseDto.from(referralId, communityServiceProvider)
  }

  private fun createSupportNeeds(
    referralId: UUID,
    personId: UUID,
    request: AdditionalSupportNeedsRequest,
    createdBy: UUID,
  ): PersonAdditionalSupportNeeds {
    val normalisedRequest = request.normaliseAgainstNeedsAdditionalSupport()
    val supportNeeds = PersonAdditionalSupportNeeds(
      id = UUID.randomUUID(),
      referralId = referralId,
      personId = personId,
      additionalSupportNeeded = normalisedRequest.needsAdditionalSupport,
      physicalHealthDetails = normalisedRequest.physicalHealth,
      mentalEmotionalHealthDetails = normalisedRequest.mentalEmotionalHealth,
      neurodiversityDetails = normalisedRequest.neurodiversity,
      locationTravelDetails = normalisedRequest.locationTravel,
      caringResponsibilitiesDetails = normalisedRequest.caringResponsibilities,
      employmentResponsibilitiesDetails = normalisedRequest.employmentResponsibilities,
      diversityDetails = normalisedRequest.diversity,
      anythingElseDetails = normalisedRequest.anythingElse,
      createdBy = createdBy,
      createdAt = OffsetDateTime.now(),
    )
    return personAdditionalSupportNeedsRepository.save(supportNeeds)
  }

  private fun updateSupportNeeds(
    existingRecord: PersonAdditionalSupportNeeds,
    newRecord: AdditionalSupportNeedsRequest,
    updatedBy: UUID,
  ): PersonAdditionalSupportNeeds {
    val normalisedRequest = newRecord.normaliseAgainstNeedsAdditionalSupport()
    val supportNeeds = PersonAdditionalSupportNeeds(
      id = existingRecord.id,
      referralId = existingRecord.referralId,
      personId = existingRecord.personId,
      additionalSupportNeeded = normalisedRequest.needsAdditionalSupport,
      physicalHealthDetails = normalisedRequest.physicalHealth,
      mentalEmotionalHealthDetails = normalisedRequest.mentalEmotionalHealth,
      neurodiversityDetails = normalisedRequest.neurodiversity,
      locationTravelDetails = normalisedRequest.locationTravel,
      caringResponsibilitiesDetails = normalisedRequest.caringResponsibilities,
      employmentResponsibilitiesDetails = normalisedRequest.employmentResponsibilities,
      diversityDetails = normalisedRequest.diversity,
      anythingElseDetails = normalisedRequest.anythingElse,
      interpreterNeeded = existingRecord.interpreterNeeded,
      interpreterLanguage = existingRecord.interpreterLanguage,
      createdBy = existingRecord.createdBy,
      createdAt = existingRecord.createdAt,
      updatedBy = updatedBy,
      updatedAt = OffsetDateTime.now(),
    )
    return personAdditionalSupportNeedsRepository.save(supportNeeds)
  }

  private fun createNeedsInterpreter(
    referralId: UUID,
    personId: UUID,
    request: NeedsInterpreterRequest,
    createdBy: UUID,
  ): PersonAdditionalSupportNeeds {
    println("createNeedsInterpreter - request = $request")
    val normalisedRequest = request.normaliseAgainstNeedsInterpreter()
    println("createNeedsInterpreter - normalisedRequest = $normalisedRequest")
    val supportNeeds = PersonAdditionalSupportNeeds(
      id = UUID.randomUUID(),
      referralId = referralId,
      personId = personId,
      interpreterLanguage = normalisedRequest.language,
      interpreterNeeded = normalisedRequest.needsInterpreter,
      createdBy = createdBy,
      createdAt = OffsetDateTime.now(),
    )
    println("createNeedsInterpreter - supportNeeds = $supportNeeds")
    return personAdditionalSupportNeedsRepository.save(supportNeeds)
  }

  private fun updateNeedsInterpreter(
    existingRecord: PersonAdditionalSupportNeeds,
    newRecord: NeedsInterpreterRequest,
    updatedBy: UUID,
  ): PersonAdditionalSupportNeeds {
    println("updateNeedsInterpreter - newRecord = $newRecord")
    val normalisedRecord = newRecord.normaliseAgainstNeedsInterpreter()
    println("updateNeedsInterpreter - normalisedRecord = $normalisedRecord")
    val copyRecord = existingRecord.copy(
      interpreterLanguage = normalisedRecord.language,
      interpreterNeeded = normalisedRecord.needsInterpreter,
      updatedBy = updatedBy,
      updatedAt = OffsetDateTime.now(),
    )
    println("updateNeedsInterpreter - copyRecord = $copyRecord")
    return personAdditionalSupportNeedsRepository.save(copyRecord)
  }

  private fun getReferralSupportNeedsContext(referralId: UUID): ReferralSupportNeedsContext {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralId") }

    val additionalSupportNeeds = personAdditionalSupportNeedsRepository.findByReferralId(referralId)

    return ReferralSupportNeedsContext(
      referral = referral,
      person = person,
      additionalSupportNeeds = additionalSupportNeeds,
    )
  }

  fun getTaskListStatus(referralId: UUID): TaskListStatusResponseDto? {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val additionalSupportNeeds = personAdditionalSupportNeedsRepository.findByReferralId(referralId)

    val riskInfo = riskInformationRepository.findByReferralId(referralId)

    val criminogenicNeeds = referralCriminogenicNeedsRepository.findByReferralId(referralId)

    val communityServiceProvider = communityServiceProviderRepository.findByReferralId(referralId)

    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralId") }

    val probationPractitionerDetails = try {
      getProbationPractitionerDetails(referralId)
    } catch (_: NotFoundException) {
      null
    } catch (_: ValidationException) {
      null
    }

    val savedProbationPractitionerDetails = probationPractitionerDetailsRepository.findByReferralId(referralId)

    return TaskListStatusResponseDto.from(
      referral,
      person,
      additionalSupportNeeds,
      riskInfo,
      criminogenicNeeds,
      communityServiceProvider,
      probationPractitionerDetails,
      savedProbationPractitionerDetails,
    )
  }

  fun getOffenceSentenceDetails(referralId: UUID): OffenceSentenceInfoBffResponseDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralId") }

    // TODO: Replace with downstream service call to retrieve offence and sentence information when client details are confirmed
    val offenceSentenceInfo = OffenceSentenceDto()

    return OffenceSentenceInfoBffResponseDto.from(person, offenceSentenceInfo)
  }

  @Transactional
  fun upsertOffenceSentenceDetails(referralId: UUID, userId: UUID, request: UpdateOffenceSentenceRequest): OffenceSentenceInfoBffResponseDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralId") }

    val validatedRequest = request.validateAndNormalise()

    val offenceSentenceInfo = OffenceSentenceDto(
      offence = validatedRequest.offence,
      offenceSubCategory = validatedRequest.offenceSubCategory,
      outcome = validatedRequest.outcome,
      sentenceEndDate = validatedRequest.sentenceEndDate,
      expectedReleaseDate = validatedRequest.expectedReleaseDate,
      hasLicenceConditionsOrZones = validatedRequest.hasLicenceConditionsOrZones,
      licenceConditionsOrZonesDetails = validatedRequest.licenceConditionsOrZonesDetails,
    )

    val existingRecord = referralOffenceSentenceRepository.findByReferralId(referralId)

    if (existingRecord == null) {
      referralOffenceSentenceRepository.save(
        ReferralOffenceSentence(
          id = UUID.randomUUID(),
          referralId = referralId,
          personId = person.id,
          offence = offenceSentenceInfo.offence,
          offenceSubCategory = offenceSentenceInfo.offenceSubCategory,
          outcome = offenceSentenceInfo.outcome,
          sentenceEndDate = offenceSentenceInfo.sentenceEndDate,
          expectedReleaseDate = offenceSentenceInfo.expectedReleaseDate,
          hasLicenceConditionsOrZones = offenceSentenceInfo.hasLicenceConditionsOrZones,
          licenceConditionsOrZonesDetails = offenceSentenceInfo.licenceConditionsOrZonesDetails,
          createdAt = OffsetDateTime.now(),
          createdBy = userId,
        ),
      )
    } else {
      existingRecord.offence = offenceSentenceInfo.offence
      existingRecord.offenceSubCategory = offenceSentenceInfo.offenceSubCategory
      existingRecord.outcome = offenceSentenceInfo.outcome
      existingRecord.sentenceEndDate = offenceSentenceInfo.sentenceEndDate
      existingRecord.expectedReleaseDate = offenceSentenceInfo.expectedReleaseDate
      existingRecord.hasLicenceConditionsOrZones = offenceSentenceInfo.hasLicenceConditionsOrZones
      existingRecord.licenceConditionsOrZonesDetails = offenceSentenceInfo.licenceConditionsOrZonesDetails
      existingRecord.updatedAt = OffsetDateTime.now()
      existingRecord.updatedBy = userId
      referralOffenceSentenceRepository.save(existingRecord)
    }

    return OffenceSentenceInfoBffResponseDto.from(person, offenceSentenceInfo)
  }

  fun getAdditionalInformationForTheDeliveryPartner(referralId: UUID): AdditionalInformationForTheDeliveryPartnerBffResponseDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralId") }
    return AdditionalInformationForTheDeliveryPartnerBffResponseDto.from(person, referral)
  }

  @Transactional
  fun updateAdditionalInformationForTheDeliveryPartner(
    referralId: UUID,
    selection: SelectionDto,
    updatedAt: OffsetDateTime,
  ): AdditionalInformationForTheDeliveryPartnerBffResponseDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralId") }

    referral.hasAdditionalInformationForTheDeliveryPartner = selection.toTriState()
    referral.additionalInformationForTheDeliveryPartner = selection.value()
    referral.updatedAt = updatedAt

    referralRepository.save(referral)
    return AdditionalInformationForTheDeliveryPartnerBffResponseDto.from(person, referral)
  }

  fun getProbationPractitionerDetails(referralId: UUID): ProbationPractitionerDetailsBffResponseDto {
    val referral = referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralId") }

    // TODO: temporary restriction until it's determined how to look up a Probation Practitioner for a person identified by prison number.
    val crn = when (val identifier = identifierValidator.validate(person.identifier)) {
      is PersonIdentifier.Crn -> identifier.value
      is PersonIdentifier.PrisonerNumber -> throw ValidationException("Cannot retrieve Probation Practitioner details for a person identified by prison number")
    }

    val communityManager = nDeliusService.getCommunityManagerByIdentifier(crn)

    return ProbationPractitionerDetailsBffResponseDto.from(communityManager)
  }

  @Transactional
  fun upsertProbationPractitionerDetails(
    referralId: UUID,
    userId: UUID,
    request: UpdateProbationPractitionerDetailsRequest,
  ): ProbationPractitionerDetailsBffResponseDto {
    referralRepository.findById(referralId)
      .orElseThrow { NotFoundException("Referral not found for id $referralId") }

    val existingRecord = probationPractitionerDetailsRepository.findByReferralId(referralId)

    val savedRecord = if (existingRecord == null) {
      probationPractitionerDetailsRepository.save(
        ProbationPractitionerDetails(
          id = UUID.randomUUID(),
          referralId = referralId,
          name = request.name,
          jobRole = request.jobRole,
          emailAddress = request.emailAddress,
          pdu = request.pdu,
          probationOffice = request.probationOffice,
          teamPhoneNumber = request.teamPhoneNumber,
          ppDetailsFoundAndCorrect = request.ppDetailsFoundAndCorrect,
          updatedAt = OffsetDateTime.now(),
          updatedBy = userId,
        ),
      )
    } else {
      existingRecord.name = request.name
      existingRecord.jobRole = request.jobRole
      existingRecord.emailAddress = request.emailAddress
      existingRecord.pdu = request.pdu
      existingRecord.probationOffice = request.probationOffice
      existingRecord.teamPhoneNumber = request.teamPhoneNumber
      existingRecord.ppDetailsFoundAndCorrect = request.ppDetailsFoundAndCorrect
      existingRecord.updatedAt = OffsetDateTime.now()
      existingRecord.updatedBy = userId
      probationPractitionerDetailsRepository.save(existingRecord)
    }

    return ProbationPractitionerDetailsBffResponseDto.from(savedRecord)
  }
}
