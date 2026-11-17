import { copyFile, readFile } from 'node:fs/promises';
const bundled = new URL('./services.json', import.meta.url);
try {
  await copyFile(new URL('../shared/src/commonMain/resources/services.json', import.meta.url), bundled);
} catch (error) {
  if (error.code !== 'ENOENT') throw error;
  // Cloud Build receives only functions/, including the catalog staged by predeploy.
  // Validate that bundle instead of requiring the shared source tree on the server.
  JSON.parse(await readFile(bundled, 'utf8'));
}
