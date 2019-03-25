package uk.gov.justice.digital.hmpps.cattool.pages

import geb.Page

class CategoriserOffendingHistoryPage extends Page {

  static String bookingId

  static url = '/form/ratings/offendingHistory/' + bookingId

  static at = {
    headingText == 'Offending history'
  }

  static content = {
    headingText { $('h1.govuk-heading-l').text() }
    headerBlock { $('div.govuk-body-s') }
    headerValue { headerBlock.$('p.govuk-\\!-font-weight-bold') }
    catAWarning(required: false) { $('div.govuk-warning-text') }
    form { $('form') }
    previousConvictionsText { $('#previousConvictionsText') }
    saveButton { $('button.govuk-button') }
    previousConvictionsYes { $('#previousConvictions-1') }
    previousConvictionsNo { $('#previousConvictions-2') }
    furtherChargesYes { $('#furtherCharges-1') }
    furtherChargesNo { $('#furtherCharges-2') }
    offendingHistoryCatBYes { $('#offendingHistoryCatB-1') }
    offendingHistoryCatBNo { $('#offendingHistoryCatB-2') }
    furtherChargesText { $('#furtherChargesText') }
    warrantCatB { $('#warrantCatB') }
    history { $('form > div.govuk-body-s > div') }
    errorSummaries(required: false) { $('ul.govuk-error-summary__list li') }
    errors(required: false) { $('span.govuk-error-message') }
  }
}
