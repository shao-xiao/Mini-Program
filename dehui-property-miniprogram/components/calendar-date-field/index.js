function pad(value) {
  return String(value).padStart(2, '0')
}

function toDateValue(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function toDisplayValue(value) {
  if (!value) return ''
  return String(value).replace(/-/g, '/')
}

function parseDate(value) {
  if (!value) return null
  const [year, month, day] = String(value).split('-').map(Number)
  if (!year || !month || !day) return null
  return new Date(year, month - 1, day)
}

function shiftMonth(year, month, offset) {
  const next = new Date(year, month - 1 + offset, 1)
  return {
    year: next.getFullYear(),
    month: next.getMonth() + 1
  }
}

function buildCalendarDays(year, month, selectedValue, minDate, todayValue) {
  const firstDay = new Date(year, month - 1, 1)
  const weekOffset = (firstDay.getDay() + 6) % 7
  const startDate = new Date(year, month - 1, 1 - weekOffset)
  const days = []

  for (let index = 0; index < 42; index += 1) {
    const date = new Date(startDate)
    date.setDate(startDate.getDate() + index)
    const dateValue = toDateValue(date)
    days.push({
      key: `${dateValue}-${index}`,
      text: date.getDate(),
      dateValue,
      currentMonth: date.getMonth() + 1 === month,
      selected: dateValue === selectedValue,
      disabled: Boolean(minDate && dateValue < minDate),
      today: dateValue === todayValue
    })
  }

  return days
}

Component({
  properties: {
    value: {
      type: String,
      value: ''
    },
    minDate: {
      type: String,
      value: ''
    },
    placeholder: {
      type: String,
      value: '选择日期'
    }
  },

  data: {
    visible: false,
    panelYear: 0,
    panelMonth: 0,
    headerText: '',
    displayValue: '',
    todayValue: toDateValue(new Date()),
    weekDays: ['一', '二', '三', '四', '五', '六', '日'],
    days: []
  },

  lifetimes: {
    attached() {
      this.syncCalendar(this.properties.value)
    }
  },

  observers: {
    'value,minDate': function (value) {
      this.syncCalendar(value)
    }
  },

  methods: {
    stopPanelTap() {},

    syncCalendar(value) {
      const baseDate = parseDate(value) || parseDate(this.properties.minDate) || new Date()
      this.setData({
        panelYear: baseDate.getFullYear(),
        panelMonth: baseDate.getMonth() + 1,
        displayValue: value ? toDisplayValue(value) : ''
      })
      this.renderCalendar()
    },

    renderCalendar() {
      const { panelYear, panelMonth, todayValue } = this.data
      this.setData({
        headerText: `${panelYear}年 ${panelMonth}月`,
        days: buildCalendarDays(
          panelYear,
          panelMonth,
          this.properties.value,
          this.properties.minDate,
          todayValue
        )
      })
    },

    openPanel() {
      const baseDate = parseDate(this.properties.value) || parseDate(this.properties.minDate) || new Date()
      this.setData({
        visible: true,
        panelYear: baseDate.getFullYear(),
        panelMonth: baseDate.getMonth() + 1
      })
      this.renderCalendar()
    },

    closePanel() {
      this.setData({ visible: false })
    },

    prevMonth() {
      const next = shiftMonth(this.data.panelYear, this.data.panelMonth, -1)
      this.setData({
        panelYear: next.year,
        panelMonth: next.month
      })
      this.renderCalendar()
    },

    nextMonth() {
      const next = shiftMonth(this.data.panelYear, this.data.panelMonth, 1)
      this.setData({
        panelYear: next.year,
        panelMonth: next.month
      })
      this.renderCalendar()
    },

    selectDay(event) {
      const { value, disabled } = event.currentTarget.dataset
      if (disabled) return
      this.triggerEvent('change', { value })
      this.setData({
        visible: false,
        displayValue: toDisplayValue(value)
      })
    }
  }
})
