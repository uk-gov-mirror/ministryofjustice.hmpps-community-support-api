package uk.gov.justice.digital.hmpps.communitysupportapi.service

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.communitysupportapi.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.communitysupportapi.model.Prison
import uk.gov.justice.digital.hmpps.communitysupportapi.model.ProbationOffice
import uk.gov.justice.digital.hmpps.communitysupportapi.repository.PduRepository
import uk.gov.justice.digital.hmpps.communitysupportapi.util.CsvFileHelper

@Service
class ReferenceDataService(
  private val pduRepository: PduRepository,
  private val prisonApiClient: PrisonApiClient,
) {
  @Volatile
  private var cachedProbationOffices: List<ProbationOffice>? = null

  @Value("\${reference-data.probation-offices.path}")
  private lateinit var probationOfficesPath: String

  @PostConstruct
  fun init() {
    cachedProbationOffices = loadProbationOffices()
  }

  private fun loadProbationOffices(): List<ProbationOffice> = CsvFileHelper.readFromClasspath(
    probationOfficesPath,
    { record ->
      ProbationOffice(
        probationOfficeId = record.get("probation_office_id")?.toIntOrNull() ?: 0,
        name = record.get("name"),
        address = record.get("address"),
        probationRegionId = record.get("probation_region_id"),
        govUkUrl = record.get("gov_uk_url"),
        deliusCRSLocationId = record.get("delius_crs_location_id"),
      )
    },
  )

  fun getProbationOffices(): List<ProbationOffice> {
    if (cachedProbationOffices == null) {
      cachedProbationOffices = loadProbationOffices()
    }
    return cachedProbationOffices!!
  }

  fun getPduNames(): List<String> = pduRepository.findAll().map { it.name }.sorted()

  fun getPrisons(): List<Prison> = prisonApiClient.getPrisons()
    .filter { it.active }
    .map {
      Prison(
        code = it.agencyId,
        description = it.description,
        longDescription = it.longDescription,
        agencyType = it.agencyType,
      )
    }
}
