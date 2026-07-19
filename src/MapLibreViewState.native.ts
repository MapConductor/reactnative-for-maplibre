import { useState } from 'react';
import {
  MapViewState,
  type MapViewStateInterface,
  type GeoPoint,
  type MapCameraPosition,
  type MapViewControllerInterface,
  type MapViewHolder,
  MapCameraPosition as MapCameraPositionNS,
  createRandomId,
} from '@mapconductor/js-sdk-core';
import { MapLibreDesign, type MapLibreMapDesignType } from './MapLibreDesign.native';

export interface MapLibreViewStateInterface extends MapViewStateInterface<MapLibreMapDesignType> {}

export interface MapLibreViewStateParams {
  id?: string;
  mapDesignType?: MapLibreMapDesignType;
  cameraPosition?: MapCameraPosition;
}

export class MapLibreViewState
  extends MapViewState<MapLibreMapDesignType>
  implements MapLibreViewStateInterface
{
  readonly id: string;
  private _cameraPosition: MapCameraPosition;
  private _mapDesignType: MapLibreMapDesignType;
  private _controller: MapViewControllerInterface | null = null;
  private _cameraPositionChangeListener: ((camera: MapCameraPosition) => void) | null = null;

  constructor({
    id = createRandomId(),
    mapDesignType = MapLibreDesign.DemoTiles,
    cameraPosition = MapCameraPositionNS.Default,
  }: MapLibreViewStateParams = {}) {
    super();
    this.id = id;
    this._cameraPosition = cameraPosition;
    this._mapDesignType = mapDesignType;
  }

  override get cameraPosition(): MapCameraPosition {
    return this._cameraPosition;
  }

  override get mapDesignType(): MapLibreMapDesignType {
    return this._mapDesignType;
  }

  override set mapDesignType(value: MapLibreMapDesignType) {
    this._mapDesignType = value;
  }

  override moveCameraTo(position: GeoPoint, durationMillis?: number): void;
  override moveCameraTo(cameraPosition: MapCameraPosition, durationMillis?: number): void;
  override moveCameraTo(
    positionOrCamera: GeoPoint | MapCameraPosition,
    durationMillis?: number
  ): void {
    const newPosition =
      'zoom' in positionOrCamera
        ? this.resolveCameraPosition(positionOrCamera as MapCameraPosition)
        : this._cameraPosition.copy({ position: positionOrCamera as GeoPoint });

    if (durationMillis && durationMillis > 0) {
      this._controller?.animateCamera(newPosition, { duration: durationMillis });
    } else {
      this._controller?.moveCamera(newPosition);
    }
    this._cameraPosition = newPosition;
    this._cameraPositionChangeListener?.(newPosition);
  }

  override getMapViewHolder(): MapViewHolder<unknown, unknown> | null {
    return this._controller?.holder ?? null;
  }

  setController(ctrl: MapViewControllerInterface | null): void {
    this._controller = ctrl;
    if (ctrl) void ctrl.moveCamera(this._cameraPosition);
  }

  updateCameraPosition(camera: MapCameraPosition): void {
    this._cameraPosition = camera;
    this._cameraPositionChangeListener?.(camera);
  }

  setCameraPositionChangeListener(listener: ((camera: MapCameraPosition) => void) | null): void {
    this._cameraPositionChangeListener = listener;
  }

  private resolveCameraPosition(target: MapCameraPosition): MapCameraPosition {
    const isUnspecified = target.zoom === 0 && target.bearing === 0 && target.tilt === 0;
    if (isUnspecified) return this._cameraPosition.copy({ position: target.position });
    return target;
  }
}

export function useMapLibreViewState(params: MapLibreViewStateParams = {}): MapLibreViewState {
  const [state] = useState(() => new MapLibreViewState(params));
  return state;
}

export function createMapLibreViewState(params: MapLibreViewStateParams = {}): MapLibreViewState {
  return new MapLibreViewState(params);
}
