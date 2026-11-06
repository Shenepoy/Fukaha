import UIKit
import UniformTypeIdentifiers
import SwiftUI
import Shared

@objc(FukahaShareViewController)
class ShareViewController: UIViewController {
    private struct SharedItemCandidate {
        let provider: NSItemProvider
        let typeIdentifier: String
    }

    private let facade = FukahaIosFacade()
    private var settings = SettingsSnapshot.load()

    // Internal so simulator integration tests can verify the actual prepared payload.
    let model = FukahaShareModel()
    private var hostingController: UIHostingController<FukahaShareScreen>?

    private var preparedClean: String?
    private var preparedEmbed: String?
    private var hasAppeared = false
    private var pendingDefaultAction = false
    private var defaultActionStarted = false
    private var finished = false

    override func viewDidLoad() {
        super.viewDidLoad()
        switch settings.theme {
        case "Light": overrideUserInterfaceStyle = .light
        case "Dark": overrideUserInterfaceStyle = .dark
        default: overrideUserInterfaceStyle = .unspecified
        }
        view.backgroundColor = .systemBackground
        setupUI()
        loadSharedUrl()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        hasAppeared = true
        if pendingDefaultAction {
            pendingDefaultAction = false
            runDefaultActionIfNeeded()
        }
    }

    deinit {
        facade.close()
    }

    private func setupUI() {
        let rootView = FukahaShareScreen(
            model: model,
            isArabic: isArabic,
            mediaDownloadEnabled: settings.hasValidCobaltBaseUrl,
            onDismiss: { [weak self] in self?.cancel() },
            onShareCleaned: { [weak self] in self?.shareClean() },
            onShareEmbed: { [weak self] in self?.shareEmbed() },
            onShareMedia: { [weak self] in self?.shareMedia() },
            onCopyOriginal: { [weak self] in self?.copyOriginal() },
            onCopyCleaned: { [weak self] in self?.copyCleaned() },
            onCopyEmbed: { [weak self] in self?.copyEmbed() },
        )
        let host = UIHostingController(rootView: rootView)
        host.view.backgroundColor = .clear
        host.view.translatesAutoresizingMaskIntoConstraints = false
        addChild(host)
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        host.didMove(toParent: self)
        hostingController = host
    }

    private func loadSharedUrl() {
        model.loading = true
        model.downloading = false
        model.error = nil

        let extensionItems = extensionContext?.inputItems.compactMap { $0 as? NSExtensionItem } ?? []
        let typeIdentifiers = [
            UTType.url.identifier,
            UTType.plainText.identifier,
            UTType.text.identifier,
            UTType.html.identifier,
            UTType.data.identifier,
        ]
        let candidates = extensionItems.flatMap { item in
            (item.attachments ?? []).flatMap { provider in
                typeIdentifiers.compactMap { typeIdentifier in
                    provider.hasItemConformingToTypeIdentifier(typeIdentifier)
                        ? SharedItemCandidate(provider: provider, typeIdentifier: typeIdentifier)
                        : nil
                }
            }
        }
        let fallbackTexts = extensionItems.flatMap { item in
            [item.attributedContentText?.string, item.attributedTitle?.string]
                .compactMap { $0 }
        }
        let fallbackText = fallbackTexts.first(where: { facade.extractUrl(text: $0) != nil })
            ?? fallbackTexts.first(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty })

        guard !candidates.isEmpty else {
            if let fallbackText {
                prepare(from: fallbackText)
            } else {
                showNoLink()
            }
            return
        }

