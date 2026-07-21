import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      }
    }
  },
  build: {
    rollupOptions: {
      // Two separate entry points, two separate bundles — the staff console
      // (web/staff/index.html -> src/staff/StaffApp.tsx) is deliberately not a route inside the
      // main student app (see docs/adr/0007-staff-console-via-lti-roles.md).
      input: {
        main: resolve(__dirname, 'index.html'),
        staff: resolve(__dirname, 'staff/index.html'),
      }
    }
  }
})
