import { readFile } from 'node:fs/promises';
import { onRequest } from 'firebase-functions/v2/https';
import { readPreview } from './preview.mjs';

const catalog = JSON.parse(await readFile(new URL('./services.json', import.meta.url), 'utf8'));

export const previewMetadata = onRequest({ region: 'us-central1', memory: '256MiB', timeoutSeconds: 15,
  maxInstances: 3, concurrency: 20, invoker: 'public' }, async (request, response) => {
  response.set('Cache-Control', 'no-store');
  response.set('X-Content-Type-Options', 'nosniff');
  if (request.method !== 'POST') return response.status(405).end();
  if (!request.is('application/json') || request.rawBody.length > 8192) return response.status(400).end();
  try {
    const result = await readPreview(request.body?.embed, catalog);
    response.json(result);
  } catch {
    // No upstream bodies, signed media URLs or request URLs in server logs/errors.
    response.status(422).json({ error: 'Preview unavailable' });
  }
});
