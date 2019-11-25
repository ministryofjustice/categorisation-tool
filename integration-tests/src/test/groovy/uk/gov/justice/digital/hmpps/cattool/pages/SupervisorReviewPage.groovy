package uk.gov.justice.digital.hmpps.cattool.pages

import geb.Page

class SupervisorReviewPage extends Page {

  static url = '/form/supervisor/review'

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

    offendingHistorySummary { $('.offendingHistorySummary .govuk-summary-list__value') }
    furtherChargesSummary { $('.furtherChargesSummary .govuk-summary-list__value') }
    violenceRatingSummary { $('.violenceRatingSummary .govuk-summary-list__value') }
    escapeRatingSummary { $('.escapeRatingSummary .govuk-summary-list__value') }
    extremismRatingSummary { $('.extremismRatingSummary .govuk-summary-list__value') }
    securityInputSummary { $('.securityInputSummary .govuk-summary-list__value') }

    riskLevel { $('.riskLevelSummary .govuk-summary-list__value') }
    furtherCharges { $('.furtherChargesOpenSummary .govuk-summary-list__value') }
    riskOfHarm { $('.riskOfHarmSummary .govuk-summary-list__value') }
    foreignNational { $('.foreignNationalSummary .govuk-summary-list__value') }
    earliestReleaseDate { $('.earliestReleaseDateSummary .govuk-summary-list__value') }

    warnings { $('div.govuk-warning-text') }

    submitButton { $('button', type: 'submit') }
    backLink { $('a.govuk-back-link') }
  }
}
