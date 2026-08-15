import {
  File,
  FileArchive,
  FileAudio,
  FileCode,
  FileSpreadsheet,
  FileText,
  FileVideo,
  Image as ImageIcon,
  Presentation,
} from 'lucide-react'
import { fileCategory, fileTypeColor, type FileCategory } from '../../lib/format'
import { cn } from '../../lib/cn'

const icons: Record<FileCategory, typeof File> = {
  image: ImageIcon,
  video: FileVideo,
  audio: FileAudio,
  document: FileText,
  spreadsheet: FileSpreadsheet,
  presentation: Presentation,
  archive: FileArchive,
  code: FileCode,
  pdf: FileText,
  other: File,
}

export function FileIcon({ category, className }: { category: FileCategory; className?: string }) {
  const Icon = icons[category]
  return <Icon aria-hidden className={className} />
}

const tileSizes = {
  sm: 'size-8 rounded-lg [&>svg]:size-4',
  md: 'size-9 rounded-lg [&>svg]:size-[18px]',
  lg: 'size-12 rounded-xl [&>svg]:size-6',
} as const

/**
 * The file-type tile used in every object listing.
 *
 * Category and tint are derived in one place so the same file never gets a
 * different colour on the drive than it does in trash.
 */
export function FileTile({
  contentType,
  objectKey,
  size = 'md',
  className,
}: {
  contentType: string | undefined
  objectKey: string
  size?: keyof typeof tileSizes
  className?: string
}) {
  const category = fileCategory(contentType, objectKey)
  const colors = fileTypeColor(category)

  return (
    <span
      aria-hidden
      className={cn('grid shrink-0 place-items-center', tileSizes[size], colors.bg, className)}
    >
      <FileIcon category={category} className={colors.text} />
    </span>
  )
}
