const serviceCreator = require('../../server/services/sqsService')
const db = require('../../server/data/dataAccess/db')

const mockTransactionalClient = { query: jest.fn(), release: jest.fn() }
db.pool.connect = jest.fn()

const offendersService = {
  getOffenderDetailWithFullInfo: jest.fn(),
  checkAndMergeOffenderNo: jest.fn(),
}

const formService = {
  createRiskChange: jest.fn(),
}

let service

beforeEach(() => {
  service = serviceCreator(offendersService, formService)
  db.pool.connect.mockResolvedValue(mockTransactionalClient)
})

afterEach(() => {
  jest.resetAllMocks()
})

describe('rpQueueConsumer', () => {
  const profile = buildProfile()
  test('should ignore old and new profiles with no change', async () => {
    await service.rpQueueConsumer.handleMessage({
      Body: `{"offenderNo": "GN123", "oldProfile":${JSON.stringify(profile)}, "newProfile":${JSON.stringify(
        profile
      )} }`,
    })
    expect(offendersService.getOffenderDetailWithFullInfo).not.toBeCalled()
  })
  test('should ignore old and new profiles with a change that is not of interest', async () => {
    const newProfile = buildProfile({ socPC: 'B' })
    await service.rpQueueConsumer.handleMessage({
      Body: `{"offenderNo": "GN123", "oldProfile":${JSON.stringify(profile)}, "newProfile":${JSON.stringify(
        newProfile
      )} }`,
    })
    expect(offendersService.getOffenderDetailWithFullInfo).not.toBeCalled()
  })
  test('should ignore an offender with a category that cannot be increased', async () => {
    offendersService.getOffenderDetailWithFullInfo.mockResolvedValue({
      offenderNo: 'B2345XY',
      bookingId: 12,
      categoryCode: 'B',
    })
    const newProfile = buildProfile({ increasedRiskOfExtremism: true })
    await service.rpQueueConsumer.handleMessage({
      Body: `{"offenderNo": "GN123", "oldProfile":${JSON.stringify(profile)}, "newProfile":${JSON.stringify(
        newProfile
      )} }`,
    })
    expect(offendersService.getOffenderDetailWithFullInfo).toBeCalledWith({ user: {} }, 'GN123')
  })
})

function buildProfile({
  activeEscapeList = false,
  activeEscapeRisk = true,
  assaults = 0,
  seriousAssaults = 0,
  notifySCL = false,
  violentOffender = false,
  escapeListAlerts = [],
  escapeRiskAlerts = [],
  notifyRCTL = false,
  increasedRiskOfExtremism = false,
  socPC = 'C',
} = {}) {
  return {
    soc: {
      nomsId: 'G1709GX',
      riskType: 'SOC',
      transferToSecurity: false,
      provisionalCategorisation: socPC,
    },
    escape: {
      nomsId: 'G1709GX',
      riskType: 'ESCAPE',
      activeEscapeList,
      activeEscapeRisk,
      escapeListAlerts,
      escapeRiskAlerts,
      provisionalCategorisation: 'C',
    },
    violence: {
      nomsId: 'G1709GX',
      riskType: 'VIOLENCE',
      displayAssaults: false,
      numberOfAssaults: assaults,
      notifySafetyCustodyLead: notifySCL,
      numberOfSeriousAssaults: seriousAssaults,
      provisionalCategorisation: 'C',
      veryHighRiskViolentOffender: violentOffender,
    },
    extremism: {
      nomsId: 'G1709GX',
      riskType: 'EXTREMISM',
      notifyRegionalCTLead: notifyRCTL,
      increasedRiskOfExtremism,
      provisionalCategorisation: 'C',
    },
  }
}

describe('eventQueueConsumer', () => {
  test('merge event', async () => {
    await service.eventQueueConsumer.handleMessage({
      MessageId: '21b86d12-74be-4208-9f0b-7ffcb4213184',
      Body: '{ "Message" : "{\\"eventType\\": \\"BOOKING_NUMBER-CHANGED\\", \\"bookingId\\": 123}"}',
    })

    expect(offendersService.checkAndMergeOffenderNo).toBeCalled()
    expect(offendersService.checkAndMergeOffenderNo.mock.calls[0][0].user).toBeTruthy()
    expect(offendersService.checkAndMergeOffenderNo.mock.calls[0][1]).toEqual(123)
    expect(offendersService.checkAndMergeOffenderNo.mock.calls[0][2]).toBeTruthy()
  })
})
