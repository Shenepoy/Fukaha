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

    private let model = FukahaShareModel()
    private var hostingController: UIHostingController<FukahaShareScreen>?

    private var preparedClean: String?
    private var preparedEmbed: String?
    private var hasAppeared = false
    private var pendingDefaultAction = false
    private var defaultActionStarted = false

    override func viewDidLoad() {
        super.viewDidLoad()
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
                guard let self else { return }
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

        let preferred: String? = {
            if let url = facade.extractUrl(text: text),
               let platform = facade.detectPlatform(url: url) {
                return settings.preferredFixers[platform]
            }
            return nil
        }()

        facade.prepare(
            text: text,
            cobaltBaseUrl: settings.cobaltBaseUrl,
            resolveShortLinks: settings.resolveShortLinks,
            preferredFixerHost: preferred
        ) { clean, embed, platform, error in
            DispatchQueue.main.async {
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
        guard let url = preparedClean else { return }
        model.downloading = true
        model.error = nil

        let cache = FileManager.default.temporaryDirectory.appendingPathComponent("fukaha", isDirectory: true)
        try? FileManager.default.createDirectory(at: cache, withIntermediateDirectories: true)

        facade.download(
            url: url,
            cobaltBaseUrl: settings.cobaltBaseUrl,
            cobaltApiKey: settings.cobaltApiKey,
            cacheDirPath: cache.path
        ) { path, mime, error in
            DispatchQueue.main.async {
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
        let vc = UIActivityViewController(activityItems: items, applicationActivities: nil)
        vc.completionWithItemsHandler = { _, _, _, _ in
            onDone?()
            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
        }
        present(vc, animated: true)
    }

    @objc private func cancel() {
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

struct SettingsSnapshot {
    var defaultAction: String = "Ask"
    var cobaltBaseUrl: String = ""
    var cobaltApiKey: String = ""
    var resolveShortLinks: Bool = true
    var deleteCacheAfterShare: Bool = true
    var language: String = "System"
    var preferredFixers: [String: String] = [:]

    static let suite = UserDefaults(suiteName: "group.app.fukaha") ?? .standard
    private static let legacyPublicCobalt = "https://api.cobalt.tools"
    private static let cobaltPublicClearedKey = "cobalt_public_default_cleared"

    var hasValidCobaltBaseUrl: Bool {
        let trimmed = cobaltBaseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        let lower = trimmed.lowercased()
        return lower.hasPrefix("http://") || lower.hasPrefix("https://")
    }

    static func load() -> SettingsSnapshot {
        let d = suite
        migrateCobaltPublicDefaultIfNeeded(d)
        var s = SettingsSnapshot()
        s.defaultAction = d.string(forKey: "default_action") ?? "Ask"
        s.cobaltBaseUrl = d.string(forKey: "cobalt_base_url") ?? ""
        s.cobaltApiKey = d.string(forKey: "cobalt_api_key") ?? ""
        if d.object(forKey: "resolve_short_links") != nil {
            s.resolveShortLinks = d.bool(forKey: "resolve_short_links")
        }
        if d.object(forKey: "delete_cache_after_share") != nil {
            s.deleteCacheAfterShare = d.bool(forKey: "delete_cache_after_share")
        }
        s.language = d.string(forKey: "language") ?? "System"
        if let raw = d.string(forKey: "preferred_fixers") {
            s.preferredFixers = Dictionary(
                uniqueKeysWithValues: raw.split(separator: "\n").compactMap { line -> (String, String)? in
                    let parts = line.split(separator: "\t", maxSplits: 1).map(String.init)
                    guard parts.count == 2 else { return nil }
                    return (parts[0], parts[1])
                }
            )
        }
        if s.defaultAction == "Download" && !s.hasValidCobaltBaseUrl {
            s.defaultAction = "Ask"
        }
        return s
    }

    private static func migrateCobaltPublicDefaultIfNeeded(_ d: UserDefaults) {
        if d.bool(forKey: cobaltPublicClearedKey) { return }
        let stored = d.string(forKey: "cobalt_base_url")
        let normalized = stored?.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if stored == nil || normalized?.caseInsensitiveCompare(legacyPublicCobalt) == .orderedSame {
            d.set("", forKey: "cobalt_base_url")
        }
        let url = d.string(forKey: "cobalt_base_url") ?? ""
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = trimmed.lowercased()
        let valid = !trimmed.isEmpty && (lower.hasPrefix("http://") || lower.hasPrefix("https://"))
        if d.string(forKey: "default_action") == "Download" && !valid {
            d.set("Ask", forKey: "default_action")
        }
        d.set(true, forKey: cobaltPublicClearedKey)
    }
}
