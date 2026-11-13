import assert from 'node:assert/strict';
import { after, beforeEach, test } from 'node:test';
import {
  createCircleState,
  createGeoPoint,
  createGeoRectBounds,
  createMapCameraPosition,
  createMarkerState,
  createPolygonState,
  createPolylineState,
} from '@mapconductor/js-sdk-core';
import { buildPackage } from './helpers/buildPackage.mjs';

// MapLibreViewController をネイティブビューの代わりに react-native の代役（UIManager が
// コマンドを記録するだけ）へ繋いで動かす。地図の読み込み前に積んだオーバーレイが
// 読み込み時に流れること、ネイティブからのイベントが該当 state に戻ること、
// マーカーの一括転送が ACK 単位で進むことを確かめる。
const { mod, rn, cleanup } = await buildPackage('controller-tests');
after(cleanup);
const { MapLibreViewController } = mod;

const NODE = 42;
const camera = createMapCameraPosition({ position: createGeoPoint({ latitude: 35.68, longitude: 139.76 }), zoom: 10 });
const tick = () => new Promise((resolve) => setImmediate(resolve));
const sent = () => rn.commands.map((c) => c.command);

function newController({ mounted = true } = {}) {
  const ref = { current: mounted ? { nodeHandle: NODE } : null };
  return { controller: new MapLibreViewController(ref, camera), ref };
}

beforeEach(() => {
  rn.commands.length = 0;
});

test('camera commands are dispatched to the native view node and remembered', async () => {
  const { controller } = newController();
  const next = createMapCameraPosition({ position: createGeoPoint({ latitude: 34.7, longitude: 135.5 }), zoom: 14 });
  assert.equal(await controller.moveCamera(next), true);
  assert.equal(await controller.animateCamera(camera, 500), true);
  assert.deepEqual(rn.commands, [
    { node: NODE, command: 'moveCamera', args: [next] },
    { node: NODE, command: 'animateCamera', args: [camera, 500] },
  ]);
  assert.equal(controller.getCameraPosition(), camera);
  controller.destroy();
});

test('nothing is dispatched while the native view is not mounted', async () => {
  const { controller } = newController({ mounted: false });
  await controller.moveCamera(camera);
  controller.applyUISettings({ scrollGesture: true, zoomGesture: true, rotateGesture: false, tiltGesture: false });
  assert.deepEqual(rn.commands, []);
  controller.destroy();
});

test('fitBounds sends the corners, and refuses empty bounds', async () => {
  const { controller } = newController();
  assert.equal(await controller.fitBounds(createGeoRectBounds(), 10), false);
  assert.deepEqual(rn.commands, []);
  const southWest = createGeoPoint({ latitude: 35, longitude: 139 });
  const northEast = createGeoPoint({ latitude: 36, longitude: 140 });
  const bounds = createGeoRectBounds({ southWest, northEast });
  assert.equal(await controller.fitBounds(bounds, 24), true);
  assert.equal(rn.commands.length, 1);
  const [{ command, args }] = rn.commands;
  assert.equal(command, 'fitBounds');
  assert.equal(args[1], 24);
  assert.equal(args[0].southWest.latitude, 35);
  assert.equal(args[0].northEast.longitude, 140);
  controller.destroy();
});

test('applyUISettings forwards only the four gesture flags', () => {
  const { controller } = newController();
  controller.applyUISettings({ scrollGesture: false, zoomGesture: true, rotateGesture: false, tiltGesture: true, extra: 'ignored' });
  assert.deepEqual(rn.commands, [{
    node: NODE,
    command: 'applyUISettings',
    args: [{ scrollGesture: false, zoomGesture: true, rotateGesture: false, tiltGesture: true }],
  }]);
  controller.destroy();
});

