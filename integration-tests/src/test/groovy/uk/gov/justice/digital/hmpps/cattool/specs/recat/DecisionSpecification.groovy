package uk.gov.justice.digital.hmpps.cattool.specs.recat

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import geb.spock.GebReportingSpec
import groovy.json.JsonSlurper
import org.junit.Rule
import uk.gov.justice.digital.hmpps.cattool.mockapis.Elite2Api
import uk.gov.justice.digital.hmpps.cattool.mockapis.OauthApi
import uk.gov.justice.digital.hmpps.cattool.mockapis.RiskProfilerApi
import uk.gov.justice.digital.hmpps.cattool.model.DatabaseUtils
import uk.gov.justice.digital.hmpps.cattool.model.TestFixture
import uk.gov.justice.digital.hmpps.cattool.pages.OpenConditionsAddedPage
import uk.gov.justice.digital.hmpps.cattool.pages.TasklistRecatPage
import uk.gov.justice.digital.hmpps.cattool.pages.recat.DecisionPage
import uk.gov.justice.digital.hmpps.cattool.pages.recat.HigherSecurityReviewPage
import uk.gov.justice.digital.hmpps.cattool.pages.recat.MiniHigherSecurityReviewPage

class DecisionSpecification extends GebReportingSpec {

  @Rule
  Elite2Api elite2Api = new Elite2Api()

  @Rule
  RiskProfilerApi riskProfilerApi = new RiskProfilerApi()

  @Rule
  OauthApi oauthApi = new OauthApi(new WireMockConfiguration()
    .extensions(new ResponseTemplateTransformer(false)))

  def setup() {
    db.clearDb()
  }

  TestFixture fixture = new TestFixture(browser, elite2Api, oauthApi, riskProfilerApi)
  DatabaseUtils db = new DatabaseUtils()

  def "The page saves details correctly"() {
    when: 'I go to the Decision page'
    fixture.gotoTasklistRecat(false)
    at TasklistRecatPage
    decisionButton.click()

    then: 'The page is displayed'
    at DecisionPage
    headerValue*.text() == fixture.MINI_HEADER
    hints*.text() == [
      'You will need to complete a higher security review for this person, after choosing this category.',
      'Choosing this category requires no additional reviews or assessments.',
      'You will need to complete an open conditions assessment for this person, to check they are suitable for Category D conditions.']

    when: 'Details are entered, saved and accessed'
    categoryCOption.click()

    submitButton.click()
    at TasklistRecatPage
    decisionButton.click()
    at DecisionPage

    then: "data is correctly retrieved"
    form.category == "C"

    def data = db.getData(12)
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    data.status == ['STARTED']
    data.cat_type.value == ['RECAT']
    response.recat == [decision: [category: "C"]]
    data.user_id == ['RECATEGORISER_USER']
    data.assigned_user_id == ['RECATEGORISER_USER']
  }

