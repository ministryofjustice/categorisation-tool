const express = require('express')
const moment = require('moment')
const flash = require('connect-flash')
const baseJoi = require('joi')
const dateExtend = require('joi-date-extensions')
const { properCaseName, calculateNextReviewDate, sanitisePrisonName } = require('../utils/utils')
const { handleCsrf } = require('../utils/routes')
const validation = require('../utils/fieldValidation')
const asyncMiddleware = require('../middleware/asyncMiddleware')

const joi = baseJoi.extend(dateExtend)

/**
 * 'Lite' categorisation is simply a P-Nomis style workflow for any cats other than B/C/D/I/J.
 * This renders it unnecessary to go to P-Nomis for category assessments at all, hopefully!
 */

const getPrisonList = (prisonListFromApi, current) => [
  { value: '' },
  ...prisonListFromApi.map(p => ({
    value: p.agencyId,
    text: sanitisePrisonName(p.description),
    selected: current === p.agencyId,
  })),
]

const getCommitteeList = current => [
  { value: 'OCA', text: 'OCA', selected: current === 'OCA' },
  { value: 'REVIEW', text: 'Review', selected: current === 'REVIEW' },
  { value: 'RECP', text: 'Reception', selected: current === 'RECP' },
  { value: 'SECUR', text: 'Security', selected: current === 'SECUR' },
  { value: 'GOV', text: 'Governor', selected: current === 'GOV' },
]

const getCatList = current => [
  { value: 'U', text: 'Unsentenced', selected: current === 'U' },
  { value: 'Z', text: 'Unclass', selected: current === 'Z' },
  { value: 'A', text: 'Cat A', selected: current === 'A' },
  { value: 'E', text: 'Cat A Ex', selected: current === 'E' },
  { value: 'H', text: 'Cat A Hi', selected: current === 'H' },
  { value: 'P', text: 'Prov A', selected: current === 'P' },
  { value: 'V', text: 'YOI Restricted', selected: current === 'V' },
  { value: 'B', text: 'Downgrade A to B', selected: current === 'B' },
  { value: 'D', text: 'Indeterminate Cat D', selected: current === 'D' },
  { value: 'R', text: 'Fem Closed', selected: current === 'R' },
  { value: 'T', text: 'Fem Open', selected: current === 'T' },
]

