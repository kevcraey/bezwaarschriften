import {mount} from 'cypress-lit';

Cypress.Commands.add('mount', mount);

Cypress.on('uncaught:exception', () => {
  return false;
});
