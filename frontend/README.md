# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

You can also install [eslint-plugin-react-x](https://npmx.dev/package/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://npmx.dev/package/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```
# Radar frontend

React 19 + TypeScript + Vite client for the Spring Boot API.

## Run locally

1. Start PostgreSQL and the backend as described in the repository README.
2. From this directory, optionally copy `.env.example` to `.env.local` and set `VITE_API_URL` to the backend API root (including `/api`). The default is `http://localhost:8080/api`.
3. Run `npm install` once, then `npm run dev`.
4. Open the Vite URL, usually `http://localhost:5173`. New registrations are employee accounts; an administrator can promote accounts through **Administration → Users**.

The API uses JWT authentication. The frontend stores the returned token in browser local storage, sends it as a Bearer token, and clears the session after an unauthorized response. The backend remains responsible for authorization.

## Available workflows

- Employee incident reporting, duplicate suggestions, calculated priority preview, incident tracking, comments, and notifications.
- Technician incident triage, legal lifecycle transitions, assignment/recommendations, resolution details, comments, SLA visibility, and workload.
- Administrator incident monitoring, role management, technician skill editing, workload, and runtime priority/assignment strategy selection.

The backend currently exposes no Additional Information Request workflow API, no aggregate reporting endpoint, and no SLA policy management endpoint. The interface identifies these limits; it does not fabricate responses or statistics.
