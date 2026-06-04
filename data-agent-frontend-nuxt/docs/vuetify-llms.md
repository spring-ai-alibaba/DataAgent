# Vuetify0

> Lightweight, modular meta-framework for building headless UI systems with Vue.js. Provides unstyled, logic-focused components and composables as building blocks for design systems.

## Guide

- [Using the Docs](https://0.vuetifyjs.com/guide/essentials/using-the-docs): Master the v0 documentation with Ask AI, skill-level filtering, interactive examples, keyboard shortcuts, and learning tracks designed for your experience.
- [Accessibility Guide](https://0.vuetifyjs.com/guide/features/accessibility): Build accessible Vue 3 applications with Vuetify0. Learn ARIA patterns, keyboard navigation, focus management, and WCAG compliance for headless UI components.
- [Constants](https://0.vuetifyjs.com/guide/features/constants): SSR-safe environment detection constants from Vuetify0. Browser checks, touch support, observer availability, and more.
- [Palettes](https://0.vuetifyjs.com/guide/features/palettes): Use pre-built color palettes from Material Design, Tailwind, Radix, and Ant Design. Generate complete themes from a single seed color with algorithmic adapters.
- [Theming Guide](https://0.vuetifyjs.com/guide/features/theming): Customize your design system with Vuetify0's theming. Manage colors, design tokens, and CSS variables with createTokens and useTheme composables for Vue 3.
- [Types](https://0.vuetifyjs.com/guide/features/types): Public TypeScript types exported by Vuetify0. ID, Activation, DeepPartial, Extensible, MaybeArray, and more for type-safe headless UI development.
- [Utilities Guide](https://0.vuetifyjs.com/guide/features/utilities): Discover Vuetify0's utility functions for common development tasks. Type guards, transformers, and helpers that enhance code reusability in Vue 3 applications.
- [Benchmarks](https://0.vuetifyjs.com/guide/fundamentals/benchmarks): Understand Vuetify0 benchmark methodology, performance tiers, and size ratings. Learn what gets benchmarked and how to interpret metrics.
- [Building Frameworks](https://0.vuetifyjs.com/guide/fundamentals/building-frameworks): Learn how to use Vuetify0 as a foundation for building your own component framework. Covers behavior composables, component wrappers, plugins, SSR, and TypeScript patterns.
- [Components Guide](https://0.vuetifyjs.com/guide/fundamentals/components): Build accessible, customizable Vue 3 components with Vuetify0. Learn the compound component pattern, context injection, and v-model integration for headless UI.
- [Composables Guide](https://0.vuetifyjs.com/guide/fundamentals/composables): Learn how to use Vuetify0 composables for headless UI logic. Understand when to use composables vs components, and how to build custom UI with type-safe APIs.
- [Core](https://0.vuetifyjs.com/guide/fundamentals/core): Explore Vuetify0's core architecture including createContext, createTrinity, and createPlugin factories. Build scalable apps with type-safe dependency injection.
- [Plugins Guide](https://0.vuetifyjs.com/guide/fundamentals/plugins): Extend Vuetify0 with plugins using createPlugin factory. Integrate third-party libraries, add global features, and enhance your Vue 3 application architecture.
- [Reactivity](https://0.vuetifyjs.com/guide/fundamentals/reactivity): Understand Vuetify0's minimal reactivity philosophy. Learn what's reactive by default, how to opt-in when needed, and avoid common pitfalls.
- [Styling Headless Components](https://0.vuetifyjs.com/guide/fundamentals/styling): Learn to style Vuetify0 headless components using data attributes and slot props with Tailwind, UnoCSS, or CSS modules.
- [Tree-Shaking](https://0.vuetifyjs.com/guide/fundamentals/tree-shaking): Understand how Vuetify0 tree-shakes. Learn import strategies, bundle size costs, and optimization techniques for production Vue 3 applications.
- [Building This Documentation](https://0.vuetifyjs.com/guide/integration/building-docs): How the v0 documentation site is built using v0 composables, UnoCSS, and vite-ssg. A real-world proof of concept for headless UI patterns.
- [Nuxt 3](https://0.vuetifyjs.com/guide/integration/nuxt): Integrate Vuetify0 with Nuxt 3. Configure SSR, auto-imports, theme persistence, and hydration handling for server-rendered Vue applications.
- [AI Tools](https://0.vuetifyjs.com/guide/tooling/ai-tools): Use llms.txt and llms-full.txt files to provide AI assistants like Claude, ChatGPT, and Cursor with comprehensive Vuetify0 documentation context.
- [Vuetify CLI](https://0.vuetifyjs.com/guide/tooling/vuetify-cli): Scaffold and manage v0 projects with the official CLI. Create new projects, update dependencies, and analyze usage.
- [Vuetify MCP](https://0.vuetifyjs.com/guide/tooling/vuetify-mcp): Set up Vuetify MCP server to give AI assistants like Claude and Cursor direct access to Vuetify component APIs and v0 headless composable docs.

## Introduction

- [Browser Support](https://0.vuetifyjs.com/introduction/browser-support): Browser compatibility matrix for Vuetify0 headless UI. Learn which browsers support v0 features and recommended browsers for the documentation site.
- [Code of Conduct](https://0.vuetifyjs.com/introduction/code-of-conduct): The Vuetify0 Code of Conduct establishes standards for respectful participation in our open source community.
- [Contributing to Vuetify0](https://0.vuetifyjs.com/introduction/contributing): Learn how to contribute to Vuetify0. Setup local development, submit pull requests, write tests, and follow coding conventions for Vue 3 composables.
- [Vuetify0 FAQ](https://0.vuetifyjs.com/introduction/frequently-asked): Find answers to common questions about Vuetify0 headless UI. Learn about accessibility, SSR support, styling approaches, and differences from Vuetify.
- [Getting Started](https://0.vuetifyjs.com/introduction/getting-started): Get started with Vuetify0 headless UI primitives for Vue 3. Install, configure, and build your own design system with unstyled, accessible components.
- [License](https://0.vuetifyjs.com/introduction/license): Vuetify0 is released under the MIT License, one of the most permissive open source licenses available.
- [Security Disclosure](https://0.vuetifyjs.com/introduction/security): Learn how to responsibly report security vulnerabilities in Vuetify0. Our security team takes all reports seriously.
- [Why v0](https://0.vuetifyjs.com/introduction/why-vuetify0): v0 is a meta-framework for building UI libraries. Headless composables, AI-native docs, interactive playground, and a decade of Vuetify battle-testing.

## Components

- [Atom](https://0.vuetifyjs.com/components/primitives/atom): Polymorphic foundation component for dynamic element rendering. Render as any HTML element with the 'as' prop or use renderless mode for zero DOM overhead.
- [Avatar](https://0.vuetifyjs.com/components/semantic/avatar): Headless avatar component with priority-based image loading and automatic fallback handling. Supports initials, icons, and multi-source images for Vue 3.
- [Breadcrumbs](https://0.vuetifyjs.com/components/semantic/breadcrumbs): Responsive breadcrumb navigation with automatic overflow detection, ellipsis collapse, custom dividers, and WAI-ARIA compliance. Compound component pattern for Vue 3.
- [Button](https://0.vuetifyjs.com/components/actions/button): Headless button component with disabled, readonly, passive, and loading states. Toggle groups, icon accessibility, loading grace period, and form submission support.
- [Checkbox](https://0.vuetifyjs.com/components/forms/checkbox): Headless checkbox component with dual-mode support. Standalone boolean v-model or group multi-selection with tri-state, batch operations, and full ARIA compliance.
- [Combobox](https://0.vuetifyjs.com/components/forms/combobox): Headless combobox component with autocomplete filtering, free-text input, single and multi-selection, virtual focus keyboard navigation, and adapter-based filtering for Vue 3.
- [Dialog](https://0.vuetifyjs.com/components/disclosure/dialog): Build accessible modal dialogs using the native HTML dialog element. Includes focus trapping, backdrop overlay, escape key handling, and ARIA support.
- [ExpansionPanel](https://0.vuetifyjs.com/components/disclosure/expansion-panel): Accessible accordion and expansion panels with single or multi-expand modes. WAI-ARIA compliant compound component with Header, Activator, and Content slots.
- [Form](https://0.vuetifyjs.com/components/forms/form): Headless form component that coordinates validation across child fields. Renders a native form element with submit, reset, and aggregate validity state.
- [Group](https://0.vuetifyjs.com/components/providers/group): Create checkbox groups with tri-state and indeterminate support. Multi-selection with batch operations, select-all patterns, and array-based v-model binding.
- [Input](https://0.vuetifyjs.com/components/forms/input): Headless text input component with integrated validation, error messages, descriptions, and full ARIA compliance for Vue 3.
- [Locale](https://0.vuetifyjs.com/components/providers/locale): A headless component that scopes a locale context to a subtree. Descendant useLocale() calls resolve to the specified locale for translations and number formatting.
- [Pagination](https://0.vuetifyjs.com/components/semantic/pagination): Accessible pagination component with responsive auto-sizing, ellipsis support, keyboard navigation, and full ARIA compliance. Compound component pattern for Vue 3.
- [Popover](https://0.vuetifyjs.com/components/disclosure/popover): Build popovers, tooltips, and dropdowns using the CSS Anchor Positioning API. Zero-JavaScript positioning with v-model state management for Vue 3.
- [Portal](https://0.vuetifyjs.com/components/primitives/portal): Renderless teleport primitive with automatic z-index stacking. Teleport content to the body or custom targets with SSR support for Vue 3.
- [Presence](https://0.vuetifyjs.com/components/primitives/presence): Renderless mount lifecycle primitive that manages lazy mounting, exit animation delay, and unmounting for Vue 3 headless components.
- [Radio](https://0.vuetifyjs.com/components/forms/radio): Headless radio button component for Vue 3 single-selection groups. Features keyboard navigation, roving tabindex, and complete ARIA compliance.
- [Scrim](https://0.vuetifyjs.com/components/providers/scrim): A headless scrim/backdrop component for overlay systems. Integrates with useStack for automatic z-index management, dismiss handling, and blocking mode support.
- [Select](https://0.vuetifyjs.com/components/forms/select): Headless dropdown select component with single and multi-selection, virtual focus keyboard navigation, native popover positioning, and full ARIA compliance.
- [Selection](https://0.vuetifyjs.com/components/providers/selection): Manage selection state in Vue 3 collections. Build checkboxes, radio groups, and listboxes with full v-model support, mandatory selection, and item enrollment.
- [Single](https://0.vuetifyjs.com/components/providers/single): Build radio buttons and single-selection UIs with automatic deselection. Extends Selection composable for tabs, toggles, and exclusive choice patterns in Vue 3.
- [Slider](https://0.vuetifyjs.com/components/forms/slider): Headless slider component with single and range mode. Pointer drag, keyboard navigation, step snapping, and full ARIA compliance for Vue 3.
- [Snackbar](https://0.vuetifyjs.com/components/semantic/snackbar): Headless compound component for rendering toast and snackbar notifications. Snackbar.Queue connects to useNotifications for queue-driven toast stacks.
- [Splitter](https://0.vuetifyjs.com/components/semantic/splitter): Headless splitter component with resizable panels and draggable handles. Supports keyboard navigation, orientation control, and nested layouts for Vue 3.
- [Step](https://0.vuetifyjs.com/components/providers/step): Navigate multi-step processes with first, last, next, and prev methods. Build form wizards and steppers with automatic disabled item skipping for Vue 3.
- [Switch](https://0.vuetifyjs.com/components/forms/switch): Headless switch component with dual-mode support. Standalone boolean v-model or group multi-selection with tri-state, batch operations, and full ARIA compliance.
- [Tabs](https://0.vuetifyjs.com/components/disclosure/tabs): Accessible tab navigation with automatic and manual activation modes. WAI-ARIA compliant compound component with roving tabindex and keyboard navigation.
- [Theme](https://0.vuetifyjs.com/components/providers/theme): A headless component that scopes a theme context to a subtree. Descendant useTheme() calls resolve to the specified theme without affecting the rest of the app.
- [Treeview](https://0.vuetifyjs.com/components/disclosure/treeview): Accessible hierarchical tree component with expand/collapse, selection, and tri-state checkbox support for Vue 3.

## Composables - Foundation

- [createContext](https://0.vuetifyjs.com/composables/foundation/create-context): Type-safe Vue dependency injection wrapper. Create reusable context to share state across components without prop drilling. Foundation for Vuetify0.
- [createPlugin](https://0.vuetifyjs.com/composables/foundation/create-plugin): Factory for creating Vue plugins with standardized context provision. Simplifies plugin creation with automatic app-level context injection and cleanup.
- [createTrinity](https://0.vuetifyjs.com/composables/foundation/create-trinity): Factory for the trinity pattern. Creates a 3-tuple with context consumer, provider, and default instance for type-safe sharable singleton state in Vue apps.

## Composables - Registration

- [createQueue](https://0.vuetifyjs.com/composables/registration/create-queue): Vue 3 queue composable for time-based collections. Features automatic timeout removal, pause and resume functionality, and FIFO ordering for items.
- [createRegistry](https://0.vuetifyjs.com/composables/registration/create-registry): A foundational composable for building registration-based systems, managing
- [createTimeline](https://0.vuetifyjs.com/composables/registration/create-timeline): Bounded undo/redo system with fixed-size history. Built on createRegistry for state management with automatic overflow handling and time-travel debugging.
- [createTokens](https://0.vuetifyjs.com/composables/registration/create-tokens): A utility for managing design tokens with support for hierarchical collections,

## Composables - Selection

- [createGroup](https://0.vuetifyjs.com/composables/selection/create-group): Multi-selection composable with tri-state support. Manage checkbox trees with indeterminate states, batch operations, and select-all patterns for Vue 3 apps.
- [createModel](https://0.vuetifyjs.com/composables/selection/create-model): Value store layer that extends createRegistry with a reactive value, disabled guards, and an apply bridge for useProxyModel sync.
- [createNested](https://0.vuetifyjs.com/composables/selection/create-nested): Hierarchical tree composable for Vue 3. Manage parent-child relationships, open/close states, and tree traversal with pluggable strategies.
- [createSelection](https://0.vuetifyjs.com/composables/selection/create-selection): Manage item selection in collections with automatic indexing. Supports single and multi-select patterns, mandatory selection, enrollment, and lifecycle management.
- [createSingle](https://0.vuetifyjs.com/composables/selection/create-single): Single-item selection with automatic deselection. Extends createSelection for radio buttons, tabs, and exclusive choice patterns. Base for theme/locale.
- [createStep](https://0.vuetifyjs.com/composables/selection/create-step): Navigate sequential steps with first, last, next, and prev methods. Build form wizards, carousels, and guided flows with circular navigation support.

## Composables - Forms

- [createCombobox](https://0.vuetifyjs.com/composables/forms/create-combobox): Orchestrator composable that coordinates selection, popover, virtual focus, and adapter-based filtering for building combobox and autocomplete components.
- [createForm](https://0.vuetifyjs.com/composables/forms/create-form): Coordinate validation across multiple inputs with submit, reset, and aggregate state. Pure registry of createValidation instances.
- [createSlider](https://0.vuetifyjs.com/composables/forms/create-slider): Manage slider state with value math, step snapping, percentage conversion, and multi-thumb support. Build single sliders, range sliders, and color pickers.
- [createValidation](https://0.vuetifyjs.com/composables/forms/create-validation): Per-input validation composable built on createGroup. Each rule is a ticket that can be enabled/disabled. Supports async rules, race safety, Standard Schema, and auto-registration with forms.

## Composables - Reactivity

- [useProxyModel](https://0.vuetifyjs.com/composables/reactivity/use-proxy-model): Bridge selection context to v-model with bidirectional sync. Supports single value or array modes with custom transform functions for Vue 3 components.
- [useProxyRegistry](https://0.vuetifyjs.com/composables/reactivity/use-proxy-registry): Vue 3 reactive proxy wrapper for registry collections. Automatically updates refs when items are registered or unregistered from the registry system.

## Composables - Plugins

- [useBreakpoints](https://0.vuetifyjs.com/composables/plugins/use-breakpoints): A composable for responsive design that detects viewport dimensions and
- [useDate](https://0.vuetifyjs.com/composables/plugins/use-date): Vue 3 composable for date manipulation using the Temporal API. Supports adapter pattern, locale-aware formatting, and Intl.DateTimeFormat integration.
- [useFeatures](https://0.vuetifyjs.com/composables/plugins/use-features): Manage feature flags and variations for A/B testing. Supports adapter pattern for external providers, dynamic toggling, and per-feature variations for Vue 3.
- [useHydration](https://0.vuetifyjs.com/composables/plugins/use-hydration): A composable for managing SSR hydration state, controlling when components
- [useLocale](https://0.vuetifyjs.com/composables/plugins/use-locale): i18n composable for managing translations and locale switching. Supports variable replacement, message linking, number formatting, and custom adapters.
- [useLogger](https://0.vuetifyjs.com/composables/plugins/use-logger): A composable for application logging with configurable adapters, log levels,
- [useNotifications](https://0.vuetifyjs.com/composables/plugins/use-notifications): A composable for managing notification lifecycle with state mutations,
- [usePermissions](https://0.vuetifyjs.com/composables/plugins/use-permissions): RBAC composable for managing user permissions. Define access control with actions, subjects, and context-aware conditions using the adapter pattern for Vue 3.
- [useRtl](https://0.vuetifyjs.com/composables/plugins/use-rtl): RTL composable for managing text direction. Reactive isRtl boolean, dir attribute management, subtree overrides, and adapter pattern for framework integration.
- [useRules](https://0.vuetifyjs.com/composables/plugins/use-rules): Headless validation composable with Standard Schema support, custom aliases, and createValidation integration.
- [useStack](https://0.vuetifyjs.com/composables/plugins/use-stack): Vue 3 composable for managing overlay z-index stacking. Automatic z-index calculation, scrim integration, and SSR-safe global overlay coordination.
- [useStorage](https://0.vuetifyjs.com/composables/plugins/use-storage): Reactive storage composable with localStorage, sessionStorage, and custom adapters. Automatic serialization, caching, and SSR-safe operations for Vue 3.
- [useTheme Composable](https://0.vuetifyjs.com/composables/plugins/use-theme): A composable for theme management that registers multiple themes, switches

## Composables - System

- [useClickOutside](https://0.vuetifyjs.com/composables/system/use-click-outside): Vue 3 composable for detecting clicks outside elements. Features two-phase detection, touch scroll handling, iframe focus detection, and auto cleanup.
- [useEventListener](https://0.vuetifyjs.com/composables/system/use-event-listener): Handle DOM events with automatic cleanup on unmount. Supports Window, Document, and HTMLElement targets with reactive listeners and multiple event handlers.
- [useHotkey](https://0.vuetifyjs.com/composables/system/use-hotkey): Handle hotkey combinations and sequences with platform-aware modifiers and automatic cleanup. Supports ctrl+k combinations, g-h sequences, and input focus.
- [useIntersectionObserver](https://0.vuetifyjs.com/composables/system/use-intersection-observer): Detect element visibility with Intersection Observer API. Perfect for lazy loading, infinite scroll, and entrance animations with automatic cleanup control.
- [useLazy](https://0.vuetifyjs.com/composables/system/use-lazy): Defer rendering of heavy content until first activation. Perfect for dialogs, menus, tooltips, and components with conditionally rendered content.
- [useMediaQuery](https://0.vuetifyjs.com/composables/system/use-media-query): Reactive CSS media query matching with automatic cleanup. Detect dark mode, reduced motion, screen orientation, and custom queries with SSR safety.
- [useMutationObserver](https://0.vuetifyjs.com/composables/system/use-mutation-observer): Detect DOM changes with Mutation Observer API. Monitor attributes, child elements, and text content with automatic cleanup and pause/resume controls for Vue 3.
- [usePopover](https://0.vuetifyjs.com/composables/system/use-popover): Composable for native popover API behavior with CSS anchor positioning. Manages open/close state, anchor styles, content attributes, and bidirectional sync with native popover events.
- [usePresence](https://0.vuetifyjs.com/composables/system/use-presence): Manage DOM mount lifecycle with lazy mounting, exit animation delay, and automatic unmounting. Works with CSS transitions, WAAPI, GSAP, or no animation.
- [useRaf](https://0.vuetifyjs.com/composables/system/use-raf): Scope-disposed safe requestAnimationFrame composable with cancel-then-request pattern. Perfect for throttling updates to animation frames with automatic cleanup.
- [useResizeObserver](https://0.vuetifyjs.com/composables/system/use-resize-observer): Detect element size changes with Resize Observer API. Perfect for responsive components, charts, and virtualized lists with automatic cleanup for Vue 3.
- [useRovingFocus](https://0.vuetifyjs.com/composables/system/use-roving-focus): Roving tabindex composable for keyboard navigation within toolbars, listboxes, grids, and other composite widgets. Supports orientation, grid mode, circular navigation, and disabled item skipping.
- [useTimer](https://0.vuetifyjs.com/composables/system/use-timer): A reactive timer composable with start, stop, pause, resume controls and remaining time tracking. Supports one-shot and repeating modes with automatic scope cleanup.
- [useToggleScope](https://0.vuetifyjs.com/composables/system/use-toggle-scope): Conditionally manage Vue effect scopes based on reactive boolean conditions. Watchers and effects automatically clean up when the scope stops or toggles off.
- [useVirtualFocus](https://0.vuetifyjs.com/composables/system/use-virtual-focus): Virtual focus composable for keyboard navigation where DOM focus stays on a control element. Powers combobox, autocomplete, and select patterns with aria-activedescendant.

## Composables - Utilities

- [createBreadcrumbs](https://0.vuetifyjs.com/composables/utilities/create-breadcrumbs): Breadcrumb navigation composable extending createSingle. Path truncation, depth tracking, and navigation methods for hierarchical trails.
- [createOverflow](https://0.vuetifyjs.com/composables/utilities/create-overflow): Compute how many items fit in a container based on available width. Enables responsive truncation for pagination, breadcrumbs, and overflow menus.

## Composables - Transformers

- [toArray](https://0.vuetifyjs.com/composables/transformers/to-array): Convert any value to an array in TypeScript/Vue. Handles null/undefined gracefully, preserves existing arrays, and ensures type-safe consistent output for Vue 3.
- [toElement](https://0.vuetifyjs.com/composables/transformers/to-element): Resolve refs, getters, raw DOM elements, or Vue component instances to a plain DOM Element. Handles cross-version Vue Ref compatibility with structural typing.
- [toReactive](https://0.vuetifyjs.com/composables/transformers/to-reactive): Convert MaybeRef objects to reactive proxies in Vue 3. Automatically unwraps refs for objects, Maps, and Sets while eliminating .value syntax for cleaner code.

## Composables - Data

- [createDataTable](https://0.vuetifyjs.com/composables/data/create-data-table): Full-featured data table composable with sorting, filtering, pagination, selection, expansion, and grouping. Adapter pattern for client, server, and virtual strategies.
- [createFilter](https://0.vuetifyjs.com/composables/data/create-filter): Filter arrays based on search queries with multiple filter modes (some, every, union, intersection) and custom filtering logic. Reactive and type-safe for Vue 3.
- [createPagination](https://0.vuetifyjs.com/composables/data/create-pagination): Lightweight composable for pagination state. Includes navigation methods (first, last, next, prev), computed visible pages, and v-model binding for Vue 3.
- [createVirtual](https://0.vuetifyjs.com/composables/data/create-virtual): Efficiently render large lists with virtual scrolling. Only renders visible items, supports dynamic heights, bidirectional scrolling, and infinite scroll.

## Resources

- [Release Notes](https://0.vuetifyjs.com/releases): Stay up to date with the latest Vuetify0 releases and changelog. View detailed release notes, breaking changes, new features, and bug fixes by version.
- [Roadmap](https://0.vuetifyjs.com/roadmap): Track upcoming features, releases, milestones, and maturity status for @vuetify/v0 headless UI library. v0 enters alpha on April 7, 2026.

## AI Resources

- [SKILL.md](https://0.vuetifyjs.com/SKILL.md): Compact reference with patterns, anti-patterns, and TypeScript types for AI coding assistants
- [Vuetify MCP](https://0.vuetifyjs.com/guide/tooling/vuetify-mcp): Model Context Protocol server for structured API access