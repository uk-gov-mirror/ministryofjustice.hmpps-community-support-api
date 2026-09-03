package uk.gov.justice.digital.hmpps.communitysupportapi.model

import jakarta.validation.ValidationException

enum class ReferralWithdrawalReasonCode {
  INELIGIBLE_REFERRAL,
  MISTAKEN_OR_DUPLICATE_REFERRAL,
  NOT_ENGAGED,
  NEEDS_MET_THROUGH_ANOTHER_ROUTE,
  USER_DIED,
  WORK_CARING_COMMITMENTS_OR_SICKNESS,
  ACQUITTED_ON_APPEAL,
  RETURNED_TO_CUSTODY,
  SENTENCE_REVOKED,
  SENTENCE_EXPIRED,
  OTHER_CHANGE_OF_CIRCUMSTANCE,
}

data class WithdrawReferralRequest(
  val reasonCode: ReferralWithdrawalReasonCode,
  val additionalDetails: String? = null,
) {
  fun validateAndNormalise(): WithdrawReferralRequest {
    if (additionalDetails != null && additionalDetails.isBlank()) {
      throw ValidationException("AdditionalDetails must not be blank")
    }

    return copy(additionalDetails = additionalDetails?.trim())
  }
}
