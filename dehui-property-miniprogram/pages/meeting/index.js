const api = require('../../utils/request')

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
  const [date, time = ''] = value.replace('T', ' ').split(' ')
  const [year, month, day] = date.split('-')
  return `${year}年${month}月${day}日 ${time.slice(0, 5)}`
}

function formatMoney(value) {
  return Number(value || 0).toFixed(2)
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
        rateText: `工作时 ¥${formatMoney(room.workdayWorkHourRate)}/小时`,
        offRateText: `非工作时 ¥${formatMoney(room.workdayOffHourRate)}/小时`,
        holidayRateText: `节假日 ¥${formatMoney(room.holidayRate)}/小时`,
        selected: this.data.form.meetingRoomId === room.id
      }))
      const bookings = (data.bookings || []).map(item => ({
        ...item,
        timeText: `${toDateTimeText(item.startTime)} 至 ${toDateTimeText(item.endTime)}`,
        amountText: formatMoney(item.calculatedAmount),
        statusText: this.toStatusText(item.status),
        cancellable: item.status === 'BOOKED' || item.status === 'CONFIRMED'
      }))
      this.setData({
        profile: data.profile || {},
        identityText: this.toIdentityText(data.profile),
        rooms,
        bookings
      })
    } catch (error) {
      this.setData({
        errorMessage: error && error.message ? error.message : '请先登录并绑定身份后预约会议室',
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
    this.setData({
      [`form.${field}`]: event.detail.value
    })
  },

  selectRoom(event) {
    const id = Number(event.currentTarget.dataset.id)
    const room = this.data.rooms.find(item => item.id === id)
    if (!room || !room.available) {
      wx.showToast({ title: room && room.unavailableReason ? room.unavailableReason : '该会议室不可预约', icon: 'none' })
      return
    }
    const rooms = this.data.rooms.map(item => ({
      ...item,
      selected: item.id === id
    }))
    this.setData({
      'form.meetingRoomId': id,
      selectedRoom: room,
      rooms
    })
  },

  async submitBooking() {
    if (!this.data.form.meetingRoomId) {
      wx.showToast({ title: '请选择会议室', icon: 'none' })
      return
    }
    if (!this.data.form.purpose.trim()) {
      wx.showToast({ title: '请填写会议用途', icon: 'none' })
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
        title: '预约已提交',
        content: `预约单号：${booking.bookingNumber}\n预计金额：¥${formatMoney(booking.calculatedAmount)}`,
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
      title: '取消预约',
      content: booking ? `确定取消 ${booking.meetingRoomName} 的预约吗？` : '确定取消该预约吗？',
      confirmText: '取消预约',
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
    wx.switchTab({ url: '/pages/mine/index' })
  },

  nextHour(time) {
    const [hour] = time.split(':').map(Number)
    return `${pad(Math.min(hour + 1, 23))}:00`
  },

  toIdentityText(profile) {
    if (!profile) return ''
    if (profile.userType === 'INTERNAL') return `内部员工：${profile.boundSysRealName || profile.boundSysUsername || profile.nickname || ''}`
    if (profile.userType === 'TENANT') return `租户：${profile.boundTenantName || profile.nickname || ''}`
    return '访客'
  },

  toStatusText(status) {
    if (status === 'BOOKED') return '待确认'
    if (status === 'CONFIRMED') return '已确认'
    if (status === 'CANCELLED') return '已取消'
    if (status === 'COMPLETED') return '已完成'
    return status || '未知'
  }
})
