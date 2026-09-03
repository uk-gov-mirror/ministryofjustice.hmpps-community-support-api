package uk.gov.justice.digital.hmpps.communitysupportapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.communitysupportapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.communitysupportapi.model.Prison
import uk.gov.justice.digital.hmpps.communitysupportapi.model.ProbationOffice
import uk.gov.justice.digital.hmpps.communitysupportapi.service.ReferenceDataService

@RestController
@RequestMapping("/bff/reference-data")
@PreAuthorize("hasAnyRole('ROLE_IPB_FRONTEND_RW')")
class ReferenceDataController(
  private val referenceDataService: ReferenceDataService,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Operation(summary = "Get all Probation Offices Information")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the list of Probation Offices Information.",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = ProbationOffice::class)))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Failed to retrieve Probation Offices Information",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/probation-offices")
  fun getProbationOffices(): ResponseEntity<List<ProbationOffice>> {
    try {
      val probationOffices = referenceDataService.getProbationOffices()
      return ResponseEntity.ok(probationOffices)
    } catch (e: Exception) {
      log.error("Error getting Probation Offices", e)
      throw NotFoundException("Probation offices information not found")
    }
  }

  @Operation(summary = "Get all Probation Delivery Unit (PDU) names")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the list of Probation Delivery Unit names.",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = String::class)))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Failed to retrieve Probation Delivery Unit names",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/pdus")
  fun getPdus(): ResponseEntity<List<String>> {
    try {
      val pdus = referenceDataService.getPduNames()
      return ResponseEntity.ok(pdus)
    } catch (e: Exception) {
      log.error("Error getting PDUs", e)
      throw NotFoundException("PDU information not found")
    }
  }

  @Operation(summary = "Get all active Prisons")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the list of active Prisons.",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = Prison::class)))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Failed to retrieve Prisons",
        content = [Content(mediaType = "application/json")],
      ),
    ],
  )
  @GetMapping("/prisons")
  fun getPrisons(): ResponseEntity<List<Prison>> {
    try {
      val prisons = referenceDataService.getPrisons()
      return ResponseEntity.ok(prisons)
    } catch (e: Exception) {
      log.error("Error getting Prisons", e)
      throw NotFoundException("Prisons information not found")
    }
  }
}
