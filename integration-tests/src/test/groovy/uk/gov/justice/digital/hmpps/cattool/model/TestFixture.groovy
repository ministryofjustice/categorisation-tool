package uk.gov.justice.digital.hmpps.cattool.model

import geb.Browser
import uk.gov.justice.digital.hmpps.cattool.mockapis.Elite2Api
import uk.gov.justice.digital.hmpps.cattool.mockapis.OauthApi
import uk.gov.justice.digital.hmpps.cattool.mockapis.RiskProfilerApi
import uk.gov.justice.digital.hmpps.cattool.pages.CategoriserHomePage
import uk.gov.justice.digital.hmpps.cattool.pages.recat.RecategoriserHomePage

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoField

import static uk.gov.justice.digital.hmpps.cattool.model.UserAccount.CATEGORISER_USER
import static uk.gov.justice.digital.hmpps.cattool.model.UserAccount.RECATEGORISER_USER

class TestFixture {

  Browser browser
  Elite2Api elite2Api
  RiskProfilerApi riskProfilerApi
  OauthApi oauthApi

  UserAccount currentUser

  public static final defaultRatingsB = [
    offendingHistory: [previousConvictions: "Yes", previousConvictionsText: "some convictions"],
    securityInput   : [securityInputNeeded: "No"],
    furtherCharges  : [furtherCharges: "Yes", furtherChargesText: "some charges"],
    violenceRating  : [highRiskOfViolence: "No", seriousThreat: "Yes"],
    escapeRating    : [escapeOtherEvidence: "Yes", escapeOtherEvidenceText: "evidence details", escapeCatB: "Yes", escapeCatBText: "cat b details"],
    extremismRating : [previousTerrorismOffences: "Yes"],
    nextReviewDate  : [date: "14/12/2019"]
  ]

  public static final defaultRatingsC = [
    offendingHistory: [previousConvictions: "Yes", previousConvictionsText: "some convictions"],
    securityInput   : [securityInputNeeded: "No"],
    furtherCharges  : [furtherCharges: "No"],
    violenceRating  : [highRiskOfViolence: "No", seriousThreat: "No"],
    escapeRating    : [escapeOtherEvidence: "No"],
    extremismRating : [previousTerrorismOffences: "No"],
    nextReviewDate  : [date: "14/12/2019"]
  ]

  public static final defaultRecat = [
    decision          : [category: "C"],
    securityInput     : [securityInputNeeded: "No"],
    nextReviewDate    : [date: "14/12/2019"],
    prisonerBackground: [offenceDetails: "offence Details text"],
    riskAssessment    : [
      lowerCategory    : "lower security category text",
      otherRelevant    : "Yes",
      higherCategory   : "higher security category text",
      otherRelevantText: "other relevant information"
    ]
  ]

  public static final defaultOpenConditions = [
    earliestReleaseDate: ['threeOrMoreYears': 'No'],
    foreignNational    : ['isForeignNational': 'No'],
    riskOfHarm         : ['seriousHarm': 'No'],
    furtherCharges     : ['increasedRisk': 'No', 'furtherChargesText': 'some charges,furtherChargesText details'],
    riskLevels         : ['likelyToAbscond': 'No']
  ]

  public static final FULL_HEADER = ['Hillmob, Ant', 'B2345YZ', '17/02/1970', 'C',
  'C-04-02', 'Coventry',
  'Latvian',
  'A Felony', 'Another Felony',
  '10/06/2020',
  '11/06/2020',
  '02/02/2020',
  '13/06/2020',
  '14/06/2020',
  '15/06/2020',
  '16/06/2020',
  '17/06/2020',
  '6 years, 3 months (Std sentence)']
  public static final MINI_HEADER = ['Hillmob, Ant', 'B2345YZ', '17/02/1970', 'C']

  TestFixture(Browser browser, Elite2Api elite2Api, OauthApi oauthApi, RiskProfilerApi riskProfilerApi1) {
    this.browser = browser
    this.elite2Api = elite2Api
    this.riskProfilerApi = riskProfilerApi1
    this.oauthApi = oauthApi
  }

  def loginAs(UserAccount user) {
    stubLogin(user)
    // Redirect to /oauth/authorise and wiremock serves a dummy login page
    browser.go '/'
    simulateLogin()
  }

  def stubLogin(UserAccount user) {
    oauthApi.resetRequests()
    currentUser = user
    elite2Api.stubHealth()
    oauthApi.stubValidOAuthTokenRequest currentUser
    elite2Api.stubGetMyDetails currentUser
    elite2Api.stubGetMyCaseloads currentUser.caseloads
  }

  def gotoTasklist(transferToSecurity = false, multipleSentences = false) {
    elite2Api.stubUncategorised()
    def date11 = LocalDate.now().plusDays(-4).toString()
    def date12 = LocalDate.now().plusDays(-1).toString()
    elite2Api.stubSentenceData(['B2345XY', 'B2345YZ'], [11, 12], [date11, date12])

    loginAs(CATEGORISER_USER)
    browser.at CategoriserHomePage
    elite2Api.stubGetOffenderDetails(12, 'B2345YZ', false,  false, 'C', multipleSentences)
    riskProfilerApi.stubGetSocProfile('B2345YZ', 'C', transferToSecurity)
    browser.selectSecondPrisoner()
  }

  def gotoTasklistRecat(transferToSecurity = false) {
    elite2Api.stubRecategorise()

    loginAs(RECATEGORISER_USER)
    browser.at RecategoriserHomePage
    elite2Api.stubGetOffenderDetails(12)
    riskProfilerApi.stubGetSocProfile('B2345YZ', 'C', transferToSecurity)
    browser.selectFirstPrisoner()
  }

  def gotoTasklistRecatForCatI(transferToSecurity = false) {
    elite2Api.stubRecategoriseWithCatI()

    loginAs(RECATEGORISER_USER)
    browser.at RecategoriserHomePage
    elite2Api.stubGetOffenderDetails(21, 'C0001AA', true, false, 'I')
    riskProfilerApi.stubGetSocProfile('C0001AA', 'I', transferToSecurity)
    browser.selectFirstPrisoner()
  }

  def simulateLogin() {
    browser.waitFor { browser.$('h1').text() == 'Sign in' }
    def requests = oauthApi.getAllServeEvents()
    // print JsonOutput.toJson(requests)
    // Capture 'state' param for passport (-2 = last but one server request)
    if (requests.empty) throw new Exception("Cannot login, possible env config problem")
    def stateParam = requests[-1].request.queryParams['state']
    def state = stateParam ? stateParam.values[0] : requests[-2].request.queryParams['state'].values[0]
    // Simulate auth server calling the callback, which then gets a token (from wiremock) and goes to homepage
    browser.go "/login/callback?code=codexxxx&state=$state"
  }

  def logout() {
    browser.$('a', href: '/logout').click()
    browser.waitFor { browser.$('h1').text() == 'Sign in' }
  }

  def sameDate(LocalDate expected, actual) {
    return actual[0].toLocalDate().equals(expected)
  }

  def get10BusinessDays(LocalDate from = LocalDate.now()) {
    def numberOfDays = 14
    switch (from.get(ChronoField.DAY_OF_WEEK)) {
      case DayOfWeek.SATURDAY.value:
        numberOfDays += 2
        break
      case DayOfWeek.SUNDAY.value:
        numberOfDays += 1
        break
    }
    return numberOfDays
  }
}
