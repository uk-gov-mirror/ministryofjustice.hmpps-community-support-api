package uk.gov.justice.digital.hmpps.communitysupportapi.service

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanActionRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanActionResponse
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanSelectANeedNeed
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanSelectANeedOutcome
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanSelectANeedResponse
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanSessionDeliveryDetailsRequest
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanSessionDeliveryDetailsResponse
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanStepQuestionDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.ActionPlanSummaryDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SessionDeliveryDetailsQuestionAnswer
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SessionDeliveryDetailsQuestionAnswers
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.SessionDeliveryQuestion
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlan
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanActivity
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanEvent
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanQuestionAnswerType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanQuestionResponseEvent
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanQuestionResponseEventType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanQuestionType
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanStepQuestion
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanStepQuestionAnswerDetails
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanStepQuestionAnswerHeader
import uk.gov.justice.digital.hmpps.communitysupportapi.entity.ActionPlanStepType
import uk.gov.justice.digital.hmpps.communitysupportapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanActivityRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanEventRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanQuestionResponseEventRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanStepQuestionAnswerDetailsRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanStepQuestionAnswerHeaderRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanStepQuestionRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanStepRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ActionPlanTemplateRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.NeedRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.OutcomeRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PersonRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.ReferralRepository
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ActionPlanService(
  private val actionPlanRepository: ActionPlanRepository,
  private val actionPlanEventRepository: ActionPlanEventRepository,
  private val actionPlanQuestionResponseEventRepository: ActionPlanQuestionResponseEventRepository,
  private val actionPlanTemplateRepository: ActionPlanTemplateRepository,
  private val actionPlanStepRepository: ActionPlanStepRepository,
  private val actionPlanStepQuestionRepository: ActionPlanStepQuestionRepository,
  private val actionPlanStepQuestionAnswerHeaderRepository: ActionPlanStepQuestionAnswerHeaderRepository,
  private val actionPlanStepQuestionAnswerDetailsRepository: ActionPlanStepQuestionAnswerDetailsRepository,
  private val actionPlanActivityRepository: ActionPlanActivityRepository,
  private val referralRepository: ReferralRepository,
  private val personRepository: PersonRepository,
  private val needRepository: NeedRepository,
  private val outcomeRepository: OutcomeRepository,
) {
  companion object {
    private val logger = LoggerFactory.getLogger(ActionPlanService::class.java)
  }

  @Transactional
  fun findOrCreateByReferralId(referralId: UUID): ActionPlan = actionPlanRepository.findByReferralId(referralId)
    ?: createForReferral(referralId)

  fun getActionPlanSummaryForReferral(referralReference: String): ActionPlanSummaryDto {
    val referral = referralRepository.findByReferenceNumber(referralReference).firstOrNull()
      ?: throw NotFoundException("Referral not found for reference $referralReference")

    val person = personRepository.findById(referral.personId)
      .orElseThrow { NotFoundException("Person not found for referral $referralReference") }

    val actionPlan = actionPlanRepository.findByReferralId(referral.id)
    val outcomesByNeedId =
      actionPlan?.let { getOutcomesByNeedIdForActionPlan(it.id, it.actionPlanTemplateId) }.orEmpty()

    val needs = needRepository.findAllByOrderByOrderNumberAsc().map {
      ActionPlanSummaryDto.ActionPlanSummaryNeed(
        id = it.id,
        label = it.label,
        outcomes = outcomesByNeedId[it.id].orEmpty(),
      )
    }

    return ActionPlanSummaryDto(
      personDetails = ActionPlanSummaryDto.ActionPlanSummaryPersonDetails(person.firstName, person.lastName),
      needs = needs,
    )
  }

  @Transactional(readOnly = true)
  fun getNeedsAndOutcomesForActionPlan(): ActionPlanSelectANeedResponse = ActionPlanSelectANeedResponse(
    needs = needRepository.findAllByOrderByOrderNumberAsc().map { need ->
      ActionPlanSelectANeedNeed(
        id = need.id,
        label = need.label,
        outcomes = need.outcomes.map { outcome ->
          ActionPlanSelectANeedOutcome(
            id = outcome.id,
            text = outcome.text,
          )
        },
      )
    },
  )

  @Transactional(readOnly = true)
  fun getSessionDeliveryDetailsForReferral(referralReference: String): ActionPlanSessionDeliveryDetailsResponse {
    val referral = referralRepository.findByReferenceNumber(referralReference).firstOrNull()
      ?: throw NotFoundException("Referral not found for reference $referralReference")

    val sessionDeliveryStep = actionPlanStepRepository.findSessionDeliveryStepsByReferralId(referral.id)
    if (sessionDeliveryStep == null) {
      logger.warn("No SESSION_DELIVERY step found for referral {}", referralReference)
      throw NotFoundException("No SESSION_DELIVERY step found for referral $referralReference")
    }

    val questions = actionPlanStepQuestionRepository
      .findAllByActionPlanStepIdOrderByOrderNumberAsc(sessionDeliveryStep.id)
    val actionPlan = actionPlanRepository.findByReferralId(referral.id)
    if (actionPlan == null || questions.isEmpty()) {
      return ActionPlanSessionDeliveryDetailsResponse(
        questions = questions.map { question ->
          val questionDto = ActionPlanStepQuestionDto.fromEntity(question)
          SessionDeliveryQuestion.fromQuestionAndResponses(
            questionDto,
            emptyList(),
            question.choices.sortedBy { choice -> choice.orderNumber },
          )
        },
      )
    }

    val activeHeaders = actionPlanStepQuestionAnswerHeaderRepository
      .findActiveByPlanAndQuestionIds(
        actionPlan.id,
        questions.map { it.id },
      )
    val answerDetails = if (activeHeaders.isEmpty()) {
      emptyList()
    } else {
      actionPlanStepQuestionAnswerDetailsRepository
        .findAllByActionPlanStepQuestionAnswerHeaderIdIn(activeHeaders.map { it.id })
    }

    return ActionPlanSessionDeliveryDetailsResponse(
      questions = questions.map { question ->
        val questionDto = ActionPlanStepQuestionDto.fromEntity(question)
        val responses = activeHeaders
          .filter { it.actionPlanStepQuestionId == question.id }
          .mapNotNull { header ->
            answerDetails
              .filter { it.actionPlanStepQuestionAnswerHeaderId == header.id }
              .maxWithOrNull(
                compareBy<ActionPlanStepQuestionAnswerDetails> { it.revisionNumber }
                  .thenBy { it.createdAt }
                  .thenBy { it.id },
              )
          }
        val choices = question.choices.sortedBy { choice -> choice.orderNumber }
        SessionDeliveryQuestion.fromQuestionAndResponses(questionDto, responses, choices)
      },
    )
  }

  @Transactional
  fun updateSessionDeliveryDetailsForActionPlan(
    referralReference: String,
    request: ActionPlanSessionDeliveryDetailsRequest,
    changedBy: String,
    changedAt: OffsetDateTime = OffsetDateTime.now(),
  ): ActionPlanSessionDeliveryDetailsResponse {
    val referral = referralRepository.findByReferenceNumber(referralReference).firstOrNull()
      ?: throw NotFoundException("Referral not found for reference $referralReference")

    val actionPlan = findOrCreateByReferralId(referral.id)

    val sessionDeliveryStep = actionPlanStepRepository
      .findSessionDeliveryStepsByReferralId(referral.id)
      ?: throw NotFoundException("No SESSION_DELIVERY step found for referral $referralReference")

    val questions = actionPlanStepQuestionRepository
      .findAllByActionPlanStepIdOrderByOrderNumberAsc(sessionDeliveryStep.id)

    val incomingAnswers = request.answers

    validateQuestionAndAnswerCounts(questions, incomingAnswers)

    val questionResponseChangeBatchId = UUID.randomUUID()

    val existingHeaders = actionPlanStepQuestionAnswerHeaderRepository
      .findActiveByPlanAndQuestionIds(
        actionPlan.id,
        questions.map { it.id },
      )

    val existingAnswerDetails = if (existingHeaders.isEmpty()) {
      emptyList()
    } else {
      actionPlanStepQuestionAnswerDetailsRepository
        .findAllByActionPlanStepQuestionAnswerHeaderIdIn(existingHeaders.map { it.id })
    }

    questions.forEach { deliveryDetailsQuestion ->
      val existingHeadersForQuestion = existingHeaders
        .filter { it.actionPlanStepQuestionId == deliveryDetailsQuestion.id }

      val existingAnswerDetailsForQuestion = existingAnswerDetails.filter { answerDetails ->
        existingHeadersForQuestion.any { it.id == answerDetails.actionPlanStepQuestionAnswerHeaderId }
      }

      val incomingAnswerToQuestion = incomingAnswers
        .filter { it.questionId == deliveryDetailsQuestion.id }
        .flatMap { it.incomingAnswerDetails }
        .map {
          it.copy(
            value = it.value.trim(),
            additionalDetails = it.additionalDetails?.trim()?.takeIf { details -> details.isNotEmpty() },
          )
        }

      fun latestDetailsForHeader(header: ActionPlanStepQuestionAnswerHeader): ActionPlanStepQuestionAnswerDetails? = existingAnswerDetailsForQuestion
        .filter { it.actionPlanStepQuestionAnswerHeaderId == header.id }
        .maxWithOrNull(
          compareBy<ActionPlanStepQuestionAnswerDetails> { it.revisionNumber }
            .thenBy { it.createdAt }
            .thenBy { it.id },
        )

      fun softDelete(questionAnswerHeader: ActionPlanStepQuestionAnswerHeader) {
        actionPlanStepQuestionAnswerHeaderRepository.save(questionAnswerHeader.delete(changedAt, changedBy))
        actionPlanQuestionResponseEventRepository.save(
          ActionPlanQuestionResponseEvent.actionPlanQuestionResponseEventForResponses(
            actionPlanId = actionPlan.id,
            responseHeaderId = questionAnswerHeader.id,
            eventType = ActionPlanQuestionResponseEventType.DELETED,
            createdBy = changedBy,
            createdAt = changedAt,
            questionResponseChangeBatchId = questionResponseChangeBatchId,
          ),
        )
      }

      fun saveResponse(
        questionAnswerHeader: ActionPlanStepQuestionAnswerHeader,
        response: SessionDeliveryDetailsQuestionAnswer,
        latestDetails: ActionPlanStepQuestionAnswerDetails?,
        eventType: ActionPlanQuestionResponseEventType,
      ) {
        actionPlanStepQuestionAnswerDetailsRepository.save(
          ActionPlanStepQuestionAnswerDetails.from(
            headerId = questionAnswerHeader.id,
            revisionNumber = (latestDetails?.revisionNumber ?: 0) + 1,
            content = response.value,
            freeTextValue = response.additionalDetails,
            createdBy = changedBy,
            createdAt = changedAt,
          ),
        )
        actionPlanQuestionResponseEventRepository.save(
          ActionPlanQuestionResponseEvent.actionPlanQuestionResponseEventForResponses(
            actionPlanId = actionPlan.id,
            responseHeaderId = questionAnswerHeader.id,
            eventType = eventType,
            createdBy = changedBy,
            createdAt = changedAt,
            questionResponseChangeBatchId = questionResponseChangeBatchId,
          ),
        )
      }

      if (deliveryDetailsQuestion.supportsMultipleResponses) {
        val requestedValues = incomingAnswerToQuestion.map { it.value }.toSet()

        existingHeadersForQuestion
          .filter { latestDetailsForHeader(it)?.content !in requestedValues }
          .forEach(::softDelete)

        var nextOrderNumber = (existingHeadersForQuestion.maxOfOrNull { it.orderNumber } ?: 0) + 1

        incomingAnswerToQuestion.forEach { response ->
          val existingHeader = existingHeadersForQuestion
            .firstOrNull { latestDetailsForHeader(it)?.content == response.value }

          val latestDetails = existingHeader?.let(::latestDetailsForHeader)

          if (latestDetails?.hasSameContentAs(response.value, response.additionalDetails) == true) {
            return@forEach
          }

          val questionAnswerHeader = existingHeader
            ?: actionPlanStepQuestionAnswerHeaderRepository.save(
              ActionPlanStepQuestionAnswerHeader.from(
                actionPlanId = actionPlan.id,
                questionId = deliveryDetailsQuestion.id,
                orderNumber = nextOrderNumber++,
                createdBy = changedBy,
                createdAt = changedAt,
              ),
            )

          saveResponse(
            questionAnswerHeader,
            response,
            latestDetails,
            if (existingHeader == null) ActionPlanQuestionResponseEventType.CREATED else ActionPlanQuestionResponseEventType.UPDATED,
          )
        }
        return@forEach
      }

      val existingHeader = existingHeadersForQuestion.singleOrNull()
      val response = incomingAnswerToQuestion.singleOrNull()
      if (response == null) {
        existingHeader?.let(::softDelete)
        return@forEach
      }

      val questionAnswerHeader = existingHeader
        ?: actionPlanStepQuestionAnswerHeaderRepository.save(
          ActionPlanStepQuestionAnswerHeader.from(
            actionPlanId = actionPlan.id,
            questionId = deliveryDetailsQuestion.id,
            orderNumber = 1,
            createdBy = changedBy,
            createdAt = changedAt,
          ),
        )
      val latestDetails = existingHeader?.let(::latestDetailsForHeader)

      if (latestDetails?.hasSameContentAs(response.value, response.additionalDetails) == true) {
        return@forEach
      }

      saveResponse(
        questionAnswerHeader,
        response,
        latestDetails,
        if (existingHeader == null) ActionPlanQuestionResponseEventType.CREATED else ActionPlanQuestionResponseEventType.UPDATED,
      )
    }

    return getSessionDeliveryDetailsForReferral(referralReference)
  }

  private fun createForReferral(referralId: UUID): ActionPlan {
    val existingActionPlan = actionPlanRepository.findByReferralId(referralId)
    if (existingActionPlan != null) {
      logger.warn("Action plan already exists for referral {}, skipping creation", referralId)
      return existingActionPlan
    }

    val actionPlanTemplate = actionPlanTemplateRepository.findFirstByActiveGlobalTrueOrderByIdAsc()
      ?: throw NotFoundException("No active global action plan template found")

    val actionPlan = ActionPlan.forReferral(actionPlanTemplate.id, referralId)
    actionPlanRepository.save(actionPlan)

    val actionPlanEvent = ActionPlanEvent.actionPlanCreatedEventForActionPlan(actionPlan.id)
    actionPlanEventRepository.save(actionPlanEvent)

    return actionPlan
  }

  private fun validateQuestionAndAnswerCounts(
    questions: List<ActionPlanStepQuestion>,
    answers: List<SessionDeliveryDetailsQuestionAnswers>,
  ) {
    questions.forEach { question ->
      val answersForQuestion = answers.filter { it.questionId == question.id }

      if (question.supportsMultipleResponses && answersForQuestion.size > question.maxNumberResponses) {
        throw ValidationException("Question ${question.id} accepts at most $question.maxNumberResponses responses (${answersForQuestion.size} provided)")
      }
    }

    answers.forEach { answer ->
      val questionForAnswer = questions.find { it.id == answer.questionId }
        ?: throw ValidationException("Question ${answer.questionId} does not belong to session delivery details")

      if (answer.incomingAnswerDetails.size > questionForAnswer.maxNumberResponses) {
        throw ValidationException("Question ${questionForAnswer.id} accepts at most $questionForAnswer.maxNumberResponses responses")
      }

      answer.incomingAnswerDetails.forEach { response ->
        validateSavedResponseForQuestionAnswers(questionForAnswer, response)
      }
    }
  }

  private fun validateSavedResponseForQuestionAnswers(
    question: ActionPlanStepQuestion,
    response: SessionDeliveryDetailsQuestionAnswer,
  ) {
    val value = response.value.trim()

    if (value.isBlank()) {
      throw ValidationException("Question ${question.id} contains a blank response value")
    }

    when (question.answerType) {
      ActionPlanQuestionAnswerType.TEXTAREA -> {
        if (!response.additionalDetails.isNullOrBlank()) {
          throw ValidationException("Question ${question.id} does not accept additionalDetails")
        }
      }

      ActionPlanQuestionAnswerType.RADIO,
      ActionPlanQuestionAnswerType.CHECKBOX,
      -> {
        val choice = question.choices.firstOrNull { it.value == value }
          ?: throw ValidationException("Question ${question.id} contains unsupported choice value '$value'")

        if (choice.hasFreeText && response.additionalDetails.isNullOrBlank()) {
          throw ValidationException("Question ${question.id} requires additionalDetails for choice '$value'")
        }

        if (!choice.hasFreeText && !response.additionalDetails.isNullOrBlank()) {
          throw ValidationException("Question ${question.id} choice '$value' does not accept additionalDetails")
        }
      }
    }
  }

  private fun getOutcomesByNeedIdForActionPlan(
    actionPlanId: UUID,
    actionPlanTemplateId: UUID,
  ): Map<UUID, List<String>> {
    val needSteps = actionPlanStepRepository
      .findAllByActionPlanTemplateIdOrderByOrderNumberAsc(actionPlanTemplateId)
      .filter { it.stepType == ActionPlanStepType.NEED }
    if (needSteps.isEmpty()) {
      return emptyMap()
    }

    val questionById = actionPlanStepQuestionRepository
      .findAllByActionPlanStepIdInOrderByOrderNumberAsc(needSteps.map { it.id })
      .filter { it.questionType == ActionPlanQuestionType.OUTCOME && it.needId != null }
      .associateBy { it.id }
    if (questionById.isEmpty()) {
      return emptyMap()
    }

    val answers = actionPlanStepQuestionAnswerHeaderRepository
      .findAllByActionPlanIdAndDeletedAtIsNull(actionPlanId)
      .filter { questionById.containsKey(it.actionPlanStepQuestionId) }
      .sortedBy { it.orderNumber }
    if (answers.isEmpty()) {
      return emptyMap()
    }

    val details =
      actionPlanStepQuestionAnswerDetailsRepository.findAllByActionPlanStepQuestionAnswerHeaderIdIn(answers.map { it.id })
    val latestDetailsByHeaderId = details
      .groupBy { it.actionPlanStepQuestionAnswerHeaderId }
      .mapValues { (_, detailItems) -> detailItems.maxByOrNull { it.revisionNumber } }

    return answers
      .mapNotNull { answer ->
        val question = questionById[answer.actionPlanStepQuestionId] ?: return@mapNotNull null
        val needId = question.needId ?: return@mapNotNull null
        val latestDetails = latestDetailsByHeaderId[answer.id] ?: return@mapNotNull null
        val content = latestDetails.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        needId to content
      }
      .groupBy(keySelector = { it.first }, valueTransform = { it.second })
  }

  @Transactional
  fun submitActionForReferral(
    referralReference: String,
    request: ActionPlanActionRequest,
    changedBy: String,
  ): ActionPlanActionResponse {
    val changedAt = OffsetDateTime.now()
    // Find the referral for our action plan
    val referral = referralRepository.findReferenceNumberOrNull(referralReference)
      ?: throw NotFoundException("Referral not found with reference=$referralReference")

    // Find the action plan for our referral or create it
    val actionPlan = findOrCreateByReferralId(referral.id)

    // find the outcome for our action plan step
    val outcome = outcomeRepository.findById(request.outcomeId)
      .orElseThrow { NotFoundException("Outcome not found with id=${request.outcomeId}") }

    // check the outcome belongs to the need
    if (outcome.needId != request.needId) {
      throw ValidationException("Outcome ${request.outcomeId} does not belong to need ${request.needId}")
    }

    // find the need step for our action plan
    val needSteps = actionPlanStepRepository.findAllByActionPlanTemplateIdOrderByOrderNumberAsc(
      actionPlan.actionPlanTemplateId,
    ).filter { it.stepType == ActionPlanStepType.NEED }

    if (needSteps.isEmpty()) {
      throw NotFoundException("No NEED step found in action plan template")
    }

    // find the need step question for our action plan
    val needStepQuestions = actionPlanStepQuestionRepository
      .findAllByActionPlanStepIdInOrderByOrderNumberAsc(needSteps.map { it.id })
      .filter { it.questionType == ActionPlanQuestionType.OUTCOME && it.needId == request.needId }

    if (needStepQuestions.isEmpty()) {
      throw NotFoundException("No outcome question found for need ${request.needId}")
    }

    val question = needStepQuestions.first()
    val questionResponseChangeBatchId = UUID.randomUUID()

    val existingHeader = actionPlanStepQuestionAnswerHeaderRepository
      .findActiveByPlanAndQuestionIds(actionPlan.id, listOf(question.id))
      .firstOrNull()

    val answerHeader = existingHeader ?: actionPlanStepQuestionAnswerHeaderRepository.save(
      ActionPlanStepQuestionAnswerHeader.from(
        actionPlanId = actionPlan.id,
        questionId = question.id,
        orderNumber = 1,
        createdBy = changedBy,
        createdAt = changedAt,
      ),
    )

    if (existingHeader != null) {
      actionPlanActivityRepository.deleteByActionPlanStepQuestionAnswerHeaderId(existingHeader.id)
    }

    val latestRevisionNumber = actionPlanStepQuestionAnswerDetailsRepository
      .findAllByActionPlanStepQuestionAnswerHeaderIdIn(listOf(answerHeader.id))
      .maxOfOrNull { it.revisionNumber } ?: 0

    actionPlanStepQuestionAnswerDetailsRepository.save(
      ActionPlanStepQuestionAnswerDetails.from(
        headerId = answerHeader.id,
        revisionNumber = latestRevisionNumber + 1,
        content = request.outcomeId.toString(),
        freeTextValue = null,
        createdBy = changedBy,
        createdAt = changedAt,
      ),
    )

    request.activities.forEach { activity ->
      actionPlanActivityRepository.save(
        ActionPlanActivity(
          id = UUID.randomUUID(),
          actionPlanStepQuestionAnswerHeaderId = answerHeader.id,
          who = activity.who,
          activityDetails = activity.activityDetails,
          status = activity.status,
        ),
      )
    }

    actionPlanQuestionResponseEventRepository.save(
      ActionPlanQuestionResponseEvent.actionPlanQuestionResponseEventForResponses(
        actionPlanId = actionPlan.id,
        responseHeaderId = answerHeader.id,
        eventType = if (existingHeader == null) ActionPlanQuestionResponseEventType.CREATED else ActionPlanQuestionResponseEventType.UPDATED,
        questionResponseChangeBatchId = questionResponseChangeBatchId,
        createdAt = changedAt,
        createdBy = changedBy,
      ),
    )

    logger.info("Successfully submitted action for referral={} with need={} and outcome={}", referralReference, request.needId, request.outcomeId)

    return ActionPlanActionResponse(
      success = true,
      message = "Action submitted successfully",
    )
  }
}
