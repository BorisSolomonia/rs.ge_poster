interface ChoiceOption {
  label: string
  info?: string
  onSelect: () => void
  danger?: boolean
}

interface Props {
  open: boolean
  title: string
  message: string
  options: ChoiceOption[]
  cancelLabel: string
  onCancel: () => void
  busy?: boolean
}

/**
 * Centered modal presenting two-or-more mutually exclusive actions, each with an
 * explanatory line. Used for the cash-flow cascade-vs-single category prompt.
 */
export default function ChoiceDialog({ open, title, message, options, cancelLabel, onCancel, busy }: Props) {
  if (!open) {
    return null
  }
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl">
        <h3 className="text-base font-black text-slate-950">{title}</h3>
        <p className="mt-1 text-sm text-slate-600">{message}</p>
        <div className="mt-5 space-y-2">
          {options.map((option) => (
            <button
              key={option.label}
              type="button"
              disabled={busy}
              onClick={option.onSelect}
              className={`w-full rounded-lg border px-4 py-3 text-left transition disabled:cursor-wait disabled:opacity-60 ${
                option.danger
                  ? 'border-red-200 hover:border-red-400 hover:bg-red-50'
                  : 'border-slate-200 hover:border-cyan-400 hover:bg-cyan-50'
              }`}
            >
              <span className="block text-sm font-black text-slate-950">{option.label}</span>
              {option.info ? <span className="mt-0.5 block text-xs text-slate-500">{option.info}</span> : null}
            </button>
          ))}
        </div>
        <div className="mt-5 flex justify-end">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
          >
            {cancelLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
