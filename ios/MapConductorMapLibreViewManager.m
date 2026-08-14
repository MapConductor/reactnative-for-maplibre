#import "MapConductorMapLibreViewManager.h"

// 実装は MCReactNativeMapViewManagerBase（js-sdk-react/ios）にある。
// ここに書くのは「どの Swift ビューか」「JS でのコンポーネント名は何か」だけ。
@implementation MapConductorMapLibreViewManager

RCT_EXPORT_MODULE(MapLibreMapView)

- (NSString *)mapViewClassName
{
  return @"MCMapLibreReactNativeView";
}


// イベント / prop / コマンドの宣言。RN は Commands をこのクラス自身の
// メソッド一覧からしか組み立てないため、基底に置けず各クラスで展開する。
MC_REACT_NATIVE_MAP_VIEW_MANAGER_BODY
@end

__attribute__((constructor)) static void MCMapLibreRegisterLegacyInterop(void)
{
  MCReactNativeRegisterLegacyViewManagerInterop(@"MapLibreMapView");
}
