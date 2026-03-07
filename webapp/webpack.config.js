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
      '/api': {
        target: 'http://localhost:8080',
        onError: (err, req, res) => {
          if (err.code === 'ECONNREFUSED') {
            console.log('\x1b[33m%s\x1b[0m', '[HPM] Backend (http://localhost:8080) not ready yet. Retrying...');
            if (res.writeHead) {
              res.writeHead(503, {
                'Content-Type': 'text/plain',
              });
              res.end('Backend is starting up, please refresh in a few seconds.');
            }
          }
        },
      },
      '/ws/taken': {
        target: 'http://localhost:8080',
        ws: true,
      },
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
