import { AppState, AppStateStatus, Platform } from 'react-native'
import AsyncStorage from '@react-native-async-storage/async-storage'
import NetInfo from '@react-native-community/netinfo'
import type {
  TestLabConfig,
  TesterSession,
  ScreenView,
  TesterEvent,
  SDKStatus,
  DevicePayload,
} from './types'

const STORAGE_KEY_DAY = 'testlab_day'
const STORAGE_KEY_QUEUE = 'testlab_event_queue'
const MAX_QUEUE_SIZE = 500

// ─── Predefined event names ───────────────────────────────────────────────────

export const Events = {
  SESSION_START: 'session_start',
  SESSION_END: 'session_end',
  SCREEN_VIEW: 'screen_view',
  LAUNCHED_FROM_TESTLAB: 'launched_from_testlab',
  SDK_ACTIVATED: 'sdk_activated',
  SDK_DEACTIVATED: 'sdk_deactivated',
} as const

// ─── SDK Class ────────────────────────────────────────────────────────────────

class TestLabSDKClass {
  private config: TestLabConfig | null = null
  private _isActive = false
  private _testerId: string | null = null
  private currentSession: TesterSession | null = null
  private screenHistory: ScreenView[] = []
  private currentScreen: ScreenView | null = null
  private eventQueue: TesterEvent[] = []
  private syncTimer: ReturnType<typeof setInterval> | null = null
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private appStateSubscription: { remove: () => void } | null = null

  get isActive(): boolean { return this._isActive }
  get testerId(): string | null { return this._testerId }

  // ─── Public API ─────────────────────────────────────────────────────────────

  async init(config: TestLabConfig): Promise<void> {
    this.config = {
      syncInterval: 60_000,
      debug: false,
      baseUrl: 'https://api.testlab.app',
      ...config,
    }

    const status = await this.fetchStatus()
    if (!status?.active) return

    this._isActive = true
    if (status.testerId) this._testerId = status.testerId

    this.track(Events.SDK_ACTIVATED)
    this.startAppStateListener()
    this.startSyncInterval()
    this.log('SDK activated')
  }

  async identify(testerId: string): Promise<void> {
    if (!this._isActive) return
    this._testerId = testerId
    if (this.currentSession) {
      this.currentSession = { ...this.currentSession, testerId }
    }
    this.log(`Tester identified: ${testerId}`)
  }

  trackScreen(screenName: string): void {
    if (!this._isActive) return
    const now = Date.now()

    if (this.currentScreen) {
      this.screenHistory.push({ ...this.currentScreen, exitTime: now, durationSeconds: (now - this.currentScreen.entryTime) / 1000 })
    }

    this.currentScreen = {
      name: screenName,
      entryTime: now,
      durationSeconds: 0,
    }
    this.track(Events.SCREEN_VIEW, { screen: screenName })
    this.log(`Screen: ${screenName}`)
  }

  trackEvent(eventName: string, properties: Record<string, unknown> = {}): void {
    if (!this._isActive) return
    this.track(eventName, properties)
  }

  launchedFromTestLab(): void {
    if (!this._isActive) return
    if (this.currentSession) {
      this.currentSession = { ...this.currentSession, launchedFromTestLab: true }
    }
    this.track(Events.LAUNCHED_FROM_TESTLAB)
  }

  shutdown(): void {
    this._isActive = false
    this.track(Events.SDK_DEACTIVATED)
    this.stopTimers()
    this.appStateSubscription?.remove()
    this.log('SDK shut down')
  }

  // ─── Session management ──────────────────────────────────────────────────────

