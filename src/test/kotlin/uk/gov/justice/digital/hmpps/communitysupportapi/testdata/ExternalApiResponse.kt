package uk.gov.justice.digital.hmpps.communitysupportapi.testdata

import uk.gov.justice.digital.hmpps.communitysupportapi.dto.CodeDescriptionDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.arns.ArnsOtherRoshRisksDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.arns.ArnsRiskConcernsToSelfDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.arns.ArnsRiskDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.arns.ArnsRiskRoshSummaryDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.arns.ArnsRoshRiskDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.cpr.CprAddressDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.cpr.CprAddressUsageDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.cpr.CprCodeDescriptionDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.cpr.CprContactDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.cpr.CprIdentifiersDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.cpr.CprPersonDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.CommunityManagerDetailsDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.CommunityManagerDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.CommunityManagerNameDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.DisabilitiesDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.HomeOfficeInterestDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.OffenderPersonalityDisorderDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.PersonalCircumstanceDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.delius.PersonalDetailsAndCircumstancesDto
import uk.gov.justice.digital.hmpps.communitysupportapi.dto.prison.PrisonDto
import uk.gov.justice.digital.hmpps.communitysupportapi.model.PersonAdditionalDetails
import uk.gov.justice.digital.hmpps.communitysupportapi.util.toJson
import uk.gov.service.notify.SendEmailResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.String

object ExternalApiResponse {

  const val CRN = "X123456"
  const val PRISONER_NUMBER = "A1234BC"

  // CPR PROBATION PERSON DATA

  private fun createProbationAddress(
    buildingNumber: String = "1",
    thoroughfareName: String = "Test Street",
    postTown: String = "Testville",
    postcode: String = "TE1 1ST",
    startDate: String = "2005-12-01",
    comment: String? = "No notes",
    addressType: String = "Friends/Family (settled) (verified)",
    noFixedAbode: Boolean = false,
  ): CprAddressDto = CprAddressDto(
    cprAddressId = "addr-probation-1",
    noFixedAbode = noFixedAbode,
    buildingNumber = buildingNumber,
    thoroughfareName = thoroughfareName,
    postTown = postTown,
    postcode = postcode,
    startDate = startDate,
    comment = comment,
    status = CprCodeDescriptionDto(code = "M", description = "Main"),
    usages = listOf(
      CprAddressUsageDto(
        code = "FF",
        description = addressType,
        isActive = true,
      ),
    ),
    contacts = listOf(
      CprContactDto(
        type = CprCodeDescriptionDto(code = "TELEPHONE", description = "Telephone"),
        value = "01234567890",
      ),
      CprContactDto(
        type = CprCodeDescriptionDto(code = "MOBILE", description = "Mobile"),
        value = "07700900002",
      ),
      CprContactDto(
        type = CprCodeDescriptionDto(code = "EMAIL", description = "Email"),
        value = "john.smith@example.com",
      ),
    ),
  )

  val standardAddresses = listOf(createProbationAddress())

  val noFixedAbodeAddress = listOf(
    createProbationAddress(
      postcode = "NF1 1NF",
      noFixedAbode = true,
    ),
  )

  fun createCprProbationPersonDto(crn: String, addresses: List<CprAddressDto> = standardAddresses): CprPersonDto = CprPersonDto(
    cprUUID = null,
    firstName = "John",
    middleNames = "David",
    lastName = "Smith",
    dateOfBirth = "1985-01-01",
    title = CprCodeDescriptionDto(code = "MR", description = "Mr"),
    sex = CprCodeDescriptionDto(code = "M", description = "Male"),
    ethnicity = CprCodeDescriptionDto(code = "W1", description = "White"),
    religion = CprCodeDescriptionDto(code = "CHR", description = "Christian"),
    disability = true,
    nationalities = listOf(
      CprCodeDescriptionDto(code = "ARG", description = "Argentine"),
      CprCodeDescriptionDto(code = "BRA", description = "Brazilian"),
    ),
    addresses = addresses,
    identifiers = CprIdentifiersDto(
      crns = listOf(crn),
      prisonNumbers = emptyList(),
      pncs = listOf("2012/0052494Q"),
      cros = listOf("123456/24A"),
      nationalInsuranceNumbers = listOf("AA123456A"),
    ),
  )

