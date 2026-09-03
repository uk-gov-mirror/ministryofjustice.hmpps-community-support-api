package uk.gov.justice.digital.hmpps.communitysupportapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import java.time.OffsetDateTime
import java.util.UUID

enum class ReferralEventType {
  CREATED,
  SUBMITTED,
  UPDATED,
  APPOINTMENT_FEEDBACK_SENT,
  WITHDRAWN,
}

enum class ActorType {
  AUTH,
  EXTERNAL,
}

@Entity
@Table(name = "referral_event")
class ReferralEvent(
  @Id
  val id: UUID,

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "referral_id", nullable = false)
  var referral: Referral,

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type")
  val eventType: ReferralEventType? = null,

  @Column(name = "created_at", nullable = false)
  @CreatedDate
  val createdAt: OffsetDateTime,

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_type", nullable = false)
  val actorType: ActorType,

  @Column(name = "actor_id")
  val actorId: UUID,
)
