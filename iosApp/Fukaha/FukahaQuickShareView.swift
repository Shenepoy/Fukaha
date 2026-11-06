import Foundation
import SwiftUI
import UIKit
import Shared

/**
 * Runs the settings quick-link field through the same processed share UI as an
 * external share. It follows the saved default action, just like a link shared
 * from Safari or Firefox, and only shows the chooser when Ask is selected.
 */
struct FukahaQuickShareView: View {
    let text: String
    let settings: SettingsSnapshot
    let client: FukahaClient
    private var facade: FukahaIosFacade { client.facade }
    let onDismiss: () -> Void

    @StateObject private var model = FukahaShareModel()
    @State private var didStartPreparing = false
    @State private var defaultActionStarted = false
    @State private var quickShareCancelled = false
    @State private var pendingShare: FukahaPendingShare?

    private var isArabic: Bool {
        switch settings.language {
        case "Arabic": return true
        case "English", "Japanese", "SimplifiedChinese", "Spanish": return false
        default:
            return Locale.current.language.languageCode?.identifier == "ar"
        }
    }

    private var effectiveDefaultAction: String {
        switch settings.defaultAction.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "clean": return "Clean"
        case "embed": return "Embed"
        case "download": return settings.hasValidCobaltBaseUrl ? "Download" : "Ask"
        default: return "Ask"
        }
    }

    private var shouldShowSelection: Bool {
        effectiveDefaultAction == "Ask" || model.error != nil || quickShareCancelled
    }

    var body: some View {
        Group {
            if shouldShowSelection {
                selectionScreen
            } else {
                autoActionProgress
            }
        }
        .task { prepare() }
        .sheet(item: $pendingShare) { pending in
            FukahaActivityView(items: pending.items) { completed in
                pending.cleanup?()
                pendingShare = nil
                if completed {
                    onDismiss()
                } else {
                    // A cancelled automatic share should return to the same
                    // choices that an Ask action would have shown.
                    quickShareCancelled = true
                }
            }
        }
    }

    private var selectionScreen: some View {
        FukahaShareScreen(
            model: model,
            isArabic: isArabic,
            mediaDownloadEnabled: settings.hasValidCobaltBaseUrl,
            onDismiss: onDismiss,
            onShareCleaned: { queueTextShare(model.cleanedUrl) },
            onShareEmbed: { queueTextShare(model.embedUrl ?? model.cleanedUrl) },
            onShareMedia: shareMedia,
            onCopyOriginal: { copyText(model.originalUrl) },
            onCopyCleaned: { copyText(model.cleanedUrl) },
            onCopyEmbed: { copyText(model.embedUrl) },
        )
    }

    private var autoActionProgress: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(isArabic ? "فكها" : "Fukaha")
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Color.fukahaOnSurface)

            HStack(spacing: 10) {
                ProgressView()
                    .tint(Color.fukahaPrimary)
                    .frame(width: 24, height: 24)
                Text(autoActionStatus)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.fukahaOnSurface)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .background(Color.fukahaSurfaceLow.ignoresSafeArea())
        .environment(\.layoutDirection, isArabic ? .rightToLeft : .leftToRight)
    }

    private var autoActionStatus: String {
        if model.downloading {
            return textFor(en: "Downloading…", ar: "جاري تحميل الوسائط…")
        }
        switch effectiveDefaultAction {
        case "Clean": return textFor(en: "Sharing the cleaned link…", ar: "جاري مشاركة الرابط المنظّف…")
        case "Embed": return textFor(en: "Sharing the preview link…", ar: "جاري مشاركة رابط المعاينة…")
        case "Download": return textFor(en: "Preparing the media…", ar: "جاري تجهيز الوسائط…")
        default: return textFor(en: "Preparing…", ar: "جاري التجهيز…")
        }
    }

    private func prepare() {
        guard !didStartPreparing else { return }
        didStartPreparing = true

        let originalUrl = facade.extractUrl(text: text) ?? text
        facade.prepareWithPreferences(
            text: text,
            cobaltBaseUrl: settings.cobaltBaseUrl,
            resolveShortLinks: settings.resolveShortLinks,
            preferredFixers: settings.preferredFixers,
            disabledEmbedPlatforms: Array(settings.disabledEmbedPlatforms),
            useOfficialShortLinks: settings.useOfficialShortLinks,
        ) { clean, embed, platform, error in
            DispatchQueue.main.async {
                guard !client.isClosed else { return }
                model.loading = false
                if let error {
                    model.error = localizedError(error)
                    return
                }
                guard let clean else {
                    model.error = textFor(en: "Could not prepare the link", ar: "تعذّر تجهيز الرابط")
                    return
                }
                model.originalUrl = originalUrl
                model.cleanedUrl = clean
                model.embedUrl = embed
                model.platform = platformDisplayName(platform)
                runDefaultActionIfNeeded()
            }
        }
    }

    private func runDefaultActionIfNeeded() {
        guard effectiveDefaultAction != "Ask",
              !defaultActionStarted,
              !model.loading,
              !model.downloading,
              model.error == nil,
              model.cleanedUrl != nil else {
            return
        }

        defaultActionStarted = true
        switch effectiveDefaultAction {
        case "Clean":
            queueTextShare(model.cleanedUrl)
        case "Embed":
            queueTextShare(model.embedUrl ?? model.cleanedUrl)
        case "Download":
            shareMedia()
        default:
            break
        }
    }

    private func queueTextShare(_ value: String?) {
        guard let value, !value.isEmpty else { return }
        pendingShare = FukahaPendingShare(items: [value])
    }

    private func shareMedia() {
        guard !client.isClosed, !model.downloading, pendingShare == nil,
              let url = model.cleanedUrl else { return }
        model.downloading = true
        model.error = nil

        let cache: URL
        do {
            cache = try FukahaMediaCache.directory()
        } catch {
            model.downloading = false
            model.error = error.localizedDescription
            return
        }

        facade.download(
            url: url,
            cobaltBaseUrl: settings.cobaltBaseUrl,
            cobaltApiKey: settings.cobaltApiKey,
            cacheDirPath: cache.path,
        ) { path, _, error in
            DispatchQueue.main.async {
                guard !client.isClosed else {
                    if settings.deleteCacheAfterShare, let path {
                        try? FileManager.default.removeItem(atPath: path)
                    }
                    return
                }
                model.downloading = false
                guard let path else {
                    model.error = localizedError(error ?? textFor(
                        en: "Download failed — large videos may fail in Share Extension",
                        ar: "عذراً، تعذّر تحميل الملف. حاول مجدداً",
                    ))
                    return
                }

                let fileUrl = URL(fileURLWithPath: path)
                pendingShare = FukahaPendingShare(
                    items: [fileUrl],
                    cleanup: settings.deleteCacheAfterShare
                        ? { try? FileManager.default.removeItem(at: fileUrl) }
                        : nil,
                )
            }
        }
    }

    private func copyText(_ value: String?) {
        guard let value else { return }
        UIPasteboard.general.string = value
        model.copiedMessage = textFor(en: "Copied", ar: "تم النسخ")
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
            guard model.copiedMessage != nil else { return }
            model.copiedMessage = nil
        }
    }

    private func localizedError(_ raw: String) -> String {
        switch raw {
        case "No link found":
            return textFor(en: "No link found", ar: "عذراً، لم نجد رابطاً في النص المُشارَك")
        case "Download failed", "cobalt.base_url.missing":
            return textFor(
                en: "Download failed — large videos may fail in Share Extension",
                ar: "عذراً، تعذّر تحميل الملف. حاول مجدداً",
            )
        default:
            return raw
        }
    }

    private func textFor(en: String, ar: String) -> String {
        isArabic ? ar : en
    }

    private func platformDisplayName(_ platform: String?) -> String? {
        guard let platform, !platform.isEmpty else { return nil }
        switch platform.lowercased() {
        case "x": return "X (Twitter)"
        case "youtube": return "YouTube"
        case "tiktok": return "TikTok"
        case "instagram": return "Instagram"
        case "facebook": return "Facebook"
        case "reddit": return "Reddit"
        case "deviantart": return "DeviantArt"
        case "bilibili": return "Bilibili"
        default: return platform.capitalized
        }
    }
}

private struct FukahaPendingShare: Identifiable {
    let id = UUID()
    let items: [Any]
    let cleanup: (() -> Void)?

    init(items: [Any], cleanup: (() -> Void)? = nil) {
        self.items = items
        self.cleanup = cleanup
    }
}

private struct FukahaActivityView: UIViewControllerRepresentable {
    let items: [Any]
    let onComplete: (Bool) -> Void

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(
            activityItems: items,
            applicationActivities: nil,
        )
        controller.completionWithItemsHandler = { _, completed, _, _ in
            DispatchQueue.main.async {
                onComplete(completed)
            }
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
