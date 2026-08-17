const api = require('../../utils/request')
const { authorizeWechatPhone, refreshCurrentProfile } = require('../../utils/auth')
const {
  canAccessFeature,
  getFeatureAccessState,
  getUserType,
  hasCapability
} = require('../../utils/capabilities')
const { acceptCurrentConsent, isConsentCurrent } = require('../../utils/consent')
const { loadModuleFieldSchema } = require('../../utils/moduleFieldSchema')
const { getBaseURL } = require('../../config/env')

const FEATURE_KEY = 'meeting'
const CONTACT_PHONE_REG = /^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/
const NATIVE_BRAND_COLOR = '#D71920'

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatTime(date) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toDateTimeText(value) {
  if (!value) return '-'
  const [date, time = ''] = String(value).replace('T', ' ').split(' ')
  const [year, month, day] = date.split('-')
  return `${year}年${month}月${day}日 ${time.slice(0, 5)}`
}

function hasPositiveAmount(value) {
  return value !== null && value !== undefined && value !== '' && Number(value) > 0
}

function formatMoney(value) {
  return Number(value).toFixed(2)
}

function initialForm() {
  const start = new Date()
  start.setHours(start.getHours() + 1, 0, 0, 0)
  if (start.getHours() < 8) {
    start.setHours(9, 0, 0, 0)
  } else if (start.getHours() >= 22) {
    start.setDate(start.getDate() + 1)
    start.setHours(9, 0, 0, 0)
  }
  const end = new Date(start.getTime() + 60 * 60 * 1000)
  return {
    date: formatDate(start),
    startTime: formatTime(start),
    endTime: formatTime(end),
    meetingRoomId: null,
    applicantName: '',
    contactPhone: '',
    purpose: '',
    participantCount: 1,
    remark: '',
    customFields: {}
  }
}

function combineDateTime(date, time) {
  return `${date}T${time}:00`
}

function roomDisplayName(room) {
  return room.roomName || room.name || room.location || `会议室${room.id}`
}

function normalizeImageUrl(value) {
  const url = String(value || '').trim()
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  const baseURL = getBaseURL()
  if (url.startsWith('/api/')) {
    return `${baseURL.replace(/\/api\/?$/, '')}${url}`
  }
  if (url.startsWith('/mobile/')) {
    return `${baseURL}${url}`
  }
  return `${baseURL.replace(/\/api\/?$/, '')}/${url.replace(/^\/+/, '')}`
}

function normalizeRoomImages(room) {
  const source = Array.isArray(room.images) ? room.images : []
  const urls = source.map(item => normalizeImageUrl(
    typeof item === 'string' ? item : (item && (item.url || item.imageUrl))
  )).filter(Boolean)
  const coverImageUrl = normalizeImageUrl(room.coverImageUrl)
  return [...new Set(coverImageUrl ? [coverImageUrl, ...urls] : urls)]
}

function formatRoomClock(value) {
  const text = String(value || '').trim()
  return text ? text.slice(0, 5) : ''
}

function buildOpenHoursText(room) {
  const openTime = formatRoomClock(room.openTime)
  const closeTime = formatRoomClock(room.closeTime)
  if (openTime && closeTime) return `${openTime}-${closeTime}`
  if (openTime) return `${openTime} 起`
  if (closeTime) return `${closeTime} 前`
  return '开放时段未配置'
}

function normalizeMeetingRoom(room, selectedRoomId) {
  const rateLines = buildRateLines(room)
  const imageUrls = normalizeRoomImages(room)
  const description = String(room.description || room.remark || '').trim()
  const facilities = String(room.facilities || '').trim()
  return {
    ...room,
    roomName: roomDisplayName(room),
    locationText: String(room.location || '').trim() || '位置未填写',
    capacityText: Number(room.capacity) > 0 ? `${Number(room.capacity)} 人` : '人数未配置',
    descriptionText: description || '暂无房间说明',
    facilitiesText: facilities || '设施未配置',
    openHoursText: buildOpenHoursText(room),
    imageUrls,
    coverImageUrl: imageUrls[0] || '',
    hasCoverImage: imageUrls.length > 0,
    rateLines,
    hasRates: rateLines.length > 0,
    selected: Number(selectedRoomId) === Number(room.id)
  }
}

