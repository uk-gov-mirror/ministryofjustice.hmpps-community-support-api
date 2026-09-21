package uk.gov.justice.digital.hmpps.communitysupportapi.dto

import jakarta.validation.Valid
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanQuestionAnswerType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanStepQuestion
import uk.gov.justice.digital.hmpps.communitysupportapi.validation.NullOrNotBlank
import uk.gov.justice.digital.hmpps.communitysupportapi.validation.actionplan.NoDuplicateAnswerValues
import java.util.UUID

data class SessionDeliveryDetailsQuestionAnswer(
  val value: String,
  @field:Valid
  @field:NullOrNotBlank
  val additionalDetails: String? = null,
)

data class SessionDeliveryDetailsQuestionAnswers(
  val questionId: UUID,
  @field:Valid
  @field:NoDuplicateAnswerValues
  val incomingAnswerDetails: List<SessionDeliveryDetailsQuestionAnswer> = emptyList(),
)

data class ActionPlanStepQuestionDto(
  val id: UUID,
  val displayOrder: Int,
  val label: String,
  val hint: String? = null,
  val answerType: ActionPlanQuestionAnswerType,
  val maximumNumberOfResponses: Int,
  val choices: List<QuestionChoice>? = null,
  val savedResponses: List<SessionDeliveryDetailsQuestionAnswer> = emptyList(),
) {
  companion object {
    fun fromEntity(question: ActionPlanStepQuestion): ActionPlanStepQuestionDto = ActionPlanStepQuestionDto(
      id = question.id,
      displayOrder = question.orderNumber,
      label = question.title,
      hint = question.hint,
      answerType = question.answerType,
      maximumNumberOfResponses = question.maxNumberResponses,
    )
  }
}

data class QuestionChoice(
  val value: String,
  val label: String,
  val displayOrder: Int,
  val displayAdditionalDetailsOnSelect: Boolean = false,
  val additionalDetailsLabel: String?,
  val additionalDetailsHint: String? = null,
)
