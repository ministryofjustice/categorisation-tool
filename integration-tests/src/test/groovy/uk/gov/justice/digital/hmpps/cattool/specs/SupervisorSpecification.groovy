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
import uk.gov.justice.digital.hmpps.cattool.pages.*
import uk.gov.justice.digital.hmpps.cattool.pages.recat.SupervisorRecatReviewPage

import java.time.LocalDate

import static uk.gov.justice.digital.hmpps.cattool.model.UserAccount.CATEGORISER_USER
import static uk.gov.justice.digital.hmpps.cattool.model.UserAccount.SUPERVISOR_USER

class SupervisorSpecification extends GebReportingSpec {

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


  def "The supervisor review page can be confirmed"() {
    when: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatus(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "C", categoryAppropriate: "Yes"]]]))
    db.createNomisSeqNo(12,5)
    db.createRiskProfileDataForExistingRow(12, JsonOutput.toJson([
      history : [catAType: 'A', finalCat: 'Cat B', catAEndYear: '2013', releaseYear: '2014', catAStartYear: '2012'],
      offences: [[bookingId: 12, offenceDate: '2019-02-21', offenceDescription: 'Libel'],
                 [bookingId: 12, offenceDate: '2019-02-22', offenceRangeDate: '2019-02-24', offenceDescription: 'Slander'],
                 [bookingId: 12, offenceDescription: 'Undated offence']]
    ]))
    navigateToReview()

    then: 'the header is correct, change links are not displayed and the buttons omit the current cat'
    headerValue*.text() == fixture.FULL_HEADER
    changeLinks.size() == 0
    // the displayed property does not work on these radios for some reason
    overriddenCategoryB.@type == 'radio'
    overriddenCategoryC.@type == null
    overriddenCategoryD.@type == 'radio'
    offendingHistorySummary*.text() == ['Cat A (2012)', 'Libel (21/02/2019)\nSlander (22/02/2019 - 24/02/2019)\nUndated offence', 'Yes\nsome convictions']

    when: 'the supervisor selects yes (after changing their mind)'
    elite2Api.stubSupervisorApprove("C")

    appropriateNo.click()
    overriddenCategoryB.click()
    overriddenCategoryText << "Im not sure"
    appropriateYes.click()
    submitButton.click()

    then: 'the review outcome page is displayed and review choices persisted'
    at SupervisorReviewOutcomePage

    def data = db.getData(12)
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsB
    response.supervisor == [review: [proposedCategory: 'C', supervisorCategoryAppropriate: 'Yes']]
    response.categoriser == [provisionalCategory: [suggestedCategory: 'C', categoryAppropriate: 'Yes']]
    response.openConditionsRequested == null
    data.status == ["APPROVED"]
  }

  def "The supervisor review page can be confirmed - youth offender"() {
    given: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatus(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "I", categoryAppropriate: "Yes"]]]))
    db.createNomisSeqNo(12,5)

    navigateToReview(true, false)
    !openConditionsHeader.isDisplayed()

    when: 'the supervisor selects no'
    appropriateNo.click()

    then: 'The page shows info Changing to Cat'
    warnings[0].text().contains 'the provisional category is YOI Closed'
    newCatMessage.text() == 'Changing to YOI Open'

    when: 'The supervisor clicks continue'
    overriddenCategoryText << "reason text"
    elite2Api.stubSentenceData(['B2345XY'], [11], [LocalDate.of(2019, 1, 28).toString()])
    submitButton.click()

    then: 'The record is sent back to the categoriser'
    at SupervisorHomePage

    def data = db.getData(12)
    data.status == ["SUPERVISOR_BACK"]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsB
    response.supervisor == [review     : [proposedCategory: 'I', supervisorOverriddenCategory: 'J', supervisorCategoryAppropriate: 'No', supervisorOverriddenCategoryText: 'reason text'],
                            confirmBack: [messageText: 'reason text']]
    response.categoriser == [provisionalCategory: [suggestedCategory: 'J', categoryAppropriate: 'Yes']]
    response.openConditionsRequested
  }

  def "The supervisor review page displays Open conditions data when category is D or J"() {
    given: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatus(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      openConditions: [riskLevels: [likelyToAbscond: "No"], riskOfHarm: [seriousHarm: "No"], foreignNational: [isForeignNational: "No"], earliestReleaseDate: [threeOrMoreYears: "No"]],
      categoriser: [provisionalCategory: [suggestedCategory: "D", categoryAppropriate: "Yes", otherInformationText: "cat info"]]]))
    db.createNomisSeqNo(12,5)

    when: 'The supervisor views the review page for a category D'
    navigateToReview(false, false)

    then: 'the review page includes Open conditions information'
    openConditionsHeader.isDisplayed()

    riskOfHarm*.text() == ['', 'No', 'Not applicable']
    foreignNational*.text() == ['', 'No', 'Not applicable', 'Not applicable', 'Not applicable']
    earliestReleaseDate*.text() == ['', 'No', 'Not applicable']
    riskLevel*.text() == ['', 'No']

    when: 'The supervisor views the review page for a category J'
    db.clearDb()
    db.createDataWithStatus(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      openConditions: [riskLevels: [likelyToAbscond: "No"], riskOfHarm: [seriousHarm: "No"], foreignNational: [isForeignNational: "No"], earliestReleaseDate: [threeOrMoreYears: "No"]],
      categoriser: [provisionalCategory: [suggestedCategory: "J", categoryAppropriate: "Yes", otherInformationText: "cat info"]]]))
    db.createNomisSeqNo(12,5)

    to SupervisorHomePage

    startButtons[1].click()

    at SupervisorReviewPage

    then: 'the review page includes Open conditions information'
    openConditionsHeader.isDisplayed()
  }

  def "The categoriser has overridden - not open conditions"() {
    given: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatus(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      ratings    : TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "C", overriddenCategory: "B", categoryAppropriate: "No", overriddenCategoryText: "Some Text"]]]))
    db.createNomisSeqNo(12,5)

    when: 'The supervisor views the review page for an overridden category B'
    navigateToReview(false, false)

    then: 'the review page includes changed category and normal answers but not open conditions information'
    !openConditionsHeader.isDisplayed()
    warning.text() == 'C\nB\nWarning\nThe category was originally C and is now B'
    offendingHistorySummary[2].text() == 'Yes\nsome convictions'
  }


  def "The supervisor can send the case back to the categoriser"() {
    given: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatus(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "C", categoryAppropriate: "Yes"]]]))
    db.createNomisSeqNo(12,5)

    navigateToReview(false, false)

    when: 'the supervisor clicks the review page "send back to categoriser" button'
    backToCategoriserButton.click()

    then: 'The confirm page is displayed'
    at SupervisorConfirmBackPage

    when: 'no is selected'
    answerNo.click()
    submitButton.click()

    then: 'The review page is re-displayed'
    at SupervisorReviewPage

    when: 'the supervisor confirms without entering a message'
    backToCategoriserButton.click()
    at SupervisorConfirmBackPage
    answerYes.click()
    submitButton.click()

    then: 'there is a validation error'
    waitFor {
      errorSummaries*.text() == ['Please enter a message for the categorisor']
      errors*.text() == ['Error:\nPlease enter a message']
    }

    when: 'the supervisor confirms to return to categoriser'
    messageText << "a message for categoriser"
    elite2Api.stubSentenceData(['B2345XY'], [11], ['28/01/2019'])
    submitButton.click()

    then: 'the supervisor home page is displayed'
    at SupervisorHomePage

    then: 'offender with booking id 12 has been removed'
    names == ['Pitstop, Penelope']

    def data = db.getData(12)
    data.status == ["SUPERVISOR_BACK"]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.supervisor == [confirmBack: [confirmation: 'Yes', messageText: 'a message for categoriser']]

    when: 'the categorisor views the tasklist and clicks the message task'
    fixture.logout()
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-4).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])
    fixture.loginAs(CATEGORISER_USER)
    at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12, 'B2345YZ', false,  false, 'C', false)
    riskProfilerApi.stubGetSocProfile('B2345YZ', 'C', false)
    selectFirstPrisoner() // has been sorted to top of list!
    at TasklistPage
    supervisorMessageButton.click()

    then: 'the message is displayed'
    at SupervisorMessagePage
    messageText.text() == 'a message for categoriser'

    when: 'the message is dismissed'
    submitButton.click()

    then: 'the supervisor message is flagged as read'
    at TasklistPage
    supervisorMessageButton.text() == 'View'
  }

  def "Overriding to an Open conditions category returns the record to the categoriser"() {
    given: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatus(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "B", categoryAppropriate: "Yes"]]]))
    db.createNomisSeqNo(12,5)

    navigateToReview(false, false)

    when: 'Supervisor chooses to override to category D'
    appropriateNo.click()
    overriddenCategoryD.click()

    then: 'A warning is displayed'
    warnings[1].text() contains "Making this category change means that the categoriser will have to provide more information."

    when: 'The continue button is clicked'
    overriddenCategoryText << "should be a D"
    elite2Api.stubSentenceData(['B2345XY'], [11], [LocalDate.of(2019, 1, 28).toString()])
    submitButton.click()

    then: 'the record is returned to categoriser with open conditions requested and suggestedCategory forced to D'
    at SupervisorHomePage

    def data = db.getData(12)
    data.status == ["SUPERVISOR_BACK"]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsB
    response.supervisor == [review     : [proposedCategory: 'B', supervisorOverriddenCategory: 'D', supervisorCategoryAppropriate: 'No', supervisorOverriddenCategoryText: 'should be a D'],
                            confirmBack: [messageText: 'should be a D']]
    response.categoriser == [provisionalCategory: [suggestedCategory: 'D', categoryAppropriate: 'Yes']]
    response.openConditionsRequested
  }

  def "Overriding to an Open conditions category returns the record to the categoriser (youth offender)"() {
    given: 'supervisor is viewing the review page for a youth offender'
    db.createDataWithStatus(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "I", categoryAppropriate: "Yes"]]]))
    db.createNomisSeqNo(12,5)

    navigateToReview(false, false)

    when: 'Supervisor chooses to override to category J'
    appropriateNo.click()

    then: 'A warning is displayed'
    warnings[1].text() contains "Making this category change means that the categoriser will have to provide more information."

    when: 'The continue button is clicked'
    overriddenCategoryText << "should be a J"
    elite2Api.stubSentenceData(['B2345XY'], [11], [LocalDate.of(2019, 1, 28).toString()])
    submitButton.click()

    then: 'the record is returned to categoriser with open conditions requested and suggestedCategory forced to J'
    at SupervisorHomePage

    def data = db.getData(12)
    data.status == ["SUPERVISOR_BACK"]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsB
    response.supervisor == [review     : [proposedCategory: 'I', supervisorOverriddenCategory: 'J', supervisorCategoryAppropriate: 'No', supervisorOverriddenCategoryText: 'should be a J'],
                            confirmBack: [messageText: 'should be a J']]
    response.categoriser == [provisionalCategory: [suggestedCategory: 'J', categoryAppropriate: 'Yes']]
    response.openConditionsRequested
  }


  def "The supervisor review page validates input, suggested category B overridden with D"() {
    given: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatus(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "D", categoryAppropriate: "Yes", otherInformationText: "Some Text"]]]))
    db.createNomisSeqNo(12,5)

    navigateToReview()

    when: 'the supervisor submits without selecting yes or no'
    submitButton.click()

    then: 'the review page is displayed with an error'
    at SupervisorReviewPage

    errorSummaries*.text() == ['Please select yes or no']

    and: 'the supervisor selects no and submits'
    appropriateNo.click()
    submitButton.click()

    then: 'the review page is displayed with errors - enter a category and a reason'
    at SupervisorReviewPage

    // the displayed property does not work on these radios for some reason
    overriddenCategoryB.@type == 'radio'
    overriddenCategoryC.@type == 'radio'
    overriddenCategoryD.@type == null

    errorSummaries*.text() == ['Please enter the new category', 'Please enter the reason why you changed the category']

    and: 'the supervisor selects a category and submits'
    appropriateNo.click()
    overriddenCategoryB.click()
    submitButton.click()

    then: 'the review page is displayed with an error - reason not provided'
    at SupervisorReviewPage

    errorSummaries*.text() == ['Please enter the reason why you changed the category']

    and: 'the supervisor selects a category, reason and submits'
    elite2Api.stubSupervisorApprove('B')
    appropriateNo.click()
    overriddenCategoryB.click()
    overriddenCategoryText << 'A good reason'
    submitButton.click()

    then: 'the review outcome page is displayed and review choices persisted'
    at SupervisorReviewOutcomePage

    def data = db.getData(12)
    data.status == ["APPROVED"]
    data.approval_date != null
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    response.ratings == TestFixture.defaultRatingsB
    response.categoriser == [provisionalCategory: [suggestedCategory: "D", categoryAppropriate: "Yes", otherInformationText: "Some Text"]]
    response.supervisor ==  [review: [proposedCategory: 'D', supervisorOverriddenCategory: 'B', supervisorCategoryAppropriate: 'No', supervisorOverriddenCategoryText: 'A good reason']]
    response.openConditionsRequested == null
  }

  private navigateToReview(youngOffender = false, indeterminateSentence = false, initial = true) {

    def sentenceStartDate11 = LocalDate.of(2019, 1, 28)
    if (initial) {
      def sentenceStartDate12 = LocalDate.of(2019, 1, 31)
      elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [sentenceStartDate11.toString(), sentenceStartDate12.toString()])
    } else {
      // Recat does not request sentence data
      elite2Api.stubSentenceData(['B2345XY'], [11], [sentenceStartDate11.toString()])
    }
    elite2Api.stubUncategorisedAwaitingApproval()

    fixture.loginAs(SUPERVISOR_USER)

    at SupervisorHomePage

    elite2Api.stubGetOffenderDetails(12, 'B2345YZ', youngOffender, indeterminateSentence)
    elite2Api.stubAssessments(['B2345YZ'])
    elite2Api.stubAgencyDetails('LPI')
    elite2Api.stubSentenceDataGetSingle('B2345YZ', '2014-11-23')

    if (initial) {
      startButtons[1].click()
      at SupervisorReviewPage
    } else {
      startButtons[0].click()
      at SupervisorRecatReviewPage
    }
  }

  def "The done page for a supervisor is present"() {
    when: 'I go to the home page as supervisor and select the done tab'

    db.createDataWithStatus(-2, 12, 'APPROVED', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "C", overriddenCategory: "D", categoryAppropriate: "No", overriddenCategoryText: "Some Text"]]]))

    db.createDataWithStatus(-1,11, 'APPROVED', JsonOutput.toJson([
      ratings: TestFixture.defaultRatingsB,
      categoriser: [provisionalCategory: [suggestedCategory: "C", overriddenCategory: "D", categoryAppropriate: "No", overriddenCategoryText: "Some Text"]]]))

    db.createNomisSeqNo(11, 7)
    db.createNomisSeqNo(12, 8)


    def sentenceStartDate11 = LocalDate.of(2019, 1, 28)
    def sentenceStartDate12 = LocalDate.of(2019, 1, 31)

    elite2Api.stubUncategorisedAwaitingApproval()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [sentenceStartDate11.toString(), sentenceStartDate12.toString()])

    fixture.loginAs(SUPERVISOR_USER)
    at SupervisorHomePage

    elite2Api.stubCategorisedMultiple()

    doneTabLink.click()

    then: 'The supervisor done page is displayed with correctly matched categorisations'

    at SupervisorDonePage

    prisonNos == ['B2345XY', 'B2345YZ']
    names == ['Scramble, Tim', 'Hemmel, Sarah']
    approvalDates == ['20/04/2019', '28/02/2019']
    categorisers == ['Lamb, John', 'Fan, Jane']
    approvers == ['Helly, James', 'Helly, James']
    outcomes == ['C', 'C']
    catTypes == ['Initial', 'Initial']
  }

  def "The supervisor review page for a recat can be confirmed"() {
    when: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatusAndCatType(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      recat: TestFixture.defaultRecat]), 'RECAT', 'B2345YZ')
    db.createNomisSeqNo(12,5)
    db.createRiskProfileDataForExistingRow(12, '''{
      "socProfile": {"nomsId": "B2345YZ", "riskType": "SOC", "transferToSecurity": false},
      "escapeProfile": {"nomsId": "B2345YZ", "riskType": "ESCAPE", "activeEscapeList": true, "activeEscapeRisk": true,
        "escapeListAlerts" : [ { "active": true, "comment": "First xel comment", "expired": false, "alertCode": "XEL", "dateCreated": "2016-09-14", "alertCodeDescription": "Escape List"}]   
      },
      "violenceProfile": {"nomsId": "B2345YZ", "riskType": "VIOLENCE", "displayAssaults": true, "numberOfAssaults": 5, "notifySafetyCustodyLead": true, "numberOfSeriousAssaults": 2, "provisionalCategorisation": "C", "veryHighRiskViolentOffender": false},
      "extremismProfile": {"nomsId": "B2345YZ", "riskType": "EXTREMISM", "notifyRegionalCTLead": true, "increasedRiskOfExtremism": true, "provisionalCategorisation": "C"}}''')
    db.createReviewReason(12, 'DUE')

    navigateToReview(false, false, false)

    then: 'the header is correct, change links are not displayed and the buttons omit the current cat'
    headerValue*.text() == fixture.FULL_HEADER

    changeLinks.size() == 0
    // the displayed property does not work on these radios for some reason
    overriddenCategoryB.@type == 'radio'
    overriddenCategoryC.@type == null
    overriddenCategoryD.@type == 'radio'

    prisonerBackgroundSummary*.text() == [
      '', 'Review due', ('Categorisation date Category decision Review location\n' +
      '24/03/2013 B LPI prison\n' +
      '04/04/2012 A LPI prison'),
      'This person has been reported as the perpetrator in 5 assaults in custody before, including 2 serious assaults in the last 12 months',
      'This person is considered an escape risk\nE-List: First xel comment 2016-09-14',
      'This person is at risk of engaging in, or vulnerable to, extremism.', 'offence Details text']
    securityInputSummary*.text() == ['', 'No', 'No', 'No']
    riskAssessmentSummary*.text() == ['', 'lower security category text', 'higher security category text', 'Yes\nother relevant information']
    assessmentSummary*.text() == ['', 'Category C']
    nextReviewDateSummary*.text() == ['', 'Saturday 14th December 2019']


    when: 'the supervisor selects yes (after changing their mind)'
    elite2Api.stubSupervisorApprove("C" )
    elite2Api.stubAssessments('B2345YZ')

    appropriateNo.click()
    overriddenCategoryB.click()
    overriddenCategoryText << "Im not sure"
    appropriateYes.click()
    submitButton.click()

    then: 'the review outcome page is displayed and review choices persisted'
    at SupervisorReviewOutcomePage

    def data = db.getData(12)
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    def riskResponse = new JsonSlurper().parseText(data.risk_profile[0].toString())
    response.recat == TestFixture.defaultRecat
    response.supervisor == [review: [proposedCategory: 'C', supervisorCategoryAppropriate: 'Yes']]
    riskResponse.catHistory == [["bookingId": -45, "offenderNo": "B2345YZ", "approvalDate": "2012-06-08", "assessmentCode": "CATEGORY", "assessmentDate": "2013-03-24", "classification": "Cat B", "nextReviewDate": "2013-09-17", "assessmentStatus": "I", "agencyDescription": "LPI prison", "assessmentAgencyId": "LPI", "classificationCode": "B", "cellSharingAlertFlag": false, "assessmentDateDisplay": "24/03/2013", "assessmentDescription": "Categorisation"], ["bookingId": -45, "offenderNo": "B2345YZ", "approvalDate": "2012-06-08", "assessmentCode": "CATEGORY", "assessmentDate": "2012-04-04", "classification": "Cat A", "nextReviewDate": "2012-06-07", "assessmentStatus": "A", "agencyDescription": "LPI prison", "assessmentAgencyId": "LPI", "classificationCode": "A", "cellSharingAlertFlag": false, "assessmentDateDisplay": "04/04/2012", "assessmentDescription": "Categorisation"]]

    response.openConditionsRequested == null
    data.status == ["APPROVED"]
    data.approved_by == ['SUPERVISOR_USER']
  }

  def "The supervisor can send the case back to the recategoriser"() {
    given: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatusAndCatType(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      recat: TestFixture.defaultRecat]), 'RECAT')
    db.createNomisSeqNo(12,5)

    navigateToReview(false, false, false)

    when: 'the supervisor clicks the review page "send back to categoriser" button'
    backToCategoriserButton.click()

    then: 'The confirm page is displayed'
    at SupervisorConfirmBackPage

    when: 'the supervisor confirms to return to recategoriser'
    answerYes.click()
    messageText << "a message for re-categoriser"
    elite2Api.stubSentenceData(['B2345XY'], [11], ['28/01/2019'])

    submitButton.click()

    then: 'the supervisor home page is displayed'
    at SupervisorHomePage

    then: 'offender with booking id 12 has been removed'
    names == ['Pitstop, Penelope']

    def data = db.getData(12)
    data.status == ["SUPERVISOR_BACK"]
    data.approved_by == [null]

    when: 'the categorisor views the tasklist and clicks the message task'
    fixture.logout()
    fixture.gotoTasklistRecat()
    at TasklistRecatPage
    supervisorMessageButton.click()

    then: 'the message is displayed'
    at SupervisorMessagePage
    messageText.text() == 'a message for re-categoriser'

    when: 'the message is dismissed'
    submitButton.click()

    then: 'the supervisor message is flagged as read'
    at TasklistRecatPage
    supervisorMessageButton.text() == 'View'
  }

  def "Overriding to an Open conditions category returns the record to the recategoriser"() {
    given: 'supervisor is viewing the review page for B2345YZ'
    db.createDataWithStatusAndCatType(12, 'AWAITING_APPROVAL', JsonOutput.toJson([
      recat: TestFixture.defaultRecat]), 'RECAT')
    db.createNomisSeqNo(12,5)

    navigateToReview(false, false, false)

    when: 'Supervisor chooses to override to category D'
    appropriateNo.click()
    overriddenCategoryD.click()

    then: 'A warning is displayed'
    warnings[1].text() contains "Making this category change means that the categoriser will have to provide more information."

    when: 'The continue button is clicked'
    overriddenCategoryText << "should be a D"
    submitButton.click()

    then: 'the record is returned to categoriser with open conditions requested and suggestedCategory forced to D'
    at SupervisorHomePage

    def data = db.getData(12)
    data.status == ["SUPERVISOR_BACK"]
    def response = new JsonSlurper().parseText(data.form_response[0].toString())
    // decision is removed when open conditions introduced by supervisor
    response.recat == [
      securityInput : [securityInputNeeded: "No"],
      nextReviewDate: [date: "14/12/2019"],
      prisonerBackground: [offenceDetails:"offence Details text"],
      riskAssessment: [
        lowerCategory    : "lower security category text",
        otherRelevant    : "Yes",
        higherCategory   : "higher security category text",
        otherRelevantText: "other relevant information"
      ]
    ]
    response.supervisor == [review     : [proposedCategory: 'C', supervisorOverriddenCategory: 'D', supervisorCategoryAppropriate: 'No', supervisorOverriddenCategoryText: 'should be a D'],
                            confirmBack: [messageText: 'should be a D']]
    response.openConditionsRequested
  }
}
