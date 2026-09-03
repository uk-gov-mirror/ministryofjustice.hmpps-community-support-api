package uk.gov.justice.digital.hmpps.communitysupportapi.dto

import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Person
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.PersonAdditionalSupportNeeds

private fun defaultResponse(refereeName: RefereeNameDto): AdditionalSupportNeedsBffResponseDto = AdditionalSupportNeedsBffResponseDto(
  refereeName,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  null,
)

private fun noneNeededResponse(refereeName: RefereeNameDto): AdditionalSupportNeedsBffResponseDto = AdditionalSupportNeedsBffResponseDto(
  refereeName,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  SelectionDto.Unanswered,
  false,
)

private fun yesOrNoSelectionFromValue(value: String?): SelectionDto = if (value.isNullOrBlank()) {
  SelectionDto.No
} else {
  SelectionDto.Yes(value)
}

private fun supportNeededResponse(refereeName: RefereeNameDto, personAdditionalSupportNeeds: PersonAdditionalSupportNeeds): AdditionalSupportNeedsBffResponseDto = AdditionalSupportNeedsBffResponseDto(
  refereeName,
  yesOrNoSelectionFromValue(personAdditionalSupportNeeds.physicalHealthDetails),
  yesOrNoSelectionFromValue(personAdditionalSupportNeeds.mentalEmotionalHealthDetails),
  yesOrNoSelectionFromValue(personAdditionalSupportNeeds.neurodiversityDetails),
  yesOrNoSelectionFromValue(personAdditionalSupportNeeds.locationTravelDetails),
  yesOrNoSelectionFromValue(personAdditionalSupportNeeds.caringResponsibilitiesDetails),
  yesOrNoSelectionFromValue(personAdditionalSupportNeeds.employmentResponsibilitiesDetails),
  yesOrNoSelectionFromValue(personAdditionalSupportNeeds.diversityDetails),
  yesOrNoSelectionFromValue(personAdditionalSupportNeeds.anythingElseDetails),
  true,
)

data class AdditionalSupportNeedsBffResponseDto(
  val refereeName: RefereeNameDto,
  val physicalHealth: SelectionDto,
  val mentalEmotionalHealth: SelectionDto,
  val neurodiversity: SelectionDto,
  val locationTravel: SelectionDto,
  val caringResponsibilities: SelectionDto,
  val employmentResponsibilities: SelectionDto,
  val diversity: SelectionDto,
  val anythingElse: SelectionDto,
  val needsAdditionalSupport: Boolean? = null,
) {
  companion object {
    fun fromNeeds(person: Person, personAdditionalSupportNeeds: PersonAdditionalSupportNeeds): AdditionalSupportNeedsBffResponseDto {
      val refereeName = RefereeNameDto(firstName = person.firstName, lastName = person.lastName)
      return when (personAdditionalSupportNeeds.additionalSupportNeeded) {
        null -> defaultResponse(refereeName)
        false -> noneNeededResponse(refereeName)
        true -> supportNeededResponse(refereeName, personAdditionalSupportNeeds)
      }
    }

    fun fromPerson(person: Person): AdditionalSupportNeedsBffResponseDto = AdditionalSupportNeedsBffResponseDto(
      refereeName = RefereeNameDto(firstName = person.firstName, lastName = person.lastName),
      physicalHealth = SelectionDto.Unanswered,
      mentalEmotionalHealth = SelectionDto.Unanswered,
      neurodiversity = SelectionDto.Unanswered,
      locationTravel = SelectionDto.Unanswered,
      caringResponsibilities = SelectionDto.Unanswered,
      employmentResponsibilities = SelectionDto.Unanswered,
      diversity = SelectionDto.Unanswered,
      anythingElse = SelectionDto.Unanswered,
      needsAdditionalSupport = null,
    )
  }
}

data class NeedsInterpreterBffResponseDto(
  val refereeName: RefereeNameDto,
  val language: SelectionDto,
) {
  companion object {
    fun from(person: Person, personAdditionalSupportNeeds: PersonAdditionalSupportNeeds): NeedsInterpreterBffResponseDto {
      val refereeName = RefereeNameDto(firstName = person.firstName, lastName = person.lastName)
      return when (personAdditionalSupportNeeds.interpreterNeeded) {
        true -> NeedsInterpreterBffResponseDto(
          refereeName,
          SelectionDto.from(
            personAdditionalSupportNeeds.interpreterNeeded,
            personAdditionalSupportNeeds.interpreterLanguage,
          ),
        )
        false -> if (personAdditionalSupportNeeds.interpreterLanguage != null) {
          NeedsInterpreterBffResponseDto(refereeName, SelectionDto.Unanswered)
        } else {
          NeedsInterpreterBffResponseDto(refereeName, SelectionDto.No)
        }
        null -> NeedsInterpreterBffResponseDto(refereeName, SelectionDto.Unanswered)
      }
    }
  }
}
