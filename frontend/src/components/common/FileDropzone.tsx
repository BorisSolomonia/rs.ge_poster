import React, { useRef, useState } from 'react'
import { Upload, X, FileCheck } from 'lucide-react'
import { env } from '../../env'

interface Props {
  label: string
  accept: string
  file: File | null
  onChange: (file: File | null) => void
}

export default function FileDropzone({ label, accept, file, onChange }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setDragging(false)
    const dropped = e.dataTransfer.files[0]
    if (dropped) onChange(dropped)
  }

  return (
    <div
      className={`relative border-2 border-dashed rounded-xl p-6 text-center transition-colors cursor-pointer
        ${dragging ? 'border-blue-400 bg-blue-50' : 'border-gray-300 hover:border-blue-400 hover:bg-gray-50'}
        ${file ? 'border-green-400 bg-green-50' : ''}`}
      onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
      onDragLeave={() => setDragging(false)}
      onDrop={handleDrop}
      onClick={() => inputRef.current?.click()}
    >
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="hidden"
        onChange={(e) => onChange(e.target.files?.[0] ?? null)}
      />

      {file ? (
        <div className="flex flex-col items-center gap-2">
          <FileCheck className="w-8 h-8 text-green-600" />
          <p className="text-sm font-medium text-green-800 break-all">{file.name}</p>
          <p className="text-xs text-green-600">{(file.size / 1024).toFixed(1)} KB</p>
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); onChange(null) }}
            className="mt-1 flex items-center gap-1 text-xs text-gray-500 hover:text-red-600"
          >
            <X className="w-3 h-3" /> {env.fileDropzoneRemoveLabel}
          </button>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-2">
          <Upload className="w-8 h-8 text-gray-400" />
          <p className="text-sm font-medium text-gray-700">{label}</p>
          <p className="text-xs text-gray-400">{env.fileDropzoneHint}</p>
          <p className="text-xs text-gray-400">{accept}</p>
        </div>
      )}
    </div>
  )
}
