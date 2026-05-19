const api = require('../../utils/request')

function initialLeadForm() {
  return {
    name: '',
    phone: '',
    companyName: '',
    desiredArea: '',
    intendedUse: '',
    preferredVisitTime: '',
    roomId: null,
    remark: ''
  }
}

function roomTitle(room) {
  const building = room.buildingName || ''
  const floor = room.floorName || (room.floorNumber ? `${room.floorNumber}层` : '')
  return [building, floor, room.roomNumber].filter(Boolean).join(' / ')
}

Page({
  data: {
    loading: false,
    roomsLoading: false,
    submitting: false,
    errorMessage: '',
    overview: null,
    rooms: [],
    selectedRoom: null,
    leadForm: initialLeadForm()
  },

  onShow() {
    this.loadInvestment()
  },

  async loadInvestment() {
    this.setData({ loading: true, roomsLoading: true, errorMessage: '' })
    const [overviewResult, roomsResult] = await Promise.allSettled([
      api.get('/mobile/investment/overview'),
      api.get('/mobile/investment/rooms')
    ])

    if (overviewResult.status === 'fulfilled') {
      this.setData({ overview: overviewResult.value })
    } else {
      this.setData({
        overview: null,
        errorMessage: overviewResult.reason && overviewResult.reason.message
          ? overviewResult.reason.message
          : '招商信息加载失败'
      })
    }

    if (roomsResult.status === 'fulfilled') {
      const rooms = (roomsResult.value || []).map(room => ({
        ...room,
        title: roomTitle(room),
        areaText: room.area ? `${room.area}㎡` : '面积待确认',
        typeText: room.roomType || '房源'
      }))
      this.setData({ rooms })
    } else {
      this.setData({ rooms: [] })
    }

    this.setData({ loading: false, roomsLoading: false })
  },

  onLeadInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`leadForm.${field}`]: event.detail.value
    })
  },

  selectRoom(event) {
    const id = Number(event.currentTarget.dataset.id)
    const selectedRoom = this.data.rooms.find(item => item.id === id) || null
    this.setData({
      selectedRoom,
      'leadForm.roomId': selectedRoom ? selectedRoom.id : null
    })
  },

  clearRoom() {
    this.setData({
      selectedRoom: null,
      'leadForm.roomId': null
    })
  },

  async submitLead() {
    const form = this.data.leadForm
    if (!form.name.trim()) {
      wx.showToast({ title: '请填写联系人', icon: 'none' })
      return
    }
    if (!form.phone.trim()) {
      wx.showToast({ title: '请填写联系电话', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      const result = await api.post('/mobile/investment/leads', {
        ...form,
        desiredArea: form.desiredArea ? Number(form.desiredArea) : null,
        roomId: form.roomId || null
      })
      wx.showModal({
        title: '已提交预约',
        content: result && result.id ? `线索ID：${result.id}\n${result.message || '招商顾问会尽快与您联系'}` : '招商顾问会尽快与您联系',
        showCancel: false
      })
      this.setData({
        leadForm: initialLeadForm(),
        selectedRoom: null
      })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
