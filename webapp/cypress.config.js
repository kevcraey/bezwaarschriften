const {defineConfig} = require('cypress');

module.exports = defineConfig({
  defaultCommandTimeout: 10000,
  video: false,
  videosFolder: './target/cypress/videos',
  screenshotsFolder: './target/cypress/screenshots',
  chromeWebSecurity: false,
  retries: 0,
  includeShadowDom: true,
  component: {
    supportFile: 'cypress/support/component.js',
    specPattern: './test/**/*.cy.js',
    devServer: {
      bundler: 'webpack',
      webpackConfig: {
        devServer: {
          allowedHosts: 'all',
        },
        module: {
          rules: [
            {
              test: /\.m?js$/i,
              resolve: {
                fullySpecified: false,
              },
            },
            {
              resourceQuery: /raw/,
              type: 'asset/source',
            },
            {
              test: /\.css$/i,
              use: ['css-loader'],
              resourceQuery: {not: [/raw/]},
            },
          ],
        },
      },
    },
    indexHtmlFile: 'cypress/support/component-index.html',
    viewportWidth: 1920,
    viewportHeight: 1080,
  },
});
