package uk.gov.justice.digital.hmpps.cattool.specs

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import geb.spock.GebReportingSpec
import groovy.json.JsonOutput
import org.junit.After
import org.junit.Rule
import uk.gov.justice.digital.hmpps.cattool.mockapis.Elite2Api
import uk.gov.justice.digital.hmpps.cattool.mockapis.OauthApi
import uk.gov.justice.digital.hmpps.cattool.mockapis.RiskProfilerApi
import uk.gov.justice.digital.hmpps.cattool.model.DatabaseUtils
import uk.gov.justice.digital.hmpps.cattool.model.TestFixture
import uk.gov.justice.digital.hmpps.cattool.pages.CategoriserHomePage
import uk.gov.justice.digital.hmpps.cattool.pages.CategoriserSecurityInputPage
import uk.gov.justice.digital.hmpps.cattool.pages.CategoriserTasklistPage
import uk.gov.justice.digital.hmpps.cattool.pages.ReviewPage

import java.time.LocalDate

import static uk.gov.justice.digital.hmpps.cattool.model.UserAccount.CATEGORISER_USER

class ReviewSpecification extends GebReportingSpec {

  @Rule
  Elite2Api elite2api = new Elite2Api()

  @Rule
  RiskProfilerApi riskProfilerApi = new RiskProfilerApi()

  @Rule
  OauthApi oauthApi = new OauthApi(new WireMockConfiguration()
    .extensions(new ResponseTemplateTransformer(false)))

  def setup() {
    db.clearDb()
  }

  def cleanup() {
    db.clearDb()
  }

  TestFixture fixture = new TestFixture(browser, elite2api, oauthApi, riskProfilerApi)
  DatabaseUtils db = new DatabaseUtils()


  def "The review page can be displayed"() {
    given: 'data has been entered for the ratings pages'
    db.createDataWithStatus(12, 'SECURITY_BACK', JsonOutput.toJson([
      ratings : [
        offendingHistory: [previousConvictions: "some convictions"],
        securityInput   : [securityInputNeeded: 'Yes', securityInputNeededText: 'Reasons why referring manually to security'],
        securityBack    : [catB: 'Yes'],
        violenceRating  : [highRiskOfViolence: "No", seriousThreat: "Yes", seriousThreatText: "Here are the serious threat details"],
        escapeRating    : [escapeOtherEvidence: "Yes", escapeOtherEvidenceText: 'Escape Other Evidence Text', escapeCatB: 'Yes', escapeCatBText: 'Reason why Cat B'],
        extremismRating : [previousTerrorismOffences: "Yes", previousTerrorismOffencesText: 'Previous Terrorism Offences Text']
      ],
      security: [
        review: [
          securityReview: 'Here is the Security information held on this prisoner'
        ]
      ]
    ]))
    when: 'The task list is displayed for a fully completed set of ratings'
    fixture.gotoTasklist()
    at(new CategoriserTasklistPage(bookingId: '12'))

    elite2api.stubAssessments('B2345YZ')
    elite2api.stubSentenceDataGetSingle('B2345YZ', '2014-11-23')
    elite2api.stubOffenceHistory('B2345YZ')
    riskProfilerApi.stubGetEscapeProfile('B2345YZ', 'C', true, true)
    riskProfilerApi.stubGetViolenceProfile('B2345YZ', 'C', true, true, false)
    riskProfilerApi.stubGetExtremismProfile('B2345YZ', 'C', true, false, true)

    then: 'the completed text is displayed'
    summarySection[0].text() == 'Review and categorisation'
    summarySection[1].text() == 'Completed'

    when: 'The continue link is selected'
    continueButton.click()

    then: 'the review is displayed with the saved form details'
    at ReviewPage
    values[0..3]*.text() == ['Cat A (2012)', '''Libel (21/02/2019)\nSlander (22/02/2019 - 24/02/2019)\nUndated offence''', 'some convictions', 'TBA']
    values[4..8]*.text() == ['Yes', '5', '2', 'No', '''Yes\nHere are the serious threat details''']
    values[9..12]*.text() == ['Yes', 'Yes', '''Yes\nEscape Other Evidence Text''', '''Yes\nReason why Cat B''']
    values[13..14]*.text() == ['Yes', '''Yes\nPrevious Terrorism Offences Text''']
    values[15..18]*.text() == ['No', 'Yes', 'Here is the Security information held on this prisoner', 'Yes']

    def response = db.getData(12)[0].form_response
    def json = response.toString()
    json.contains '"history": {"catAType": "A",'
    json.contains '"socProfile": {"nomsId": "B2345YZ", "riskType": "SOC", "transferToSecurity": false'
    json.contains '"escapeProfile": {"nomsId": "B2345YZ", "riskType": "ESCAPE", "activeEscapeList": true, "activeEscapeRisk": true,'
    json.contains '"violenceProfile": {"nomsId": "B2345YZ", "riskType": "VIOLENCE", "displayAssaults": false, "numberOfAssaults": 5, "notifySafetyCustodyLead": true, "numberOfSeriousAssaults": 2, "provisionalCategorisation": "C", "veryHighRiskViolentOffender": true}'
    json.contains '"extremismProfile": {"nomsId": "B2345YZ", "riskType": "EXTREMISM", "increasedRisk": true, "notifyRegionalCTLead": false, "provisionalCategorisation": "C"}'
  }
}
