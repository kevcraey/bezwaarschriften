/**
 * Mock Ollama server voor E2E tests.
 *
 * Retourneert vaste 1024-dimensionale embeddings voor /api/embeddings.
 * Embeddings zijn subtiel verschillend per tekst zodat HDBSCAN kan clusteren.
 *
 * Start: node webapp/test/e2e/mock-ollama.js
 * Stop: Ctrl+C of kill process
 */
const http = require('http');

const PORT = 11434;
const DIMENSIONS = 1024;

function hashCode(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash + str.charCodeAt(i)) | 0;
  }
  return hash;
}

function generateEmbedding(tekst) {
  const seed = hashCode(tekst);
  const embedding = new Array(DIMENSIONS);
  for (let i = 0; i < DIMENSIONS; i++) {
    // Deterministic pseudo-random based on text hash + dimension index
    // Base vector + small perturbation so similar texts cluster together
    const base = 0.1;
    const perturbation = Math.sin(seed + i * 0.01) * 0.05;
    embedding[i] = base + perturbation;
  }
  return embedding;
}

const server = http.createServer((req, res) => {
  if (req.method === 'POST' && req.url === '/api/embeddings') {
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', () => {
      try {
        const {prompt} = JSON.parse(body);
        const embedding = generateEmbedding(prompt || '');
        res.writeHead(200, {'Content-Type': 'application/json'});
        res.end(JSON.stringify({embedding}));
      } catch (e) {
        res.writeHead(400, {'Content-Type': 'application/json'});
        res.end(JSON.stringify({error: e.message}));
      }
    });
  } else {
    res.writeHead(404);
    res.end('Not found');
  }
});

server.listen(PORT, () => {
  console.log(`Mock Ollama listening on http://localhost:${PORT}`);
});
