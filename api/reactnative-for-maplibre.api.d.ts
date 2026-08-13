import { MapLibreViewStateInterface } from '@mapconductor/react-for-maplibre/state';
export { MapLibreDesign, MapLibreMapDesignType, MapLibreViewState, MapLibreViewStateInterface, MapLibreViewStateParams, useMapLibreViewState } from '@mapconductor/react-for-maplibre/state';
import * as React from 'react';
import React__default from 'react';
import { HostComponent, NativeMethods, StyleProp, ViewStyle } from 'react-native';
import { NativeMapViewProps, NativeMapViewEvent, ReactNativeBridgeMapViewController, ReactNativeMapViewHolder } from '@mapconductor/js-sdk-react/internal';
export { NativeMarkerStatePayload as NativeMapLibreMarkerState, NativeMarkerTilingOptions, markerStateToNative, toNativeCameraPosition, toNativeMarkerTilingOptions } from '@mapconductor/js-sdk-react/internal';
import { MapViewControllerInterface, MarkerTilingOptions } from '@mapconductor/js-sdk-core';
import { MapViewBaseProps } from '@mapconductor/js-sdk-react/native';

type NativeMapLibreViewEvent<T> = NativeMapViewEvent<T>;
interface NativeMapLibreViewProps extends NativeMapViewProps {
}

type MapLibreMapViewRef = React__default.ComponentRef<HostComponent<NativeMapLibreViewProps>> & NativeMethods;
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
    children?: React__default.ReactNode;
    markerTilingOptions?: MarkerTilingOptions;
}

/**
 * ネイティブイベントの配線・オーバーレイ収集・InfoBubble レイヤは全 RN プロバイダで
 * 同一なので {@link NativeMapViewHost} に集約してある。ここで渡すのは
 * 「どのネイティブビューか」「デザインをどう文字列化するか」だけ。
 */
declare function MapLibreMapView(props: MapLibreMapViewProps): React.JSX.Element;

export { type MapLibreMap, MapLibreMapView, MapLibreMapViewHolder, type MapLibreMapViewProps, type MapLibreMapViewRef, MapLibreViewController, type MapLibreViewControllerInterface, type NativeMapLibreViewEvent, type NativeMapLibreViewProps };
