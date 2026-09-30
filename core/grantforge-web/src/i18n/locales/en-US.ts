// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import type { Messages } from './zh-CN'

/** English (United States) messages; must match the keys of the zh-CN dictionary. */
const enUS: Messages = {
  common: {
    retry: 'Retry',
    reload: 'Reload',
    loadFailed: 'The data could not be loaded',
    emptyTitle: 'No data yet',
    emptyDescription: 'Create the first record to start managing your workspace.',
  },
  status: { active: 'Active', inactive: 'Disabled', locked: 'Locked' },
  pagination: {
    total: '{count} records',
    perPage: 'Records per page',
    perPageOption: '{size} / page',
    previous: 'Previous page',
    next: 'Next page',
  },
  controls: {
    dismissToast: 'Dismiss notification',
    closeDialog: 'Close dialog',
    increase: 'Increase {label}',
    decrease: 'Decrease {label}',
    selectPlaceholder: 'Select',
    selectEmpty: 'No options',
  },
  titles: {
    app: 'Access workspace',
    dashboard: 'Overview',
    users: 'Users',
    roles: 'Roles',
    menus: 'Menus',
    methods: 'HTTP methods',
    json: 'JSON workbench',
    forbidden: 'Access denied',
    network: 'Connection lost',
  },
  layout: {
    skipToContent: 'Skip to content',
    mainNavigation: 'Main navigation',
    openNavigation: 'Open navigation',
    closeNavigation: 'Close navigation',
    groupWorkspace: 'Workspace',
    groupAccess: 'Access control',
    groupTools: 'Developer tools',
    workspaceName: 'Access workspace',
    promoTitle: 'Build clearer access boundaries',
    promoText: 'From users to roles, every grant has a reason.',
    readDocs: 'Read the documentation',
    breadcrumbRoot: 'Workspace',
    quickJump: 'Quick jump',
    quickJumpDescription: 'Search pages in the workspace',
    searchPages: 'Search pages',
    searchPlaceholder: 'Type a page name…',
    noMatchingPages: 'No matching pages',
    lightTheme: 'Switch to light theme',
    darkTheme: 'Switch to dark theme',
    switchLanguage: '切换到中文',
    logout: 'Sign out',
  },
  errors: {
    network: 'Cannot reach the service. Check your network and try again.',
    unauthorized: 'Your session has expired. Please sign in again.',
    forbidden: 'You do not have permission to perform this action.',
    status: 'Request failed ({status})',
    unavailable: 'The service is temporarily unavailable. Please try again later.',
    unavailableWithId: 'The service is temporarily unavailable. Please try again later (request ID {id}).',
    tooManyOptions: 'Too many options. Ask an administrator to narrow the data scope.',
    generic: 'The operation failed. Please try again later.',
    navigation: 'Navigation permissions are not loaded yet. You can reload them.',
    missingToken: 'The sign-in response did not contain a valid token.',
  },
}

export default enUS
