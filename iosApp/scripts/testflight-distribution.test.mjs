import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { attachGroups, selectGroup, selectGroups, token } from './testflight-distribution.mjs';
test('external group selection refuses ambiguity and internal groups', () => {
  const internal = { id: '1', attributes: { name: 'Team', isInternalGroup: true } };
  const external = { id: '2', attributes: { name: 'Friends', isInternalGroup: false } };
  assert.equal(selectGroup([internal, external]), external);
  assert.equal(selectGroup([internal, external], 'Friends'), external);
  assert.throws(() => selectGroup([internal, external], '1'));
  assert.throws(() => selectGroup([external, { ...external, id: '3' }]));
});
test('all-group distribution includes internal and external groups', () => {
  const internal = { id: '1', attributes: { name: 'Team', isInternalGroup: true } };
  const external = { id: '2', attributes: { name: 'Friends', isInternalGroup: false } };
  const another = { id: '3', attributes: { name: 'Community', isInternalGroup: false } };
  assert.deepEqual(selectGroups([internal, external, another], undefined, true), [internal, external, another]);
  assert.deepEqual(selectGroups([internal, external, another], 'Friends', false), [external]);
  assert.throws(() => selectGroups([internal], undefined, true));
});
test('group attachment verifies access and isolates a permission failure', async () => {
  const groups = [
    { id: 'automatic', attributes: { name: 'Automatic', isInternalGroup: true, hasAccessToAllBuilds: true } },
    { id: 'external', attributes: { name: 'Friends', isInternalGroup: false } },
    { id: 'internal', attributes: { name: 'Team', isInternalGroup: true } },
  ];
  const attempted = [];
  let reads = 0;
  const assignments = await attachGroups(groups, async () => {
    reads++;
    return new Set(reads >= 3 ? ['internal'] : []);
  }, async id => {
    attempted.push(id);
    if (id === 'external') throw new Error('403 Forbidden');
  }, async () => {});
  assert.deepEqual(attempted, ['external', 'internal']);
  assert.deepEqual(assignments.map(({ verified, error }) => [verified, error]), [
    [true, undefined], [false, '403 Forbidden'], [true, undefined],
  ]);
});
test('ASC JWT uses valid ES256 signature and bounded lifetime', () => {
  const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const jwt = token({ ASC_KEY_ID: 'KEY', ASC_ISSUER_ID: 'issuer', ASC_PRIVATE_KEY_BASE64: Buffer.from(privateKey.export({ type: 'pkcs8', format: 'pem' })).toString('base64') }, 1000);
  const [header, payload, signature] = jwt.split('.');
  assert.equal(verify('sha256', Buffer.from(`${header}.${payload}`), { key: publicKey, dsaEncoding: 'ieee-p1363' }, Buffer.from(signature, 'base64url')), true);
  assert.deepEqual(JSON.parse(Buffer.from(payload, 'base64url')), { iss: 'issuer', iat: 995, exp: 1600, aud: 'appstoreconnect-v1' });
});
