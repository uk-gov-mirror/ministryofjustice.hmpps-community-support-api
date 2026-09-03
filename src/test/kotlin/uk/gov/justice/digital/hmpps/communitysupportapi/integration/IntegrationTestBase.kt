package uk.gov.justice.digital.hmpps.communitysupportapi.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import uk.gov.justice.digital.hmpps.communitysupportapi.common.TestDataCleaner
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@ExtendWith(HmppsAuthApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestWebClientConfiguration::class)
@ActiveProfiles("test")
abstract class IntegrationTestBase {

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  @Autowired
  protected lateinit var testDataCleaner: TestDataCleaner

  @BeforeEach
  fun setup() {
    wireMockServer.resetRequests()
    stubAuthTokenEndpoint()
    stubPingWithResponse(200)
    testDataCleaner.cleanAllTables()
  }

  companion object {

    @JvmStatic
    private val postgresContainer = PostgreSQLContainer<Nothing>("postgres:17")
      .apply {
        withUsername("admin")
        withPassword("admin_password")
        withReuse(true)
      }

    @JvmStatic
    val wireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

    @BeforeAll
    @JvmStatic
    fun startContainers() {
      postgresContainer.start()
      wireMockServer.start()
      WireMock.configureFor("localhost", wireMockServer.port())
    }

    @DynamicPropertySource
    @JvmStatic
    fun setUpProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
      registry.add("spring.datasource.username") { postgresContainer.username }
      registry.add("spring.datasource.password") { postgresContainer.password }
      registry.add("services.core-person-record-api.base-url") { "http://localhost:${wireMockServer.port()}" }
      registry.add("services.manage-users-api.base-url") { "http://localhost:${wireMockServer.port()}" }
      registry.add("services.assess-risks-and-needs-api.base-url") { "http://localhost:${wireMockServer.port()}" }
      registry.add("services.hmpps-auth-api.base-url") { "http://localhost:8090/auth" }
      registry.add("services.nDelius-api.base-url") { "http://localhost:${wireMockServer.port()}" }
      registry.add("services.prison-api.base-url") { "http://localhost:${wireMockServer.port()}" }
    }
  }

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf("ROLE_IPB_FRONTEND_RW"),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  fun assertUnauthorized(method: HttpMethod, uri: String) {
    webTestClient.method(method)
      .uri(uri)
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  fun assertForbiddenNoRole(method: HttpMethod, uri: String) {
    webTestClient.method(method)
      .uri(uri)
      .headers(setAuthorisation("AUTH_ADM", listOf(), listOf("read")))
      .exchange()
      .expectStatus()
      .isForbidden
  }

  fun assertForbiddenNoRole(method: HttpMethod, uri: String, body: Any) {
    webTestClient.method(method)
      .uri(uri)
      .headers(setAuthorisation("AUTH_ADM", listOf(), listOf("read")))
      .bodyValue(body)
      .exchange()
      .expectStatus()
      .isForbidden
  }

  fun assertForbiddenWrongRole(method: HttpMethod, uri: String) {
    webTestClient.method(method)
      .uri(uri)
      .headers(setAuthorisation(roles = listOf("ROLE_WRONG")))
      .exchange()
      .expectStatus()
      .isForbidden
  }

  fun assertForbiddenWrongRole(method: HttpMethod, uri: String, body: Any) {
    webTestClient.method(method)
      .uri(uri)
      .headers(setAuthorisation(roles = listOf("ROLE_WRONG")))
      .bodyValue(body)
      .exchange()
      .expectStatus()
      .isForbidden
  }

  fun assertNotFound(method: HttpMethod, uri: String) {
    webTestClient.method(method)
      .uri(uri)
      .headers(setAuthorisation())
      .exchange()
      .expectStatus()
      .isNotFound
  }

  fun assertNotFound(method: HttpMethod, uri: String, body: Any) {
    webTestClient.method(method)
      .uri(uri)
      .headers(setAuthorisation())
      .bodyValue(body)
      .exchange()
      .expectStatus()
      .isNotFound
  }

  fun assertBadRequest(method: HttpMethod, uri: String) {
    webTestClient.method(method)
      .uri(uri)
      .headers(setAuthorisation())
      .exchange()
      .expectStatus()
      .isBadRequest
  }

  fun assertServerError(method: HttpMethod, uri: String) {
    webTestClient
      .method(method)
      .uri(uri)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_IPB_FRONTEND_RW")))
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus()
      .is5xxServerError
  }

  protected fun stubManageUsersGetUserGroups(userId: String, groups: List<Pair<String, String>>) {
    val groupsJson = groups.joinToString(",") { """{"groupCode":"${it.first}","groupName":"${it.second}"}""" }
    wireMockServer.stubFor(
      get(urlPathMatching("/externalusers/$userId/groups"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("[$groupsJson]")
            .withStatus(200),
        ),
    )
  }

  protected fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
  }

  fun stubAuthTokenEndpoint() {
    hmppsAuth.stubGrantToken()
  }
}
