const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');

module.exports = {
  mode: 'production',
  entry: {
    'index': './src/js/index.js',
  },
  output: {
    filename: '[name].bundle.[chunkhash].js',
    path: path.resolve(__dirname, 'build'),
    hashFunction: 'sha256',
  },
  performance: {
    hints: false,
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
        use: [MiniCssExtractPlugin.loader, 'css-loader'],
      }],
  },
  devServer: {
    static: './build',
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  plugins: [
    new HtmlWebpackPlugin({
      filename: 'index.html',
      template: 'src/html/index.html',
      scriptLoading: 'defer',
      chunks: ['browser-support', 'index'],
      favicon: 'src/img/favicon.ico',
    }),
    new CopyWebpackPlugin({
      patterns: [
        {
          from: 'src/img',
          to: 'img',
        },
      ],
    }),
    new MiniCssExtractPlugin()],
};
