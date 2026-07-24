import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Separate from vite.config.ts (which defines two build entry points, main + staff, that vitest
// doesn't need) but reuses the same React plugin so JSX/TSX transform matches production.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Vitest's default glob matches **/*.spec.ts anywhere in the project, which picks up
    // e2e/accessibility.spec.ts (a Playwright spec, not a Vitest one) — scope to src/ explicitly.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
})
