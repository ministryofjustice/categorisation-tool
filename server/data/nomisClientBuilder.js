const superagent = require('superagent')
const moment = require('moment')
const logger = require('../../log')
const config = require('../config')
const { getApiClientToken } = require('../authentication/clientCredentials')

const timeoutSpec = {
  response: config.apis.elite2.timeout.response,
  deadline: config.apis.elite2.timeout.deadline,
}

const apiUrl = config.apis.elite2.url

module.exports = token => {
  const nomisUserGet = nomisUserGetBuilder(token)
  const nomisClientGet = nomisClientGetBuilder()
  const nomisPost = nomisPushBuilder('post', token)
  const nomisClientPost = nomisClientPostBuilder()
  const nomisPut = nomisPushBuilder('put', token)

  return {
    getUncategorisedOffenders(agencyId) {
      const path = `${apiUrl}api/offender-assessments/category/${agencyId}?type=UNCATEGORISED`
      return nomisUserGet({ path })
    },
    getCategorisedOffenders(agencyId, bookingIds) {
      const path = `${apiUrl}api/offender-assessments/category/${agencyId}?latestOnly=false`
      return nomisPost({ path, body: bookingIds })
    },
    getLatestCategorisationForOffenders(offenderNos) {
      if (offenderNos.length === 0) return []
      const path = `${apiUrl}api/offender-assessments/CATEGORY?latestOnly=true&activeOnly=false`
      return nomisPost({ path, body: offenderNos })
    },
    getRecategoriseOffenders(agencyId, cutoff) {
      const path = `${apiUrl}api/offender-assessments/category/${agencyId}?type=RECATEGORISATIONS&date=${cutoff}`
      return nomisUserGet({ path })
    },
    getPrisonersAtLocation(agencyId, fromDob, toDob) {
      const path = `${apiUrl}api/locations/description/${agencyId}/inmates?fromDob=${fromDob}&toDob=${toDob}&returnCategory=true`
      const headers = { 'Page-Limit': 5000 }
      return nomisUserGet({ path, headers })
    },
    getSentenceDatesForOffenders(bookingIds) {
      if (bookingIds.length === 0) return []
      const path = `${apiUrl}api/offender-sentences/bookings`
      return nomisPost({ path, body: bookingIds })
    },
    getSentenceHistory(offenderNo) {
      const path = `${apiUrl}api/offender-sentences?offenderNo=${offenderNo}`
      return nomisUserGet({ path })
    },
    getSentenceDetails(bookingId) {
      const path = `${apiUrl}api/bookings/${bookingId}/sentenceDetail`
      return nomisClientGet({ path })
    },
    getSentenceTerms(bookingId) {
      const path = `${apiUrl}api/offender-sentences/booking/${bookingId}/sentenceTerms?earliestOnly=false`
      return nomisClientGet({ path })
    },
    getUser() {
      const path = `${apiUrl}api/users/me`
      return nomisUserGet({ path })
    },
    getUserByUserId(userId) {
      const path = `${apiUrl}api/users/${userId}`
      return nomisUserGet({ path })
    },
    getUserCaseLoads() {
      const path = `${apiUrl}api/users/me/caseLoads`
      return nomisUserGet({ path })
    },
    getImageData(imageId) {
      const path = `${apiUrl}api/images/${imageId}/data`
      return nomisUserGet({ path, responseType: 'stream', raw: true })
    },
    getOffenderDetails(bookingId) {
      const path = `${apiUrl}api/bookings/${bookingId}?basicInfo=false`
      return nomisClientGet({ path })
    },
    getBasicOffenderDetails(bookingId) {
      const path = `${apiUrl}api/bookings/${bookingId}?basicInfo=true`
      return nomisClientGet({ path })
    },
    getOffenderDetailsByOffenderNo(offenderNo) {
      const path = `${apiUrl}api/bookings/offenderNo/${offenderNo}?fullInfo=true`
      return nomisClientGet({ path })
    },
    getMainOffence(bookingId) {
      const path = `${apiUrl}api/bookings/${bookingId}/mainOffence`
      return nomisClientGet({ path })
    },
    getOffenceHistory(offenderNo) {
      const path = `${apiUrl}api/bookings/offenderNo/${offenderNo}/offenceHistory`
      return nomisUserGet({ path })
    },
    getCategoryHistory(offenderNo) {
      const path = `${apiUrl}api/offender-assessments/CATEGORY?offenderNo=${offenderNo}&latestOnly=false&activeOnly=false`
      return nomisUserGet({ path })
    },
    getCategory(bookingId) {
      const path = `${apiUrl}api/bookings/${bookingId}/assessment/CATEGORY`
      return nomisUserGet({ path })
    },
    getAgencyDetail(agencyId) {
      const path = `${apiUrl}api/agencies/${agencyId}?activeOnly=false`
      return nomisUserGet({ path })
    },
    createSupervisorApproval(details) {
      const path = `${apiUrl}api/offender-assessments/category/approve`
      return nomisPut({ path, body: details })
    },
    createInitialCategorisation(details) {
      const path = `${apiUrl}api/offender-assessments/category/categorise`
      return nomisPost({ path, body: details })
    },
    getOffenderDetailList(offenderNos) {
      const path = `${apiUrl}api/bookings/offenders?activeOnly=false`
      return nomisClientPost({ path, body: offenderNos })
    },
    getUserDetailList(usernames) {
      const path = `${apiUrl}api/users/list`
      return nomisPost({ path, body: usernames })
    },
    // todo complete when eliteapi endpoint is defined
    updateNextReviewDate(bookingId, nextReviewDate) {
      const path = `${apiUrl}api/offender-assessments/category/nextReviewDate`
      return nomisClientPost({ path, body: { bookingId, nextReviewDate } })
    },
  }
}