test('shapes composed before the map loads are queued and flushed on load', async () => {
  const { controller } = newController();
  const circle = createCircleState({ id: 'c1', center: createGeoPoint({ latitude: 35, longitude: 139 }), radiusMeters: 100, strokeColor: '#112233' });
  const polyline = createPolylineState({ id: 'l1', points: [createGeoPoint({ latitude: 35, longitude: 139 }), createGeoPoint({ latitude: 36, longitude: 140 })] });
  const polygon = createPolygonState({ id: 'p1', points: [createGeoPoint({ latitude: 35, longitude: 139 }), createGeoPoint({ latitude: 36, longitude: 139 }), createGeoPoint({ latitude: 36, longitude: 140 })], fillColor: '#00ff0080' });
  await controller.compositionCircles([circle]);
  await controller.compositionPolylines([polyline]);
  await controller.compositionPolygons([polygon]);
  assert.deepEqual(rn.commands, [], 'nothing reaches native before onNativeMapLoaded');

  let initialized = 0;
  controller.setMapInitializedListener(() => initialized++);
  controller.onNativeMapLoaded();
  // 読み込み時の流し順は circle → groundImage → polygon → polyline → raster で固定。
  assert.deepEqual(sent(), ['compositionCircles', 'compositionPolygons', 'compositionPolylines']);
  assert.equal(initialized, 1);

  const [circles] = rn.commands[0].args;
  assert.equal(circles[0].id, 'c1');
  assert.equal(circles[0].radiusMeters, 100);
  assert.deepEqual(circles[0].strokeColor, { color: '#112233' });
  const [polygons] = rn.commands[1].args;
  assert.deepEqual(polygons[0].fillColor, { color: '#00ff0080' });
  assert.equal(polygons[0].points.length, 3);

  // 読み込み後は即時に送る。
  rn.commands.length = 0;
  await controller.updateCircle(circle.copy({ radiusMeters: 250 }));
  assert.deepEqual(sent(), ['updateCircle']);
  assert.equal(rn.commands[0].args[0].radiusMeters, 250);

  // 読み込み済みなら、あとから付けた初期化リスナーにも即時に知らせる。
  controller.setMapInitializedListener(() => initialized++);
  assert.equal(initialized, 2);
  controller.destroy();
});

test('an update before load replaces the queued composition instead of being sent', async () => {
  const { controller } = newController();
  const center = createGeoPoint({ latitude: 35, longitude: 139 });
  await controller.compositionCircles([createCircleState({ id: 'a', center, radiusMeters: 10 })]);
  await controller.updateCircle(createCircleState({ id: 'b', center, radiusMeters: 20 }));
  assert.deepEqual(rn.commands, []);
  controller.onNativeMapLoaded();
  const [payload] = rn.commands.find((c) => c.command === 'compositionCircles').args;
  assert.deepEqual(payload.map((c) => c.id), ['a', 'b']);
  controller.destroy();
});

test('native click and drag events come back to the matching state and listener', async () => {
  const { controller } = newController();
  controller.onNativeMapLoaded();
  const calls = [];
  const marker = createMarkerState({
    id: 'm1',
    position: createGeoPoint({ latitude: 35, longitude: 139 }),
    draggable: true,
    onClick: (s) => calls.push(['state-click', s.id]),
    onDragEnd: (s) => calls.push(['state-drag-end', s.position.latitude]),
  });
  // 読み込み済みなら一括転送がすぐ始まり、ネイティブの ACK を待つ。ACK を返して完了させる。
  const composed = controller.compositionMarkers([marker]);
  const begin = rn.commands.find((c) => c.command === 'beginMarkerComposition');
  controller.onNativeMarkerCompositionBatchProcessed(begin.args[0], 0);
  await composed;
  assert.ok(sent().includes('commitMarkerComposition'));
  controller.setOnMarkerClickListener((s) => calls.push(['listener-click', s.id]));
  controller.onNativeMarkerClick('m1');
  controller.onNativeMarkerClick('unknown');
  controller.onNativeMarkerDragEnd('m1', createGeoPoint({ latitude: 36.5, longitude: 139 }));
  assert.deepEqual(calls, [['state-click', 'm1'], ['listener-click', 'm1'], ['state-drag-end', 36.5]]);
  assert.equal(marker.position.latitude, 36.5, 'drag writes the new position back to the state');

  const polygonClicks = [];
  const polygon = createPolygonState({
    id: 'p1',
    points: [createGeoPoint({ latitude: 0, longitude: 0 }), createGeoPoint({ latitude: 1, longitude: 0 }), createGeoPoint({ latitude: 1, longitude: 1 })],
    onClick: (e) => polygonClicks.push(e),
  });
  await controller.compositionPolygons([polygon]);
  controller.onNativePolygonClick('p1', createGeoPoint({ latitude: 0.5, longitude: 0.2 }));
  assert.equal(polygonClicks.length, 1);
  assert.equal(polygonClicks[0].state, polygon);
  assert.equal(polygonClicks[0].clicked.latitude, 0.5);
  controller.destroy();
});