        loadSharedItem(from: candidates, at: 0, fallbackText: fallbackText)
    }

    private func loadSharedItem(
        from candidates: [SharedItemCandidate],
        at index: Int,
        fallbackText: String?,
    ) {
        guard index < candidates.count else {
            if let fallbackText {
                prepare(from: fallbackText)
            } else {
                showNoLink()
            }
            return
        }

        let candidate = candidates[index]
        candidate.provider.loadItem(forTypeIdentifier: candidate.typeIdentifier, options: nil) { [weak self] item, _ in
            DispatchQueue.main.async {
                guard let self, !self.finished else { return }
                guard let text = self.sharedText(from: item),
                      self.facade.extractUrl(text: text) != nil else {
                    self.loadSharedItem(
                        from: candidates,
                        at: index + 1,
                        fallbackText: fallbackText,
                    )
                    return
                }
                self.prepare(from: text)
            }
        }
    }

    private func sharedText(from item: NSSecureCoding?) -> String? {
        let value: String? = {
            if let url = item as? URL { return url.absoluteString }
            if let url = item as? NSURL { return url.absoluteString }
            if let text = item as? String { return text }
            if let text = item as? NSString { return text as String }
            if let text = item as? NSAttributedString { return text.string }
            if let data = item as? Data { return String(data: data, encoding: .utf8) }
            return nil
        }()
        guard let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return value
    }

    private func showNoLink() {
        model.loading = false
        model.error = t("عذراً، لم نجد رابطاً في النص المُشارَك", "No link found")
    }

    private func prepare(from text: String?) {
        guard let text else {
            model.loading = false
            model.error = t("عذراً، لم نجد رابطاً في النص المُشارَك", "No link found")
            return
        }

        model.loading = true
        model.downloading = false
        model.error = nil

        facade.prepareWithPreferences(
            text: text,
            cobaltBaseUrl: settings.cobaltBaseUrl,
            resolveShortLinks: settings.resolveShortLinks,
            preferredFixers: settings.preferredFixers,
            disabledEmbedPlatforms: Array(settings.disabledEmbedPlatforms),
            useOfficialShortLinks: settings.useOfficialShortLinks
        ) { [weak self] clean, embed, platform, error in
            DispatchQueue.main.async {
                guard let self, !self.finished else { return }
                self.model.loading = false
                if let error {
                    self.model.error = self.localizedError(error)
                    return
                }
                self.preparedClean = clean
                self.preparedEmbed = embed
                self.model.originalUrl = self.facade.extractUrl(text: text) ?? text
                self.model.cleanedUrl = clean
                self.model.embedUrl = embed
                self.model.platform = self.platformDisplayName(platform)
                self.model.error = clean == nil
                    ? self.t("تعذّر تجهيز الرابط", "Could not prepare the link")
                    : nil
                self.scheduleDefaultActionIfNeeded()
            }
        }
    }

    private func scheduleDefaultActionIfNeeded() {
        guard effectiveDefaultAction != "Ask" else { return }
        guard preparedClean != nil, model.error == nil else { return }
        if hasAppeared {
            runDefaultActionIfNeeded()
        } else {
            pendingDefaultAction = true
        }
    }

    private func runDefaultActionIfNeeded() {
        guard !defaultActionStarted else { return }
        guard preparedClean != nil, model.error == nil else { return }
        defaultActionStarted = true
        switch effectiveDefaultAction {
        case "Clean":
            shareClean()
        case "Embed":
            shareEmbed()
        case "Download":
            if settings.hasValidCobaltBaseUrl {
                shareMedia()
            }
        default:
            break
        }
    }

    @objc private func shareClean() {
        guard let text = preparedClean else { return }
        presentShare([text])
    }

    @objc private func shareEmbed() {
        presentShare([preparedEmbed ?? preparedClean ?? ""])
    }

    @objc private func shareMedia() {
        guard !finished, !model.downloading, let url = preparedClean else { return }
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
            cacheDirPath: cache.path
        ) { [weak self] path, _, error in
            DispatchQueue.main.async {
                guard let self, !self.finished else { return }
                self.model.downloading = false
                if let path {
                    let fileUrl = URL(fileURLWithPath: path)
                    self.presentShare([fileUrl]) {
                        if self.settings.deleteCacheAfterShare {
                            try? FileManager.default.removeItem(at: fileUrl)
                        }
                    }
                } else {
                    self.model.error = self.localizedError(error ?? self.t(
                        "عذراً، تعذّر تحميل الملف. حاول مجدداً",
                        "Download failed — large videos may fail in Share Extension"
                    ))
                }
            }
        }
    }

    private func copyOriginal() {
        if let originalUrl = model.originalUrl {
            copyText(originalUrl)
        }
    }

    private func copyCleaned() {
        if let cleanedUrl = model.cleanedUrl {
            copyText(cleanedUrl)
        }
    }

    private func copyEmbed() {
        if let embedUrl = model.embedUrl {
            copyText(embedUrl)
        }
    }

    private func copyText(_ text: String) {
        UIPasteboard.general.string = text
        model.copiedMessage = t("تم النسخ", "Copied")
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { [weak self] in
            guard let self, self.model.copiedMessage != nil else { return }
            self.model.copiedMessage = nil
        }
    }

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

    private func localizedError(_ raw: String) -> String {
        switch raw {
        case "No link found":
            return t("عذراً، لم نجد رابطاً في النص المُشارَك", "No link found")
        case "Download failed", "cobalt.base_url.missing":
            return t(
                "عذراً، تعذّر تحميل الملف. حاول مجدداً",
                "Download failed — large videos may fail in Share Extension"
            )
        default:
            return raw
        }
    }

    private func presentShare(_ items: [Any], onDone: (() -> Void)? = nil) {
        guard !finished else { return }
        let vc = UIActivityViewController(activityItems: items, applicationActivities: nil)
        if let popover = vc.popoverPresentationController {
            popover.sourceView = view
            popover.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
            popover.permittedArrowDirections = []
        }
        vc.completionWithItemsHandler = { [weak self] _, completed, _, _ in
            DispatchQueue.main.async {
                onDone?()
                // Cancelling the system share sheet returns to the link choices.
                if completed { self?.cancel() }
            }
        }
        present(vc, animated: true)
    }

    @objc private func cancel() {
        guard !finished else { return }
        finished = true
        facade.close()
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }

    private func t(_ ar: String, _ en: String) -> String {
        switch settings.language {
        case "Arabic": return ar
        case "English", "Japanese", "SimplifiedChinese", "Spanish": return en
        default:
            return Locale.current.language.languageCode?.identifier == "ar" ? ar : en
        }
    }
}
