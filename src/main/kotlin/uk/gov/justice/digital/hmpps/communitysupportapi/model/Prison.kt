package uk.gov.justice.digital.hmpps.communitysupportapi.model

data class Prison(
  val code: String,
  val description: String,
  val longDescription: String? = null,
  val agencyType: String,
)
