import { MapLibreViewStateInterface } from '@mapconductor/react-for-maplibre/state';
export { MapLibreDesign, MapLibreMapDesignType, MapLibreViewState, MapLibreViewStateInterface, MapLibreViewStateParams, useMapLibreViewState } from '@mapconductor/react-for-maplibre/state';
import React from 'react';
import { ViewProps, HostComponent, NativeMethods, StyleProp, ViewStyle } from 'react-native';
import { GeoPoint, MapCameraPosition, MarkerTilingOptions, MapViewControllerInterface } from '@mapconductor/js-sdk-core';
import { NativeMapExtensionEvent, MapViewBaseProps } from '@mapconductor/js-sdk-react/native';
import { ReactNativeBridgeMapViewController, ReactNativeMapViewHolder } from '@mapconductor/js-sdk-react/internal';
export { NativeMarkerStatePayload as NativeMapLibreMarkerState, markerStateToNative } from '@mapconductor/js-sdk-react/internal';

interface NativeMapLibreViewEvent<T> {
    nativeEvent: T;
}
interface NativeMarkerTilingOptions {
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
interface NativeMapLibreViewProps extends ViewProps {
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
    onMarkerCompositionBatchProcessed?: (event: NativeMapLibreViewEvent<{
        generation: number;
        sequence: number;
    }>) => void;
    onMapClick?: (event: NativeMapLibreViewEvent<{
        point: GeoPoint;
    }>) => void;
    onMapLongClick?: (event: NativeMapLibreViewEvent<{
        point: GeoPoint;
    }>) => void;
    onCameraMoveStart?: (event: NativeMapLibreViewEvent<{
        cameraPosition: MapCameraPosition;
    }>) => void;
    onCameraMove?: (event: NativeMapLibreViewEvent<{
        cameraPosition: MapCameraPosition;
    }>) => void;
    onCameraMoveEnd?: (event: NativeMapLibreViewEvent<{
        cameraPosition: MapCameraPosition;
    }>) => void;
    onMarkerClick?: (event: NativeMapLibreViewEvent<{
        markerId: string;
    }>) => void;
    onCircleClick?: (event: NativeMapLibreViewEvent<{
        circleId: string;
        point: GeoPoint;
    }>) => void;
    onGroundImageClick?: (event: NativeMapLibreViewEvent<{
        groundImageId: string;
        point: GeoPoint;
    }>) => void;
    onPolylineClick?: (event: NativeMapLibreViewEvent<{
        polylineId: string;
        point: GeoPoint;
    }>) => void;
    onPolygonClick?: (event: NativeMapLibreViewEvent<{
        polygonId: string;
        point: GeoPoint;
    }>) => void;
    onMarkerDragStart?: (event: NativeMapLibreViewEvent<{
        markerId: string;
        point: GeoPoint;
    }>) => void;
    onMarkerDrag?: (event: NativeMapLibreViewEvent<{
        markerId: string;
        point: GeoPoint;
    }>) => void;
    onMarkerDragEnd?: (event: NativeMapLibreViewEvent<{
        markerId: string;
        point: GeoPoint;
    }>) => void;
    onMarkerAnimateStart?: (event: NativeMapLibreViewEvent<{
        markerId: string;
    }>) => void;
    onMarkerAnimateEnd?: (event: NativeMapLibreViewEvent<{
        markerId: string;
    }>) => void;
    onMarkerScreenPositions?: (event: NativeMapLibreViewEvent<{
        positions: Array<{
            markerId: string;
            x: number;
            y: number;
        }>;
    }>) => void;
    onInfoBubbleScreenPositions?: (event: NativeMapLibreViewEvent<{
        positions: Array<{
            id: string;
            x: number;
            y: number;
        }>;
    }>) => void;
    onNativeMapExtensionEvent?: (event: NativeMapLibreViewEvent<NativeMapExtensionEvent>) => void;
}
declare function toNativeMarkerTilingOptions(markerTilingOptions: MarkerTilingOptions | undefined): NativeMarkerTilingOptions | undefined;
declare function toNativeCameraPosition(cameraPosition: MapCameraPosition | undefined): {
    position: {
        latitude: number;
        longitude: number;
        altitude: number;
    };
    zoom: number;
    bearing: number;
    tilt: number;
} | undefined;

type MapLibreMapViewRef = React.ComponentRef<HostComponent<NativeMapLibreViewProps>> & NativeMethods;
type MapLibreMap = null;

type MapLibreViewControllerInterface = MapViewControllerInterface;

/**
 * ネイティブブリッジの実装は全 RN プロバイダで同一なので
 * {@link ReactNativeBridgeMapViewController} に集約してある。ここはネイティブビューの
 * ref 型を与えるだけ。プロバイダ固有の振る舞いが要るときだけメソッドを override する。
 */
declare class MapLibreViewController extends ReactNativeBridgeMapViewController<MapLibreMapViewRef> {
}

/**
 * RN のホルダーは全プロバイダで同一（投影はネイティブ側が行う）なので
 * {@link ReactNativeMapViewHolder} に集約してある。ここは ref 型を与えるだけ。
 */
declare class MapLibreMapViewHolder extends ReactNativeMapViewHolder<MapLibreMapViewRef> {
}

interface MapLibreMapViewProps extends MapViewBaseProps<MapLibreViewStateInterface> {
    maxZoom?: number;
    minZoom?: number;
    className?: string;
    containerStyle?: StyleProp<ViewStyle>;
    onError?: (error: Error) => void;
    children?: React.ReactNode;
    markerTilingOptions?: MarkerTilingOptions;
}

declare function MapLibreMapView({ state, style, onMapLoaded, onMapClick, onMapLongClick, onCameraMoveStart, onCameraMove, onCameraMoveEnd, cameraRestriction, markerTilingOptions, children, }: MapLibreMapViewProps): React.JSX.Element;

export { type MapLibreMap, MapLibreMapView, MapLibreMapViewHolder, type MapLibreMapViewProps, type MapLibreMapViewRef, MapLibreViewController, type MapLibreViewControllerInterface, type NativeMapLibreViewEvent, type NativeMapLibreViewProps, type NativeMarkerTilingOptions, toNativeCameraPosition, toNativeMarkerTilingOptions };
