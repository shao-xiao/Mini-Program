const api = require('../../utils/request')
const { formatMoney, formatDateTime } = require('../../utils/format')

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatTime(date) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function initialForm() {
  const start = new Date()
  start.setHours(start.getHours() + 1, 0, 0, 0)
  const end = new Date(start.getTime() + 60 * 60 * 1000)
  return {
    date: formatDate(start),
    startTime: formatTime(start),
    endTime: formatTime(end),
    meetingRoomId: null,
    purpose: '',
    departmentName: '',
    contactPhone: ''
  }
}

function combineDateTime(date, time) {
  return `${date}T${time}:00`
}

Page({
  data: {
    loading: false,
    submitting: false,
    errorMessage: '',
    identityText: '',
    profile: {},
    form: initialForm(),
    rooms: [],
    bookings: [],
    selectedRoom: null
  },

  onShow() {
    this.loadMeetingHome()
  },

  async loadMeetingHome() {
    this.setData({ loading: true, errorMessage: '' })
    try {
      const data = await api.get('/mobile/meetings', {
        startTime: combineDateTime(this.data.form.date, this.data.form.startTime),
        endTime: combineDateTime(this.data.form.date, this.data.form.endTime)
      })
      const rooms = (data.rooms || []).map(room => ({
        ...room,
        rateText: `工作时段：${formatMoney(room.workdayWorkHourRate)}/小时`,
        offRateText: `加班时段：${formatMoney(room.workdayOffHourRate)}/小时`,
        holidayRateText: `节假日：${formatMoney(room.holidayRate)}/小时`,
        selected: this.data.form.meetingRoomId === room.id
      }))
      const bookings = (data.bookings || []).map(item => ({
        ...item,
        timeText: `${formatDateTime(item.startTime)} 至 ${formatDateTime(item.endTime)}`,
        amountText: formatMoney(item.amount || item.calculatedAmount),
        feeText: item.feeType === 'INTERNAL_FREE' ? '内部免费' : `预计费用 ¥ ${formatMoney(item.amount || item.calculatedAmount)}`,
        statusText: this.toStatusText(item.status),
        cancellable: item.status === 'PENDING' || item.status === 'BOOKED' || item.status === 'CONFIRMED'
      }))
      this.setData({
        profile: data.profile || {},
        identityText: this.toIdentityText(data.profile),
        rooms,
        bookings
      })
    } catch (error) {
      this.setData({
        errorMessage: error && error.message ? error.message : '请先登录后再获取会议信息',
        rooms: [],
        bookings: []
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  onDateChange(event) {
    this.setData({
      'form.date': event.detail.value,
      'form.meetingRoomId': null,
      selectedRoom: null
    }, () => this.loadMeetingHome())
  },

  onStartTimeChange(event) {
    const startTime = event.detail.value
    const endTime = this.data.form.endTime <= startTime ? this.nextHour(startTime) : this.data.form.endTime
    this.setData({
      'form.startTime': startTime,
      'form.endTime': endTime,
      'form.meetingRoomId': null,
      selectedRoom: null
    }, () => this.loadMeetingHome())
  },

  onEndTimeChange(event) {
    const endTime = event.detail.value
    if (endTime <= this.data.form.startTime) {
      wx.showToast({ title: '结束时间需晚于开始时间', icon: 'none' })
      return
    }
    this.setData({
      'form.endTime': endTime,
      'form.meetingRoomId': null,
      selectedRoom: null
    }, () => this.loadMeetingHome())
  },

  onInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: event.detail.value })
  },

  selectRoom(event) {
    const id = Number(event.currentTarget.dataset.id)
    const room = this.data.rooms.find(item => item.id === id)
    if (!room || !room.available) {
      wx.showToast({ title: room && room.unavailableReason ? room.unavailableReason : '请重新选择可用会议室', icon: 'none' })
      return
    }
    const rooms = this.data.rooms.map(item => ({
      ...item,
      selected: item.id === id
    }))
    this.setData({ 'form.meetingRoomId': id, selectedRoom: room, rooms })
  },

  async submitBooking() {
    if (!this.data.form.meetingRoomId) {
      wx.showToast({ title: '请先选择会议室', icon: 'none' })
      return
    }
    if (!this.data.form.purpose.trim()) {
      wx.showToast({ title: '请填写使用目的', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      const booking = await api.post('/mobile/meetings/bookings', {
        meetingRoomId: this.data.form.meetingRoomId,
        startTime: combineDateTime(this.data.form.date, this.data.form.startTime),
        endTime: combineDateTime(this.data.form.date, this.data.form.endTime),
        purpose: this.data.form.purpose,
        departmentName: this.data.form.departmentName,
        contactPhone: this.data.form.contactPhone,
        billingMode: 'HOURLY'
      })
      wx.showModal({
        title: '预约成功',
        content: `预约号：${booking.bookingNo || booking.bookingNumber}\n${booking.feeType === 'INTERNAL_FREE' ? '内部免费' : `预计费用：${formatMoney(booking.amount || booking.calculatedAmount)}`}`,
        showCancel: false
      })
      this.setData({
        form: {
          ...this.data.form,
          meetingRoomId: null,
          purpose: '',
          departmentName: ''
        },
        selectedRoom: null
      })
      this.loadMeetingHome()
    } finally {
      this.setData({ submitting: false })
    }
  },

  cancelBooking(event) {
    const id = event.currentTarget.dataset.id
    const booking = this.data.bookings.find(item => item.id === id)
    wx.showModal({
      title: '取消会议',
      content: booking ? `确认取消 ${booking.meetingRoomName} 的预约？` : '确认取消该预约？',
      confirmText: '取消',
      confirmColor: '#d93025',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await api.post(`/mobile/meetings/bookings/${id}/cancel`)
          wx.showToast({ title: '已取消', icon: 'success' })
          this.loadMeetingHome()
        } catch (error) {
          // request.js already displays the backend message.
        }
      }
    })
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/me/index' })
  },

  nextHour(time) {
    const [hour] = time.split(':').map(Number)
    return `${pad(Math.min(hour + 1, 23))}:00`
  },

  toIdentityText(profile) {
    if (!profile) return ''
    if (profile.userType === 'INTERNAL') return `内部人员：${profile.boundSysRealName || profile.boundSysUsername || profile.nickname || ''}`
    if (profile.userType === 'TENANT') return `租户：${profile.boundTenantName || profile.nickname || ''}`
    return '游客'
  },

  toStatusText(status) {
    if (status === 'BOOKED' || status === 'PENDING') return '待确认'
    if (status === 'CONFIRMED') return '已确认'
    if (status === 'CANCELLED') return '已取消'
    if (status === 'COMPLETED') return '已完成'
    return status || '未知'
  }
})
