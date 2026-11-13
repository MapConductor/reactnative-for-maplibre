# @mapconductor/reactnative-for-maplibre

MapConductor の MapLibre 用 React Native プロバイダーです。Web 用の
`@mapconductor/react-for-maplibre` とは別パッケージで、Android の `com.mapconductor:for-maplibre` と iOS の `MapConductorForMapLibre` を薄くラップします。

## デモ

[![Web 版 MapLibre の店舗マップのサンプル](https://raw.githubusercontent.com/MapConductor/reactnative-for-maplibre/main/docs/images/demo.jpg)](https://demo-react.mapconductor.com/maplibre/map/en)

画像は Web 版（[`@mapconductor/react-for-maplibre`](https://github.com/MapConductor/react-for-maplibre)）の[ライブデモ](https://demo-react.mapconductor.com/maplibre/map/en)です。同じ店舗マップを React Native で書くと次のとおりです。

```tsx
import { useMemo, useState } from 'react';
import { Text } from 'react-native';
import {
  createGeoPoint,
  createMapCameraPosition,
  createMarkerState,
  type MarkerState,
} from '@mapconductor/js-sdk-core';
import { InfoBubble, Markers } from '@mapconductor/js-sdk-react/native';
import { MapLibreDesign, MapLibreMapView, useMapLibreViewState } from '@mapconductor/reactnative-for-maplibre';

const STORES = [
  { id: 'waikiki', name: 'Waikiki', latitude: 21.2793, longitude: -157.8292 },
  { id: 'kailua', name: 'Kailua', latitude: 21.3972, longitude: -157.7394 },
  { id: 'haleiwa', name: 'Haleiwa', latitude: 21.5928, longitude: -158.1034 },
];

const INITIAL_CAMERA = createMapCameraPosition({
  position: createGeoPoint({ latitude: 21.38, longitude: -157.93 }),
  zoom: 10,
});

export function StoreMap() {
  const mapViewState = useMapLibreViewState({
    mapDesignType: MapLibreDesign.DemoTiles,
    cameraPosition: INITIAL_CAMERA,
  });
  const [selected, setSelected] = useState<MarkerState | null>(null);
  const markers = useMemo(
    () =>
      STORES.map(store =>
        createMarkerState({
          id: store.id,
          position: createGeoPoint({ latitude: store.latitude, longitude: store.longitude }),
          extra: store,
          onClick: marker => setSelected(marker),
        }),
      ),
    [],
  );

  return (
    <MapLibreMapView state={mapViewState} style={{ flex: 1 }} onMapClick={() => setSelected(null)}>
      <Markers states={markers} />
      {selected && (
        <InfoBubble marker={selected}>
          <Text>{(selected.extra as (typeof STORES)[number]).name}</Text>
        </InfoBubble>
      )}
    </MapLibreMapView>
  );
}
```
