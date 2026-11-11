import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { build } from 'tsup';

// src/index.ts を node で読める形に束ねる。
// - react-native は test/helpers/react-native-stub.mjs に差し替える（外部参照のまま残すので、
//   テストからも同じモジュール実体を import してコマンドを覗ける）
// - js-sdk-react は `react-native` 条件で解決して束ねる（RN 用の共有ブリッジを使うため）
// - js-sdk-core / react / react-for-*/state は普通に node から解決する
const stubPath = fileURLToPath(new URL('./react-native-stub.mjs', import.meta.url));

export async function buildPackage(prefix) {
  const buildRoot = new URL('../../build/', import.meta.url);
  mkdirSync(buildRoot, { recursive: true });
  const outDir = mkdtempSync(fileURLToPath(new URL(`${prefix}-`, buildRoot)));
  const entry = resolve(outDir, 'entry.ts');
  writeFileSync(
    entry,
    [
      `export * from ${JSON.stringify(fileURLToPath(new URL('../../src/index.ts', import.meta.url)))};`,
      `export { NativeMapViewHost, ReactNativeBridgeMapViewController, ReactNativeMapViewHolder } from '@mapconductor/js-sdk-react/internal';`,
    ].join('\n')
  );
  await build({
    entry: { index: entry },
    outDir, format: ['esm'], platform: 'node', splitting: false,
    dts: false, sourcemap: false, config: false, silent: true,
    noExternal: ['react-native', /^@mapconductor\/js-sdk-react/],
    esbuildOptions(options) {
      options.conditions = ['react-native'];
      options.jsx = 'automatic';
    },
    esbuildPlugins: [{
      name: 'react-native-stub',
      setup(b) {
        b.onResolve({ filter: /^react-native$/ }, () => ({ path: stubPath, external: true }));
      },
    }],
  });
  const mod = await import(pathToFileURL(resolve(outDir, 'index.mjs')));
  const rn = await import(pathToFileURL(stubPath));
  return { mod, rn, outDir, rnStubPath: stubPath, cleanup: () => rmSync(outDir, { recursive: true, force: true }) };
}
