import { useState, useEffect, useCallback } from 'react'
import { TestLabSDK } from '../TestLabSDK'

export function useTestLab() {
  const [isActive, setIsActive] = useState(TestLabSDK.isActive)
  const [testerId, setTesterId] = useState<string | null>(TestLabSDK.testerId)

  useEffect(() => {
    setIsActive(TestLabSDK.isActive)
    setTesterId(TestLabSDK.testerId)
  }, [])

  const trackEvent = useCallback(
    (name: string, properties?: Record<string, unknown>) =>
      TestLabSDK.trackEvent(name, properties),
    []
  )

  const trackScreen = useCallback(
    (name: string) => TestLabSDK.trackScreen(name),
    []
  )

  return { isActive, testerId, trackScreen, trackEvent }
}
