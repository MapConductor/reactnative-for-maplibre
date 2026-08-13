import { MapLibreViewStateInterface } from '@mapconductor/react-for-maplibre/state';
export { MapLibreDesign, MapLibreMapDesignType, MapLibreViewState, MapLibreViewStateInterface, MapLibreViewStateParams, useMapLibreViewState } from '@mapconductor/react-for-maplibre/state';
import React from 'react';
import { ViewProps, HostComponent, NativeMethods, StyleProp, ViewStyle } from 'react-native';
import { GeoPoint, MapCameraPosition, MarkerTilingOptions, MapViewControllerInterface, MapViewHolder, Offset, BaseMapViewController, CircleCapable, GroundImageCapable, MarkerCapable, PolygonCapable, PolylineCapable, RasterLayerCapable, NativeMapExtensionCapable, GeoRectBounds, MapUISettings, MarkerState, PolylineState, CircleState, OnCircleEventHandler, GroundImageState, OnGroundImageEventHandler, PolygonState, OnPolygonEventHandler, OnPolylineEventHandler, RasterLayerState, NativeMapExtensionDescriptor, NativeMapExtensionEventHandler, NativeMapExtensionEvent as NativeMapExtensionEvent$1, OnMarkerEventHandler, MarkerAnimationOverlayHost, MarkerAnimation } from '@mapconductor/js-sdk-core';
import { NativeMapExtensionEvent, MapViewBaseProps, NativeMarkerIconPayload } from '@mapconductor/js-sdk-react/native';

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

declare class MapLibreMapViewHolder implements MapViewHolder<MapLibreMapViewRef | null, null> {
    private readonly nativeRef;
    readonly map: null;
    constructor(nativeRef: React.RefObject<MapLibreMapViewRef | null>);
    get mapView(): MapLibreMapViewRef | null;
    toScreenOffset(_position: GeoPoint): null;
    fromScreenOffset(_offset: Offset): Promise<GeoPoint | null>;
    fromScreenOffsetSync(_offset: Offset): GeoPoint | null;
}