function nomisUserGetBuilder(token) {
  return async ({ path, query = '', headers = {}, responseType = '', raw = false } = {}) => {
    const time = moment()
    try {
      const result = await superagent
        .get(path)
        .query(query)
        .set('Authorization', `Bearer ${token}`)
        .set(headers)
        .responseType(responseType)
        .timeout(timeoutSpec)

      const durationMillis = moment().diff(time)
      logger.debug({ path, query, durationMillis }, 'Nomis GET using user credentials')
      return raw ? result : result.body
    } catch (error) {
      logger.error({ ...error, path, query }, 'Error in Nomis GET using user credentials')
      throw error
    }
  }
}

function nomisClientGetBuilder() {
  return async ({ path, query = '', headers = {}, responseType = '', raw = false } = {}) => {
    const time = moment()
    try {
      const clientToken = await getApiClientToken()

      const result = await superagent
        .get(path)
        .query(query)
        .set('Authorization', `Bearer ${clientToken.body.access_token}`)
        .set(headers)
        .responseType(responseType)
        .timeout(timeoutSpec)

      const durationMillis = moment().diff(time)
      logger.debug({ path, query, durationMillis }, 'Nomis GET using clientId credentials')
      return raw ? result : result.body
    } catch (error) {
      logger.error({ ...error, path, query }, 'Error in Nomis GET using clientId credentials')
      throw error
    }
  }
}

function nomisPushBuilder(verb, token) {
  const updateMethod = {
    put,
    post,
  }

  return async ({ path, body = '', headers = {}, responseType = '' } = {}) => {
    const time = moment()
    try {
      const result = await updateMethod[verb](token, path, body, headers, responseType)

      const durationMillis = moment().diff(time)
      logger.debug({ path, body, durationMillis }, 'Nomis PUSH')
      return result.body
    } catch (error) {
      logger.error({ ...error, path }, 'Error in Nomis PUSH')
      throw error
    }
  }
}

function nomisClientPostBuilder() {
  return async ({ path, body = '', headers = {}, responseType = '' } = {}) => {
    const time = moment()
    try {
      const clientToken = await getApiClientToken()

      const result = await superagent
        .post(path)
        .send(body)
        .set('Authorization', `Bearer ${clientToken.body.access_token}`)
        .set(headers)
        .responseType(responseType)
        .timeout(timeoutSpec)

      const durationMillis = moment().diff(time)
      logger.debug({ path, body, durationMillis }, 'Nomis POST')
      return result.body
    } catch (error) {
      logger.error({ ...error, path }, 'Error in Nomis POST')
      throw error
    }
  }
}

async function post(token, path, body, headers, responseType) {
  return superagent
    .post(path)
    .send(body)
    .set('Authorization', `Bearer ${token}`)
    .set(headers)
    .responseType(responseType)
    .timeout(timeoutSpec)
}

async function put(token, path, body, headers, responseType) {
  return superagent
    .put(path)
    .send(body)
    .set('Authorization', `Bearer ${token}`)
    .set(headers)
    .responseType(responseType)
    .timeout(timeoutSpec)
}
