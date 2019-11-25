const request = require('supertest')
const moment = require('moment')
const appSetup = require('./utils/appSetup')
const { authenticationMiddleware } = require('./utils/mockAuthentication')
const db = require('../../server/data/dataAccess/db')

const categoriser = require('../../server/config/categoriser')
const recat = require('../../server/config/recat')

const mockTransactionalClient = { query: jest.fn(), release: jest.fn() }

let roles
// This needs mocking early, before 'requiring' jwt-decode (via home.js)
jest.doMock('jwt-decode', () => jest.fn(() => ({ authorities: roles })))

const createRouter = require('../../server/routes/recat')

const formConfig = {
  recat,
  categoriser,
}

const formService = {
  getCategorisationRecord: jest.fn(),
  referToSecurityIfRiskAssessed: jest.fn(),
  referToSecurityIfRequested: jest.fn(),
  update: jest.fn(),
  isYoungOffender: jest.fn(),
  getValidationErrors: jest.fn().mockReturnValue([]),
  computeSuggestedCat: jest.fn().mockReturnValue('B'),
  updateFormData: jest.fn(),
  setAwaitingApproval: jest.fn(),
  requiresOpenConditions: jest.fn(),
  cancelOpenConditions: jest.fn(),
  mergeRiskProfileData: jest.fn(),
  backToCategoriser: jest.fn(),
  isValid: jest.fn(),
  deleteFormData: jest.fn(),
  recordNomisSeqNumber: jest.fn(),
  categoriserDecision: jest.fn(),
}

const riskProfilerService = {
  getSecurityProfile: jest.fn(),
  getViolenceProfile: jest.fn(),
  getEscapeProfile: jest.fn(),
  getExtremismProfile: jest.fn(),
}

const offendersService = {
  getUncategorisedOffenders: jest.fn(),
  getOffenderDetails: jest.fn(),
  getImage: jest.fn(),
  getOffenceHistory: jest.fn(),
  createSupervisorApproval: jest.fn(),
  createInitialCategorisation: jest.fn(),
  getPrisonerBackground: jest.fn(),
  getRiskChangeForOffender: jest.fn(),
}

const userService = {
  getUser: jest.fn(),
}

const formRoute = createRouter({
  formService,
  offendersService,
  userService,
  riskProfilerService,
  authenticationMiddleware,
})

let app

beforeEach(() => {
  app = appSetup(formRoute)
  roles = ['ROLE_CREATE_RECATEGORISATION']
  formService.getCategorisationRecord.mockResolvedValue({
    status: 'STARTED',
    offenderNo: 'GH123',
    bookingId: 12345,
    formObject: {},
  })
  formService.referToSecurityIfRiskAssessed.mockResolvedValue({})
  formService.referToSecurityIfRequested.mockResolvedValue({})
  formService.isValid.mockResolvedValue(true)
  formService.isYoungOffender.mockReturnValue(false)
  formService.deleteFormData.mockReturnValue({})
  formService.recordNomisSeqNumber.mockReturnValue({})
  formService.categoriserDecision.mockReturnValue({})
  offendersService.createInitialCategorisation.mockReturnValue({ bookingId: 12345, seq: 4 })
  offendersService.getOffenderDetails.mockResolvedValue({
    displayName: 'Claire Dent',
    bookingId: 12345,
    offenderNo: 'GH123',
  })
  offendersService.getOffenceHistory.mockResolvedValue({})
  offendersService.getPrisonerBackground.mockResolvedValue({})
  userService.getUser.mockResolvedValue({})
  riskProfilerService.getSecurityProfile.mockResolvedValue({})
  riskProfilerService.getViolenceProfile.mockResolvedValue({})
  riskProfilerService.getExtremismProfile.mockResolvedValue({})
  riskProfilerService.getEscapeProfile.mockResolvedValue({})
  db.pool.connect = jest.fn()
  db.pool.connect.mockResolvedValue(mockTransactionalClient)
  moment.now = jest.fn()
  // NOTE: mock current date!
  moment.now.mockReturnValue(moment('2019-06-05', 'YYYY-MM-DD'))
})

