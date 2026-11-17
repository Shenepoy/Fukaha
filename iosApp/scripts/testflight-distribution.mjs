import { createPrivateKey, sign } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export function token(env, now = Math.floor(Date.now() / 1000)) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const data = `${encode({ alg: 'ES256', kid: env.ASC_KEY_ID, typ: 'JWT' })}.${encode({ iss: env.ASC_ISSUER_ID, iat: now - 5, exp: now + 600, aud: 'appstoreconnect-v1' })}`;
  const key = createPrivateKey(Buffer.from(env.ASC_PRIVATE_KEY_BASE64, 'base64'));
  return `${data}.${sign('sha256', Buffer.from(data), { key, dsaEncoding: 'ieee-p1363' }).toString('base64url')}`;
}
export function selectGroup(groups, requested) {
  const external = groups.filter(group => group.attributes.isInternalGroup === false);
  const matches = requested ? external.filter(group => group.id === requested || group.attributes.name === requested) : external;
  if (matches.length !== 1) throw new Error(`Select exactly one existing external group using TESTFLIGHT_EXTERNAL_GROUP. Available: ${external.map(group => `${group.attributes.name} (${group.id})`).join(', ')}`);
  return matches[0];
}
async function main() {
  for (const name of ['ASC_KEY_ID', 'ASC_ISSUER_ID', 'ASC_PRIVATE_KEY_BASE64']) if (!process.env[name]) throw new Error(`Missing ${name}`);
  const api = async (path, method = 'GET', body) => {
    const url = new URL(path, 'https://api.appstoreconnect.apple.com');
    if (url.origin !== 'https://api.appstoreconnect.apple.com') throw new Error('Unexpected API origin');
    for (let attempt = 0; attempt < 4; attempt++) {
      const response = await fetch(url, { method, headers: { Authorization: `Bearer ${token(process.env)}`, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000) });
      if ((response.status === 429 || response.status >= 500) && attempt < 3) { await new Promise(resolve => setTimeout(resolve, 5000 * (attempt + 1))); continue; }
      const result = response.status === 204 ? {} : await response.json();
      if (!response.ok) throw new Error(`ASC ${method} ${url.pathname} ${response.status}: ${(result.errors || []).map(error => `${error.code}: ${error.title}`).join('; ')}`);
      return result;
    }
  };
  const list = async path => {
    const records = [];
    while (path) { const page = await api(path); records.push(...page.data); path = page.links?.next; }
    return records;
  };
  const apps = await list('/v1/apps?filter[bundleId]=com.shenepoy.fukaha&limit=200');
  if (apps.length !== 1) throw new Error('Expected one Fukaha app');
  const app = apps[0];
  const groups = await list(`/v1/betaGroups?filter[app]=${app.id}&limit=200`);
  const report = { app: app.id, appName: app.attributes.name, bundleId: app.attributes.bundleId, groups: groups.map(group => ({ id: group.id, name: group.attributes.name, internal: group.attributes.isInternalGroup })) };
  console.log(JSON.stringify(report, null, 2));
  if (process.argv[2] === 'inspect') {
    const detail = (await api(`/v1/apps/${app.id}/betaAppReviewDetail`)).data;
    const localizations = await list(`/v1/apps/${app.id}/betaAppLocalizations?limit=200`);
    console.log(JSON.stringify({ reviewContactMissing: ['contactFirstName', 'contactLastName', 'contactPhone', 'contactEmail'].filter(field => !detail?.attributes[field]), betaDescriptions: localizations.map(item => ({ locale: item.attributes.locale, hasDescription: Boolean(item.attributes.description), hasFeedbackEmail: Boolean(item.attributes.feedbackEmail) })) }, null, 2));
    return;
  }
  if (process.argv[2] !== 'distribute') throw new Error('Usage: testflight-distribution.mjs inspect|distribute');
  const group = selectGroup(groups, process.env.TESTFLIGHT_EXTERNAL_GROUP);
  const version = readFileSync(new URL('../../VERSION', import.meta.url), 'utf8').trim();
  const buildNumber = process.env.IOS_BUILD_NUMBER;
  if (!buildNumber) throw new Error('Missing IOS_BUILD_NUMBER');
  let build;
  const deadline = Date.now() + 30 * 60 * 1000;
  while (Date.now() < deadline) {
    const result = await api(`/v1/builds?filter[app]=${app.id}&filter[version]=${encodeURIComponent(buildNumber)}&include=preReleaseVersion&limit=200`);
    build = result.data.find(item => result.included?.some(pre => pre.type === 'preReleaseVersions' && pre.id === item.relationships.preReleaseVersion.data?.id && pre.attributes.version === version && pre.attributes.platform === 'IOS'));
    if (build?.attributes.processingState === 'VALID') break;
    if (build && ['FAILED', 'INVALID'].includes(build.attributes.processingState)) throw new Error(`Build processing ${build.attributes.processingState}`);
    console.log(`Waiting for ${version} (${buildNumber}) processing`);
    await new Promise(resolve => setTimeout(resolve, 30000));
  }
  if (build?.attributes.processingState !== 'VALID') throw new Error('Build processing timeout');
  if (build.attributes.usesNonExemptEncryption == null) await api(`/v1/builds/${build.id}`, 'PATCH', { data: { type: 'builds', id: build.id, attributes: { usesNonExemptEncryption: false } } });
  else if (build.attributes.usesNonExemptEncryption !== false) throw new Error('Unexpected encryption declaration');
  const notes = readFileSync(new URL('../testflight/what-to-test.en.txt', import.meta.url), 'utf8');
  const localizations = await list(`/v1/builds/${build.id}/betaBuildLocalizations?limit=200`);
  const existing = localizations.find(item => item.attributes.locale === 'en-US');
  const data = { type: 'betaBuildLocalizations', attributes: { whatsNew: notes } };
  if (existing) { data.id = existing.id; await api(`/v1/betaBuildLocalizations/${existing.id}`, 'PATCH', { data }); }
  else { data.attributes.locale = 'en-US'; data.relationships = { build: { data: { type: 'builds', id: build.id } } }; await api('/v1/betaBuildLocalizations', 'POST', { data }); }
  let detail = (await api(`/v1/builds/${build.id}/buildBetaDetail`)).data;
  if (detail.attributes.externalBuildState === 'READY_FOR_BETA_SUBMISSION') await api('/v1/betaAppReviewSubmissions', 'POST', { data: { type: 'betaAppReviewSubmissions', relationships: { build: { data: { type: 'builds', id: build.id } } } } });
  else if (!['WAITING_FOR_BETA_REVIEW', 'IN_BETA_REVIEW', 'BETA_APPROVED', 'READY_FOR_BETA_TESTING', 'IN_BETA_TESTING'].includes(detail.attributes.externalBuildState)) throw new Error(`Build cannot distribute: ${detail.attributes.externalBuildState}`);
  await api(`/v1/builds/${build.id}/relationships/betaGroups`, 'POST', { data: [{ type: 'betaGroups', id: group.id }] });
  let confirmed = false;
  for (let attempt = 0; attempt < 10; attempt++) {
    const assigned = await list(`/v1/builds/${build.id}/betaGroups?limit=200`);
    if (assigned.some(item => item.id === group.id)) { confirmed = true; break; }
    await new Promise(resolve => setTimeout(resolve, 3000));
  }
  if (!confirmed) throw new Error('External group assignment verification failed');
  detail = (await api(`/v1/builds/${build.id}/buildBetaDetail`)).data;
  Object.assign(report, { version, buildNumber, build: build.id, externalGroup: group.attributes.name, externalGroupId: group.id, externalState: detail.attributes.externalBuildState });
  console.log(JSON.stringify(report, null, 2));
  writeFileSync('testflight-distribution.json', JSON.stringify(report, null, 2));
}
if (process.argv[1] === fileURLToPath(import.meta.url)) main().catch(error => { console.error(error.message); process.exitCode = 1; });
