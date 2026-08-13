import { NativeMapViewHost } from '@mapconductor/js-sdk-react/internal';
import type { MapLibreViewStateInterface } from '@mapconductor/react-for-maplibre/state';
import { MapLibreViewController } from './MapLibreViewController.native';
import type { MapLibreMapViewProps } from './MapLibreViewProps.native';
import type { MapLibreMapViewRef } from './MapLibreTypeAlias.native';
import NativeMapLibreMapView from './MapLibreViewNativeComponent';

/**
 * ネイティブイベントの配線・オーバーレイ収集・InfoBubble レイヤは全 RN プロバイダで
 * 同一なので {@link NativeMapViewHost} に集約してある。ここで渡すのは
 * 「どのネイティブビューか」「デザインをどう文字列化するか」だけ。
 */
export function MapLibreMapView(props: MapLibreMapViewProps) {
  return (
    <NativeMapViewHost<MapLibreMapViewRef, MapLibreViewStateInterface>
      {...props}
      nativeComponent={NativeMapLibreMapView}
      // MapLibre は id とスタイル URL を 1 本の文字列へ詰める（ネイティブ側で分解する）。
      mapDesignValue={props.state.mapDesignType.getValue()}
      createController={(ref, camera) => new MapLibreViewController(ref, camera)}
    />
  );
}
