import { useEffect, useRef, useState } from 'react'

export function useInView(options?: IntersectionObserverInit) {
  const ref = useRef<HTMLDivElement>(null)
  const [inView, setInView] = useState(false)
  const optionsRef = useRef(options)

  useEffect(() => {
    optionsRef.current = options
  }, [options])

  useEffect(() => {
    const el = ref.current
    if (!el) return
    let rafId = 0
    let observer: IntersectionObserver | undefined

    function markVisible() {
      setInView(true)
      if (observer) {
        observer.disconnect()
        observer = undefined
      }
    }

    // Defer observer creation: IntersectionObserver can fire before layout settles
    // after SPA navigation, leaving visible sections at opacity 0.
    rafId = requestAnimationFrame(() => {
      // If element is already in viewport (e.g. after back/forward nav), mark visible immediately.
      const rect = el!.getBoundingClientRect()
      const viewportHeight = window.innerHeight || document.documentElement.clientHeight
      if (rect.top < viewportHeight * 0.85 && rect.bottom > 0) {
        markVisible()
        return
      }

      observer = new IntersectionObserver(
        ([entry]) => {
          if (entry.isIntersecting) {
            markVisible()
          }
        },
        { threshold: 0.15, ...optionsRef.current },
      )
      observer.observe(el!)
    })

    return () => {
      cancelAnimationFrame(rafId)
      if (observer) observer.disconnect()
    }
  }, [])

  return { ref, inView }
}
