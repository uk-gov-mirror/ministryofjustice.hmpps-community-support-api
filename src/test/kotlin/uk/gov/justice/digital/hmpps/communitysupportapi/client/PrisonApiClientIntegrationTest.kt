package uk.gov.justice.digital.hmpps.communitysupportapi.client

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.prisonsJson

class PrisonApiClientIntegrationTest : IntegrationTestBase() {

  @Autowired
  lateinit var prisonApiClient: PrisonApiClient

  @Test
  fun `should return prisons when Prison API returns 200`() {
    stubFor(
      get(urlEqualTo("/api/agencies/prisons"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(prisonsJson()),
        ),
    )

    val result = prisonApiClient.getPrisons()

    assertThat(result).hasSize(3)
    assertThat(result.map { it.agencyId }).containsExactly("MDI", "LEI", "ZZGHI")
    assertThat(result.first { it.agencyId == "ZZGHI" }.active).isFalse()
  }

  @Test
  fun `should throw exception when Prison API returns 500`() {
    stubFor(
      get(urlEqualTo("/api/agencies/prisons"))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"error": "Internal Server Error"}"""),
        ),
    )

    assertThrows(RuntimeException::class.java) {
      prisonApiClient.getPrisons()
    }
  }
}
