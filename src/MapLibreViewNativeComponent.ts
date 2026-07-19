import type { ViewProps } from 'react-native';
import { requireNativeComponent } from 'react-native';
import type { GeoPoint, MapCameraPosition, MarkerTilingOptions } from '@mapconductor/js-sdk-core';
import type { NativeMapExtensionEvent } from '@mapconductor/js-sdk-react/native';

export interface NativeMapLibreViewEvent<T> {
  nativeEvent: T;
}

export interface NativeMarkerTilingOptions {
  enabled: boolean;
  debugTileOverlay: boolean;
  minMarkerCount: number;
  cacheSize: number;
  /**
   * A JS function can't cross the RN bridge, so this only signals that
   * `iconScaleCallback` is set; the native wrapper resolves the actual
   * per-marker scale by calling back into JS via MarkerScaleBridge (JSI).
   */
  hasIconScaleCallback: boolean;
}

export interface NativeMapLibreViewProps extends ViewProps {
  cameraPosition?: {
    position: {
      latitude: number;
      longitude: number;
      altitude?: number | null;
    };
    zoom: number;
    bearing: number;
    tilt: number;
  };
  mapDesignType?: string;
  markerTilingOptions?: NativeMarkerTilingOptions;
  infoBubblePositions?: Array<{
    id: string;
    latitude: number;
    longitude: number;
    altitude?: number | null;
  }>;
  onMapLoaded?: () => void;
  onMarkerCompositionBatchProcessed?: (
    event: NativeMapLibreViewEvent<{ generation: number; sequence: number }>
  ) => void;
  onMapClick?: (event: NativeMapLibreViewEvent<{ point: GeoPoint }>) => void;
  onMapLongClick?: (event: NativeMapLibreViewEvent<{ point: GeoPoint }>) => void;
  onCameraMoveStart?: (
    event: NativeMapLibreViewEvent<{ cameraPosition: MapCameraPosition }>
  ) => void;
  onCameraMove?: (event: NativeMapLibreViewEvent<{ cameraPosition: MapCameraPosition }>) => void;
  onCameraMoveEnd?: (event: NativeMapLibreViewEvent<{ cameraPosition: MapCameraPosition }>) => void;
  onMarkerClick?: (event: NativeMapLibreViewEvent<{ markerId: string }>) => void;
  onCircleClick?: (
    event: NativeMapLibreViewEvent<{ circleId: string; point: GeoPoint }>
  ) => void;
  onGroundImageClick?: (
    event: NativeMapLibreViewEvent<{ groundImageId: string; point: GeoPoint }>
  ) => void;
  onPolylineClick?: (
    event: NativeMapLibreViewEvent<{ polylineId: string; point: GeoPoint }>
  ) => void;
  onPolygonClick?: (
    event: NativeMapLibreViewEvent<{ polygonId: string; point: GeoPoint }>
  ) => void;
  onMarkerDragStart?: (
    event: NativeMapLibreViewEvent<{ markerId: string; point: GeoPoint }>
  ) => void;
  onMarkerDrag?: (event: NativeMapLibreViewEvent<{ markerId: string; point: GeoPoint }>) => void;
  onMarkerDragEnd?: (event: NativeMapLibreViewEvent<{ markerId: string; point: GeoPoint }>) => void;
  onMarkerAnimateStart?: (
    event: NativeMapLibreViewEvent<{ markerId: string }>
  ) => void;
  onMarkerAnimateEnd?: (
    event: NativeMapLibreViewEvent<{ markerId: string }>
  ) => void;
  onMarkerScreenPositions?: (
    event: NativeMapLibreViewEvent<{
      positions: Array<{ markerId: string; x: number; y: number }>;
    }>
  ) => void;
  onInfoBubbleScreenPositions?: (
    event: NativeMapLibreViewEvent<{
      positions: Array<{ id: string; x: number; y: number }>;
    }>
  ) => void;
  onNativeMapExtensionEvent?: (
    event: NativeMapLibreViewEvent<NativeMapExtensionEvent>
  ) => void;
}

export function toNativeMarkerTilingOptions(
  markerTilingOptions: MarkerTilingOptions | undefined
): NativeMarkerTilingOptions | undefined {
  if (!markerTilingOptions) return undefined;
  return {
    enabled: markerTilingOptions.enabled,
    debugTileOverlay: markerTilingOptions.debugTileOverlay,
    minMarkerCount: markerTilingOptions.minMarkerCount,
    cacheSize: markerTilingOptions.cacheSize,
    hasIconScaleCallback: markerTilingOptions.iconScaleCallback != null,
  };
}

export function toNativeCameraPosition(cameraPosition: MapCameraPosition | undefined) {
  if (!cameraPosition) return undefined;

  return {
    position: {
      latitude: cameraPosition.position.latitude,
      longitude: cameraPosition.position.longitude,
      altitude: cameraPosition.position.altitude ?? 0,
    },
    zoom: cameraPosition.zoom,
    bearing: cameraPosition.bearing,
    tilt: cameraPosition.tilt,
  };
}

export default requireNativeComponent<NativeMapLibreViewProps>(
  // Align to android/src/main/java/com/mapconductor/react/maplibre/MapConductorMapLibreViewManager.kt (REACT_CLASS)
  'MapLibreMapView'
);
