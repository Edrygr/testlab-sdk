export interface TestLabConfig {
  apiKey: string
  appId: string
  /** Milliseconds between background syncs. Default: 60000 */
  syncInterval?: number
  debug?: boolean
  baseUrl?: string
}

export interface TesterSession {
  id: string
  testerId: string | null
  startTime: number
  endTime?: number
  durationSeconds?: number
  launchedFromTestLab: boolean
  dayNumber: number
}

export interface ScreenView {
  name: string
  entryTime: number
  exitTime?: number
  readonly durationSeconds: number
}

export interface TesterEvent {
  sessionId: string
  name: string
  properties: Record<string, unknown>
  timestamp: number
}

export interface SDKStatus {
  active: boolean
  testerId?: string
  status: 'active' | 'inactive' | 'suspended' | 'expired'
}

export interface DevicePayload {
  platform: string
  osVersion: string
  appVersion: string
  locale: string
  timezone: string
  networkType: string
}
