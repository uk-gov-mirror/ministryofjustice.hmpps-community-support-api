package uk.gov.justice.digital.hmpps.communitysupportapi.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "selected",
  visible = true,
)
@JsonSubTypes(
  JsonSubTypes.Type(value = SelectionDto.Yes::class, name = "Yes"),
  JsonSubTypes.Type(value = SelectionDto.No::class, name = "No"),
  JsonSubTypes.Type(value = SelectionDto.Unanswered::class, name = "Unanswered"),
)
sealed interface SelectionDto {
  data class Yes(val value: String) : SelectionDto
  data object No : SelectionDto
  data object Unanswered : SelectionDto
  companion object {
    fun fromString(value: String?): SelectionDto = if (value == null) No else Yes(value)
    fun fromDB(selected: Boolean?, value: String?): SelectionDto = when (selected) {
      null if value == null -> SelectionDto.Unanswered
      false if value == null -> SelectionDto.No
      true if !value.isNullOrBlank() -> SelectionDto.Yes(value)
      else -> throw IllegalStateException("Invalid Selection state: selected=$selected, value=$value")
    }
  }
}

fun SelectionDto.toTriState(): Boolean? = when (this) {
  is SelectionDto.Yes -> true
  SelectionDto.No -> false
  SelectionDto.Unanswered -> null
}

fun SelectionDto.value(): String? = if (this is SelectionDto.Yes) this.value else null
