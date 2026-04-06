# Dark Theme Design

**Date:** 2026-04-06  
**Scope:** `data-agent-frontend`

## Goal

Add dark theme support to the frontend. Default white theme is preserved. Dark theme can be triggered manually (toggle button) or automatically (system preference). User's manual choice persists via `localStorage`.

## Approach

Use `data-theme` attribute on `<html>`. CSS variables are overridden under `[data-theme="dark"]`. Manual toggle and `prefers-color-scheme` both activate the same variable overrides.

## Files Changed

| File | Change |
|------|--------|
| `src/styles/global.css` | Add `[data-theme="dark"]` variable overrides; add dark overrides for hardcoded-color classes in the same file |
| `src/App.vue` | Add theme initialization logic and expose `toggleTheme()` via `provide` |
| `src/layouts/BaseLayout.vue` | Add theme toggle button in header; replace hardcoded colors with CSS variables; inject `toggleTheme` via `inject` |

## CSS Layer

Dark variables are declared under **both** selectors so manual toggle and system preference are independent:

```css
[data-theme="dark"] {
  --text-primary: #e8e8e8;
  --text-secondary: #b3b3b3;
  --text-tertiary: #808080;
  --text-quaternary: #4d4d4d;
  --text-disabled: #383838;
  --bg-primary: #1a1a1a;
  --bg-secondary: #242424;
  --bg-tertiary: #2d2d2d;
  --bg-layout: #141414;
  --border-primary: #383838;
  --border-secondary: #2d2d2d;
  --border-tertiary: #242424;
  --border-color: #383838;
  --shadow-xs: 0 1px 2px rgba(0,0,0,0.2);
  --shadow-sm: 0 2px 4px rgba(0,0,0,0.3);
  --shadow-base: 0 4px 8px rgba(0,0,0,0.4);
  --shadow-md: 0 6px 16px rgba(0,0,0,0.5);
  --shadow-lg: 0 8px 24px rgba(0,0,0,0.6);
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    /* same variables */
  }
}
```

Also add dark overrides for `global.css` classes that use hardcoded colors:
- `.agent-response-block` → dark border/background
- `.agent-response-title` → dark background
- `.agent-response-content`, `pre`, `code` → dark background + light text
- `.html-rendered-content` → kept white (report HTML is user-generated, not suitable for inversion)

## Theme State Management (`App.vue`)

**Init order:**
1. Read `localStorage.getItem('theme')`
2. If found, apply it (`'dark'` or `'light'`)
3. If not found, check `window.matchMedia('(prefers-color-scheme: dark)').matches`
4. Set `document.documentElement.setAttribute('data-theme', resolvedTheme)`
5. Listen for system changes via `matchMedia.addEventListener('change', ...)` — only applies when user has not manually set a preference

**`toggleTheme()`:**
1. Read current `data-theme` from `<html>`
2. Flip to opposite
3. Write to `localStorage`
4. Update `<html>` attribute

`toggleTheme` is shared to child components via Vue `provide/inject`.

## Header Button (`BaseLayout.vue`)

- Position: right side of `header-content`
- Icon: `bi-moon` (light mode) / `bi-sun` (dark mode)
- Reactive: reads current theme from a `ref` updated by `toggleTheme()`
- All scoped hardcoded colors in `BaseLayout.vue` are replaced with CSS variables

## Out of Scope

- Other `.vue` component hardcoded colors
- Element Plus component library dark theme (requires importing `element-plus/theme-chalk/dark/css-vars.css`)
