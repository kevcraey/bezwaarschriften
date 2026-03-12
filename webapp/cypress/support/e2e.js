// E2E test support file
// Catch uncaught exceptions to prevent test failures from unrelated errors
Cypress.on('uncaught:exception', () => false);
