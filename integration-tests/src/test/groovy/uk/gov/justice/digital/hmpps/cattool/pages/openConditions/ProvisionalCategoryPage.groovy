package uk.gov.justice.digital.hmpps.cattool.pages.openConditions

import geb.Page

class ProvisionalCategoryPage extends Page {

  static url = '/form/openConditions/provisionalCategory'

  static at = {
    headingText == 'Provisional category'
  }

  static content = {
    headingText { $('h1.govuk-heading-l').text() }
    headerBlock { $('div.govuk-body-s') }
    headerValue { headerBlock.$('p.govuk-\\!-font-weight-bold') }

    warning { $('div.govuk-warning-text') }
    form { $('form') }
    appropriateYes(required: false) { $('#categoryAppropriate-1') }
    appropriateNo(required: false) { $('#categoryAppropriate-2') }
    overriddenCategoryB(required: false) { $('#overriddenCategoryB') }
    overriddenCategoryC(required: false) { $('#overriddenCategoryC') }
    overriddenCategoryD(required: false) { $('#overriddenCategoryD') }
    newCatMessage(required: false) { $('h2.govuk-heading-m') }
    overriddenCategoryText(required: false) { $('#overriddenCategoryText') }
    otherInformationText(required: false) { $('#otherInformationText') }
    submitButton { $('button', type: 'submit') }
    backLink { $('a.govuk-back-link') }
    errorSummaries(required: false) { $('ul.govuk-error-summary__list li') }
    errors(required: false) { $('span.govuk-error-message') }
  }
}