  fun createCprProbationPersonAdditionalDetails(): PersonAdditionalDetails = PersonAdditionalDetails(
    ethnicity = "White",
    preferredLanguage = null,
    neurodiverseConditions = null,
    religionOrBelief = "Christian",
    nationalities = listOf("Argentine", "Brazilian"),
    address = "1, Test Street, Testville, TE1 1ST",
    addressType = "Friends/Family (settled) (verified)",
    addressStartDate = LocalDate.of(2005, 12, 1),
    addressNotes = "No notes",
    noFixedAbode = false,
    phoneNumber = "01234567890",
    mobileNumber = "07700900002",
    emailAddress = "john.smith@example.com",
    disability = true,
  )

  // CPR PRISON PERSON DATA
  fun createCprPrisonPersonDto(vararg prisonNumbers: String): CprPersonDto = CprPersonDto(
    cprUUID = null,
    firstName = "John",
    middleNames = "James",
    lastName = "Smith",
    dateOfBirth = "1985-01-01",
    title = CprCodeDescriptionDto(code = "MR", description = "Mr"),
    sex = CprCodeDescriptionDto(code = "M", description = "Male"),
    ethnicity = CprCodeDescriptionDto(code = "W1", description = "White"),
    religion = CprCodeDescriptionDto(code = "CHR", description = "Christian"),
    disability = false,
    nationalities = listOf(
      CprCodeDescriptionDto(code = "GBR", description = "British"),
    ),
    addresses = listOf(
      CprAddressDto(
        cprAddressId = "addr-prison-1",
        buildingNumber = "10",
        thoroughfareName = "Prison Road",
        postTown = "Leeds",
        postcode = "LS1 1AA",
        startDate = "2020-04-03",
        comment = null,
        status = CprCodeDescriptionDto(code = "M", description = "Main"),
        usages = listOf(
          CprAddressUsageDto(
            code = "HOME",
            description = "Home",
            isActive = true,
          ),
        ),
        contacts = listOf(
          CprContactDto(
            type = CprCodeDescriptionDto(code = "TELEPHONE", description = "Telephone"),
            value = "01234567890",
          ),
          CprContactDto(
            type = CprCodeDescriptionDto(code = "MOBILE", description = "Mobile"),
            value = "07700900002",
          ),
          CprContactDto(
            type = CprCodeDescriptionDto(code = "EMAIL", description = "Email"),
            value = "john.smith@example.com",
          ),
        ),
      ),
    ),
    identifiers = CprIdentifiersDto(
      crns = emptyList(),
      prisonNumbers = prisonNumbers.asList(),
      pncs = listOf("12/394773H"),
      cros = listOf("29906/12J"),
    ),
  )

  fun createCprPrisonPersonAdditionalDetails(): PersonAdditionalDetails = PersonAdditionalDetails(
    ethnicity = "White",
    preferredLanguage = null,
    neurodiverseConditions = null,
    religionOrBelief = "Christian",
    nationalities = listOf("British"),
    address = "10, Prison Road, Leeds, LS1 1AA",
    addressType = "Home",
    addressStartDate = LocalDate.of(2020, 4, 3),
    addressNotes = null,
    noFixedAbode = false,
    phoneNumber = "01234567890",
    mobileNumber = "07700900002",
    emailAddress = "john.smith@example.com",
    disability = false,
  )

  // Generic factory for tests that don't depend on source system (e.g. PersonAggregateMapper tests)
  fun createTestPersonAdditionalDetails(): PersonAdditionalDetails = PersonAdditionalDetails(
    ethnicity = "White",
    preferredLanguage = "English",
    neurodiverseConditions = null,
    religionOrBelief = "Christian",
    nationalities = listOf("Argentine", "Brazilian"),
    address = "1, Test Street, Testville, TE1 1ST",
    addressType = "Friends/Family (settled) (verified)",
    addressStartDate = LocalDate.of(2005, 12, 1),
    addressNotes = "No notes",
    phoneNumber = "01234567890",
    mobileNumber = "07700900002",
    emailAddress = "john.smith@example.com",
    disability = true,
  )

  fun cprProbationPersonJson(crn: String) = createCprProbationPersonDto(crn).toJson()
  fun cprProbationPersonNoFixAbodeJson(crn: String) = createCprProbationPersonDto(crn, noFixedAbodeAddress).toJson()

  fun cprPrisonPersonJson(prisonNumber: String) = createCprPrisonPersonDto(prisonNumber).toJson()