  def "The correct mini higher security page is displayed for I->B"() {
    when: 'I go to the Mini Higher Security Review page'
    fixture.gotoTasklistRecatForCatI(false)
    at TasklistRecatPage
    decisionButton.click()

    then: 'The page is displayed'
    at DecisionPage
    headerValue[1].text() == 'C0001AA'
    hints*.text() == [
      'You will need to complete a higher security review for this person, after choosing this category.',
      'Choosing this category requires no additional reviews or assessments.',
      'You will need to complete an open conditions assessment for this person, to check they are suitable for Category D conditions.',
      'Choosing this category requires no additional reviews or assessments.',
      'You will need to complete an open conditions assessment for this person, to check they are suitable for YOI open conditions.']

    when: 'Details are entered, saved and accessed'
    categoryBOption.click()

    submitButton.click()
    at MiniHigherSecurityReviewPage
    conditions << 'some text'
    submitButton.click()
    at TasklistRecatPage

    then: "data is correctly retrieved"
    decisionButton.click()
    at DecisionPage
    form.category == "B"

    def data = db.getData(21)
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    data.status == ['STARTED']
    data.cat_type.value == ['RECAT']
    response.recat == [decision: [category: "B"], miniHigherSecurityReview: [conditions: 'some text']]
    data.user_id == ['RECATEGORISER_USER']
    data.assigned_user_id == ['RECATEGORISER_USER']

    when: 'user changes their mind - mini higher security data is cleared'
    categoryDOption.click()
    submitButton.click()
    at OpenConditionsAddedPage
    button.click()
    at TasklistRecatPage

    then: "data no longer includes higher security data"

    def dataAfterClear = db.getData(21)
    def responseAfterClear = new JsonSlurper().parseText(dataAfterClear.form_response[0].toString())
    dataAfterClear.status == ['STARTED']
    dataAfterClear.cat_type.value == ['RECAT']
    responseAfterClear.recat == [decision: [category: "D"]]
    dataAfterClear.user_id == ['RECATEGORISER_USER']
    dataAfterClear.assigned_user_id == ['RECATEGORISER_USER']
  }

  def "The correct higher security page is displayed for C->B"() {
    when: 'I go to the Decision page'
    fixture.gotoTasklistRecat(false)
    at TasklistRecatPage
    decisionButton.click()

    then: 'The page is displayed'
    at DecisionPage
    headerValue*.text() == fixture.MINI_HEADER

    when: 'Details are entered, saved and accessed'
    categoryBOption.click()

    submitButton.click()
    at HigherSecurityReviewPage
    behaviour << "Some behaviour text"
    steps << "Some steps text"
    transferNo.click()
    transferText << "Some transfer text"
    conditions << "Some conditions text"
    submitButton.click()
    at TasklistRecatPage

    then: "data is correctly retrieved"
    decisionButton.click()
    at DecisionPage
    form.category == "B"

    when: "Higher security is accessed again"
    submitButton.click()

    then: "data is correctly retrieved"
    at HigherSecurityReviewPage
    behaviour == "Some behaviour text"
    steps == "Some steps text"
    form.transfer == "No"
    transferText == "Some transfer text"
    conditions == "Some conditions text"

    def data = db.getData(12)
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    data.status == ['STARTED']
    data.cat_type.value == ['RECAT']
    response.recat == [
      decision            : [category: "B"],
      higherSecurityReview: [steps       : "Some steps text",
                             behaviour   : "Some behaviour text",
                             conditions  : "Some conditions text",
                             transfer    : "No",
                             transferText: "Some transfer text"]]
    data.user_id == ['RECATEGORISER_USER']
    data.assigned_user_id == ['RECATEGORISER_USER']

    when: 'user changes their mind - higher security data is cleared'
    submitButton.click()
    at TasklistRecatPage
    decisionButton.click()
    at DecisionPage
    categoryDOption.click()
    submitButton.click()
    at OpenConditionsAddedPage
    button.click()
    at TasklistRecatPage

    then: "data no longer includes higher security data"

    def dataAfterClear = db.getData(12)
    def responseAfterClear = new JsonSlurper().parseText(dataAfterClear.form_response[0].toString())
    dataAfterClear.status == ['STARTED']
    dataAfterClear.cat_type.value == ['RECAT']
    responseAfterClear.recat == [decision: [category: "D"]]
    dataAfterClear.user_id == ['RECATEGORISER_USER']
    dataAfterClear.assigned_user_id == ['RECATEGORISER_USER']
  }

  def 'Validation test'() {
    when: 'I submit the page with empty details'
    fixture.gotoTasklistRecat(false)
    at TasklistRecatPage
    decisionButton.click()
    to DecisionPage, '12'

    at DecisionPage
    submitButton.click()

    then: 'I stay on the page with validation errors'
    at DecisionPage
    errorSummaries*.text() == ['Please select a security condition']
    errors*.text() == ['Error:\nPlease select a security condition']
  }
}