declare class MapLibreViewController extends BaseMapViewController implements MapViewControllerInterface, CircleCapable, GroundImageCapable, MarkerCapable, PolygonCapable, PolylineCapable, RasterLayerCapable, NativeMapExtensionCapable {
    private readonly nativeRef;
    readonly holder: MapLibreMapViewHolder;
    private cameraPosition;
    private mapLoaded;
    private markerCompositionGeneration;
    private activeMarkerComposition;
    private pendingMarkerComposition;
    private markerBatchAck;
    private readonly pendingMarkerUpdates;
    private readonly markerStates;
    private readonly circleStates;
    private readonly groundImageStates;
    private readonly polygonStates;
    private readonly polylineStates;
    private readonly rasterLayerStates;
    private pendingPolygons;
    private pendingCircles;
    private pendingGroundImages;
    private pendingPolylines;
    private pendingRasterLayers;
    private markerClickListener;
    private circleClickListener;
    private groundImageClickListener;
    private markerDragStartListener;
    private markerDragListener;
    private markerDragEndListener;
    private markerAnimateStartListener;
    private markerAnimateEndListener;
    private polygonClickListener;
    private polylineClickListener;
    private readonly nativeMapExtensionEventHandlers;
    constructor(nativeRef: React.RefObject<MapLibreMapViewRef | null>, cameraPosition: MapCameraPosition);
    clearOverlays(): Promise<void>;
    moveCamera(position: MapCameraPosition): Promise<boolean>;
    animateCamera(position: MapCameraPosition, durationMillis: number): Promise<boolean>;
    fitBounds(bounds: GeoRectBounds, padding: number): Promise<boolean>;
    getCameraPosition(): MapCameraPosition | null;
    /**
     * ジェスチャ設定をネイティブへ転送する。web 版が地図エンジンへ直接適用するのに対し、
     * RN はネイティブのコントローラが `applyUISettings` を持つのでブリッジ 1 本で済む。
     */
    applyUISettings(settings: MapUISettings): void;
    compositionMarkers(data: MarkerState[]): Promise<void>;
    updateMarker(state: MarkerState): Promise<void>;
    compositionPolylines(data: PolylineState[]): Promise<void>;
    compositionCircles(data: CircleState[]): Promise<void>;
    updateCircle(state: CircleState): Promise<void>;
    hasCircle(state: CircleState): boolean;
    setOnCircleClickListener(listener: OnCircleEventHandler | null): void;
    compositionGroundImages(data: GroundImageState[]): Promise<void>;
    updateGroundImage(state: GroundImageState): Promise<void>;
    hasGroundImage(state: GroundImageState): boolean;
    setOnGroundImageClickListener(listener: OnGroundImageEventHandler | null): void;
    compositionPolygons(data: PolygonState[]): Promise<void>;
    updatePolygon(state: PolygonState): Promise<void>;
    hasPolygon(state: PolygonState): boolean;
    setOnPolygonClickListener(listener: OnPolygonEventHandler | null): void;
    updatePolyline(state: PolylineState): Promise<void>;
    hasPolyline(state: PolylineState): boolean;
    setOnPolylineClickListener(listener: OnPolylineEventHandler | null): void;
    compositionRasterLayers(data: RasterLayerState[]): Promise<void>;
    updateRasterLayer(state: RasterLayerState): Promise<void>;
    hasRasterLayer(state: RasterLayerState): boolean;
    upsertNativeMapExtension(extension: NativeMapExtensionDescriptor, eventHandler?: NativeMapExtensionEventHandler | null): void;
    removeNativeMapExtension(extensionId: string): void;
    onNativeMapExtensionEvent(event: NativeMapExtensionEvent$1): void;
    hasMarker(state: MarkerState): boolean;
    setOnMarkerClickListener(listener: OnMarkerEventHandler | null): void;
    setOnMarkerDragStart(listener: OnMarkerEventHandler | null): void;
    setOnMarkerDrag(listener: OnMarkerEventHandler | null): void;
    setOnMarkerDragEnd(listener: OnMarkerEventHandler | null): void;
    setOnMarkerAnimateStart(listener: OnMarkerEventHandler | null): void;
    setOnMarkerAnimateEnd(listener: OnMarkerEventHandler | null): void;
    setMarkerAnimationOverlayHost(_host: MarkerAnimationOverlayHost | null): void;
    setMapInitializedListener(listener: (() => void) | null): void;
    destroy(): void;
    onNativeMapLoaded(): void;
    onNativeMarkerCompositionBatchProcessed(generation: number, sequence: number): void;
    onNativeMapClick(point: GeoPoint): void;
    onNativeMapLongClick(point: GeoPoint): void;
    onNativeMarkerClick(markerId: string): void;
    onNativeCircleClick(circleId: string, clicked: GeoPoint): void;
    onNativeGroundImageClick(groundImageId: string, clicked: GeoPoint): void;
    onNativePolylineClick(polylineId: string, clicked: GeoPoint): void;
    onNativePolygonClick(polygonId: string, clicked: GeoPoint): void;
    onNativeMarkerDragStart(markerId: string, point: GeoPoint): void;
    onNativeMarkerDrag(markerId: string, point: GeoPoint): void;
    onNativeMarkerDragEnd(markerId: string, point: GeoPoint): void;
    onNativeMarkerAnimateStart(markerId: string): void;
    onNativeMarkerAnimateEnd(markerId: string): void;
    onNativeCameraMoveStart(camera: MapCameraPosition): void;
    onNativeCameraMove(camera: MapCameraPosition): void;
    onNativeCameraMoveEnd(camera: MapCameraPosition): void;
    private dispatchCommand;
    private flushPendingMarkerUpdates;
    private startPendingMarkerComposition;
    private waitForMarkerBatchAck;
    private cancelMarkerBatchAck;
    private cancelMarkerComposition;
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

interface NativeMapLibreMarkerState {
    id: string;
    position: MarkerState['position'];
    clickable: boolean;
    draggable: boolean;
    zIndex: number;
    icon: NativeMarkerIconPayload | null;
    animation: MarkerAnimation | null;
}
declare function markerStateToNative(state: MarkerState): NativeMapLibreMarkerState;

export { type MapLibreMap, MapLibreMapView, MapLibreMapViewHolder, type MapLibreMapViewProps, type MapLibreMapViewRef, MapLibreViewController, type MapLibreViewControllerInterface, type NativeMapLibreMarkerState, type NativeMapLibreViewEvent, type NativeMapLibreViewProps, type NativeMarkerTilingOptions, markerStateToNative, toNativeCameraPosition, toNativeMarkerTilingOptions };
