import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { catalogHosts, readPreview, readHead, MAX_HEAD_BYTES } from './preview.mjs';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';

const catalog = JSON.parse(await readFile(new URL('../shared/src/commonMain/resources/services.json', import.meta.url), 'utf8'));

test('all catalog hosts are accepted, arbitrary destinations and credentials are rejected before I/O', async () => {
  for (const host of catalogHosts(catalog)) {
    const result = await readPreview(`https://${host}/post/123`, catalog, { fetchHead: async () => ({status:200,contentType:'text/html',html:'<head></head>'}) });
    assert.equal(result.status, 200);
  }
  for (const url of ['https://localhost/', 'http://fixvx.com/post', 'https://user@fixvx.com/post', 'https://fixvx.com:8443/post', 'https://fixvx.com.evil.example/post']) {
    await assert.rejects(readPreview(url, catalog, { fetchHead: () => { throw Error('Must not perform I/O'); } }), /Invalid source/);
  }
});

test('only catalog and observed provider redirect hosts are followed, with a hop limit', async () => {
  let requests = 0;
  const result = await readPreview('https://imgurez.com/gallery/123', catalog, { fetchHead: async () => ++requests === 1
    ? { status:302,location:'https://embedez.com/download?q=post' }
    : { status:200,contentType:'text/html',html:'<head>preview</head>' } });
  assert.equal(requests, 2); assert.equal(result.finalUrl, 'https://embedez.com/download?q=post');
  for (const location of ['http://embedez.com/', 'https://127.0.0.1/', 'https://github.com/', 'https://embedez.com.evil.example/']) {
    await assert.rejects(readPreview('https://imgurez.com/gallery/123', catalog, { fetchHead: async () => ({status:302,location}) }));
  }
  await assert.rejects(readPreview('https://imgurez.com/gallery/123', catalog, { fetchHead: async () => ({status:302,location:'https://imgurez.com/loop'}) }), /Too many redirects/);
});

test('returned HTML is bounded and media responses are not downloaded or proxied', async () => {
  const result = await readPreview('https://fixvx.com/post/123', catalog, { fetchHead: async () => ({status:200,contentType:'text/html',html:'x'.repeat(MAX_HEAD_BYTES+100)}) });
  assert.equal(result.html.length, MAX_HEAD_BYTES);
  const media = await readPreview('https://kktiktok.com/post/123', catalog, { fetchHead: async () => ({status:200,contentType:'video/mp4',html:''}) });
  assert.equal(media.html, ''); assert.equal(media.contentType, 'video/mp4');
});

test('upstream transfer stops at the head or byte limit, and video bodies have no data reader', async () => {
  for (const [contentType, body, expected] of [
    ['text/html', '<head>preview</head>', '<head>preview</head>'],
    ['text/html', 'x'.repeat(MAX_HEAD_BYTES * 2), 'x'.repeat(MAX_HEAD_BYTES)],
    ['video/mp4', 'video bytes must not be consumed', ''],
  ]) {
    const stream = new PassThrough(); stream.statusCode = 200;
    stream.headers = {'content-type': contentType};
    const result = await readHead(new URL('https://fixvx.com/post'), {
      signal: AbortSignal.timeout(1000),
      get(url, options, callback) {
        assert.equal(options.headers.Range, 'bytes=0-131071');
        queueMicrotask(() => { callback(stream); if (!stream.destroyed) stream.end(body); });
        return new EventEmitter();
      },
    });
    assert.equal(result.html, expected);
    assert.equal(stream.destroyed, true);
    if (contentType === 'video/mp4') assert.equal(stream.listenerCount('data'), 0);
  }
});
