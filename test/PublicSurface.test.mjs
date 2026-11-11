import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { after, test } from 'node:test';
import { createGeoPoint, createMapCameraPosition, createMarkerState } from '@mapconductor/js-sdk-core';
import * as webState from '@mapconductor/react-for-maplibre/state';
import { buildPackage } from './helpers/buildPackage.mjs';

// パッケージの入口が何を出すかと、そこから再輸出しているネイティブ向け変換の振る舞い。
// 状態とデザインは web 版の `/state` サブパスと同一実体であること（ルートの barrel は
// maplibre-gl を引き込み、Metro/Hermes が読み込み時に落ちる）も確かめる。
const { mod, cleanup, outDir, rnStubPath } = await buildPackage('surface-tests');
after(cleanup);

test('state and design come from react-for-maplibre/state, the same objects as the web package', () => {
  assert.equal(mod.MapLibreViewState, webState.MapLibreViewState);
  assert.equal(mod.MapLibreDesign, webState.MapLibreDesign);
  assert.equal(mod.useMapLibreViewState, webState.useMapLibreViewState);
});

test('the bundle reaches only react, js-sdk-core and the /state subpath at runtime', () => {
  const code = readdirSync(outDir)
    .filter((f) => f.endsWith('.mjs'))
    .map((f) => readFileSync(join(outDir, f), 'utf8'))
    .join('\n');
  const imported = new Set([...code.matchAll(/(?:from|import)\s*["']([^"']+)["']/g)].map((m) => m[1]));
  imported.delete(rnStubPath);
  assert.deepEqual([...imported].sort(), [
    '@mapconductor/js-sdk-core',
    '@mapconductor/react-for-maplibre/state',
    'react',
    'react/jsx-runtime',
  ]);
});

test('toNativeCameraPosition flattens the camera and defaults altitude to 0', () => {
  assert.equal(mod.toNativeCameraPosition(undefined), undefined);
  const camera = createMapCameraPosition({ position: createGeoPoint({ latitude: 35.1, longitude: 139.2, altitude: null }), zoom: 11, bearing: 30, tilt: 45 });
  assert.deepEqual(mod.toNativeCameraPosition(camera), {
    position: { latitude: 35.1, longitude: 139.2, altitude: 0 },
    zoom: 11,
    bearing: 30,
    tilt: 45,
  });
});

test('toNativeMarkerTilingOptions replaces the scale callback with a flag', () => {
  assert.equal(mod.toNativeMarkerTilingOptions(undefined), undefined);
  const base = { enabled: true, debugTileOverlay: true, minMarkerCount: 100, cacheSize: 2048, declutterPx: 0 };
  assert.deepEqual(mod.toNativeMarkerTilingOptions({ ...base, iconScaleCallback: () => 2 }), {
    enabled: true, debugTileOverlay: true, minMarkerCount: 100, cacheSize: 2048, hasIconScaleCallback: true,
  });
  assert.equal(mod.toNativeMarkerTilingOptions({ ...base, iconScaleCallback: null }).hasIconScaleCallback, false);
});

test('markerStateToNative sends a numeric zIndex and no icon when there is none', () => {
  const state = createMarkerState({ id: 'm', position: createGeoPoint({ latitude: 1, longitude: 2 }), draggable: true });
  const payload = mod.markerStateToNative(state);
  assert.equal(payload.id, 'm');
  assert.equal(payload.zIndex, 0);
  assert.equal(payload.icon, null);
  assert.equal(payload.draggable, true);
  assert.equal(payload.position, state.position);
});
