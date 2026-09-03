package uk.gov.justice.digital.hmpps.communitysupportapi

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.communitysupportapi.integration.TestWebClientConfiguration

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestWebClientConfiguration::class)
@TestPropertySource(
  properties = [
    "services.hmpps-auth-api.base-url=http://localhost:8090/auth",
    "services.manage-users-api.base-url=http://localhost:9999",
    "services.core-person-record-api.base-url=http://localhost:9999",
    "services.assess-risks-and-needs-api.base-url=http://localhost:9999",
    "services.nDelius-api.base-url=http://localhost:9999",
    "services.prison-api.base-url=http://localhost:9999",
  ],
)
class InitialiseDatabase {

  // This is needed to initialize the database for schema spy
  @Test
  fun `initialises database`() {
    println("Database has been initialised by IntegrationTestBase")
  }
}
