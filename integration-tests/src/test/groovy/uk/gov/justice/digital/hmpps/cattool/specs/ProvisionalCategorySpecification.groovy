package uk.gov.justice.digital.hmpps.cattool.specs

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import geb.spock.GebReportingSpec
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Rule
import uk.gov.justice.digital.hmpps.cattool.mockapis.Elite2Api
import uk.gov.justice.digital.hmpps.cattool.mockapis.OauthApi
import uk.gov.justice.digital.hmpps.cattool.mockapis.RiskProfilerApi
import uk.gov.justice.digital.hmpps.cattool.model.DatabaseUtils
import uk.gov.justice.digital.hmpps.cattool.model.TestFixture
import uk.gov.justice.digital.hmpps.cattool.pages.CategoriserHomePage
import uk.gov.justice.digital.hmpps.cattool.pages.CategoriserSubmittedPage
import uk.gov.justice.digital.hmpps.cattool.pages.OpenConditionsAddedPage
import uk.gov.justice.digital.hmpps.cattool.pages.TasklistPage
import uk.gov.justice.digital.hmpps.cattool.pages.ErrorPage
import uk.gov.justice.digital.hmpps.cattool.pages.ProvisionalCategoryPage
import uk.gov.justice.digital.hmpps.cattool.pages.openConditions.ProvisionalCategoryOpenPage

import java.time.LocalDate

import static uk.gov.justice.digital.hmpps.cattool.model.UserAccount.CATEGORISER_USER

class ProvisionalCategorySpecification extends GebReportingSpec {

  public static final TRICKY_TEXT = '!"£$%^&*()_+-={}[]:@~;\'#<>?,./|\\'

  @Rule
  Elite2Api elite2Api = new Elite2Api()

  @Rule
  RiskProfilerApi riskProfilerApi = new RiskProfilerApi()

  @Rule
  OauthApi oauthApi = new OauthApi(new WireMockConfiguration()
    .extensions(new ResponseTemplateTransformer(false)))

  TestFixture fixture = new TestFixture(browser, elite2Api, oauthApi, riskProfilerApi)
  DatabaseUtils db = new DatabaseUtils()

  def setup() {
    db.clearDb()
  }

