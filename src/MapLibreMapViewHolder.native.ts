import type React from 'react';
import { MapViewHolderBase } from '@mapconductor/js-sdk-core';
import type { GeoPoint, Offset } from '@mapconductor/js-sdk-core';
import type { MapLibreMapViewRef } from './MapLibreTypeAlias.native';

export class MapLibreMapViewHolder
  extends MapViewHolderBase<MapLibreMapViewRef | null, null>
{
  readonly map = null;

  constructor(private readonly nativeRef: React.RefObject<MapLibreMapViewRef | null>) {
    super();
  }

  get mapView(): MapLibreMapViewRef | null {
    return this.nativeRef.current;
  }

  toScreenOffset(_position: GeoPoint): null {
    return null;
  }

  fromScreenOffsetSync(_offset: Offset): GeoPoint | null {
    return null;
  }
}
