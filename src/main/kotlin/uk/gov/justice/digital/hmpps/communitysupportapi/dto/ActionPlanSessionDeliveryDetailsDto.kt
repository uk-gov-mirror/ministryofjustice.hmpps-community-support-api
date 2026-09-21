package uk.gov.justice.digital.hmpps.communitysupportapi.dto

import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanQuestionAnswerType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanStepQuestionAnswerDetails
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanStepQuestionChoice
import java.util.UUID

data class ActionPlanSessionDeliveryDetailsResponse(
  val questions: List<SessionDeliveryQuestion>,
)

class SessionDeliveryQuestion(
  val id: UUID,
  val displayOrder: Int,
  val label: String,
  val hint: String? = null,
  val answerType: ActionPlanQuestionAnswerType,
  val maximumNumberOfResponses: Int,
  val choices: List<QuestionChoice>? = null,
  val savedResponses: List<SessionDeliveryQuestionSavedResponse> = emptyList(),
) {
  companion object {
    fun fromQuestionAndResponses(
      question: ActionPlanStepQuestionDto,
      responses: List<ActionPlanStepQuestionAnswerDetails>,
      choices: List<ActionPlanStepQuestionChoice>,
    ): SessionDeliveryQuestion = SessionDeliveryQuestion(
      id = question.id,
      displayOrder = question.displayOrder,
      label = question.label,
      hint = question.hint,
      answerType = question.answerType,
      maximumNumberOfResponses = question.maximumNumberOfResponses,
      savedResponses = responses.map { response ->
        SessionDeliveryQuestionSavedResponse(response.content ?: "", response.freeTextValue)
      },
      choices = choices.map { choice ->
        QuestionChoice(
          value = choice.value,
          label = choice.label,
          displayAdditionalDetailsOnSelect = choice.hasFreeText,
          additionalDetailsLabel = if (choice.hasFreeText) choice.freeTextLabel else null,
          additionalDetailsHint = if (choice.hasFreeText) choice.freeTextHint else null,
          displayOrder = choice.orderNumber,
        )
      }
        .takeIf { it.isNotEmpty() },
    )
  }
}

data class SessionDeliveryQuestionSavedResponse(
  val value: String,
  val additionalDetails: String?,
)
