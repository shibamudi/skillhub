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
  const url = new URL(returnTo, 'http://localhost')
  let pathname = url.pathname
  // When deployed behind a reverse proxy (e.g. Traefik with PathPrefix),
  // window.location.pathname includes the base path (e.g. "/skillhub/search")
  // but navigate({ to }) expects a route path without it.
  const basePath = import.meta.env.VITE_BASE_PATH
  if (basePath && basePath !== '/' && pathname.startsWith(basePath)) {
    pathname = pathname.slice(basePath.length) || '/'
  }
  const search: Record<string, string> = {}
  url.searchParams.forEach((value, key) => {
    // TanStack Router's default stringifySearch JSON-stringifies values that
    // parse as JSON. URLSearchParams always returns strings, so '0' would
    // become '"0"' in the URL. Parse back to native types to avoid this.
    try {
      search[key] = JSON.parse(value)
    } catch {
      search[key] = value
    }
  })
  return { to: pathname, search }
}
