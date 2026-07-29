function pad(value) {
  return String(value).padStart(2, '0')
}

function buildHourOptions() {
  return Array.from({ length: 24 }, (_, index) => pad(index))
}

function buildMinuteOptions() {
  return Array.from({ length: 60 }, (_, index) => pad(index))
}

function parseTimeValue(value) {
  if (!value) {
    return { hourIndex: 9, minuteIndex: 0 }
  }

  const [hour, minute] = String(value).split(':').map(Number)
  return {
    hourIndex: Number.isFinite(hour) ? Math.max(0, Math.min(23, hour)) : 9,
    minuteIndex: Number.isFinite(minute) ? Math.max(0, Math.min(59, minute)) : 0
  }
}

Component({
  properties: {
    value: {
      type: String,
      value: ''
    },
    placeholder: {
      type: String,
      value: '选择时间'
    },
    title: {
      type: String,
      value: '选择时间'
    }
  },

  data: {
    visible: false,
    displayValue: '',
    hours: buildHourOptions(),
    minutes: buildMinuteOptions(),
    pickerValue: [9, 0]
  },

  lifetimes: {
    attached() {
      this.syncValue(this.properties.value)
    }
  },

  observers: {
    value: function (value) {
      this.syncValue(value)
    }
  },

  methods: {
    stopPanelTap() {},

    syncValue(value) {
      const next = parseTimeValue(value)
      this.setData({
        displayValue: value || '',
        pickerValue: [next.hourIndex, next.minuteIndex]
      })
    },

    openPanel() {
      const next = parseTimeValue(this.properties.value)
      this.setData({
        visible: true,
        pickerValue: [next.hourIndex, next.minuteIndex]
      })
    },

    closePanel() {
      this.setData({ visible: false })
    },

    onPickerChange(event) {
      this.setData({
        pickerValue: event.detail.value
      })
    },

    confirmTime() {
      const [hourIndex, minuteIndex] = this.data.pickerValue
      const value = `${this.data.hours[hourIndex]}:${this.data.minutes[minuteIndex]}`
      this.triggerEvent('change', { value })
      this.setData({
        visible: false,
        displayValue: value
      })
    }
  }
})
