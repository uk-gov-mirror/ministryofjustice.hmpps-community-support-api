package uk.gov.justice.digital.hmpps.communitysupportapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.prison.PrisonDto

@Component
class PrisonApiClient(
  @Qualifier("prisonApiWebClient") private val webClient: WebClient,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun getPrisons(): List<PrisonDto> {
    log.debug("Retrieving prisons from Prison API")

    return webClient.get()
      .uri("/api/agencies/prisons")
      .accept(MediaType.APPLICATION_JSON)
      .exchangeToMono { response ->
        when {
          response.statusCode().is4xxClientError ->
            Mono.error(RuntimeException("Client error from Prison API: ${response.statusCode()}"))

          response.statusCode().is5xxServerError ->
            Mono.error(RuntimeException("Server error from Prison API: ${response.statusCode()}"))

          else -> response.bodyToMono<List<PrisonDto>>()
        }
      }
      .doOnError { e -> log.error("Error calling Prison API to retrieve prisons", e) }
      .block()!!
  }
}
