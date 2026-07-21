import js from '@eslint/js'
import globals from 'globals'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
      jsxA11y.flatConfigs.recommended,
    ],
    languageOptions: {
      globals: globals.browser,
    },
    rules: {
      // Flags this codebase's standard "fetch on mount via an async helper" pattern
      // (useEffect(() => { fetchThing() }, [])) as unsafe purely because fetchThing eventually
      // calls setState — that's the React-docs-recommended shape for effect-driven data fetching,
      // not a bug. Downgraded rather than restructuring every data-fetching effect in the app to
      // satisfy a very new, opinionated rule; revisit if a future eslint-plugin-react-hooks
      // release narrows what it flags.
      'react-hooks/set-state-in-effect': 'off',
    },
  },
])
