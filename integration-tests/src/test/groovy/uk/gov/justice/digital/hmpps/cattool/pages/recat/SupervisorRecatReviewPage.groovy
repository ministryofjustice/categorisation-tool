package uk.gov.justice.digital.hmpps.cattool.pages.recat

import geb.Page

class SupervisorRecatReviewPage extends Page {

  static url = '/form/supervisor/recatReview'

  static at = {
    headingText == 'Approve category'
  }

  static content = {
    headingText { $('h1.mainHeading').text() }
    headerBlock { $('div.govuk-grid-column-one-third') }
    headerValue { headerBlock.$('div.govuk-\\!-font-weight-bold') }

    warning { $('div.govuk-warning-text', 0) }
    form { $('form') }

    appropriateYes { $('#supervisorCategoryAppropriate') }
    appropriateNo { $('#supervisorCategoryAppropriate-2') }
    overriddenCategoryB(required: false) { $('#overriddenCategoryB') }
    overriddenCategoryC(required: false) { $('#overriddenCategoryC') }
    overriddenCategoryD(required: false) { $('#overriddenCategoryD') }
    changeLinks(required: false) { $('a.govuk-link', text: startsWith('Change')) }
    newCatMessage(required: false) { $('#newCatMessage') }
    overriddenCategoryText(required: false) { $('#supervisorOverriddenCategoryText') }
    otherInformationText { $('#otherInformationText') }

    errorSummaries(required: false) { $('ul.govuk-error-summary__list li') }
    errors(required: false) { $('span.govuk-error-message') }
    openConditionsHeader(required: false) { $('.openConditionsHeader') }
    backToCategoriserButton { $('.rightAlignedButton') }

    prisonerBackgroundSummary { $('.prisonerBackgroundSummary .govuk-summary-list__value') }
    securityInputSummary { $('.securityInputSummary .govuk-summary-list__value') }
    riskAssessmentSummary { $('.riskAssessmentSummary .govuk-summary-list__value') }
    assessmentSummary { $('.assessmentSummary .govuk-summary-list__value') }
    higherSecurityReviewSummary { $('.higherSecurityReview .govuk-summary-list__value') }
    nextReviewDateSummary { $('.nextReviewDateSummary .govuk-summary-list__value') }

    riskLevel(required: false)  { $('.riskLevelSummary .govuk-summary-list__value') }
    furtherCharges(required: false)  { $('.furtherChargesOpenSummary .govuk-summary-list__value') }
    riskOfHarm(required: false)  { $('.riskOfHarmSummary .govuk-summary-list__value') }
    foreignNational(required: false)  { $('.foreignNationalSummary .govuk-summary-list__value') }
    earliestReleaseDate(required: false) { $('.earliestReleaseDateSummary .govuk-summary-list__value') }

    warnings { $('div.govuk-warning-text') }

    submitButton { $('button', type: 'submit') }
    backLink { $('a.govuk-back-link') }
  }
}
