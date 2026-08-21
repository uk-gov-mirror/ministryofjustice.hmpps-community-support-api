package uk.gov.justice.digital.hmpps.communitysupportapi.service

import org.springframework.stereotype.Component
import java.time.OffsetDateTime

interface TimePort {
  fun now(): OffsetDateTime
}

@Component
class SystemTimePort : TimePort {
  override fun now(): OffsetDateTime = OffsetDateTime.now()
}