module.exports = function Index({ formService, offendersService, userService, authenticationMiddleware }) {
  const router = express.Router()

  router.use(authenticationMiddleware())
  router.use(flash())
  router.use(handleCsrf)

  router.get(
    '/approveList',
    asyncMiddleware(async (req, res, transactionalDbClient) => {
      const user = await userService.getUser(res.locals)
      res.locals.user = { ...user, ...res.locals.user }
      const offenders = await offendersService.getUnapprovedLite(res.locals, transactionalDbClient)
      res.render(`pages/supervisorLite`, { offenders })
    })
  )

  router.get(
    '/:bookingId',
    asyncMiddleware(async (req, res, transactionalDbClient) => {
      const { bookingId } = req.params
      const user = await userService.getUser(res.locals)
      res.locals.user = { ...user, ...res.locals.user }

      const [details, categorisationRecord, existingData, prisonListFromApi] = await Promise.all([
        offendersService.getOffenderDetails(res.locals, bookingId),
        formService.getCategorisationRecord(bookingId, transactionalDbClient),
        formService.getLiteCategorisation(bookingId, transactionalDbClient),
        offendersService.getAgencies(res.locals),
      ])
      const liteInProgress = existingData.bookingId && !existingData.approvedDate

      const prisonList = getPrisonList(prisonListFromApi)
      const committees = getCommitteeList()
      const cats = getCatList()

      res.render(`pages/liteCategories`, {
        bookingId,
        errors: [],
        liteInProgress,
        cats,
        committees,
        prisonList,
        nextReviewDate: calculateNextReviewDate('6'),
        data: { details, status: categorisationRecord.status },
      })
    })
  )

  router.get(
    '/confirmed/:bookingId',
    asyncMiddleware(async (req, res) => {
      const { bookingId } = req.params
      res.render(`pages/liteCategoriesConfirmed`, { bookingId })
    })
  )

  router.get(
    '/approve/:bookingId',
    asyncMiddleware(async (req, res, transactionalDbClient) => {
      const { bookingId } = req.params
      const user = await userService.getUser(res.locals)
      res.locals.user = { ...user, ...res.locals.user }

      const [details, rawAssessmentData, prisonListFromApi] = await Promise.all([
        offendersService.getOffenderDetails(res.locals, bookingId),
        formService.getLiteCategorisation(bookingId, transactionalDbClient),
        offendersService.getAgencies(res.locals),
      ])
      const cats = getCatList(rawAssessmentData.category)
      const category = cats.find(c => c.value === rawAssessmentData.category)
      const categoryDisplay = category ? category.text : rawAssessmentData.category

      const committees = getCommitteeList()
      const committee = committees.find(c => c.value === rawAssessmentData.assessmentCommittee)
      const assessmentCommitteeDisplay = committee ? committee.text : rawAssessmentData.assessmentCommittee

      const assessedBy = await userService.getUserByUserId(res.locals, rawAssessmentData.assessedBy)
      const assessedByDisplay = assessedBy
        ? `${properCaseName(assessedBy.firstName)} ${properCaseName(assessedBy.lastName)}`
        : rawAssessmentData.assessedBy

      const prisonList = getPrisonList(prisonListFromApi)
      const prison = prisonList.find(p => p.value === rawAssessmentData.placementPrisonId)
      const placementPrisonIdDisplay = prison ? prison.text : rawAssessmentData.placementPrisonId

      const liteInProgress = rawAssessmentData.bookingId && !rawAssessmentData.approvedDate
      const sameUser = res.locals.user.username === rawAssessmentData.assessedBy

      const assessmentData = {
        categoryDisplay,
        assessedByDisplay,
        assessmentCommitteeDisplay,
        placementPrisonIdDisplay,
        ...rawAssessmentData,
      }

      res.render(`pages/liteApprove`, {
        bookingId,
        liteInProgress,
        sameUser,
        errors: [],
        assessmentData,
        cats,
        committees,
        prisonList,
        approvedDate: moment().format('DD/MM/YYYY'),
        nextReviewDate: assessmentData.displayNextReviewDate,
        data: { details },
      })
    })
  )

  router.post(
    '/:bookingId',
    asyncMiddleware(async (req, res, transactionalDbClient) => {
      const { bookingId } = req.params
      const bookingIdInt = parseInt(bookingId, 10)
      const { category, authority, nextReviewDate, placement, comment } = req.body
      const user = await userService.getUser(res.locals)
      res.locals.user = { ...user, ...res.locals.user }
      const details = await offendersService.getOffenderDetails(res.locals, bookingIdInt)

      const tomorrow = moment()
        .add(1, 'd')
        .format('MM/DD/YYYY')

      const fieldOptions = {
        nextReviewDate: joi
          .date()
          .format('D/M/YYYY')
          .min(tomorrow)
          .required(),
      }

      const joiErrors = joi.validate(req.body, fieldOptions, { stripUnknown: true, abortEarly: false })
      const errors = validation.mapJoiErrors(joiErrors, [
        {
          nextReviewDate: {
            responseType: 'nextReviewDate',
            validationMessage: 'Enter a valid date that is after today',
          },
        },
      ])

      if (errors.length) {
        const prisonListFromApi = await offendersService.getAgencies(res.locals)
        const prisonList = getPrisonList(prisonListFromApi, placement)
        const cats = getCatList(category)
        const committees = getCommitteeList(authority)

        res.render(`pages/liteCategories`, {
          bookingId,
          errors,
          cats,
          committees,
          prisonList,
          category,
          authority,
          nextReviewDate,
          placement,
          comment,
          data: { details },
        })
      } else {
        await offendersService.createLiteCategorisation({
          context: res.locals,
          bookingId: bookingIdInt,
          category,
          authority,
          nextReviewDate,
          placement,
          comment,
          offenderNo: details.offenderNo,
          prisonId: details.agencyId,
          transactionalClient: transactionalDbClient,
        })
        res.redirect(`/liteCategories/confirmed/${bookingId}`)
      }
    })
  )

  router.post(
    '/approve/:bookingId',
    asyncMiddleware(async (req, res, transactionalDbClient) => {
      const { bookingId } = req.params
      const bookingIdInt = parseInt(bookingId, 10)
      const {
        approvedDate,
        supervisorCategory,
        approvedCategoryComment,
        approvedCommittee,
        nextReviewDate,
        approvedPlacement,
        approvedPlacementComment,
        approvedComment,
      } = req.body
      const user = await userService.getUser(res.locals)
      res.locals.user = { ...user, ...res.locals.user }

      const assessmentData = await formService.getLiteCategorisation(bookingId, transactionalDbClient)

      const tomorrow = moment()
        .add(1, 'd')
        .format('MM/DD/YYYY')
      const today = moment().format('MM/DD/YYYY')

      const fieldOptions = {
        nextReviewDate: joi
          .date()
          .format('D/M/YYYY')
          .min(tomorrow)
          .required(),
        approvedDate: joi
          .date()
          .format('D/M/YYYY')
          .max(today)
          .required(),
      }

      const joiErrors = joi.validate(req.body, fieldOptions, { stripUnknown: true, abortEarly: false })
      const errors = validation.mapJoiErrors(joiErrors, [
        {
          approvedDate: {
            responseType: 'approvedDate',
            validationMessage: 'Enter a valid date that is today or earlier',
          },
        },
        {
          nextReviewDate: {
            responseType: 'nextReviewDate',
            validationMessage: 'Enter a valid date that is after today',
          },
        },
      ])

      if (errors.length) {
        const [details, prisonListFromApi] = await Promise.all([
          offendersService.getOffenderDetails(res.locals, bookingIdInt),
          offendersService.getAgencies(res.locals),
        ])

        const liteInProgress = assessmentData.bookingId && !assessmentData.approvedDate

        const prisonList = getPrisonList(prisonListFromApi, approvedPlacement)
        const cats = getCatList(supervisorCategory)
        const committees = getCommitteeList(approvedCommittee)

        res.render(`pages/liteApprove`, {
          bookingId,
          errors,
          liteInProgress,
          assessmentData,
          cats,
          committees,
          prisonList,
          approvedDate,
          supervisorCategory,
          approvedCategoryComment,
          approvedCommittee,
          nextReviewDate,
          approvedPlacement,
          approvedPlacementComment,
          approvedComment,
          data: { details },
        })
      } else {
        await offendersService.approveLiteCategorisation({
          context: res.locals,
          bookingId: bookingIdInt,
          sequence: assessmentData.sequence,
          approvedDate,
          supervisorCategory,
          approvedCategoryComment,
          approvedCommittee,
          nextReviewDate,
          approvedPlacement,
          approvedPlacementComment,
          approvedComment,
          transactionalClient: transactionalDbClient,
        })
        res.redirect(`/liteCategories/confirmed/${bookingId}`)
      }
    })
  )

  return router
}
