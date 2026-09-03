package uk.gov.justice.digital.hmpps.communitysupportapi.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.communitysupportapi.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.communitysupportapi.model.Prison
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PduRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.testdata.ExternalApiResponse.createPrisonDto

@ExtendWith(MockitoExtension::class)
class ReferenceDataServiceUnitTest {

  @Mock
  lateinit var pduRepository: PduRepository

  @Mock
  lateinit var prisonApiClient: PrisonApiClient

  private lateinit var referenceDataService: ReferenceDataService

  @BeforeEach
  fun setup() {
    referenceDataService = ReferenceDataService(pduRepository, prisonApiClient)
  }

  @Test
  fun `should return only active prisons mapped from Prison API`() {
    val activePrison1 = createPrisonDto(agencyId = "MDI", description = "Moorland (HMP & YOI)", longDescription = "Moorland (HMP & YOI)", active = true)
    val activePrison2 = createPrisonDto(agencyId = "LEI", description = "Leeds (HMP)", longDescription = "Leeds (HMP)", active = true)
    val inactivePrison = createPrisonDto(agencyId = "ZZGHI", description = "Inactive Prison (Closed)", active = false)

    whenever(prisonApiClient.getPrisons()).thenReturn(listOf(activePrison1, inactivePrison, activePrison2))

    val result = referenceDataService.getPrisons()

    assertEquals(
      listOf(
        Prison(code = "MDI", description = "Moorland (HMP & YOI)", longDescription = "Moorland (HMP & YOI)", agencyType = "INST"),
        Prison(code = "LEI", description = "Leeds (HMP)", longDescription = "Leeds (HMP)", agencyType = "INST"),
      ),
      result,
    )
  }

  @Test
  fun `should return empty list when no prisons are active`() {
    whenever(prisonApiClient.getPrisons()).thenReturn(listOf(createPrisonDto(active = false)))

    val result = referenceDataService.getPrisons()

    assertEquals(emptyList<Prison>(), result)
  }
}
