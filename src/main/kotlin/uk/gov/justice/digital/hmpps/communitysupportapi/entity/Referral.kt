package uk.gov.justice.digital.hmpps.communitysupportapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "referral")
class Referral(
  @Id
  val id: UUID,

  @Column(name = "person_id", nullable = false)
  val personId: UUID,

  @Column(name = "person_identifier")
  val personIdentifier: String,

  @Column(name = "reference_number")
  var referenceNumber: String? = null,

  @Column(name = "created_at", nullable = false)
  @CreatedDate
  val createdAt: OffsetDateTime,

  @Column(name = "updated_at")
  var updatedAt: OffsetDateTime? = null,

  @Column(name = "urgency")
  val urgency: Boolean? = null,

  @Column(name = "created_by")
  val createdBy: UUID,

  @Column(name = "target_service_completion_date", nullable = true)
  var targetServiceCompletionDate: OffsetDateTime? = null,

  @Column(name = "target_service_completion_date_reason", nullable = true)
  var targetServiceCompletionDateReason: String? = null,

  @Column(name = "service_days", nullable = true)
  var serviceDays: Int? = null,

  @Column(name = "has_additonal_information_for_delivery_partner", nullable = true)
  var hasAdditionalInformationForTheDeliveryPartner: Boolean? = null,

  @Column(name = "additonal_information_for_delivery_partner", nullable = true)
  var additionalInformationForTheDeliveryPartner: String? = null,

  @OneToMany(mappedBy = "referral", cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
  val referralEvents: MutableList<ReferralEvent> = mutableListOf(),
) {
  val submittedEvent: ReferralEvent?
    get() = referralEvents.firstOrNull { it.eventType == ReferralEventType.SUBMITTED }

  fun addEvent(event: ReferralEvent) {
    referralEvents.add(event)
    event.referral = this
  }
}