function buildRateLines(room) {
  return [
    { label: '工作时', value: room.workdayWorkHourRate },
    { label: '非工作时', value: room.workdayOffHourRate },
    { label: '节假日', value: room.holidayRate }
  ].filter(item => hasPositiveAmount(item.value)).map(item => ({
    label: item.label,
    text: `¥${formatMoney(item.value)}/小时`
  }))
}

function emptyBookingIdentity() {
  return {
    sourceTypeText: '租户',
    tenantName: '',
    applicantName: '',
    contactPhone: ''
  }
}

function bookingIdentityFromProfile(source) {
  const profile = source && source.profile && typeof source.profile === 'object'
    ? source.profile
    : (source || {})
  const tenantInfo = (source && source.tenantInfo) || profile.tenantInfo || {}
  return {
    sourceTypeText: '租户',
    tenantName: tenantInfo.name || profile.boundTenantName || '',
    applicantName: profile.name || profile.nickname || '',
    contactPhone: profile.maskedPhone || ''
  }
}

function publicBookingIdentityFromProfile(source) {
  const profile = source && source.profile && typeof source.profile === 'object'
    ? source.profile
    : (source || {})
  return {
    sourceTypeText: '游客预约',
    tenantName: '',
    applicantName: '',
    contactPhone: profile.maskedPhone || ''
  }
}

function canCreateGuestBooking(profile) {
  return getUserType(profile) === 'PUBLIC' &&
    profile &&
    profile.bindingState === 'UNMATCHED' &&
    hasCapability('meeting.guest.book', profile)
}

function customSchema(schema) {
  return {
    ...(schema || {}),
    fields: ((schema && schema.fields) || []).filter(field => field.source === 'CUSTOM')
  }
}

function hasRequiredCustomFields(schema) {
  return ((schema && schema.fields) || []).some(field => field.required && !field.readonly)
}

function requestFailureState(error) {
  const code = Number(error && (error.code || error.statusCode || error.status))
  if (code === 401) {
    return {
      pageState: 'login-required',
      errorMessage: '登录状态已失效，请重新登录后预约会议室。'
    }
  }
  if (code === 403) {
    return {
      pageState: 'unauthorized',
      errorMessage: '当前账号没有租户会议预约资格，请联系物业确认租户联系人状态。'
    }
  }
  return {
    pageState: 'error',
    errorMessage: error && error.message ? error.message : '会议数据加载失败，请稍后重试。'
  }
}

function deniedState(profile) {
  const result = getFeatureAccessState(FEATURE_KEY, profile)
  const state = typeof result === 'string' ? result : result && (result.state || result.reason)
  if (state === 'login-required' || (result && result.loginRequired)) {
    return {
      pageState: 'login-required',
      errorMessage: '请先登录租户账号，再预约或查看会议。'
    }
  }
  return {
    pageState: 'unauthorized',
    errorMessage: '会议预约仅向已匹配的有效租户联系人开放，当前账号暂无使用资格。'
  }
}

