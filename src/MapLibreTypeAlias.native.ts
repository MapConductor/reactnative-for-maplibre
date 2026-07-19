import type React from 'react';
import type { HostComponent, NativeMethods } from 'react-native';
import type { NativeMapLibreViewProps } from './MapLibreViewNativeComponent';

export type MapLibreMapViewRef =
  React.ComponentRef<HostComponent<NativeMapLibreViewProps>> & NativeMethods;
export type MapLibreMapView = MapLibreMapViewRef | null;
export type MapLibreMap = null;
