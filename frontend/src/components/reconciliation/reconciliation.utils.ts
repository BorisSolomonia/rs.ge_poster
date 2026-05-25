import { env } from '../../env'

const gelFormatter = new Intl.NumberFormat('en-GB', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatGel(value: number) {
  return `${gelFormatter.format(value)} GEL`
}

export function getDefaultDateRange() {
  const end = new Date()
  const start = new Date()

  if (env.defaultDateRangeMode === 'current-month') {
    start.setDate(1)
  }

  return {
    from: start.toISOString().slice(0, 10),
    to: end.toISOString().slice(0, 10),
  }
}
