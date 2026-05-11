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

Page({
  data: {
    loading: false,
    submitting: false,
    overview: null,
    rooms: [],
    selectedRoom: null,
    leadForm: initialLeadForm()
  },

  onShow() {
    this.loadInvestment()
  },

  async loadInvestment() {
    this.setData({ loading: true })
    try {
      const [overview, rooms] = await Promise.all([
        api.get('/mobile/investment/overview'),
        api.get('/mobile/investment/rooms')
      ])
      this.setData({
        overview,
        rooms: (rooms || []).map(item => ({
          ...item,
          floorText: item.floorName || (item.floorNumber ? `${item.floorNumber}层` : ''),
          areaText: item.area ? `${item.area}㎡` : '-'
        }))
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  selectRoom(event) {
    const id = Number(event.currentTarget.dataset.id)
    const selectedRoom = this.data.rooms.find(item => item.id === id) || null
    this.setData({
      selectedRoom,
      'leadForm.roomId': selectedRoom ? selectedRoom.id : null,
      'leadForm.desiredArea': selectedRoom && selectedRoom.area ? String(selectedRoom.area) : this.data.leadForm.desiredArea,
      'leadForm.remark': selectedRoom ? `意向房源：${selectedRoom.roomNumber}` : this.data.leadForm.remark
    })
  },

  clearSelectedRoom() {
    this.setData({
      selectedRoom: null,
      'leadForm.roomId': null
    })
  },

  onLeadInput(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`leadForm.${field}`]: event.detail.value
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
        desiredArea: form.desiredArea ? Number(form.desiredArea) : null
      })
      wx.showModal({
        title: '已提交预约',
        content: result && result.message ? result.message : '招商顾问会尽快与您联系',
        showCancel: false
      })
      this.setData({
        selectedRoom: null,
        leadForm: initialLeadForm()
      })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