  private async startSession(): Promise<void> {
    const sessionId = `sess_${uuid()}`
    const dayNumber = await this.incrementDayNumber()
    this.currentSession = {
      id: sessionId,
      testerId: this._testerId,
      startTime: Date.now(),
      launchedFromTestLab: false,
      dayNumber,
    }
    this.screenHistory = []
    this.currentScreen = null
    this.track(Events.SESSION_START)

    if (await this.isOnline()) {
      const device = getDevicePayload()
      const sessionPayload = {
        id: sessionId,
        startTime: toIso8601(this.currentSession.startTime),
        launchedFromTestLab: false,
        dayNumber,
      }
      await this.apiPost('/sdk/session', {
        apiKey: this.config!.apiKey,
        testerId: this._testerId,
        sessionData: sessionPayload,
        deviceData: device,
      })
    }

    this.startHeartbeat()
  }

  private async endSession(): Promise<void> {
    const session = this.currentSession
    if (!session) return

    this.stopHeartbeat()
    const endTime = Date.now()
    const durationSeconds = Math.round((endTime - session.startTime) / 1000)

    if (this.currentScreen) {
      this.screenHistory.push({
        ...this.currentScreen,
        exitTime: endTime,
        durationSeconds: (endTime - this.currentScreen.entryTime) / 1000,
      })
      this.currentScreen = null
    }

    this.track(Events.SESSION_END, { durationSeconds })

    const screens = this.screenHistory.map(s => ({
      name: s.name,
      durationSeconds: s.durationSeconds,
      timestamp: toIso8601(s.entryTime),
    }))
    const events = this.eventQueue.map(e => ({
      name: e.name,
      properties: e.properties,
      timestamp: e.timestamp,
    }))
    this.eventQueue = []
    this.currentSession = null

    if (await this.isOnline()) {
      await this.apiPatch(`/sdk/session/${session.id}`, {
        endTime,
        duration: durationSeconds,
        screens,
        events,
      })
      await this.flushOfflineQueue()
    } else {
      await this.persistOffline({ session, endTime, durationSeconds, screens, events })
    }
  }

  // ─── Event queue ─────────────────────────────────────────────────────────────

  private track(name: string, properties: Record<string, unknown> = {}): void {
    const session = this.currentSession
    if (!session || this.eventQueue.length >= MAX_QUEUE_SIZE) return
    this.eventQueue.push({
      sessionId: session.id,
      name,
      properties,
      timestamp: Date.now(),
    })
  }

  private async flushEventQueue(): Promise<void> {
    if (this.eventQueue.length === 0 || !(await this.isOnline())) return
    const batch = [...this.eventQueue]
    this.eventQueue = []
    const ok = await this.apiPost('/sdk/events/batch', {
      apiKey: this.config!.apiKey,
      testerId: this._testerId,
      events: batch.map(e => ({ name: e.name, properties: e.properties, timestamp: e.timestamp })),
    })
    if (!ok) this.eventQueue.unshift(...batch)
  }

  private async flushOfflineQueue(): Promise<void> {
    const raw = await AsyncStorage.getItem(STORAGE_KEY_QUEUE)
    if (!raw) return
    const queue: unknown[] = JSON.parse(raw)
    if (queue.length === 0) return

    await this.apiPost('/sdk/events/batch', {
      apiKey: this.config!.apiKey,
      testerId: this._testerId,
      events: queue,
    })
    await AsyncStorage.removeItem(STORAGE_KEY_QUEUE)
  }

  private async persistOffline(data: {
    session: TesterSession
    endTime: number
    durationSeconds: number
    screens: object[]
    events: object[]
  }): Promise<void> {
    const raw = await AsyncStorage.getItem(STORAGE_KEY_QUEUE)
    const queue: unknown[] = raw ? JSON.parse(raw) : []
    queue.push(data)
    await AsyncStorage.setItem(STORAGE_KEY_QUEUE, JSON.stringify(queue))
  }

  // ─── App state / lifecycle ───────────────────────────────────────────────────

  private startAppStateListener(): void {
    let previousState: AppStateStatus = AppState.currentState

    this.appStateSubscription = AppState.addEventListener('change', (nextState) => {
      if (previousState.match(/inactive|background/) && nextState === 'active') {
        this.startSession()
      } else if (previousState === 'active' && nextState.match(/inactive|background/)) {
        this.endSession()
      }
      previousState = nextState
    })

    if (AppState.currentState === 'active') {
      this.startSession()
    }
  }

