import { useCallback, useEffect, useRef, useState } from 'react'

interface AsyncState<T> {
  data: T | null
  error: unknown
  loading: boolean
}

export interface AsyncData<T> extends AsyncState<T> {
  reload: (options?: { clear?: boolean }) => void
}

export function useAsyncData<T>(loader: (signal: AbortSignal) => Promise<T>): AsyncData<T> {
  const [state, setState] = useState<AsyncState<T>>({ data: null, error: null, loading: true })
  const [tick, setTick] = useState(0)
  const clearRef = useRef(false)
  const loaderRef = useRef(loader)

  useEffect(() => {
    const controller = new AbortController()
    const clear = clearRef.current || loaderRef.current !== loader
    clearRef.current = false
    loaderRef.current = loader
    setState((previous) => ({ data: clear ? null : previous.data, error: null, loading: true }))
    loader(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setState({ data, error: null, loading: false })
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setState({ data: null, error, loading: false })
      },
    )
    return () => controller.abort()
  }, [loader, tick])

  const reload = useCallback((options?: { clear?: boolean }) => {
    if (options?.clear) clearRef.current = true
    setTick((value) => value + 1)
  }, [])

  return { ...state, reload }
}
