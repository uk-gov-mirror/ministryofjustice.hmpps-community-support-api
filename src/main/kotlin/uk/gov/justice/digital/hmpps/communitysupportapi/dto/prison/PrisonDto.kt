package uk.gov.justice.digital.hmpps.communitysupportapi.dto.prison

data class PrisonDto(
  val agencyId: String,
  val description: String,
  val longDescription: String? = null,
  val agencyType: String,
  val active: Boolean,
)
