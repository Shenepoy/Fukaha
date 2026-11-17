import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { selectGroup, token } from './testflight-distribution.mjs';
test('external group selection refuses ambiguity and internal groups', () => {
  const internal = { id: '1', attributes: { name: 'Team', isInternalGroup: true } };
  const external = { id: '2', attributes: { name: 'Friends', isInternalGroup: false } };
  assert.equal(selectGroup([internal, external]), external);
  assert.equal(selectGroup([internal, external], 'Friends'), external);
  assert.throws(() => selectGroup([internal, external], '1'));
  assert.throws(() => selectGroup([external, { ...external, id: '3' }]));
});
test('ASC JWT uses valid ES256 signature and bounded lifetime', () => {
  const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const jwt = token({ ASC_KEY_ID: 'KEY', ASC_ISSUER_ID: 'issuer', ASC_PRIVATE_KEY_BASE64: Buffer.from(privateKey.export({ type: 'pkcs8', format: 'pem' })).toString('base64') }, 1000);
  const [header, payload, signature] = jwt.split('.');
  assert.equal(verify('sha256', Buffer.from(`${header}.${payload}`), { key: publicKey, dsaEncoding: 'ieee-p1363' }, Buffer.from(signature, 'base64url')), true);
  assert.deepEqual(JSON.parse(Buffer.from(payload, 'base64url')), { iss: 'issuer', iat: 995, exp: 1600, aud: 'appstoreconnect-v1' });
});
