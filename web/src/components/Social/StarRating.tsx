interface Props {
  rating: number
  max?: number
  interactive?: boolean
  onRate?: (rating: number) => void
  size?: number
}

export function StarRating({ rating, max = 5, interactive, onRate, size = 16 }: Props) {
  return (
    <div className="flex items-center gap-0.5">
      {Array.from({ length: max }, (_, i) => {
        const filled = i < rating
        return (
          <svg
            key={i}
            viewBox="0 0 24 24"
            width={size}
            height={size}
            className={`${interactive ? 'cursor-pointer' : ''} ${filled ? 'text-yellow-400' : 'text-gray-300 dark:text-gray-600'}`}
            fill="currentColor"
            onClick={interactive && onRate ? () => onRate(i + 1) : undefined}
          >
            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
          </svg>
        )
      })}
    </div>
  )
}
