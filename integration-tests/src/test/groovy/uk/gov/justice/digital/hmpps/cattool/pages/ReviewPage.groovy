package uk.gov.justice.digital.hmpps.cattool.pages

import geb.Page

class ReviewPage extends Page {

  static url = '/form/categoriser/review'

  static at = {
    headingText == 'Check your answers before you continue'
  }

  static content = {
    headingText { $('h1.govuk-heading-l').text() }

    form {$('form')}

    submitButton { $('button', type:'submit') }
    backLink { $( 'a.govuk-back-link') }
  }
}