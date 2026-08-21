package uk.gov.justice.digital.hmpps.communitysupportapi.dto

import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Person
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.Referral

data class AdditionalInformationForTheDeliveryPartnerBffResponseDto(
  val refereeName: RefereeNameDto,
  val details: SelectionDto,
) {
  companion object {
    fun from(person: Person, referral: Referral): AdditionalInformationForTheDeliveryPartnerBffResponseDto = AdditionalInformationForTheDeliveryPartnerBffResponseDto(
      RefereeNameDto(
        firstName = person.firstName,
        lastName = person.lastName,
      ),
      SelectionDto.fromDB(
        referral.hasAdditionalInformationForTheDeliveryPartner,
        referral.additionalInformationForTheDeliveryPartner,
      ),
    )
  }
}
