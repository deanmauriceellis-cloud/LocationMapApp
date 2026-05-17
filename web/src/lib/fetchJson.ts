/**
 * Shared fetch wrapper that throws a useful Error on non-2xx responses.
 *
 * Surfaces `body.error` from the JSON payload when present, prefixed with
 * the HTTP status code. Caller-side error handlers can read `err.message`
 * directly for toast/UI rendering. Use `credentials: 'same-origin'` by
 * default to match the rest of the admin tool.
 *
 * S271 — extracted from three identical local implementations in
 * `CollectionTab.tsx`, `TourTree.tsx`, and `WalkCollectionDialog.tsx` to
 * cut copy-paste drift.
 */
export async function fetchJson<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const res = await fetch(input, { credentials: 'same-origin', ...(init ?? {}) })
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body?.error) msg = `${res.status} ${body.error}`
    } catch {
      /* not json — keep status-line fallback */
    }
    throw new Error(msg)
  }
  return (await res.json()) as T
}
