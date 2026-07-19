import type { MapDesignTypeInterface } from '@mapconductor/js-sdk-core';

export interface MapLibreMapDesignType extends MapDesignTypeInterface<string> {
  readonly styleJsonURL: string;
}

export type MapLibreDesignType = MapLibreMapDesignType;

export class MapLibreDesign implements MapLibreMapDesignType {
  readonly id: string;
  readonly styleJsonURL: string;

  constructor(id: string, styleJsonURL: string) {
    this.id = id;
    this.styleJsonURL = styleJsonURL;
  }

  getValue(): string {
    return `mapDesign_id=${this.id},style=${this.styleJsonURL}`;
  }

  static readonly DemoTiles = new MapLibreDesign(
    'demo',
    'https://demotiles.maplibre.org/style.json',
  );
  static readonly OsmBright = new MapLibreDesign(
    'osm-bright',
    'https://tile.openstreetmap.jp/styles/osm-bright/style.json',
  );
  static readonly OsmBrightEn = new MapLibreDesign(
    'osm-bright-en',
    'https://tile.openstreetmap.jp/styles/osm-bright-en/style.json',
  );
  static readonly OsmBrightJa = new MapLibreDesign(
    'osm-bright-ja',
    'https://tile.openstreetmap.jp/styles/osm-bright-ja/style.json',
  );
  static readonly MapTilerTonerJa = new MapLibreDesign(
    'maptiler-toner-ja',
    'https://tile.openstreetmap.jp/styles/maptiler-toner-ja/style.json',
  );
  static readonly MapTilerTonerEn = new MapLibreDesign(
    'maptiler-toner-en',
    'https://tile.openstreetmap.jp/styles/maptiler-toner-en/style.json',
  );
  static readonly MapTilerBasicEn = new MapLibreDesign(
    'maptiler-basic-en',
    'https://tile.openstreetmap.jp/styles/maptiler-basic-en/style.json',
  );
  static readonly MapTilerBasicJa = new MapLibreDesign(
    'maptiler-basic-ja',
    'https://tile.openstreetmap.jp/styles/maptiler-basic-ja/style.json',
  );
  static readonly OpenMapTiles = new MapLibreDesign(
    'openmaptiles',
    'https://tile.openstreetmap.jp/styles/openmaptiles/style.json',
  );
}
