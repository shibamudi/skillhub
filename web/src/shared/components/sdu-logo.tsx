interface SDULogoProps {
  className?: string
  height?: number
}

export function SDULogo({ className, height = 28 }: SDULogoProps) {
  const width = height * (91 / 28)
  return (
    <img
      src="/sdu-logo.svg"
      alt="Shandong University"
      width={width}
      height={height}
      className={className}
    />
  )
}
