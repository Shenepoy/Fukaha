import SwiftUI
import WebKit
import Shared

/// Shared by the main app, Share Extension and Action Extension.
struct FukahaInlinePreview: View {
    let originalUrl: String
    let embedUrl: String
    let isArabic: Bool
    @Environment(\.scenePhase) private var scenePhase
    @State private var document: String?
    @State private var loading = false
    @State private var unavailable = false
    @State private var facade: FukahaIosFacade?
    @State private var requestId = UUID()

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let document {
                Button(isArabic ? "إغلاق المعاينة" : "Close preview", action: close)
                InlinePreviewPlayer(document: document)
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
            } else {
                Button(action: load) {
                    if loading { ProgressView() }
                    Text(loading ? (isArabic ? "جاري تحميل المعاينة…" : "Loading preview…")
                        : unavailable ? (isArabic ? "إعادة محاولة المعاينة" : "Retry preview")
                        : (isArabic ? "عرض المعاينة" : "Show preview"))
                }
                .disabled(loading)
                if unavailable {
                    Text(isArabic ? "تعذّر عرض المعاينة. جرّب مصدراً آخر." : "Preview unavailable. Try another source.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .task(id: embedUrl) { load() }
        .onDisappear(perform: close)
        .onChange(of: scenePhase) { phase in
            if phase != .active { close() }
            else if document == nil && !loading && !unavailable { load() }
        }
    }

    private func load() {
        guard !loading else { return }
        close()
        loading = true
        unavailable = false
        let id = UUID()
        requestId = id
        let client = FukahaIosFacade()
        facade = client
        client.loadPreview(
            originalUrl: originalUrl,
            embedUrl: embedUrl,
            playLabel: isArabic ? "تشغيل" : "Play",
            errorLabel: isArabic ? "تعذّر تشغيل الوسائط. جرّب مصدراً آخر." : "Could not play this media. Try another source."
        ) { html in
            DispatchQueue.main.async {
                guard requestId == id else { return }
                document = html
                unavailable = html == nil
                loading = false
                facade?.close()
                facade = nil
            }
        }
    }

    private func close() {
        requestId = UUID()
        facade?.close()
        facade = nil
        loading = false
        document = nil
    }
}

private struct InlinePreviewPlayer: UIViewRepresentable {
    let document: String

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = .all
        let view = WKWebView(frame: .zero, configuration: configuration)
        view.navigationDelegate = context.coordinator
        view.scrollView.isScrollEnabled = true
        view.isOpaque = false
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {
        guard context.coordinator.document != document else { return }
        context.coordinator.document = document
        view.loadHTMLString(document, baseURL: nil)
    }

    static func dismantleUIView(_ view: WKWebView, coordinator: Coordinator) {
        view.stopLoading()
        view.loadHTMLString("", baseURL: nil)
        view.navigationDelegate = nil
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var document: String?
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            // Only the local player document may navigate. Media requests are subresources.
            decisionHandler(navigationAction.request.url?.absoluteString == "about:blank" ? .allow : .cancel)
        }
    }
}
