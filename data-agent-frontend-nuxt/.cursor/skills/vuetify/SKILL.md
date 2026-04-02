---
name: vuetify0
description: Build with @vuetify/v0 headless composables and components for Vue 3. Use when creating selection state (single, multi, grouped, stepped), form validation, tab/dialog/popover UI, provide/inject context, registries, virtual scrolling, pagination, keyboard shortcuts, resize observers, theming, breakpoints, or SSR-safe browser detection. Triggers on v0, vuetify0, headless components, or WAI-ARIA patterns.
---

# Vuetify0 - Headless Composables & Components

Transform Vue 3 apps with unstyled, logic-focused building blocks for design systems.
  
## Quick Start

```bash
pnpm install @vuetify/v0
```

No global plugin required. Import only what you need:

```ts
import { createSelection } from '@vuetify/v0/composables'
import { Tabs } from '@vuetify/v0/components'
```

## Core Decision Tree

**Before writing custom logic**, check if v0 already provides it:

| Need | Use | Guide |
|------|-----|-------|
| Single item selection | `createSingle` | [Single selection patterns](references/selection-patterns.md#single) |
| Multi-item selection | `createSelection` | [Multi selection patterns](references/selection-patterns.md#multi) |
| Selection with "select all" | `createGroup` | [Group patterns](references/selection-patterns.md#group) |
| Step wizard / carousel | `createStep` | [Stepper patterns](references/selection-patterns.md#step) |
| Form validation | `createForm` | [Form patterns](references/form-patterns.md) |
| Shared state (provide/inject) | `createContext` | [Context patterns](references/context-patterns.md) |
| Browser utilities | See utilities | [Browser & DOM patterns](references/browser-patterns.md) |

**Full API reference**: See [REFERENCE.md](references/REFERENCE.md)

## Component Architecture

All components are **headless** (unstyled) and follow WAI-ARIA patterns:

```vue
<script setup>
import { Tabs } from '@vuetify/v0/components'
</script>

<template>
  <Tabs.Root v-model="active">
    <Tabs.List>
      <Tabs.Item value="overview">Overview</Tabs.Item>
    </Tabs.List>
    <Tabs.Panel value="overview">Content</Tabs.Panel>
  </Tabs.Root>
</template>
```

**Available components**: Dialog, Tabs, ExpansionPanel, Checkbox, Radio, Popover, Pagination

**Component examples**: See [component-examples.md](references/component-examples.md)

## Essential Patterns

### Selection State
```ts
// Single selection (tabs, theme picker)
const single = createSingle({ mandatory: 'force' })

// Multi-selection (tags, filters)
const selection = createSelection({ multiple: true })

// Group with "select all" (data tables)  
const group = createGroup()
```

### Context Sharing
```ts
// Type-safe provide/inject
const [useTheme, provideTheme] = createContext<Theme>('Theme')
```

### Form Validation
```ts
const form = createForm()
form.register({ id: 'email', rules: [required, email] })
```

## Anti-Patterns

**Don't reinvent these wheels:**

❌ **Custom selection logic** → Use `createSelection`
❌ **Manual provide/inject** → Use `createContext` 
❌ **SSR checks** → Use `IN_BROWSER` constant

**Detailed anti-patterns**: See [anti-patterns.md](references/anti-patterns.md)

## Development Tools

**Generate common patterns:**
```bash
python scripts/scaffold_pattern.py --type selection --output ./composables
```

**Check for anti-patterns:**
```bash
python scripts/check_patterns.py ./src
```

**Vuetify MCP** for structured API access:
```bash
claude mcp add vuetify-mcp https://mcp.vuetifyjs.com/mcp
```

## Resources

- **Detailed examples**: [references/component-examples.md](references/component-examples.md)
- **Advanced patterns**: [references/advanced-patterns.md](references/advanced-patterns.md)  
- **Migration guide**: [references/migration-guide.md](references/migration-guide.md)
- **Full API**: [references/REFERENCE.md](references/REFERENCE.md)
- **Live docs**: https://0.vuetifyjs.com