  fun cprPersonNotFoundJson() = """
        {
          "error": "Not Found",
          "status": 404,
          "message": "Person not found"
        }
  """.trimIndent()

  // ARNS ROSH RISK DATA

  fun createArnsRoshRiskDto(assessedOn: LocalDateTime? = LocalDateTime.now().minusDays(30)): ArnsRoshRiskDto = ArnsRoshRiskDto(
    riskToSelf = ArnsRiskConcernsToSelfDto(
      suicide = ArnsRiskDto(
        riskIndicator = "YES",
        previousConcern = "YES",
        previousConcernsReason = "Previous suicide concerns",
        currentConcern = "YES",
        currentConcernsReason = "Current suicide concerns",
      ),
      selfHarm = ArnsRiskDto(
        riskIndicator = "YES",
        previousConcern = "NO",
        previousConcernsReason = null,
        currentConcern = "YES",
        currentConcernsReason = "Current self harm concerns",
      ),
      custody = ArnsRiskDto(
        riskIndicator = "NO",
        previousConcern = "NO",
        previousConcernsReason = null,
        currentConcern = "NO",
        currentConcernsReason = null,
      ),
      hostelSetting = ArnsRiskDto(
        riskIndicator = "DK",
        previousConcern = "DK",
        previousConcernsReason = null,
        currentConcern = "DK",
        currentConcernsReason = null,
      ),
      vulnerability = ArnsRiskDto(
        riskIndicator = "YES",
        previousConcern = "NO",
        previousConcernsReason = null,
        currentConcern = "YES",
        currentConcernsReason = "Vulnerability concerns noted",
      ),
    ),
    otherRisks = ArnsOtherRoshRisksDto(
      escapeOrAbscond = "NO",
      controlIssuesDisruptiveBehaviour = "YES",
      breachOfTrust = "NO",
      riskToOtherPrisoners = "YES",
    ),
    summary = ArnsRiskRoshSummaryDto(
      whoIsAtRisk = "Staff and public are at risk",
      natureOfRisk = "Risk of violence",
      riskImminence = "Risk is imminent in community",
      riskIncreaseFactors = "Substance misuse increases risk",
      riskMitigationFactors = "Regular supervision reduces risk",
      analysisOfRiskFactors = "Historical pattern of violence",
      riskInCommunity = mapOf(
        "HIGH" to listOf("Public", "Known Adult"),
        "MEDIUM" to listOf("Staff"),
        "LOW" to listOf("Children", "Prisoners"),
      ),
      riskInCustody = mapOf(
        "VERY_HIGH" to listOf("Staff"),
        "HIGH" to listOf("Prisoners"),
        "LOW" to listOf("Children", "Public"),
      ),
      overallRiskLevel = "HIGH",
    ),
    assessedOn = assessedOn,
  )

  fun createStaleArnsRoshRiskDto(): ArnsRoshRiskDto = createArnsRoshRiskDto(assessedOn = LocalDateTime.now().minusMonths(13))

  fun arnsRoshRiskJson(assessedOn: LocalDateTime = LocalDateTime.now().minusDays(30)) = createArnsRoshRiskDto(assessedOn).toJson()

  fun arnsStaleRoshRiskJson() = createStaleArnsRoshRiskDto().toJson()

  fun arnsRoshRiskNotFoundJson() = """
        {
          "status": 404,
          "userMessage": "CRN Not Found",
          "developerMessage": "ROSH risks not found for CRN"
        }
  """.trimIndent()

  fun createSendEmailResponse(
    notificationId: UUID = UUID.randomUUID(),
    templateId: UUID = UUID.randomUUID(),
    templateVersion: Int = 1,
    templateUri: String = "https://api.notifications.service.gov.uk/templates/$templateId",
    body: String = "Email body",
    subject: String = "Email subject",
    fromEmail: String? = "noreply@example.com",
    reference: String? = "test-reference",
  ): SendEmailResponse {
    val jsonBody = """
        {
            "id": "$notificationId",
            "reference": ${reference?.let { "\"$it\"" } ?: "null"},
            "content": {
                "body": "$body",
                "subject": "$subject",
                "from_email": ${fromEmail?.let { "\"$it\"" } ?: "null"}
            },
            "template": {
                "id": "$templateId",
                "version": $templateVersion,
                "uri": "$templateUri"
            }
        }
    """.trimIndent()
    return SendEmailResponse(jsonBody)
  }

