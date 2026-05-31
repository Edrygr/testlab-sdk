import { useEffect, useRef } from 'react'
import type { NavigationContainerRef, ParamListBase } from '@react-navigation/native'
import { TestLabSDK } from '../TestLabSDK'

/**
 * Auto-tracks screen views via React Navigation v6+.
 *
 * Usage:
 *   const navigationRef = useNavigationContainerRef()
 *   useScreenTracker(navigationRef)
 *   <NavigationContainer ref={navigationRef}>...</NavigationContainer>
 */
export function useScreenTracker(
  navigationRef: React.RefObject<NavigationContainerRef<ParamListBase>>
): void {
  const currentRouteRef = useRef<string | undefined>(undefined)

  useEffect(() => {
    const nav = navigationRef.current
    if (!nav) return

    const unsubscribe = nav.addListener('state', () => {
      const route = nav.getCurrentRoute()
      if (route && route.name !== currentRouteRef.current) {
        currentRouteRef.current = route.name
        TestLabSDK.trackScreen(route.name)
      }
    })

    // Track initial screen
    const initialRoute = nav.getCurrentRoute()
    if (initialRoute?.name) {
      currentRouteRef.current = initialRoute.name
      TestLabSDK.trackScreen(initialRoute.name)
    }

    return unsubscribe
  }, [navigationRef])
}