afterEach(() => {
  jest.resetAllMocks()
})

describe('recat', () => {
  test.each`
    path                          | expectedContent
    ${'higherSecurityReview'}     | ${'Higher Security Review'}
    ${'miniHigherSecurityReview'} | ${'Higher Security Review'}
    ${'riskAssessment'}           | ${'Risk assessment'}
  `('Get should render $expectedContent for $path', ({ path, expectedContent }) =>
    request(app)
      .get(`/${path}/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain(expectedContent)
      })
  )

  describe('GET /form/recat/securityInput', () => {
    test.each`
      path                     | expectedContent
      ${'securityInput/12345'} | ${'Security information'}
    `('should render $expectedContent for $path', ({ path, expectedContent }) =>
      request(app)
        .get(`/${path}`)
        .expect(200)
        .expect('Content-Type', /html/)
        .expect(res => {
          expect(res.text).toContain(expectedContent)
          expect(riskProfilerService.getSecurityProfile).toBeCalledTimes(0)
        })
    )
    test('categoriser cannot edit security page if page is locked - redirect to tasklist)', () => {
      formService.getCategorisationRecord.mockResolvedValue({
        status: 'SECURITY_MANUAL',
        bookingId: 12345,
        displayName: 'Tim Handle',
        displayStatus: 'Any other status',
        formObject: {},
      })
      return request(app)
        .get('/securityInput/12345')
        .expect(302)
        .expect('Location', `/tasklistRecat/12345`)
    })
  })

  test.each`
    formName                      | userInput                  | nextPath
    ${'securityInput'}            | ${{ dummy: 'No' }}         | ${'/tasklistRecat/'}
    ${'higherSecurityReview'}     | ${{ transfer: 'No' }}      | ${'/tasklistRecat/'}
    ${'miniHigherSecurityReview'} | ${{ transfer: 'No' }}      | ${'/tasklistRecat/'}
    ${'riskAssessment'}           | ${{ otherRelevant: 'No' }} | ${'/tasklistRecat/'}
  `('Post $formName should go to $nextPath', ({ formName, userInput, nextPath }) => {
    formService.getCategorisationRecord.mockResolvedValue({
      bookingId: 12345,
      formObject: {},
    })
    return request(app)
      .post(`/${formName}/12345`)
      .send(userInput)
      .expect(302)
      .expect('Location', `${nextPath}12345`)
      .expect(() => {
        expect(formService.update).toBeCalledWith({
          bookingId: 12345,
          userId: 'CA_USER_TEST',
          config: formConfig.recat[formName],
          userInput,
          formSection: 'recat',
          formName,
          transactionalClient: mockTransactionalClient,
        })
      })
  })

  test('Get category decision for offender 21 or over)', () => {
    formService.isYoungOffender.mockReturnValue(false)
    return request(app)
      .get(`/decision/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain('Category decision')
        expect(res.text).not.toContain('catIOption')
      })
  })

  test('Get category decision for offender under 21)', () => {
    formService.isYoungOffender.mockReturnValue(true)
    return request(app)
      .get(`/decision/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain('Category decision')
        expect(res.text).toContain('catIOption')
        expect(res.text).not.toContain('Prisoner has an indeterminate sentence')
      })
  })

  test('Category decision should present open conditions options for indeterminate sentences)', () => {
    offendersService.getOffenderDetails.mockResolvedValue({
      sentence: { indeterminate: true },
    })
    formService.isYoungOffender.mockReturnValue(false)
    return request(app)
      .get(`/decision/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain('catBOption')
        expect(res.text).toContain('catCOption')
        expect(res.text).not.toContain('catIOption')
        expect(res.text).not.toContain('catJOption')
        expect(res.text).toContain('catDOption')
      })
  })

  test('GET /form/recat/prisonerBackground', () =>
    request(app)
      .get(`/prisonerBackground/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain('extremismInfo')
        expect(res.text).toMatch(/Home.+Categorisation home.+Prisoner background/s)
        expect(res.text).toContain('escapeInfo')
        expect(res.text).toContain('/offenders/GH123/case-notes')
        expect(res.text).toContain('/offenders/GH123/alerts')
        expect(res.text).toContain('/offenders/GH123/adjudications')
        expect(res.text).toContain('/categoryHistory/12345')
      }))

  test('GET /form/recat/review violence profile - displayAssault', () => {
    riskProfilerService.getViolenceProfile.mockResolvedValue({
      nomsId: '1234AN',
      riskType: 'VIOLENCE',
      veryHighRiskViolentOffender: false,
      notifySafetyCustodyLead: false,
      displayAssaults: true,
      numberOfAssaults: 5,
      numberOfSeriousAssaults: 2,
    })
    return request(app)
      .get(`/review/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain('This person has been reported as the perpetrator in 5 assaults in custody before,')
        expect(res.text).toContain('including 2 serious assaults in the last 12 months')
        expect(res.text).toMatch(/Home.+Categorisation home.+Category review task list.+Check your answers/s)
      })
  })

  test('GET /form/recat/review violence profile - all fine', () => {
    riskProfilerService.getViolenceProfile.mockReturnValue({
      nomsId: '1234AN',
      riskType: 'VIOLENCE',
      veryHighRiskViolentOffender: false,
      notifySafetyCustodyLead: false,
      displayAssaults: false,
      numberOfAssaults: 5,
      numberOfSeriousAssaults: 2,
    })
    return request(app)
      .get(`/review/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).not.toContain('This person has been reported as the perpetrator')
        expect(res.text).toContain(
          'This person has not been reported as the perpetrator in any assaults in custody before'
        )
      })
  })

  test('GET /form/recat/review extremism profile - increasedRiskOfExtremism', () => {
    riskProfilerService.getExtremismProfile.mockReturnValue({
      nomsId: '123AD',
      riskType: 'EXTREMISM',
      increasedRiskOfExtremism: true,
      notifyRegionalCTLead: true,
    })
    return request(app)
      .get(`/review/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain('This person is at risk of engaging in, or vulnerable to, extremism.')
        expect(res.text).not.toContain(
          'This person is not currently considered to be at risk of engaging in, or vulnerable to, extremism.'
        )
      })
  })

  test('GET /form/recat/review extremism profile - all fine', () => {
    riskProfilerService.getExtremismProfile.mockReturnValue({
      nomsId: '123AD',
      riskType: 'EXTREMISM',
      increasedRiskOfExtremism: false,
      notifyRegionalCTLead: false,
    })
    return request(app)
      .get(`/review/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).not.toContain('This person is at risk of engaging in, or vulnerable to, extremism.')
        expect(res.text).toMatch(/Home.+Categorisation home.+Check your answers/s)
        expect(res.text).toContain(
          'This person is not currently considered to be at risk of engaging in, or vulnerable to, extremism.'
        )
      })
  })

  test('GET /form/recat/review higherSecurityReview link is displayed', () => {
    formService.getCategorisationRecord.mockResolvedValue({
      status: 'STARTED',
      bookingId: 12345,
      formObject: { recat: { higherSecurityReview: { behaviour: 'good' } } },
    })
    offendersService.getOffenderDetails.mockResolvedValue({ bookingId: '12345' })
    return request(app)
      .get(`/review/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain('/higherSecurityReview/12345')
        expect(res.text).not.toContain('/miniHigherSecurityReview/12345')
      })
  })

  test('GET /form/recat/review miniHigherSecurityReview link is displayed', () => {
    formService.getCategorisationRecord.mockResolvedValue({
      status: 'STARTED',
      bookingId: 12345,
      formObject: { recat: { miniHigherSecurityReview: { conditions: 'text' } } },
    })
    offendersService.getOffenderDetails.mockResolvedValue({ bookingId: '12345' })
    return request(app)
      .get(`/review/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain('/miniHigherSecurityReview/12345')
        expect(res.text).toMatch(/Home.+Categorisation home.+Check your answers/s)
        expect(res.text).not.toContain('/higherSecurityReview/12345')
      })
  })
})

describe('GET /riskProfileChangeDetail/:bookingId', () => {
  test('happy path sections are displayed', () => {
    offendersService.getRiskChangeForOffender.mockResolvedValue({
      socNewlyReferred: true,
      escapeList: true,
      notifyRegionalCTLeadExtremism: true,
      violenceChange: true,
      bookingId: 12345,
      newProfile: { violence: { numberOfSeriousAssaults: 1, numberOfAssaults: 0, provisionalCategorisation: 'C' } },
      oldProfile: { violence: { numberOfSeriousAssaults: 0, numberOfAssaults: 0, provisionalCategorisation: 'B' } },
    })
    return request(app)
      .get(`/riskProfileChangeDetail/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toMatch(/Home.+Categorisation home.+Check change in risk status/s)
        expect(res.text).toContain(
          'This person needs to be considered by security. Please start a review and refer this person to security.'
        )
        expect(res.text).toContain('Notify your safer custody lead about this prisoner')
        expect(res.text).toContain('Notify your regional CT leader that you have this person in custody')
        expect(res.text).toContain('This person is now considered a risk of escape')
        expect(res.text).toContain(
          'They have been reported as the perpetrator of 1 assaults in total during this sentence'
        )
        expect(res.text).toContain('Of these, 1 was a serious assault committed in the past 12 months')
        expect(res.text).toContain('There are no reported assaults during this sentence')
        expect(res.text).toContain('Do you want to start an early category review?')
      })
  })

  test('Assaults wording', () => {
    offendersService.getRiskChangeForOffender.mockResolvedValue({
      violenceChange: true,
      bookingId: 12345,
      newProfile: { violence: { numberOfSeriousAssaults: 3, numberOfAssaults: 1 } },
      oldProfile: { violence: { numberOfSeriousAssaults: 0, numberOfAssaults: 1 } },
    })
    return request(app)
      .get(`/riskProfileChangeDetail/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).not.toContain('Extremism')
        expect(res.text).not.toContain('Risk of escape')
        expect(res.text).not.toContain('Security referral')
        expect(res.text).toContain(
          'They have been reported as the perpetrator of 4 assaults in total during this sentence'
        )
        expect(res.text).toContain(
          'They have been reported as the perpetrator of 1 assaults in total during this sentence'
        )
        expect(res.text).toContain('Of these, 3 were serious assaults committed in the past 12 months')
        expect(res.text).toContain('Of these, 0 were serious assaults committed in the past 12 months')
      })
  })

  test('Assaults wording', () => {
    offendersService.getRiskChangeForOffender.mockResolvedValue({
      violenceChange: true,
      bookingId: 12345,
      newProfile: { violence: { numberOfSeriousAssaults: 3, numberOfAssaults: 1 } },
      oldProfile: { violence: { numberOfSeriousAssaults: 0, numberOfAssaults: 1 } },
    })
    return request(app)
      .get(`/riskProfileChangeDetail/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).not.toContain('Extremism')
        expect(res.text).not.toContain('Risk of escape')
        expect(res.text).not.toContain('Security referral')
        expect(res.text).toContain(
          'They have been reported as the perpetrator of 4 assaults in total during this sentence'
        )
        expect(res.text).toContain(
          'They have been reported as the perpetrator of 1 assaults in total during this sentence'
        )
        expect(res.text).toContain('Of these, 3 were serious assaults committed in the past 12 months')
        expect(res.text).toContain('Of these, 0 were serious assaults committed in the past 12 months')
      })
  })

  test('Assaults wording - 1 serious assault', () => {
    offendersService.getRiskChangeForOffender.mockResolvedValue({
      violenceChange: true,
      bookingId: 12345,
      newProfile: { violence: { numberOfSeriousAssaults: 1, numberOfAssaults: 2 } },
      oldProfile: { violence: { numberOfSeriousAssaults: 1, numberOfAssaults: 1 } },
    })
    return request(app)
      .get(`/riskProfileChangeDetail/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).not.toContain('Extremism')
        expect(res.text).not.toContain('Risk of escape')
        expect(res.text).not.toContain('Security referral')
        expect(res.text).toContain(
          'They have been reported as the perpetrator of 2 assaults in total during this sentence'
        )
        expect(res.text).toContain(
          'They have been reported as the perpetrator of 3 assaults in total during this sentence'
        )
        expect(res.text).toContain('Of these, 1 was a serious assault committed in the past 12 months')
      })
  })

  test('No violence change', () => {
    offendersService.getRiskChangeForOffender.mockResolvedValue({
      violenceChange: false,
      notifyRegionalCTLeadExtremism: true,
      bookingId: 12345,
    })
    return request(app)
      .get(`/riskProfileChangeDetail/12345`)
      .expect(200)
      .expect('Content-Type', /html/)
      .expect(res => {
        expect(res.text).toContain('Extremism')
        expect(res.text).not.toContain('Risk of escape')
        expect(res.text).not.toContain('Safety and good order')
        expect(res.text).not.toContain('Security referral')
      })
  })
})

describe('POST /form/recat/decision', () => {
  test.each`
    formName      | userInput                                  | nextPath
    ${'decision'} | ${{ currentCategory: 'I', category: 'B' }} | ${'/form/recat/miniHigherSecurityReview/12345'}
    ${'decision'} | ${{ currentCategory: 'J', category: 'I' }} | ${'/form/recat/higherSecurityReview/12345'}
    ${'decision'} | ${{ currentCategory: 'J', category: 'C' }} | ${'/form/recat/higherSecurityReview/12345'}
    ${'decision'} | ${{ currentCategory: 'J', category: 'B' }} | ${'/form/recat/higherSecurityReview/12345'}
    ${'decision'} | ${{ currentCategory: 'D', category: 'C' }} | ${'/form/recat/higherSecurityReview/12345'}
    ${'decision'} | ${{ currentCategory: 'D', category: 'B' }} | ${'/form/recat/higherSecurityReview/12345'}
    ${'decision'} | ${{ currentCategory: 'D', category: 'C' }} | ${'/form/recat/higherSecurityReview/12345'}
    ${'decision'} | ${{ currentCategory: 'I', category: 'C' }} | ${'/tasklistRecat/12345'}
    ${'decision'} | ${{ currentCategory: 'D', category: 'D' }} | ${'/openConditionsAdded/12345?catType=RECAT'}
    ${'decision'} | ${{ currentCategory: 'I', category: 'J' }} | ${'/openConditionsAdded/12345?catType=RECAT'}
    ${'decision'} | ${{ currentCategory: 'I', category: 'I' }} | ${'/tasklistRecat/12345'}
    ${'decision'} | ${{ currentCategory: 'B', category: 'D' }} | ${'/openConditionsAdded/12345?catType=RECAT'}
    ${'decision'} | ${{ currentCategory: 'B', category: 'C' }} | ${'/tasklistRecat/12345'}
    ${'decision'} | ${{ currentCategory: 'B', category: 'B' }} | ${'/tasklistRecat/12345'}
    ${'decision'} | ${{ currentCategory: 'C', category: 'C' }} | ${'/tasklistRecat/12345'}
  `('Post for input $userInput should go to $nextPath', ({ formName, userInput, nextPath }) => {
    formService.getCategorisationRecord.mockResolvedValue({
      status: 'STARTED',
      bookingId: 12345,
      displayName: 'Tim Handle',
      displayStatus: 'Any other status',
    })
    return request(app)
      .post(`/${formName}/12345`)
      .send(userInput)
      .expect(302)
      .expect('Location', `${nextPath}`)
      .expect(() => {
        expect(formService.update).toBeCalledWith({
          bookingId: 12345,
          userId: 'CA_USER_TEST',
          config: formConfig.recat[formName],
          userInput,
          formSection: 'recat',
          formName,
          transactionalClient: mockTransactionalClient,
        })
      })
  })
})

describe('POST /form/recat/review', () => {
  test.each`
    formName    | userInput | nextPath
    ${'review'} | ${{}}     | ${'/tasklistRecat/recategoriserSubmitted/'}
  `('Post for $sectionName/$formName, input $userInput should go to $nextPath', ({ formName, userInput, nextPath }) => {
    formService.getCategorisationRecord.mockResolvedValue({
      status: 'STARTED',
      bookingId: 12345,
      formObject: { recat: { decision: { category: 'B' }, nextReviewDate: { date: '16/02/2020' } } },
    })
    return request(app)
      .post(`/${formName}/12345`)
      .send(userInput)
      .expect(302)
      .expect('Location', `${nextPath}12345`)
      .expect(() => {
        expect(formService.update).toBeCalledTimes(0)
        expect(offendersService.createInitialCategorisation).toBeCalledWith({
          context: { user: { token: 'ABCDEF' } },
          bookingId: 12345,
          overriddenCategoryText: 'Cat-tool Recat',
          suggestedCategory: 'B',
          nextReviewDate: '16/02/2020',
        })
        expect(formService.categoriserDecision).toBeCalledWith('12345', 'CA_USER_TEST', mockTransactionalClient)
      })
  })
})

describe('POST /form/recat/fasttrackProgress', () => {
  test('Risk Assessment, next review date and decision are defaulted', () => {
    formService.getCategorisationRecord.mockResolvedValue({
      status: 'STARTED',
      bookingId: 12345,
      formObject: { something: 'alreadyOnTheForm' },
    })
    return request(app)
      .post(`/fasttrackProgress/12345`)
      .send({ progressText: 'They have done very well' })
      .expect(302)
      .expect('Location', `/form/recat/fasttrackConfirmation/12345`)
      .expect(() => {
        expect(formService.update).toBeCalledTimes(1)
        expect(formService.updateFormData).toBeCalledWith(
          '12345',
          {
            recat: {
              decision: { category: 'C' },
              nextReviewDate: { date: '05/06/2020' },
              riskAssessment: {
                higherCategory:
                  'They pose no additional risks. There’s no reason to consider them for higher security conditions.',
                lowerCategory:
                  "They could not be considered for open conditions early. Their circumstances weren't exceptional enough.",
                otherRelevant: 'No',
              },
              securityInput: { securityInputNeeded: 'No' },
            },
            something: 'alreadyOnTheForm',
          },
          mockTransactionalClient
        )
      })
  })

  test('Only missing data is defaulted - all fields complete', () => {
    formService.getCategorisationRecord.mockResolvedValue({
      status: 'STARTED',
      bookingId: 12345,
      formObject: {
        something: 'alreadyOnTheForm',
        recat: {
          nextReviewDate: { date: '10/06/2020' },
          riskAssessment: {
            higherCategory: 'higherCategory Text was already here - should not be cleared',
            lowerCategory: 'lowerCategory Text was already here - should not be cleared',
          },
        },
      },
    })
    return request(app)
      .post(`/fasttrackProgress/12345`)
      .send({ progressText: 'They have done very well' })
      .expect(302)
      .expect('Location', `/form/recat/fasttrackConfirmation/12345`)
      .expect(() => {
        expect(formService.update).toBeCalledTimes(1)
        expect(formService.updateFormData).toBeCalledWith(
          '12345',
          {
            recat: {
              decision: { category: 'C' },
              nextReviewDate: { date: '10/06/2020' },
              riskAssessment: {
                higherCategory: 'higherCategory Text was already here - should not be cleared',
                lowerCategory: 'lowerCategory Text was already here - should not be cleared',
                otherRelevant: 'No',
              },
              securityInput: { securityInputNeeded: 'No' }, // fasttrack not available if any security input
            },
            something: 'alreadyOnTheForm',
          },
          mockTransactionalClient
        )
      })
  })
})