  // ─── Timers ──────────────────────────────────────────────────────────────────

  private startSyncInterval(): void {
    const interval = this.config!.syncInterval!
    this.syncTimer = setInterval(() => {
      this.flushEventQueue()
      this.checkRemoteStatus()
    }, interval)
  }

  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(async () => {
      const session = this.currentSession
      if (!session || !(await this.isOnline())) return
      await this.apiPost('/sdk/heartbeat', {
        apiKey: this.config!.apiKey,
        testerId: this._testerId,
        sessionId: session.id,
      })
    }, 5 * 60_000)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private stopTimers(): void {
    this.stopHeartbeat()
    if (this.syncTimer) {
      clearInterval(this.syncTimer)
      this.syncTimer = null
    }
  }

  // ─── Remote status check ─────────────────────────────────────────────────────

  private async checkRemoteStatus(): Promise<void> {
    const status = await this.fetchStatus()
    if (!status) return
    if (!status.active && this._isActive) this.shutdown()
  }

  private async fetchStatus(): Promise<SDKStatus | null> {
    const cfg = this.config
    if (!cfg) return null
    return this.apiPost<SDKStatus>('/sdk/status', { apiKey: cfg.apiKey, appId: cfg.appId })
  }

  // ─── Day counter ─────────────────────────────────────────────────────────────

  private async incrementDayNumber(): Promise<number> {
    const today = new Date().toISOString().slice(0, 10)
    const raw = await AsyncStorage.getItem(STORAGE_KEY_DAY)
    const stored: { lastDate: string; count: number } = raw
      ? JSON.parse(raw)
      : { lastDate: '', count: 0 }

    const newCount = stored.lastDate === today ? stored.count : stored.count + 1
    await AsyncStorage.setItem(STORAGE_KEY_DAY, JSON.stringify({ lastDate: today, count: newCount }))
    return newCount
  }

  // ─── HTTP ─────────────────────────────────────────────────────────────────────

  private async apiPost<T = boolean>(path: string, body: object): Promise<T | null> {
    return this.request<T>('POST', path, body)
  }

  private async apiPatch<T = boolean>(path: string, body: object): Promise<T | null> {
    return this.request<T>('PATCH', path, body)
  }

  private async request<T>(
    method: string,
    path: string,
    body: object,
    attempt = 0
  ): Promise<T | null> {
    const cfg = this.config
    if (!cfg) return null
    const url = `${cfg.baseUrl}${path}`
    try {
      const controller = new AbortController()
      const timeout = setTimeout(() => controller.abort(), 10_000)
      const res = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: controller.signal,
      })
      clearTimeout(timeout)
      if (res.ok) return res.json() as Promise<T>
    } catch {
      if (attempt < 3) {
        await sleep(1000 * 2 ** attempt)
        return this.request<T>(method, path, body, attempt + 1)
      }
    }
    return null
  }

  // ─── Network ──────────────────────────────────────────────────────────────────

  private async isOnline(): Promise<boolean> {
    const state = await NetInfo.fetch()
    return state.isConnected === true
  }

  private log(msg: string): void {
    if (this.config?.debug) console.log(`[TestLabSDK] ${msg}`)
  }
}

// ─── Utilities ────────────────────────────────────────────────────────────────

function uuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function toIso8601(ms: number): string {
  return new Date(ms).toISOString()
}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function getDevicePayload(): DevicePayload {
  const { NativeModules } = require('react-native')
  return {
    platform: Platform.OS,
    osVersion: Platform.Version.toString(),
    appVersion: NativeModules?.RNDeviceInfo?.appVersion ?? 'unknown',
    locale: NativeModules?.SettingsManager?.settings?.AppleLocale
      ?? NativeModules?.I18nManager?.localeIdentifier
      ?? 'unknown',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    networkType: 'unknown',
  }
}

export const TestLabSDK = new TestLabSDKClass()
