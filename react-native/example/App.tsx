import React, { useEffect } from 'react'
import { Button, StyleSheet, Text, View } from 'react-native'
import { NavigationContainer, useNavigationContainerRef } from '@react-navigation/native'
import { createNativeStackNavigator } from '@react-navigation/native-stack'
import { TestLabSDK, useTestLab, useScreenTracker } from 'testlab-sdk-rn'

const Stack = createNativeStackNavigator()

// Initialize SDK once at app startup
TestLabSDK.init({
  apiKey: 'tl_live_xxxxxxxxxxxx',
  appId: 'app.testlab.example',
  debug: __DEV__,
}).catch(console.error)

// ─── Screens ──────────────────────────────────────────────────────────────────

function HomeScreen({ navigation }: any) {
  const { trackEvent } = useTestLab()

  return (
    <View style={styles.screen}>
      <Text style={styles.title}>TestLab SDK Example</Text>
      <Button
        title="Track Button Tap"
        onPress={() => trackEvent('button_tapped', { id: 'btn_home' })}
      />
      <Button
        title="Go to Profile"
        onPress={() => navigation.navigate('Profile')}
      />
    </View>
  )
}

function ProfileScreen() {
  const { trackEvent } = useTestLab()

  useEffect(() => {
    trackEvent('profile_viewed')
  }, [trackEvent])

  return (
    <View style={styles.screen}>
      <Text style={styles.title}>Profile</Text>
    </View>
  )
}

// ─── Root ─────────────────────────────────────────────────────────────────────

export default function App() {
  const navigationRef = useNavigationContainerRef()

  // Auto-track screen views from React Navigation
  useScreenTracker(navigationRef)

  return (
    <NavigationContainer ref={navigationRef}>
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="Profile" component={ProfileScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  )
}

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 16 },
  title: { fontSize: 20, fontWeight: 'bold' },
})
