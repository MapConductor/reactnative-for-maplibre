import { requireNativeComponent } from 'react-native';
import type {
  NativeMapViewEvent,
  NativeMapViewProps,
} from '@mapconductor/js-sdk-react/internal';

// 共通のブリッジ props / イベント型は js-sdk-react に集約してある。
export type NativeMapLibreViewEvent<T> = NativeMapViewEvent<T>;

export interface NativeMapLibreViewProps extends NativeMapViewProps {
}

export {
  toNativeCameraPosition,
  toNativeMarkerTilingOptions,
  type NativeMarkerTilingOptions,
} from '@mapconductor/js-sdk-react/internal';

export default requireNativeComponent<NativeMapLibreViewProps>(
  // Align to android/src/main/java/com/mapconductor/react/maplibre/MapConductorMapLibreViewManager.kt (REACT_CLASS)
  'MapLibreMapView'
);
