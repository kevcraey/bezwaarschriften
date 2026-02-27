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
        test: /\.css$/i,
        use: [
          'style-loader',
          {
            loader: MiniCssExtractPlugin.loader,
            options: {
              esModule: false,
            },
          },
          'css-loader',
        ],
        include: [
          path.resolve(__dirname, 'src/css/index.css'),
        ],
      }, {
        test: /\.css$/i,
        use: ['css-loader'],
        exclude: [
          path.resolve(__dirname, 'src/css/index.css'),
        ],
      }],
  },
  devServer: {
    static: './build',
    proxy: {
      '/rest': 'http://localhost:8080',
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
