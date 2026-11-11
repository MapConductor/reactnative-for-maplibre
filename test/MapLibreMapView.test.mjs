import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createGeoPoint, createMapCameraPosition } from '@mapconductor/js-sdk-core';
import { MapLibreDesign } from '@mapconductor/react-for-maplibre/state';
import { buildPackage } from './helpers/buildPackage.mjs';

// MapLibreMapView は共有の NativeMapViewHost に「どのネイティブビューか」「デザインをどう文字列化するか」を
// 渡すだけの層。React を描画せず関数として呼び、返ってきた要素の props で受け渡しを確かめる。
// MapLibre は getValue() の合成形（mapDesign_id=...,style=...）を渡し、ネイティブ側が style= 以降を取り出す。
const { mod, cleanup } = await buildPackage('mapview-tests');
after(cleanup);
const { MapLibreMapView, MapLibreViewState, MapLibreViewController, NativeMapViewHost, ReactNativeBridgeMapViewController } = mod;

test('the shared native host is rendered with the MapLibreMapView native view', () => {
  const element = MapLibreMapView({ state: new MapLibreViewState() });
  assert.equal(element.type, NativeMapViewHost);
  // Android の REACT_CLASS / iOS の RCT_EXPORT_MODULE と揃っていないと
  // 「View config not found」で落ちる名前。
  assert.deepEqual(element.props.nativeComponent, { nativeViewName: 'MapLibreMapView' });
  // このプロバイダはネイティブ固有の prop（API キー等）を渡さない。
  assert.equal(element.props.nativeProps, undefined);
});

test('the design value sent to native follows the state design', () => {
  const state = new MapLibreViewState();
  assert.equal(MapLibreMapView({ state }).props.mapDesignValue, 'mapDesign_id=osm-bright,style=https://tile.openstreetmap.jp/styles/osm-bright/style.json');
  state.mapDesignType = MapLibreDesign.DemoTiles;
  assert.equal(MapLibreMapView({ state }).props.mapDesignValue, 'mapDesign_id=demo,style=https://demotiles.maplibre.org/style.json');
});

test('app props pass through to the host untouched', () => {
  const state = new MapLibreViewState();
  const onMapClick = () => {};
  const props = MapLibreMapView({ state, onMapClick, children: 'child' }).props;
  assert.equal(props.state, state);
  assert.equal(props.onMapClick, onMapClick);
  assert.equal(props.children, 'child');
});

test('createController builds a MapLibreViewController bound to the native ref and initial camera', () => {
  const view = { nodeHandle: 7 };
  const camera = createMapCameraPosition({ position: createGeoPoint({ latitude: 35.68, longitude: 139.76 }), zoom: 12 });
  const controller = MapLibreMapView({ state: new MapLibreViewState() }).props.createController({ current: view }, camera);
  assert.ok(controller instanceof MapLibreViewController);
  assert.ok(controller instanceof ReactNativeBridgeMapViewController);
  assert.equal(controller.getCameraPosition(), camera);
  assert.equal(controller.holder.mapView, view);
  controller.destroy();
});

test('an object style keeps a composite value that names it by id', () => {
  const design = new MapLibreDesign('vt-abc', { version: 8, sources: {}, layers: [] });
  const value = MapLibreMapView({ state: new MapLibreViewState({ mapDesignType: design }) }).props.mapDesignValue;
  assert.equal(value, 'mapDesign_id=vt-abc,style=object');
});