  fun createPersonalCircumstances(): List<PersonalCircumstanceDto> = listOf(
    PersonalCircumstanceDto(
      CodeDescriptionDto("REL", "Relationships"),
      CodeDescriptionDto("REL_SUB", "Relationships sub type"),
      OffsetDateTime.of(2026, 3, 12, 14, 25, 0, 0, ZoneOffset.ofHours(1)),
    ),
    PersonalCircumstanceDto(
      CodeDescriptionDto("EMP", "Employment"),
      CodeDescriptionDto("EMP_SUB", "Employment sub type"),
      OffsetDateTime.of(2026, 2, 12, 14, 25, 0, 0, ZoneOffset.ofHours(1)),
    ),
    PersonalCircumstanceDto(
      CodeDescriptionDto("DEP", "Dependants"),
      CodeDescriptionDto("DEP_SUB", "Dependants sub type"),
      OffsetDateTime.of(2026, 1, 12, 14, 25, 0, 0, ZoneOffset.ofHours(1)),
    ),
  )

  fun createDisabilities(): List<DisabilitiesDto> = listOf(
    DisabilitiesDto(
      CodeDescriptionDto("BLN", "Blind"),
      updatedAt = OffsetDateTime.of(2026, 3, 12, 14, 25, 0, 0, ZoneOffset.ofHours(1)),
    ),
  )

  fun createPersonDetailsAndCircumstancesDto(
    preferredLanguage: CodeDescriptionDto = CodeDescriptionDto("EN", "English"),
    personalCircumstances: List<PersonalCircumstanceDto> = createPersonalCircumstances(),
    disabilities: List<DisabilitiesDto> = createDisabilities(),
    offenderPersonalityDisorderDto: OffenderPersonalityDisorderDto = OffenderPersonalityDisorderDto(status = CodeDescriptionDto("NO", "N/A")),
  ): PersonalDetailsAndCircumstancesDto = PersonalDetailsAndCircumstancesDto(
    preferredLanguage,
    personalCircumstances,
    disabilities,
    offenderPersonalityDisorderDto,
  )

  fun createHomeOfficeInterestDto(): HomeOfficeInterestDto = HomeOfficeInterestDto(true, "Is of interest")

  fun createPersonDetailsAndCircumstances() = createPersonDetailsAndCircumstancesDto().toJson()
  fun createHomeOfficeInterest() = createHomeOfficeInterestDto().toJson()

  fun createCommunityManagerDto(
    crn: String = CRN,
    forename: String = "TestForename",
    middleName: String? = null,
    surname: String = "TestSurname",
    jobRole: String? = "Probation practitioner",
    emailAddress: String? = "testForename.testSurname@digital.justice.gov.uk",
    pdu: String = "Northumberland",
    officeName: String? = null,
    teamPhoneNumber: String? = null,
  ): CommunityManagerDto = CommunityManagerDto(
    crn = crn,
    communityManager = CommunityManagerDetailsDto(
      jobRole = jobRole,
      emailAddress = emailAddress,
      pdu = pdu,
      officeName = officeName,
      name = CommunityManagerNameDto(forename = forename, middleName = middleName, surname = surname),
      teamPhoneNumber = teamPhoneNumber,
    ),
  )

  fun createCommunityManager() = createCommunityManagerDto().toJson()

  fun personDetailsAndCircumstancesNotFoundJson() = """
        {
          "error": "Not Found",
          "status": 404,
          "message": "Person not found"
        }
  """.trimIndent()

  // PRISON API DATA

  fun createPrisonDto(
    agencyId: String = "MDI",
    description: String = "Moorland (HMP & YOI)",
    longDescription: String? = "Moorland (HMP & YOI)",
    agencyType: String = "INST",
    active: Boolean = true,
  ): PrisonDto = PrisonDto(
    agencyId = agencyId,
    description = description,
    longDescription = longDescription,
    agencyType = agencyType,
    active = active,
  )

  fun createPrisonsList(): List<PrisonDto> = listOf(
    createPrisonDto(agencyId = "MDI", description = "Moorland (HMP & YOI)", active = true),
    createPrisonDto(agencyId = "LEI", description = "Leeds (HMP)", active = true),
    createPrisonDto(agencyId = "ZZGHI", description = "Inactive Prison (Closed)", active = false),
  )

  fun prisonsJson() = createPrisonsList().toJson()
}
