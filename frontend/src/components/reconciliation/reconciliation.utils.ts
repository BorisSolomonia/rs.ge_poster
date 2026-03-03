import { env } from '../../env'

export function formatGel(value: number) {
  return `${value.toFixed(2)} GEL`
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