  def 'The Provisional Category page is present'() {
    given: 'Ratings data exists for B2345YZ'
    db.createDataWithStatus(12, 'STARTED', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB
    ]))

    when: 'I go to the Provisional Category page'
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-3).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])
    fixture.loginAs(CATEGORISER_USER)
    at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12)
    to new ProvisionalCategoryPage(bookingId: '12'), '12'

    then: 'The page is displayed correctly'
    warning[0].text() == 'B\nWarning\nBased on the information provided, the provisional category is B'

    when: 'I enter some data, save and return to the page'
    elite2Api.stubCategorise('C', '2019-12-14')
    appropriateNo.click()

    // the displayed property does not work on these radios for some reason
    overriddenCategoryB.@type == null
    overriddenCategoryC.@type == 'radio'
    overriddenCategoryD.@type == 'radio'

    overriddenCategoryC.click()
    overriddenCategoryText << "Some Text"
    otherInformationText << "other info  Text"
    submitButton.click()
    at CategoriserSubmittedPage
    to new ProvisionalCategoryPage(bookingId: '12'), '12'

    then: 'The data is correct and is shown on return'
    form.categoryAppropriate == "No"
    form.overriddenCategory == "C"
    form.otherInformationText == "other info  Text"
    form.overriddenCategoryText == "Some Text"

    def data = db.getData(12)
    data.status == ["AWAITING_APPROVAL"]
    data.assessed_by == ["CATEGORISER_USER"]
    data.approved_by == [null]
    data.assessment_date != null
    data.nomis_sequence_no == [4]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsB
    response.supervisor == null
    response.categoriser == [provisionalCategory: [suggestedCategory  : 'B', overriddenCategory: 'C',
                                                   categoryAppropriate: 'No', otherInformationText: 'other info  Text', overriddenCategoryText: 'Some Text']]
    response.openConditionsRequested == null
  }

  def 'Validation test'() {
    db.createDataWithStatus(12, 'STARTED', JsonOutput.toJson([ratings: TestFixture.defaultRatingsC]))

    when: 'I submit the Provisional Category page without selecting anything'
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-3).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])
    fixture.loginAs(CATEGORISER_USER)
    at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12)
    to new ProvisionalCategoryPage(bookingId: '12'), '12'
    submitButton.click()

    then: 'I stay on the page with validation errors'
    errorSummaries*.text() == ['Please select yes or no']
    errors*.text() == ['Error:\nPlease select yes or no']

    when: 'I just select appropriate "No"'
    appropriateNo.click()
    submitButton.click()

    then: 'I stay on the page with validation errors'
    at new ProvisionalCategoryPage(bookingId: '12')
    errorSummaries*.text() == ['Please enter the new category',
                               'Please enter the reason why you changed the category']
    errors*.text() == ['Error:\nPlease select the new category',
                       'Error:\nPlease enter the reason why you changed the category']

    when: 'I submit the Provisional Category page with an empty text area'
    overriddenCategoryB.click()
    submitButton.click()

    then: 'I stay on the page with validation errors'
    at new ProvisionalCategoryPage(bookingId: '12')
    errorSummaries*.text() == ['Please enter the reason why you changed the category']
    errors*.text() == ['Error:\nPlease enter the reason why you changed the category']
  }

  def 'young offender redirects to open conditions flow'() {
    given: 'Ratings data exists for B2345YZ'
    db.createDataWithStatus(12, 'STARTED', JsonOutput.toJson([
      ratings    : TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "I", categoryAppropriate: "Yes"]]]))

    when: 'I go to the Provisional Category page for young offender'
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-3).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])
    fixture.loginAs(CATEGORISER_USER)
    at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12, 'B2345YZ', true)
    to new ProvisionalCategoryPage(bookingId: '12'), '12'
    !newCatMessage.displayed
    appropriateNo.click()

    then: 'The page shows info Changing to Cat J'
    warning.text().contains 'the provisional category is YOI Closed'
    newCatMessage.text() == 'Changing to YOI Open'

    when: 'Changing to Cat J'
    overriddenCategoryText << "Some Text"
    elite2Api.stubGetOffenderDetails(12)
    riskProfilerApi.stubGetSocProfile('B2345YZ', 'C', false)
    submitButton.click()
    at OpenConditionsAddedPage
    button.click()

    then: 'user is redirected to the categoriser tasklist with the open conditions flow available'
    at TasklistPage

    def data = db.getData(12)
    data.status == ["STARTED"]
    data.nomis_sequence_no == [null]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsB
    response.supervisor == null
    response.categoriser == [provisionalCategory: [suggestedCategory  : 'I', overriddenCategory: 'J',
                                                   categoryAppropriate: 'No', overriddenCategoryText: 'Some Text']]
    response.openConditionsRequested
  }

  def 'Category D redirects to open conditions flow'() {
    given: 'Ratings data exists for B2345YZ'
    db.createDataWithStatus(12, 'STARTED', JsonOutput.toJson([
      ratings    : TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "C", categoryAppropriate: "Yes"]]]))

    when: 'I go to the Provisional Category page for the offender'
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-3).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])
    fixture.loginAs(CATEGORISER_USER)
    at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12, 'B2345YZ', false)
    to new ProvisionalCategoryPage(bookingId: '12'), '12'
    !newCatMessage.displayed
    appropriateNo.click()
    overriddenCategoryText << "Some Text"
    otherInformationText << TRICKY_TEXT
    overriddenCategoryD.click()
    elite2Api.stubGetOffenderDetails(12)
    riskProfilerApi.stubGetSocProfile('B2345YZ', 'C', false)
    submitButton.click()
    at OpenConditionsAddedPage
    button.click()

    then: 'user is redirected to open conditions flow'
    at TasklistPage
    openConditionsButton.displayed

    def data = db.getData(12)
    data.status == ["STARTED"]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsB
    response.supervisor == null
    response.categoriser == [provisionalCategory: [suggestedCategory  : 'B', overriddenCategory: 'D',
                                                   categoryAppropriate: 'No', otherInformationText: TRICKY_TEXT, overriddenCategoryText: 'Some Text']]
    response.openConditionsRequested
  }

  def 'indefinite sentence test'() {
    given: 'Ratings data exists'
    db.createDataWithStatus(12, 'STARTED', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsC]))

    when: 'I go to the Provisional Category page'
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-3).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])
    fixture.loginAs(CATEGORISER_USER)
    at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12, 'B2345YZ', false, true)
    to new ProvisionalCategoryPage(bookingId: '12'), '12'

    then: 'The page is displayed correctly'
    appropriateNo.click()

    then: 'The page shows cat B and C'
    warning.text() == 'C\nWarning\nBased on the information provided, the provisional category is C'
    newCatMessage.text() == 'Changing to Cat B'

    when: 'Changing to Cat B'
    elite2Api.stubCategorise('B', '2019-12-14')
    overriddenCategoryText << "Explanation"
    otherInformationText << "other info"
    submitButton.click()

    then: 'Data is stored correctly'
    at CategoriserSubmittedPage

    def data = db.getData(12)
    data.status == ["AWAITING_APPROVAL"]
    data.nomis_sequence_no == [4]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsC
    response.supervisor == null
    response.categoriser == [provisionalCategory: [suggestedCategory  : 'C', overriddenCategory: 'B',
                                                   categoryAppropriate: 'No', otherInformationText: 'other info', overriddenCategoryText: 'Explanation']]
    response.openConditionsRequested == null
  }

  def 'Rollback on elite2Api failure'() {
    given: 'I am at the Provisional Category page'
    db.createDataWithStatus(12, 'STARTED', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB
    ]))
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-3).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])
    fixture.loginAs(CATEGORISER_USER)
    at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12)
    to new ProvisionalCategoryPage(bookingId: '12'), '12'

    when: 'I save some data, but an api error occurs'
    elite2Api.stubCategoriseError()
    appropriateYes.click()
    submitButton.click()

    then: 'An error is displayed and the data is not persisted'
    at ErrorPage
    errorSummaryTitle.text() == 'Server Error'

    def data = db.getData(12)
    data.status == ["STARTED"]
    !data.form_response.value[0].contains("provisionalCategory")
  }

  def 'Confirmation of Cat D'() {
    given: 'Prisoner has completed the open conditions pages'

    db.createDataWithStatus(12, 'STARTED', JsonOutput.toJson([
      ratings                : TestFixture.defaultRatingsC,
      categoriser            : [provisionalCategory: [suggestedCategory  : 'B', overriddenCategory: 'D',
                                                      categoryAppropriate: 'No', otherInformationText: TRICKY_TEXT, overriddenCategoryText: 'Some Text']],
      openConditionsRequested: true,
      openConditions         : TestFixture.defaultOpenConditions,
    ]))

    when: 'I go to the Provisional Category page'
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-3).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])
    fixture.loginAs(CATEGORISER_USER)
    at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12)
    via ProvisionalCategoryPage, '12'

    then: 'The page redirects to the open conditions version and shows cat D'
    at new ProvisionalCategoryOpenPage(bookingId: '12')
    warning[0].text() == 'D\nWarning\nBased on the information provided, the provisional category is D'

    when: 'I confirm Cat D'
    elite2Api.stubCategorise('D', '2019-12-14')
    appropriateYes.click()

    submitButton.click()
    at CategoriserSubmittedPage
    via ProvisionalCategoryPage, '12'

    then: 'The data is correct and is shown on return'
    at new ProvisionalCategoryOpenPage(bookingId: '12')
    // blank   form.categoryAppropriate == ""

    def data = db.getData(12)
    data.status == ["AWAITING_APPROVAL"]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsC
    response.supervisor == null
    response.categoriser == [provisionalCategory: [suggestedCategory  : 'B', overriddenCategory: 'D',
                                                   categoryAppropriate: 'No', otherInformationText: TRICKY_TEXT, overriddenCategoryText: 'Some Text']]
    response.openConditionsRequested
    response.openConditions == TestFixture.defaultOpenConditions
  }

  def 'Confirmation of Cat D rejected'() {
    given: 'Prisoner has completed the open conditions pages'

    db.createDataWithStatus(12, 'STARTED', JsonOutput.toJson([
      ratings                : TestFixture.defaultRatingsC,
      categoriser            : [provisionalCategory: [suggestedCategory  : 'B', overriddenCategory: 'D',
                                                      categoryAppropriate: 'No', otherInformationText: TRICKY_TEXT, overriddenCategoryText: 'Some Text']],
      openConditionsRequested: true,
      openConditions         : TestFixture.defaultOpenConditions,
    ]))

    when: 'I go to the Provisional Category page'
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-3).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])
    fixture.loginAs(CATEGORISER_USER)
    at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12)
    via ProvisionalCategoryPage, '12'

    then: 'The page redirects to the open conditions version and shows cat D'
    at new ProvisionalCategoryOpenPage(bookingId: '12')
    warning[0].text() == 'D\nWarning\nBased on the information provided, the provisional category is D'

    when: 'I reject Cat D'
    riskProfilerApi.stubGetSocProfile('B2345YZ', 'C', false)
    appropriateNo.click()
    submitButton.click()

    then: 'The tasklist is shown with open conditions task removed but form data retained in database'
    at TasklistPage
    !openConditionsButton.displayed

    def data = db.getData(12)
    data.status == ["STARTED"]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsC
    response.supervisor == null
    response.categoriser == [:]
    response.openConditionsRequested == false
    response.openConditions == TestFixture.defaultOpenConditions
  }
}
