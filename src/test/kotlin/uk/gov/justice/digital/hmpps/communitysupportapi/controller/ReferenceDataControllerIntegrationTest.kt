package uk.gov.justice.digital.hmpps.communitysupportapi.controller

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.communitysupportapi.model.Prison
import uk.gov.justice.digital.hmpps.communitysupportapi.model.ProbationOffice
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.prisonsJson

class ReferenceDataControllerIntegrationTest : IntegrationTestBase() {

  @Nested
  @DisplayName("GET /bff/reference-data/probation-offices")
  inner class ReferenceDataEndpoint {

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(HttpMethod.GET, "/bff/reference-data/probation-offices")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(HttpMethod.GET, "/bff/reference-data/probation-offices")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(HttpMethod.GET, "/bff/reference-data/probation-offices")
    }

    @Test
    fun `should return list of probation offices information`() {
      val response = webTestClient.get()
        .uri { uriBuilder ->
          uriBuilder
            .path("/bff/reference-data/probation-offices")
            .build()
        }
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody(object : ParameterizedTypeReference<List<ProbationOffice>>() {})
        .returnResult().responseBody!!

      assertThat(response).hasSize(130)
      response.forEach { probationOffice ->
        assertThat(probationOffice.probationOfficeId).isNotNull()
        assertThat(probationOffice.name).isNotBlank()
        assertThat(probationOffice.address).isNotBlank()
        assertThat(probationOffice.probationRegionId).isNotBlank()
      }
    }
  }

  @Nested
  @DisplayName("GET /bff/reference-data/pdus")
  inner class PdusEndpoint {

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(HttpMethod.GET, "/bff/reference-data/pdus")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(HttpMethod.GET, "/bff/reference-data/pdus")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(HttpMethod.GET, "/bff/reference-data/pdus")
    }

    @Test
    fun `should return list of PDU names`() {
      val response = webTestClient.get()
        .uri { uriBuilder ->
          uriBuilder
            .path("/bff/reference-data/pdus")
            .build()
        }
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody(object : ParameterizedTypeReference<List<String>>() {})
        .returnResult().responseBody!!

      assertThat(response).hasSize(99)
      assertThat(response).isEqualTo(response.sorted())
      response.forEach { pduName -> assertThat(pduName).isNotBlank() }
      assertThat(response).contains("County Durham and Darlington", "Gateshead and South Tyneside")
    }
  }

  @Nested
  @DisplayName("GET /bff/reference-data/prisons")
  inner class PrisonsEndpoint {

    @Test
    fun `should return unauthorized if no token`() {
      assertUnauthorized(HttpMethod.GET, "/bff/reference-data/prisons")
    }

    @Test
    fun `should return forbidden if no role`() {
      assertForbiddenNoRole(HttpMethod.GET, "/bff/reference-data/prisons")
    }

    @Test
    fun `should return forbidden if wrong role`() {
      assertForbiddenWrongRole(HttpMethod.GET, "/bff/reference-data/prisons")
    }

    @Test
    fun `should return only active prisons`() {
      stubFor(
        get(urlEqualTo("/api/agencies/prisons"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(prisonsJson()),
          ),
      )

      val response = webTestClient.get()
        .uri { uriBuilder ->
          uriBuilder
            .path("/bff/reference-data/prisons")
            .build()
        }
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody(object : ParameterizedTypeReference<List<Prison>>() {})
        .returnResult().responseBody!!

      assertThat(response).hasSize(2)
      assertThat(response.map { it.code }).containsExactly("MDI", "LEI")
    }
  }
}
