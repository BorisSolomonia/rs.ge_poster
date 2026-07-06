import { env } from '../../env'

const gelFormatter = new Intl.NumberFormat('en-GB', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatGel(value: number) {
  return `${gelFormatter.format(value)} GEL`
}

export function formatLocalDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function getDefaultDateRange() {
  const end = new Date()
  const start = new Date()

  if (env.defaultDateRangeMode === 'current-month') {
    start.setDate(1)
  }

  return {
    from: formatLocalDate(start),
    to: formatLocalDate(end),
  }
}
