/**
 * Helpers for constructing and validating navigation state around skill-detail pages.
 */
export function getSkillSquareSearch() {
  return {
    q: '',
    sort: 'relevance' as const,
    page: 0,
    starredOnly: false,
  }
}

export function normalizeSkillDetailReturnTo(returnTo?: string) {
  return returnTo && returnTo.startsWith('/') ? returnTo : undefined
}

/**
 * Splits a return-to URL string (e.g. "/search?q=foo&page=1") into a clean
 * pathname and a search-params object so TanStack Router can match the route
 * by path and pass the search params through validateSearch separately.
 *
 * Without this, passing the full string to navigate({ to }) causes the query
 * string to become part of the path segment, route matching fails, and the
 * page renders blank.
 */
export function parseReturnToNavigation(returnTo: string): {
  to: string
  search: Record<string, string>
} {
  // returnTo is always a relative path starting with '/', so use a dummy
  // origin to avoid relying on window (works in SSR/test contexts too).
  const url = new URL(returnTo, 'http://localhost')
  const search: Record<string, string> = {}
  url.searchParams.forEach((value, key) => {
    search[key] = value
  })
  return { to: url.pathname, search }
}
