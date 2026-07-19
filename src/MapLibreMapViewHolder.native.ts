import type React from 'react';
import type { GeoPoint, MapViewHolder, Offset } from '@mapconductor/js-sdk-core';
import type { MapLibreMapViewRef } from './MapLibreTypeAlias.native';

export class MapLibreMapViewHolder
  implements MapViewHolder<MapLibreMapViewRef | null, null>
{
  readonly map = null;

  constructor(private readonly nativeRef: React.RefObject<MapLibreMapViewRef | null>) {}

  get mapView(): MapLibreMapViewRef | null {
    return this.nativeRef.current;
  }

  toScreenOffset(_position: GeoPoint): null {
    return null;
  }

  async fromScreenOffset(_offset: Offset): Promise<GeoPoint | null> {
    return null;
  }

  fromScreenOffsetSync(_offset: Offset): GeoPoint | null {
    return null;
  }
}