test('native camera events update the remembered camera and notify listeners', () => {
  const { controller } = newController();
  const seen = [];
  controller.setCameraMoveEndListener((c) => seen.push(c.zoom));
  const moved = createMapCameraPosition({ position: createGeoPoint({ latitude: 1, longitude: 2 }), zoom: 5 });
  controller.onNativeCameraMoveEnd(moved);
  assert.equal(controller.getCameraPosition(), moved);
  assert.deepEqual(seen, [5]);
  controller.destroy();
});

test('marker composition is streamed in acknowledged batches; updates wait for the commit', async () => {
  const { controller } = newController();
  const markers = Array.from({ length: 3 }, (_, i) =>
    createMarkerState({ id: `m${i}`, position: createGeoPoint({ latitude: i, longitude: i * 2 }), zIndex: i === 0 ? null : i })
  );
  await controller.compositionMarkers(markers);
  assert.deepEqual(rn.commands, [], 'markers wait for the map to load');

  controller.onNativeMapLoaded();
  assert.deepEqual(sent(), ['beginMarkerComposition', 'appendMarkerComposition']);
  const generation = rn.commands[0].args[0];
  const [, sequence, payload] = rn.commands[1].args;
  assert.equal(sequence, 0);
  assert.deepEqual(payload.ids, ['m0', 'm1', 'm2']);
  assert.deepEqual(payload.positions, [0, 0, 0, 1, 2, 0, 2, 4, 0]);
  assert.deepEqual(payload.zIndex, [0, 1, 2], 'a null zIndex goes over as 0');

  // コミット前の個別更新は保留され、コミット後にまとめて送られる。
  await controller.updateMarker(markers[1]);
  assert.ok(!sent().includes('updateMarker'));

  // 世代違い・連番違いの ACK は無視される。
  controller.onNativeMarkerCompositionBatchProcessed(generation + 1, 0);
  controller.onNativeMarkerCompositionBatchProcessed(generation, 1);
  await tick();
  assert.ok(!sent().includes('commitMarkerComposition'));

  controller.onNativeMarkerCompositionBatchProcessed(generation, 0);
  await tick();
  assert.deepEqual(sent().slice(2), ['commitMarkerComposition', 'updateMarker']);
  assert.deepEqual(rn.commands[2].args, [generation]);
  assert.equal(rn.commands[3].args[0].id, 'm1');

  // コミット後は即時に送る。
  rn.commands.length = 0;
  await controller.updateMarker(markers[2]);
  assert.deepEqual(sent(), ['updateMarker']);
  controller.destroy();
});

test('clearOverlays drops tracked overlays and tells native to clear', async () => {
  const { controller } = newController();
  controller.onNativeMapLoaded();
  const circle = createCircleState({ id: 'c', center: createGeoPoint({ latitude: 0, longitude: 0 }), radiusMeters: 1 });
  await controller.compositionCircles([circle]);
  assert.equal(controller.hasCircle(circle), true);
  rn.commands.length = 0;
  await controller.clearOverlays();
  assert.deepEqual(sent(), ['clearOverlays']);
  assert.equal(controller.hasCircle(circle), false);
  controller.destroy();
});
