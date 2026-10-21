// ESLint flat config: typescript-eslint strict-type-checked + react-hooks (as in apps/mobile).
import js from '@eslint/js';
import { defineConfig, globalIgnores } from 'eslint/config';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default defineConfig(
  globalIgnores(['node_modules/', 'dist/', 'coverage/']),
  js.configs.recommended,
  tseslint.configs.strictTypeChecked,
  reactHooks.configs.flat['recommended-latest'],
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      '@typescript-eslint/no-confusing-void-expression': ['error', { ignoreArrowShorthand: true }],
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/consistent-type-imports': 'error',
    },
  },
  {
    files: ['**/*.test.{ts,tsx}', 'src/test/**'],
    rules: { '@typescript-eslint/no-unsafe-assignment': 'off', '@typescript-eslint/no-non-null-assertion': 'off' },
  },
  {
    files: ['eslint.config.mjs'],
    extends: [tseslint.configs.disableTypeChecked],
  },
);
