import https from 'node:https';

export const MAX_HEAD_BYTES = 131072;
const CRAWLER = 'Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)';

export function catalogHosts(catalog) {
  return new Set(Object.values(catalog).flatMap(platform => platform.services)
    .filter(service => service.disabled !== true)
    .flatMap(service => [service.host, ...(service.alternate_hosts || [])])
    .map(host => new URL(host).hostname));
}

function checkedUrl(value, allowed) {
  const url = new URL(value);
  if (typeof value !== 'string' || value.length > 4096 || url.protocol !== 'https:' ||
      url.username || url.password || url.port || !allowed.has(url.hostname)) throw Error('Invalid source');
  return url;
}

/** HTML headers only. Range limits upstream transfer; destroy also bounds non-compliant servers. */
export function readHead(url, { signal, get = https.get }) {
  return new Promise((resolve, reject) => {
    const request = get(url, { signal, headers: {
      'User-Agent': CRAWLER, Accept: 'text/html,application/xhtml+xml', Range: 'bytes=0-131071',
    } }, response => {
      const status = response.statusCode;
      const contentType = (response.headers['content-type'] || '').split(';')[0].toLowerCase();
      const base = { status, contentType, location: response.headers.location };
      let settled = false;
      function finish(html = '') {
        if (settled) return;
        settled = true;
        resolve({ ...base, html });
        response.destroy();
      }
      // A redirect or media response is decided from headers, never buffered as a file.
      if (status < 200 || status >= 300 || !['text/html', 'application/xhtml+xml'].includes(contentType)) return finish();
      const chunks = [];
      let size = 0;
      response.on('data', chunk => {
        const part = chunk.subarray(0, MAX_HEAD_BYTES - size);
        chunks.push(part); size += part.length;
        const html = Buffer.concat(chunks).toString('utf8');
        if (size === MAX_HEAD_BYTES || /<\/head>/i.test(html)) finish(html);
      });
      response.on('end', () => finish(Buffer.concat(chunks).toString('utf8')));
      response.on('error', error => { if (!settled) reject(error); });
    });
    request.on('error', reject);
  });
}

export async function readPreview(embed, catalog, { fetchHead = readHead, signal = AbortSignal.timeout(10000) } = {}) {
  const hosts = catalogHosts(catalog);
  let url = checkedUrl(embed, hosts);
  // Only these observed, provider-owned redirect destinations are needed by the catalog.
  const redirectedHosts = new Set([...hosts, 'embedez.com', 'vxtwitter.com', 'v16m.tiktokcdn-us.com']);
  for (let count = 0; count <= 4; count++) {
    const result = await fetchHead(url, { signal });
    if (result.status >= 300 && result.status < 400 && result.location) {
      url = checkedUrl(new URL(result.location, url).href, redirectedHosts);
      continue;
    }
    return { status: result.status, contentType: result.contentType, html: result.html.slice(0, MAX_HEAD_BYTES), finalUrl: url.href };
  }
  throw Error('Too many redirects');
}
