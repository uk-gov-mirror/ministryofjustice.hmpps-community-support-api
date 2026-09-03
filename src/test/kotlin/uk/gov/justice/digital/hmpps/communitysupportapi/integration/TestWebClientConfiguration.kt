package uk.gov.justice.digital.hmpps.communitysupportapi.integration

import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@TestConfiguration
class TestWebClientConfiguration {

  @Bean
  @Qualifier("hmppsAuthHealthWebClient")
  fun hmppsAuthHealthWebClient(
    @Value("\${services.hmpps-auth-api.base-url}") hmppsAuthBaseUrl: String,
  ): WebClient = WebClient.builder()
    .healthWebClient(hmppsAuthBaseUrl, Duration.ofSeconds(2))

  @Bean
  fun reactiveOAuth2AuthorizedClientManager(): ReactiveOAuth2AuthorizedClientManager = mock(ReactiveOAuth2AuthorizedClientManager::class.java)

  @Bean
  @Qualifier("corePersonRecordWebClient")
  fun corePersonRecordWebClient(
    @Value("\${services.core-person-record-api.base-url}") corePersonRecordBaseUrl: String,
  ): WebClient = WebClient.builder().baseUrl(corePersonRecordBaseUrl).build()

  @Bean
  @Qualifier("manageUsersWebClient")
  fun manageUsersWebClient(
    @Value("\${services.manage-users-api.base-url}") manageUsersBaseUrl: String,
  ): WebClient = WebClient.builder().baseUrl(manageUsersBaseUrl).build()

  @Bean
  @Qualifier("assessRisksAndNeedsWebClient")
  fun assessRisksAndNeedsWebClient(
    @Value("\${services.assess-risks-and-needs-api.base-url}") assessRisksAndNeedsBaseUrl: String,
  ): WebClient = WebClient.builder().baseUrl(assessRisksAndNeedsBaseUrl).build()

  @Bean
  @Qualifier("nDeliusWebClient")
  fun nDeliusWebClient(
    @Value("\${services.nDelius-api.base-url}") nDeliusBaseUrl: String,
  ): WebClient = WebClient.builder().baseUrl(nDeliusBaseUrl).build()

  @Bean
  @Qualifier("prisonApiWebClient")
  fun prisonApiWebClient(
    @Value("\${services.prison-api.base-url}") prisonApiBaseUrl: String,
  ): WebClient = WebClient.builder().baseUrl(prisonApiBaseUrl).build()
}
