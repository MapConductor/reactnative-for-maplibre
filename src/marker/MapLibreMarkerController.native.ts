/**
 * マーカーの変換は全 RN プロバイダで同一なので js-sdk-react に集約してある。
 * ここは既存 import 経路を保つための再輸出のみ。
 */
export {
  markerStateToNative,
  type NativeMarkerStatePayload as NativeMapLibreMarkerState,
} from '@mapconductor/js-sdk-react/internal';
