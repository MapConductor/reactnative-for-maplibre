import MapConductorCore
@_spi(MapConductorDriver) import MapConductorForMapLibre
import MapConductorReactMarkerClustering
import MapConductorReactNativeCore
import MapLibre
import UIKit

/// RN の MapLibre ビュー。
///
/// コマンドの受け口・マーカー取り込み・スクリーン座標の通知は
/// ``MCReactNativeMapViewBase``（js-sdk-react/ios）が全部持っているので、ここは
/// プロバイダ固有のアダプタを差すだけ。android の `MapLibreMapViewWrapper` が
/// `createMapLibreViewController()` を呼ぶだけになっているのと同じ形。
@objc(MCMapLibreReactNativeView)
public final class MapLibreReactNativeView: MCReactNativeMapViewBase {
    public override func makeHost() -> MCReactNativeMapHost {
        MapLibreReactNativeHost()
    }
}

/// `MapLibreMapHost`（ios-sdk）を RN の基底クラスが扱える非ジェネリックな形へ翻訳する。
@MainActor
final class MapLibreReactNativeHost: MCReactNativeMapHost {
    weak var mcDelegate: MCReactNativeMapHostDelegate?

    private let state = MapLibreViewState()
    private lazy var mapHost: MapLibreMapHost = {
        MapLibreMapHost(
            state: state,
            handlers: MapViewHandlers(
                onMapLoaded: { [weak self] _ in self?.mcDelegate?.mcMapLoaded() },
                onMapClick: { [weak self] point in self?.mcDelegate?.mcMapClick(point) },
                onMapLongClick: { [weak self] point in self?.mcDelegate?.mcMapLongClick(point) },
                onCameraMoveStart: { [weak self] camera in self?.mcDelegate?.mcCameraMoveStart(camera) },
                onCameraMove: { [weak self] camera in self?.mcDelegate?.mcCameraMove(camera) },
                onCameraMoveEnd: { [weak self] camera in self?.mcDelegate?.mcCameraMoveEnd(camera) }
            )
        )
    }()

    var mcServiceRegistry: MutableMapServiceRegistry { state.serviceRegistry }
    var mcCameraZoom: Double { state.cameraPosition.zoom }

    func mcMakeMapView(content: MapViewContent) -> UIView {
        mapHost.makeMapView(cameraRestriction: nil, content: content)
    }

    func mcUpdateContent(_ content: MapViewContent) {
        mapHost.updateContent(content)
        mapHost.updateInfoBubbleLayouts()
    }

    func mcSyncNativeViewSettings() {
        mapHost.syncNativeViewSettings()
    }

    func mcUnbind() {
        mapHost.unbind()
    }

    func mcSetMapDesign(id: String?) {
        let styleUrl = Self.mapLibreStyleURL(from: id)
        state.mapDesignType = MapLibreDesign(id: styleUrl, styleJsonURL: styleUrl)
    }

    func mcMoveCamera(_ camera: MapCameraPosition, durationMillis: Int64?) {
        if let durationMillis {
            state.moveCameraTo(cameraPosition: camera, durationMillis: durationMillis)
        } else {
            state.moveCameraTo(cameraPosition: camera)
        }
    }

    func mcFitBounds(_ bounds: GeoRectBounds, padding: Int) {
        state.fitBounds(bounds: bounds, padding: padding)
    }

    func mcApplyUISettings(_ settings: MapUISettings) {
        state.uiSettings = settings
    }

    func mcToScreenOffset(_ position: GeoPointProtocol) -> CGPoint? {
        state.getMapViewHolder()?.toScreenOffset(position: position)
    }

    func mcMakeLocalExtensionRenderer(
        type: String,
        extensionId: String,
        eventSink: @escaping NativeMapExtensionEventSink
    ) -> NativeMapExtensionRenderer? {
        guard type == "marker-clustering" else { return nil }
        return MarkerClusterExtensionRenderer<MapLibreActualMarker>(extensionId: extensionId, eventSink: eventSink)
    }

    /// JS から来るデザイン ID は `...style=<url>` 形式のことがある。
    private static func mapLibreStyleURL(from value: String?) -> String {
        let defaultURL = "https://demotiles.maplibre.org/style.json"
        guard let value, !value.trimmingCharacters(in: .whitespaces).isEmpty else { return defaultURL }
        guard let range = value.range(of: "style=") else { return value }
        let style = String(value[range.upperBound...])
        return style.isEmpty ? defaultURL : style
    }
}