Page({
  data: {
    pageState: 'loading',
    loading: true,
    submitting: false,
    phoneLoginLoading: false,
    showExtraFields: false,
    errorMessage: '',
    identityText: '',
    today: formatDate(new Date()),
    mode: 'booking',
    isRecordMode: false,
    isBookingMode: true,
    isPublicMode: false,
    guestPhoneReady: false,
    agreementsAccepted: false,
    profile: null,
    availabilityState: 'idle',
    availabilityMessage: '选定会议室后设置预约时间',
    availabilityCheckedKey: '',
    availabilityRangeText: '',
    form: initialForm(),
    bookingIdentity: emptyBookingIdentity(),
    fieldSchema: { fields: [] },
    customFieldSchema: { fields: [] },
    rooms: [],
    bookings: [],
    recentBookings: [],
    hasMoreBookings: false,
    page: 1,
    pageSize: 20,
    total: 0,
    noMore: false,
    loadingMore: false,
    selectedRoom: null
  },

  onLoad(options = {}) {
    this._active = true
    this._loadSequence = 0
    this._availabilitySequence = 0
    const isPublicMode = options.mode === 'public'
    const isRecordMode = options.mode === 'records'
    this.setData({
      mode: isPublicMode ? 'public' : (isRecordMode ? 'records' : 'booking'),
      isRecordMode,
      isBookingMode: !isRecordMode,
      isPublicMode,
      showExtraFields: false,
      availabilityState: 'idle',
      availabilityMessage: '选定会议室后设置预约时间',
      availabilityCheckedKey: '',
      availabilityRangeText: '',
      rooms: [],
      bookings: [],
      recentBookings: [],
      hasMoreBookings: false,
      bookingIdentity: emptyBookingIdentity(),
      agreementsAccepted: isConsentCurrent(),
      selectedRoom: null
    })
    wx.setNavigationBarTitle({
      title: isPublicMode ? '会议室' : (isRecordMode ? '我的会议' : '会议预约')
    })
  },

  onShow() {
    this._active = true
    this.safeSetData({ agreementsAccepted: isConsentCurrent() })
    this.loadMeetingHome()
  },

  onUnload() {
    this._active = false
    this._loadSequence = Number(this._loadSequence || 0) + 1
    this._availabilitySequence = Number(this._availabilitySequence || 0) + 1
  },

  safeSetData(payload, callback) {
    if (!this._active) return
    this.setData(payload, callback)
  },

  isCurrentRequest(requestId) {
    return this._active && requestId === this._loadSequence
  },

  async loadMeetingHome(options = {}) {
    const append = Boolean(options.append)
    if (append && (this.data.loadingMore || this.data.noMore)) return
    const shouldShowRooms = this.data.isBookingMode
    const requestId = Number(this._loadSequence || 0) + 1
    this._loadSequence = requestId
    if (!append) {
      this._availabilitySequence = Number(this._availabilitySequence || 0) + 1
    }
    this.safeSetData(append ? { loadingMore: true } : {
      pageState: 'loading',
      loading: true,
      errorMessage: '',
      identityText: '',
      rooms: [],
      bookings: [],
      recentBookings: [],
      hasMoreBookings: false,
      availabilityState: 'idle',
      availabilityMessage: '选定会议室后设置预约时间',
      availabilityCheckedKey: '',
      availabilityRangeText: '',
      bookingIdentity: emptyBookingIdentity(),
      'form.meetingRoomId': null,
      selectedRoom: null
    })

    try {
      const profile = await refreshCurrentProfile()
      if (!this.isCurrentRequest(requestId)) return
      const allowed = this.data.isPublicMode
        ? canAccessFeature('meetingPublic', profile)
        : canAccessFeature(FEATURE_KEY, profile)
      if (!allowed) {
        this.safeSetData({
          ...deniedState(profile),
          loading: false
        })
        return
      }

      const page = append ? this.data.page + 1 : 1
      const [data, fieldSchema] = await Promise.all([
        api.get(this.data.isPublicMode ? '/mobile/public/meeting-rooms' : '/mobile/meetings', {
          startTime: combineDateTime(this.data.form.date, this.data.form.startTime),
          endTime: combineDateTime(this.data.form.date, this.data.form.endTime),
          page,
          pageSize: this.data.pageSize
        }),
        !this.data.isPublicMode && this.data.isBookingMode && !this.data.fieldSchema.moduleKey
          ? loadModuleFieldSchema('meeting-bookings')
          : Promise.resolve(this.data.fieldSchema)
      ])
      if (!this.isCurrentRequest(requestId)) return
      const incomingRooms = shouldShowRooms
        ? (this.data.isPublicMode ? (data.records || []) : (data.rooms || []))
            .map(room => normalizeMeetingRoom(room, this.data.form.meetingRoomId))
        : []
      const incomingBookings = (this.data.isPublicMode ? [] : (data.bookings || data.records || [])).map(item => ({
        ...item,
        timeText: `${toDateTimeText(item.startTime)} 至 ${toDateTimeText(item.endTime)}`,
        hasAmount: hasPositiveAmount(item.calculatedAmount),
        amountText: hasPositiveAmount(item.calculatedAmount) ? formatMoney(item.calculatedAmount) : '',
        statusText: this.toStatusText(item.status),
        reviewReply: String(item.reviewReply || '').trim(),
        cancellable: ['PENDING', 'BOOKED', 'CONFIRMED'].includes(item.status)
      }))
      const rooms = append && this.data.isPublicMode ? this.data.rooms.concat(incomingRooms) : incomingRooms
      const bookings = append && !this.data.isPublicMode ? this.data.bookings.concat(incomingBookings) : incomingBookings
      const recordCount = this.data.isPublicMode ? rooms.length : bookings.length
      const total = Number(data.total || 0)
      const currentProfile = data.profile || profile
      const bookingIdentity = this.data.isPublicMode
        ? publicBookingIdentityFromProfile(currentProfile)
        : bookingIdentityFromProfile(currentProfile)
      const guestPhoneReady = this.data.isPublicMode && canCreateGuestBooking(currentProfile)
      const nextCustomFieldSchema = customSchema(fieldSchema)
      this.safeSetData({
        pageState: this.data.isRecordMode && bookings.length === 0 ? 'empty' : 'success',
        loading: false,
        loadingMore: false,
        page: Number(data.page || page),
        total,
        noMore: recordCount >= total,
        identityText: this.data.isPublicMode
          ? (guestPhoneReady ? `手机号已验证：${bookingIdentity.contactPhone}` : '游客浏览')
          : this.toIdentityText(currentProfile),
        profile: currentProfile,
        guestPhoneReady,
        bookingIdentity,
        fieldSchema,
        customFieldSchema: nextCustomFieldSchema,
        showExtraFields: this.data.showExtraFields || hasRequiredCustomFields(nextCustomFieldSchema),
        rooms,
        bookings,
        recentBookings: bookings.slice(0, 1),
        hasMoreBookings: bookings.length > 1 || total > 1
      })
    } catch (error) {
      if (!this.isCurrentRequest(requestId)) return
      this.safeSetData({
        ...requestFailureState(error),
        loading: false,
        loadingMore: false,
        identityText: '',
        rooms: [],
        bookings: [],
        recentBookings: [],
        hasMoreBookings: false,
        availabilityState: 'idle',
        availabilityMessage: '选定会议室后设置预约时间',
        availabilityCheckedKey: '',
        availabilityRangeText: '',
        bookingIdentity: emptyBookingIdentity(),
        selectedRoom: null
      })
    }
  },

  retryLoad() {
    this.loadMeetingHome()
  },

  onPullDownRefresh() {
    this.loadMeetingHome().finally(() => wx.stopPullDownRefresh())
  },

  onReachBottom() {
    this.loadMeetingHome({ append: true })
  },

  openBookingDetail(event) {
    wx.navigateTo({ url: `/pages/business-detail/index?type=meeting&id=${event.currentTarget.dataset.id}` })
  },

  openAllBookingRecords() {
    wx.navigateTo({ url: '/pages/meeting/index?mode=records' })
  },

  handleAccessError(error) {
    const state = requestFailureState(error)
    if (state.pageState === 'error') return false
    this.safeSetData({
      ...state,
      loading: false,
      identityText: '',
      rooms: [],
      bookings: [],
      recentBookings: [],
      hasMoreBookings: false,
      availabilityState: 'idle',
      availabilityMessage: '选定会议室后设置预约时间',
      availabilityCheckedKey: '',
      availabilityRangeText: '',
      bookingIdentity: emptyBookingIdentity(),
      selectedRoom: null
    })
    return true
  },

  onDateChange(event) {
    this.safeSetData({
      'form.date': event.detail.value,
      availabilityState: 'idle',
      availabilityMessage: '正在检查该时间段…',
      availabilityCheckedKey: ''
    }, () => this.checkSelectedRoomAvailability())
  },

  onStartTimeChange(event) {
    const startTime = event.detail.value
    const endTime = this.data.form.endTime <= startTime ? this.nextHour(startTime) : this.data.form.endTime
    this.safeSetData({
      'form.startTime': startTime,
      'form.endTime': endTime,
      availabilityState: 'idle',
      availabilityMessage: '正在检查该时间段…',
      availabilityCheckedKey: ''
    }, () => this.checkSelectedRoomAvailability())
  },

  onEndTimeChange(event) {
    const endTime = event.detail.value
    this.safeSetData({
      'form.endTime': endTime,
      availabilityState: 'idle',
      availabilityMessage: '正在检查该时间段…',
      availabilityCheckedKey: ''
    }, () => this.checkSelectedRoomAvailability())
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field
    this.safeSetData({ [`form.${field}`]: event.detail.value })
  },

  onSchemaChange(event) {
    const values = (event.detail && event.detail.values) || {}
    this.safeSetData({ 'form.customFields': values })
  },

  onUploadState() {},

  onRoomImageError(event) {
    const id = Number(event.currentTarget.dataset.id)
    if (!id) return
    const hideCover = room => Number(room.id) === id
      ? { ...room, coverImageUrl: '', hasCoverImage: false }
      : room
    const selectedRoom = this.data.selectedRoom && Number(this.data.selectedRoom.id) === id
      ? { ...this.data.selectedRoom, coverImageUrl: '', hasCoverImage: false }
      : this.data.selectedRoom
    this.safeSetData({
      rooms: this.data.rooms.map(hideCover),
      selectedRoom
    })
  },

  currentAvailabilityKey() {
    const { meetingRoomId, date, startTime, endTime } = this.data.form
    return `${meetingRoomId || ''}|${date || ''}|${startTime || ''}|${endTime || ''}`
  },

  timeRangeIssue() {
    const { date, startTime, endTime } = this.data.form
    if (!date || !startTime || !endTime) return '请选择完整预约时间'
    const start = new Date(combineDateTime(date, startTime))
    const end = new Date(combineDateTime(date, endTime))
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
      return '结束时间需晚于开始时间'
    }
    if (start.getTime() <= Date.now()) return '开始时间需晚于当前时间'
    if (this.data.selectedRoom) {
      const openTime = formatRoomClock(this.data.selectedRoom.openTime)
      const closeTime = formatRoomClock(this.data.selectedRoom.closeTime)
      if ((openTime && startTime < openTime) || (closeTime && endTime > closeTime)) {
        return `该会议室开放时间为 ${this.data.selectedRoom.openHoursText}，请调整时间`
      }
    }
    return ''
  },

  async checkSelectedRoomAvailability() {
    const roomId = Number(this.data.form.meetingRoomId)
    if (!roomId || !this.data.selectedRoom) return
    const requestId = Number(this._availabilitySequence || 0) + 1
    this._availabilitySequence = requestId
    const key = this.currentAvailabilityKey()
    const { date, startTime, endTime } = this.data.form
    const rangeText = `${String(date || '').replace(/-/g, '/')} ${startTime || '--:--'}-${endTime || '--:--'}`
    const issue = this.timeRangeIssue()
    if (issue) {
      this.safeSetData({
        availabilityState: 'invalid',
        availabilityMessage: issue,
        availabilityCheckedKey: '',
        availabilityRangeText: rangeText
      })
      return
    }

    this.safeSetData({
      availabilityState: 'checking',
      availabilityMessage: '正在检查该时间段…',
      availabilityCheckedKey: '',
      availabilityRangeText: rangeText
    })
    try {
      const result = await api.get(`/mobile/public/meeting-rooms/${roomId}/availability`, {
        startTime: combineDateTime(date, startTime),
        endTime: combineDateTime(date, endTime)
      }, { silent: true })
      if (!this._active || requestId !== this._availabilitySequence || key !== this.currentAvailabilityKey()) return
      const available = Boolean(result && (result.available === true || Number(result.available) === 1))
      this.safeSetData({
        availabilityState: available ? 'available' : 'occupied',
        availabilityMessage: available
          ? '该时间段空闲，可以预约'
          : '该时间段已被占用，请调整时间',
        availabilityCheckedKey: key,
        availabilityRangeText: rangeText
      })
    } catch (error) {
      if (!this._active || requestId !== this._availabilitySequence || key !== this.currentAvailabilityKey()) return
      this.safeSetData({
        availabilityState: 'error',
        availabilityMessage: error && error.message
          ? `时间段检查失败：${error.message}`
          : '时间段检查失败，请重试',
        availabilityCheckedKey: '',
        availabilityRangeText: rangeText
      })
    }
  },

  retryAvailabilityCheck() {
    this.checkSelectedRoomAvailability()
  },

  selectRoom(event) {
    const id = Number(event.currentTarget.dataset.id)
    const room = this.data.rooms.find(item => Number(item.id) === id)
    if (!room) return
    const rooms = this.data.rooms.map(item => ({
      ...item,
      selected: Number(item.id) === id
    }))
    this.safeSetData({
      'form.meetingRoomId': id,
      selectedRoom: room,
      rooms,
      availabilityState: 'idle',
      availabilityMessage: '正在检查该时间段…',
      availabilityCheckedKey: '',
      availabilityRangeText: ''
    }, () => {
      this.checkSelectedRoomAvailability()
      if (wx.pageScrollTo) {
        wx.pageScrollTo({ selector: '#bookingForm', duration: 240 })
      }
    })
  },

  changeRoom() {
    this._availabilitySequence = Number(this._availabilitySequence || 0) + 1
    this.safeSetData({
      'form.meetingRoomId': null,
      selectedRoom: null,
      rooms: this.data.rooms.map(item => ({ ...item, selected: false })),
      availabilityState: 'idle',
      availabilityMessage: '选定会议室后设置预约时间',
      availabilityCheckedKey: '',
      availabilityRangeText: ''
    }, () => {
      if (wx.pageScrollTo) {
        wx.pageScrollTo({ selector: '.room-section-head', duration: 220 })
      }
    })
  },

  toggleExtraFields() {
    this.safeSetData({ showExtraFields: !this.data.showExtraFields })
  },

  async submitBooking() {
    if (this.data.submitting || !this.validateTimeRange()) return
    if (this.data.isPublicMode && !this.data.guestPhoneReady) {
      wx.showModal({
        title: '请先完成手机号快捷登录',
        content: '会议室可以直接查看；提交预约前需完成手机号快捷登录以验证身份。本次预约联系使用表单中填写的联系电话。',
        confirmColor: NATIVE_BRAND_COLOR,
        showCancel: false
      })
      return
    }
    if (!this.data.form.meetingRoomId) {
      wx.showToast({ title: '请选择会议室', icon: 'none' })
      return
    }
    if (this.data.availabilityState !== 'available'
      || this.data.availabilityCheckedKey !== this.currentAvailabilityKey()) {
      wx.showToast({
        title: this.data.availabilityState === 'occupied'
          ? '该时间段已被占用，请调整'
          : '请先确认预约时间空闲',
        icon: 'none'
      })
      if (this.data.availabilityState !== 'checking') this.checkSelectedRoomAvailability()
      return
    }
    const purpose = String(this.data.form.purpose || '').trim()
    const applicantName = String(this.data.form.applicantName || '').trim()
    const contactPhone = String(this.data.form.contactPhone || '').trim()
    const remark = String(this.data.form.remark || '').trim()
    const participantCount = Number(this.data.form.participantCount)
    if (!purpose) {
      wx.showToast({ title: '请填写会议用途', icon: 'none' })
      return
    }
    if (this.data.isPublicMode && !applicantName) {
      wx.showToast({ title: '请填写预约人姓名', icon: 'none' })
      return
    }
    if (applicantName.length > 64) {
      wx.showToast({ title: '预约人姓名不能超过64字', icon: 'none' })
      return
    }
    if (this.data.isPublicMode && !CONTACT_PHONE_REG.test(contactPhone)) {
      wx.showToast({ title: '请输入正确的联系电话', icon: 'none' })
      return
    }
    if (purpose.length > 500) {
      wx.showToast({ title: '会议用途不能超过500字', icon: 'none' })
      return
    }
    if (!Number.isInteger(participantCount) || participantCount < 1) {
      wx.showToast({ title: '参会人数须为正整数', icon: 'none' })
      return
    }
    if (this.data.selectedRoom && Number(this.data.selectedRoom.capacity) > 0
      && participantCount > Number(this.data.selectedRoom.capacity)) {
      wx.showToast({ title: `参会人数不能超过${this.data.selectedRoom.capacity}人`, icon: 'none' })
      return
    }
    if (remark.length > 500) {
      wx.showToast({ title: '备注不能超过500字', icon: 'none' })
      return
    }
    const schemaForm = this.data.isPublicMode ? null : this.selectComponent('#meetingCustomForm')
    const schemaValidation = schemaForm && schemaForm.validate()
    if (schemaValidation && !schemaValidation.valid) {
      this.safeSetData({ showExtraFields: true })
      wx.showToast({ title: schemaValidation.uploading ? '文件上传中，请稍候' : '请完善补充信息', icon: 'none' })
      return
    }
    const customFields = schemaForm
      ? schemaForm.getPayload().customFields
      : this.data.form.customFields

    this.safeSetData({ submitting: true })
    try {
      const booking = await api.post(
        this.data.isPublicMode ? '/mobile/public/meeting-bookings' : '/mobile/meetings/bookings',
        {
        meetingRoomId: this.data.form.meetingRoomId,
        startTime: combineDateTime(this.data.form.date, this.data.form.startTime),
        endTime: combineDateTime(this.data.form.date, this.data.form.endTime),
        ...(this.data.isPublicMode ? {
          contactName: applicantName,
          contactPhone: contactPhone
        } : {}),
        purpose,
        participantCount: Number(this.data.form.participantCount),
        remark: this.data.form.remark,
        ...(!this.data.isPublicMode ? { customFields } : {})
        }
      )
      if (!this._active) return
      const details = []
      if (booking.bookingNumber) details.push(`预约单号：${booking.bookingNumber}`)
      if (booking.status) details.push(`当前状态：${this.toStatusText(booking.status)}`)
      if (booking.status === 'PENDING') details.push('后续状态：等待物业审核')
      if (hasPositiveAmount(booking.calculatedAmount)) {
        details.push(`预计金额：¥${formatMoney(booking.calculatedAmount)}`)
      }
      wx.showModal({
        title: '预约已提交',
        content: details.join('\n') || (this.data.isPublicMode
          ? '物业将通过您填写的联系电话联系您；已验证手机号仅用于身份核验。请保存预约单号。'
          : '预约已进入物业审核，请在“我的会议”查看审核结果与物业回复。'),
        showCancel: false
      })
      this.safeSetData({
        form: {
          ...this.data.form,
          meetingRoomId: null,
          applicantName: this.data.isPublicMode ? applicantName : '',
          contactPhone: this.data.isPublicMode ? contactPhone : '',
          purpose: '',
          participantCount: 1,
          remark: '',
          customFields: {}
        },
        rooms: [],
        selectedRoom: null,
        availabilityState: 'idle',
        availabilityMessage: '选定会议室后设置预约时间',
        availabilityCheckedKey: '',
        availabilityRangeText: '',
        showExtraFields: hasRequiredCustomFields(this.data.customFieldSchema)
      })
      this.loadMeetingHome()
    } catch (error) {
      this.handleAccessError(error)
    } finally {
      this.safeSetData({ submitting: false })
    }
  },

  goProfile() {
    wx.switchTab({ url: '/pages/mine/index' })
  },

  nextHour(time) {
    const [hour, minute] = time.split(':').map(Number)
    const nextMinutes = Math.min((hour * 60) + minute + 60, (24 * 60) - 1)
    return `${pad(Math.floor(nextMinutes / 60))}:${pad(nextMinutes % 60)}`
  },

  onAgreementChange(event) {
    const accepted = (event.detail.value || []).includes('accepted')
    if (accepted) acceptCurrentConsent()
    this.safeSetData({ agreementsAccepted: accepted })
  },

  promptAgreementRequired() {
    wx.showToast({ title: '请先阅读并同意服务协议和隐私保护指引', icon: 'none' })
  },

  async authorizeGuestPhone(event) {
    if (this.data.phoneLoginLoading) return
    if (!this.data.agreementsAccepted || !isConsentCurrent()) {
      this.promptAgreementRequired()
      return
    }
    const detail = (event && event.detail) || {}
    if (!detail.code || (detail.errMsg && !detail.errMsg.includes('ok'))) {
      wx.showToast({ title: '未完成手机号快捷登录', icon: 'none' })
      return
    }

    this.safeSetData({ phoneLoginLoading: true })
    try {
      const profile = await authorizeWechatPhone(detail.code, { silent: true })
      if (!this._active) return
      const userType = getUserType(profile)
      if (profile.bindingState === 'PENDING_SELECTION') {
        wx.showModal({
          title: '请选择业务身份',
          content: '该手机号匹配到多个业务身份，请先在账号管理中选择当前身份。',
          confirmText: '去选择',
          showCancel: false,
          success: () => wx.navigateTo({ url: '/pages/me/index' })
        })
        return
      }
      if (userType === 'TENANT') {
        wx.showModal({
          title: '已识别为租户',
          content: '将进入租户会议预约，手机号和企业信息由服务端自动关联。',
          confirmText: '继续预约',
          showCancel: false,
          success: () => wx.redirectTo({ url: '/pages/meeting/index' })
        })
        return
      }
      if (userType !== 'PUBLIC' || !canCreateGuestBooking(profile)) {
        wx.showModal({
          title: '当前身份不能使用游客预约',
          content: userType === 'STAFF'
            ? '物业员工身份不能作为游客提交会议预约，请切换到租户身份或使用后台管理。'
            : '手机号验证状态尚未生效，请重新进入页面后再试。',
          showCancel: false
        })
        return
      }

      const bookingIdentity = publicBookingIdentityFromProfile(profile)
      this.safeSetData({
        profile,
        guestPhoneReady: true,
        bookingIdentity,
        identityText: `手机号已验证：${bookingIdentity.contactPhone}`
      })
      wx.showToast({ title: '手机号验证成功', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error && error.message ? error.message : '手机号快捷登录失败', icon: 'none' })
    } finally {
      this.safeSetData({ phoneLoginLoading: false })
    }
  },

  validateTimeRange() {
    const { date, startTime, endTime } = this.data.form
    if (!date || !startTime || !endTime) {
      wx.showToast({ title: '请选择完整预约时间', icon: 'none' })
      return false
    }
    const start = new Date(combineDateTime(date, startTime))
    const end = new Date(combineDateTime(date, endTime))
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
      wx.showToast({ title: '结束时间需晚于开始时间', icon: 'none' })
      return false
    }
    if (start.getTime() <= Date.now()) {
      wx.showToast({ title: '开始时间需晚于当前时间', icon: 'none' })
      return false
    }
    return true
  },

  toIdentityText(profile) {
    const identity = bookingIdentityFromProfile(profile)
    return identity.tenantName ? `租户：${identity.tenantName}` : identity.applicantName
  },

  toStatusText(status) {
    if (status === 'AVAILABLE') return '可预约'
    if (status === 'PENDING') return '待审核'
    if (status === 'BOOKED') return '待审核'
    if (status === 'CONFIRMED') return '审核通过'
    if (status === 'REJECTED') return '已驳回'
    if (status === 'CANCELLED') return '已取消'
    if (status === 'COMPLETED') return '已完成'
    return status || '未知'
  }
})
