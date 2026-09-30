import { mergeConfig, defineConfig } from 'vitest/config'
import base from './vite.config.ts'

export default mergeConfig(base({ mode: 'test', command: 'serve' }), defineConfig({
  test: { environment: 'jsdom', include: ['src/**/*.test.ts'], restoreMocks: true, clearMocks: true },
}))
