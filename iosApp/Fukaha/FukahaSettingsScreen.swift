import Foundation
import SwiftUI
import UIKit
import Shared

private let iosTestShareUrl =
    "https://x.com/makkahregion/status/1902619525532512361" +
        "?utm_source=share&utm_medium=ios_app&fbclid=IwAR0fukaha_test"

private struct IosServiceOption: Identifiable {
    let name: String
    let host: String

    var id: String { host }
}

private struct IosCreditSource: Identifiable {
    let englishTitle: String
    let arabicTitle: String
    let url: String

    var id: String { url }
}

private let iosCreditSources = [
    IosCreditSource(
        englishTitle: "Lexedia’s embed fixer list",
        arabicTitle: "قائمة Lexedia لخدمات المعاينة",
        url: "https://gist.github.com/Lexedia/bbbde4dbbf628b0bfe8476a96a977a8f",
    ),
    IosCreditSource(
        englishTitle: "FixTweetBot fixer list",
        arabicTitle: "قائمة FixTweetBot",
        url: "https://github.com/Kyrela/FixTweetBot#awesome-fixers",
    ),
    IosCreditSource(
        englishTitle: "mohsreg’s Discord embed list",
        arabicTitle: "قائمة mohsreg لمعاينات ديسكورد",
        url: "https://gist.github.com/mohsreg/927bf8b2092515ee1a8ee88c3e4d2c14",
    ),
    IosCreditSource(
        englishTitle: "meqativ’s embed fixer list",
        arabicTitle: "قائمة meqativ لخدمات المعاينة",
        url: "https://gist.github.com/meqativ/ea15d319f7889a02c893605c62f148c2",
    ),
    IosCreditSource(
        englishTitle: "Postrediori’s embed list",
        arabicTitle: "قائمة Postrediori",
        url: "https://gist.github.com/Postrediori/cc52b0ca054179a91aab2e63582265b6",
    ),
    IosCreditSource(
        englishTitle: "EmbedFixer plugin",
        arabicTitle: "إضافة EmbedFixer",
        url: "https://github.com/k33bs/EmbedFixer",
    ),
]

struct FukahaSettingsScreen: View {
    @Binding var settings: SettingsSnapshot
    let onClearCache: () -> Void
    let onCheckUpdates: () -> Void
    let onOpenQuickLink: (String) -> Void
    let updateChecking: Bool

    @State private var linkInput = ""
    @State private var cobaltExpanded = false
    @State private var selectedFixerPlatform: String?
    @State private var helpPresented = false
    @StateObject private var sourceHealth = FukahaSourceHealth()
    @State private var sourceRanking = FukahaSourceRanking.load(from: .standard)
    @Environment(\.scenePhase) private var scenePhase

    @StateObject private var client = FukahaClient()
    private var facade: FukahaIosFacade { client.facade }

    private var isArabic: Bool {
        switch settings.language {
        case "Arabic": return true
        case "English", "Japanese", "SimplifiedChinese", "Spanish": return false
        default:
            return Locale.current.language.languageCode?.identifier == "ar"
        }
    }

    private var platformKeys: [String] {
        facade.platformKeys().filter { !facade.serviceNames(platformKey: $0).isEmpty }
    }

    private var shareableUrl: URL? {
        let trimmed = linkInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let raw = facade.extractUrl(text: trimmed) ?? {
            let firstToken = trimmed.split(separator: " ").first.map(String.init) ?? ""
            let hasHost = firstToken.contains(".") && !firstToken.contains("://")
            return hasHost ? "https://\(firstToken)" : nil
        }()
        guard let raw, let url = URL(string: raw) else { return nil }
        return url
    }

    private var showInvalidLink: Bool {
        !linkInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && shareableUrl == nil
    }

    private var actionOptions: [(id: String, icon: String, title: String, subtitle: String)] {
        [
            (
                "Ask",
                "hand.tap",
                text(en: "Ask each time", ar: "اسأل في كل مرة"),
                text(en: "Show the share sheet and let you choose", ar: "يعرض ورقة المشاركة لتختار بنفسك"),
            ),
            (
                "Clean",
                "sparkles",
                text(en: "Clean link", ar: "رابط نظيف"),
                text(en: "Share the cleaned URL immediately", ar: "مشاركة الرابط المنظّف فوراً"),
            ),
            (
                "Embed",
                "eye",
                text(en: "Embed link", ar: "رابط معاينة"),
                text(en: "Share the embed-friendly URL immediately", ar: "مشاركة الرابط المناسب للمعاينة فوراً"),
            ),
            (
                "Download",
                "arrow.down.to.line",
                text(en: "Download media", ar: "تحميل الوسائط"),
                settings.hasValidCobaltBaseUrl
                    ? text(en: "Download the file and share it", ar: "تحميل الملف ومشاركته")
                    : text(
                        en: "Needs your own Cobalt server URL, set under Media download in Settings.",
                        ar: "يتطلب عنوان خادم Cobalt عبر HTTPS الخاص بك، ويُضبط تحت تحميل الوسائط.",
                    ),
            ),
        ]
    }

    private var effectiveAction: String {
        settings.defaultAction == "Download" && !settings.hasValidCobaltBaseUrl
            ? "Ask"
            : settings.defaultAction
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 20) {
                quickLinkSection
                defaultActionSection
                preferredFixersSection
                aboutSection
                developerSection
                creditsSection
                sharingSection
                updatesSection
                mediaDownloadSection
                storageSection
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 32)
        }
        .background(Color.fukahaBackground.ignoresSafeArea())
        .safeAreaInset(edge: .top, spacing: 0) {
            FukahaBrandAppBar(
                language: $settings.language,
                theme: $settings.theme,
                isArabic: isArabic,
                onHelp: { helpPresented = true },
            )
        }
        .tint(Color.fukahaPrimary)
        .onChange(of: settings.defaultAction) { _ in settings.save() }
        .onChange(of: settings.cobaltBaseUrl) { _ in persistCobaltUrl() }
        .onChange(of: settings.cobaltApiKey) { _ in settings.save() }
        .onChange(of: settings.useOfficialShortLinks) { _ in settings.save() }
        .onChange(of: settings.disabledEmbedPlatforms) { _ in settings.save() }
        .onChange(of: settings.resolveShortLinks) { _ in settings.save() }
        .onChange(of: settings.deleteCacheAfterShare) { _ in settings.save() }
        .onChange(of: settings.language) { _ in settings.save() }
        .onChange(of: settings.theme) { _ in settings.save() }
        .onChange(of: settings.checkUpdatesOnLaunch) { _ in settings.save() }
        .onChange(of: sourceRanking) { ranking in ranking.save(to: .standard) }
        .onChange(of: scenePhase) { phase in
            if phase == .background { sourceHealth.cancel() }
        }
        .sheet(isPresented: Binding(
            get: { selectedFixerPlatform != nil },
            set: { if !$0 { selectedFixerPlatform = nil } },
        )) {
            if let key = selectedFixerPlatform {
                IosFixerPickerSheet(
                    platformKey: key,
                    sourceRanking: $sourceRanking,
                    platformName: platformTitle(key),
                    services: serviceOptions(for: key),
                    selectedHost: currentService(for: key)?.host,
                    sourceHealth: sourceHealth,
                    isArabic: isArabic,
                    onSelect: { host in
                        var next = settings
                        next.preferredFixers[key] = host
                        settings = next
                        settings.save()
                        selectedFixerPlatform = nil
                    },
                )
            }
        }
        .sheet(isPresented: $helpPresented) {
            FukahaHelpView(isArabic: isArabic)
        }
    }

    private var quickLinkSection: some View {
        FukahaSection(title: text(en: "Paste a link", ar: "الصق رابطاً")) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 10) {
                    Button {
                        linkInput = iosTestShareUrl
                    } label: {
                        Image(systemName: "flask")
                            .font(.system(size: 23, weight: .medium))
                    }
                    .accessibilityLabel(text(en: "Try a sample link", ar: "جرّب رابطاً تجريبياً"))

                    Image(systemName: "link")
                        .font(.system(size: 23, weight: .medium))
                        .foregroundStyle(Color.fukahaSecondary)

                    TextField(
                        text(en: "https://x.com/…", ar: "https://x.com/…"),
                        text: $linkInput,
                    )
                    .font(.system(size: 17))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                    .submitLabel(.go)

                    if linkInput.isEmpty {
                        Button {
                            linkInput = UIPasteboard.general.string?.trimmingCharacters(
                                in: .whitespacesAndNewlines,
                            ) ?? ""
                        } label: {
                            Image(systemName: "doc.on.clipboard")
                                .font(.system(size: 23, weight: .medium))
                        }
                        .accessibilityLabel(text(en: "Paste from clipboard", ar: "لصق من الحافظة"))
                    } else {
                        Button { linkInput = "" } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 21, weight: .medium))
                        }
                        .accessibilityLabel(text(en: "Clear link", ar: "إفراغ الحقل"))
                    }

                    if let shareableUrl {
                        Button {
                            onOpenQuickLink(shareableUrl.absoluteString)
                        } label: {
                            Image(systemName: "arrow.forward.circle.fill")
                                .font(.system(size: 24, weight: .medium))
                        }
                        .accessibilityLabel(text(en: "Open share screen", ar: "افتح شاشة المشاركة"))
                    }
                }
                .foregroundStyle(Color.fukahaSecondary)
                .padding(.horizontal, 12)
                .frame(minHeight: 58)
                .overlay {
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .stroke(
                            showInvalidLink ? Color.fukahaError : Color.fukahaSecondary,
                            lineWidth: 1.5,
                        )
                }
                .environment(\.layoutDirection, .leftToRight)
                .onSubmit {
                    if let shareableUrl {
                        onOpenQuickLink(shareableUrl.absoluteString)
                    }
                }
                Text(showInvalidLink
                     ? text(en: "That does not look like a link yet", ar: "لا يبدو هذا رابطاً بعد")
                     : text(en: "Clean it, fix the preview, or download it.", ar: "نظّفه، أو صحّح معاينته، أو حمّله."))
                    .font(.system(size: 17))
                    .foregroundStyle(showInvalidLink ? Color.fukahaError : Color.fukahaSecondary)
                    .padding(.horizontal, 16)
            }
            .padding(.horizontal, 16)
        }
    }

    private var defaultActionSection: some View {
        FukahaSection(title: text(en: "Default action", ar: "الإجراء الافتراضي")) {
            VStack(alignment: .leading, spacing: 8) {
                Text(text(
                    en: "What Fukaha should do when you share a link into it",
                    ar: "ما الذي ينفّذه فكها عند مشاركة رابط إليه؟",
                ))
                .font(.system(size: 17))
                .foregroundStyle(Color.fukahaSecondary)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

                ForEach(Array(actionOptions.enumerated()), id: \.offset) { _, option in
                    FukahaActionRow(
                        icon: option.icon,
                        title: option.title,
                        subtitle: option.subtitle,
                        selected: effectiveAction == option.id,
                        enabled: option.id != "Download" || settings.hasValidCobaltBaseUrl,
                        selectedLabel: text(en: "Selected", ar: "محدد"),
                        onTap: {
                            guard option.id != "Download" || settings.hasValidCobaltBaseUrl else { return }
                            settings.defaultAction = option.id
                            settings.save()
                        },
                    )
                    .padding(.horizontal, 12)
                }
            }
        }
    }

    private var preferredFixersSection: some View {
        FukahaSection(title: text(en: "Preferred embed fixers", ar: "خدمات المعاينة المفضّلة")) {
            VStack(spacing: 0) {
                IosSourceCheckControl(health: sourceHealth, isArabic: isArabic)
                    .padding(16)
                FukahaDivider()
                ForEach(Array(platformKeys.enumerated()), id: \.offset) { index, key in
                    fixerRow(for: key)
                    if index != platformKeys.count - 1 {
                        FukahaDivider()
                    }
                }
            }
        }
    }

    private func fixerRow(for key: String) -> some View {
        let service = currentService(for: key)
        let serviceName = service?.name ?? text(en: "No fixer selected", ar: "لم يتم اختيار خدمة")
        let host = service.map { displayHost($0.host) } ?? ""
        let infoUrl = service.flatMap { urlForHost($0.host) }
        let status = sourceHealth.cache.snapshot.status(for: service?.host ?? "")
        let statusLabel = sourceHealth.cache.snapshot.label(for: service?.host ?? "", isArabic: isArabic)

        return HStack(spacing: 12) {
            Circle()
                .fill(status.indicatorColor)
                .frame(width: 10, height: 10)
                .accessibilityHidden(true)
            Button {
                selectedFixerPlatform = key
            } label: {
                VStack(alignment: .leading, spacing: 3) {
                    Text("\(platformTitle(key)) · \(serviceName)")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color.fukahaOnSurface)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Text("\(host) · \(statusLabel)")
                        .font(.system(size: 16))
                        .foregroundStyle(Color.fukahaSecondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            if let infoUrl {
                Link(destination: infoUrl) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 22, weight: .medium))
                }
                .accessibilityLabel(text(en: "Open embedder info", ar: "فتح معلومات خدمة المعاينة"))
            }
            Button {
                selectedFixerPlatform = key
            } label: {
                Image(systemName: "chevron.right")
                    .font(.system(size: 18, weight: .semibold))
            }
            .accessibilityLabel(text(en: "Choose embed fixer", ar: "اختيار خدمة المعاينة"))
            Toggle(isOn: Binding(
                get: { !settings.disabledEmbedPlatforms.contains(key) },
                set: { enabled in
                    if enabled { settings.disabledEmbedPlatforms.remove(key) }
                    else { settings.disabledEmbedPlatforms.insert(key) }
                }
            )) {
                Text("\(text(en: "Embed links", ar: "تفعيل روابط المعاينة")) · \(platformTitle(key))")
            }
            .labelsHidden()
            .fixedSize()
            .accessibilityHint(text(en: "When off, Embed shares the cleaned link for this site.", ar: "عند الإيقاف، يشارك خيار المعاينة الرابط المنظّف لهذا الموقع."))
        }
        .foregroundStyle(Color.fukahaPrimary)
        .padding(.horizontal, 16)
        .padding(.vertical, 13)
        .contentShape(Rectangle())
        .environment(\.layoutDirection, .leftToRight)
    }

    private var aboutSection: some View {
        FukahaSection(title: text(en: "Fukaha", ar: "فكها")) {
            VStack(spacing: 0) {
                FukahaListRow(
                    icon: "info.circle",
                    title: "Fukaha",
                    subtitle: text(
                        en: "Fukaha (فكها) cleans tracking from social links, rewrites them to embed-friendly hosts, or downloads media to re-share as a file (requires your own Cobalt instance URL (HTTPS) in Settings). Open it from the system share menu.",
                        ar: "فكها يزيل التتبع من روابط التواصل الاجتماعي، ويحوّلها إلى مضيفات مناسبة للمعاينة، أو يحمّل الوسائط لمشاركتها كملف (يتطلب عنوان خادم Cobalt عبر HTTPS الخاص بك في الإعدادات). افتحه من قائمة المشاركة في النظام.",
                    ),
                    trailingIcon: nil,
                )
                FukahaDivider()
                Button { helpPresented = true } label: {
                    FukahaListRow(
                        icon: "questionmark.circle",
                        title: text(en: "How to use Fukaha", ar: "كيف تستخدم فكها"),
                        subtitle: text(en: "Replay the quick tour any time", ar: "أعِد الجولة السريعة في أي وقت"),
                        trailingIcon: "chevron.right",
                    )
                }
                .buttonStyle(.plain)
                FukahaDivider()
                HStack(spacing: 14) {
                    FukahaListRow(
                        icon: "info.circle",
                        title: text(en: "Version", ar: "الإصدار"),
                        subtitle: versionLabel,
                        trailingIcon: nil,
                    )
                    Button(action: onCheckUpdates) {
                        if updateChecking {
                            ProgressView()
                                .tint(Color.fukahaPrimary)
                        } else {
                            Image(systemName: "arrow.clockwise")
                                .font(.system(size: 23, weight: .medium))
                        }
                    }
                    .disabled(updateChecking)
                    .accessibilityLabel(text(en: "Check for updates", ar: "البحث عن تحديثات"))
                    .padding(.trailing, 16)
                }
            }
        }
    }

    private var sharingSection: some View {
        FukahaSection(title: text(en: "Sharing", ar: "المشاركة")) {
            VStack(spacing: 0) {
                toggleRow(
                    icon: "link",
                    title: text(en: "Use official shorter links", ar: "استخدام الروابط الرسمية الأقصر"),
                    subtitle: text(en: "Use youtu.be for YouTube videos. Keeps timestamps and playlists; other sites stay unchanged.", ar: "استخدام youtu.be لفيديوهات YouTube مع الحفاظ على الوقت وقائمة التشغيل. تبقى المواقع الأخرى دون تغيير."),
                    isOn: $settings.useOfficialShortLinks
                )
                FukahaDivider()
                toggleRow(
                    icon: "link",
                    title: text(en: "Resolve short links", ar: "تتبّع الروابط المختصرة"),
                    subtitle: text(
                        en: "Follow t.co, vm.tiktok.com, and similar redirects",
                        ar: "تتبع اختصارات مثل t.co وvm.tiktok.com للوصول إلى الرابط الأصلي",
                    ),
                    isOn: $settings.resolveShortLinks,
                )
                FukahaDivider()
                toggleRow(
                    icon: "trash",
                    title: text(en: "Delete cache after share", ar: "حذف الملفات المؤقتة بعد المشاركة"),
                    subtitle: text(
                        en: "Remove downloaded media once sharing finishes",
                        ar: "يزيل الوسائط المحمّلة بعد اكتمال المشاركة",
                    ),
                    isOn: $settings.deleteCacheAfterShare,
                )
            }
        }
    }

    private var developerSection: some View {
        FukahaSection(title: text(en: "Developer", ar: "المطوّر")) {
            VStack(spacing: 0) {
                externalLinkRow(
                    icon: "hand.raised",
                    title: text(en: "Privacy policy", ar: "سياسة الخصوصية"),
                    subtitle: text(en: "How Fukaha handles your data", ar: "كيف يتعامل Fukaha مع بياناتك"),
                    url: "https://fukaha.shenepoy.com/privacy.html",
                )
                FukahaDivider()
                externalLinkRow(
                    icon: "questionmark.circle",
                    title: text(en: "Help and support", ar: "المساعدة والدعم"),
                    subtitle: text(en: "Get help or report a problem", ar: "احصل على مساعدة أو أبلغ عن مشكلة"),
                    url: "https://fukaha.shenepoy.com/support.html",
                )
                FukahaDivider()
                externalLinkRow(
                    icon: "person",
                    title: "shenepoy",
                    subtitle: "shenepoy.com",
                    url: "https://shenepoy.com",
                )
                FukahaDivider()
                externalLinkRow(
                    icon: "chevron.left.forwardslash.chevron.right",
                    title: "GitHub",
                    subtitle: text(en: "View source and releases", ar: "عرض المصدر والإصدارات"),
                    url: "https://github.com/Zyzto/Fukaha",
                )
                FukahaDivider()
                externalLinkRow(
                    icon: "heart",
                    title: text(en: "Donate", ar: "تبرع"),
                    subtitle: text(en: "Support the developer on GitHub", ar: "ادعم المطوّر عبر GitHub"),
                    url: "https://github.com/sponsors/Zyzto",
                )
            }
        }
    }

    private var creditsSection: some View {
        FukahaSection(title: text(en: "Credits", ar: "شكر وتقدير")) {
            VStack(spacing: 0) {
                FukahaListRow(
                    icon: "link",
                    title: text(en: "Embed fixers", ar: "خدمات المعاينة"),
                    subtitle: text(
                        en: "The catalog is assembled from several community collections. Thanks to their maintainers and the authors of the listed services.",
                        ar: "القائمة مجمّعة من عدة مجموعات مجتمعية. شكراً للقائمين عليها ولمؤلفي الخدمات المدرجة.",
                    ),
                    trailingIcon: nil,
                )
                ForEach(iosCreditSources) { source in
                    FukahaDivider()
                    externalLinkRow(
                        icon: "chevron.left.forwardslash.chevron.right",
                        title: text(en: source.englishTitle, ar: source.arabicTitle),
                        subtitle: nil,
                        url: source.url,
                    )
                }
            }
        }
    }

    private var updatesSection: some View {
        FukahaSection(title: text(en: "Updates", ar: "التحديثات")) {
            toggleRow(
                icon: "arrow.down.app",
                title: text(en: "Check for updates on launch", ar: "البحث عن تحديثات عند الفتح"),
                subtitle: text(
                    en: "Looks at GitHub Releases about once a day. A found update can install from the dialog.",
                    ar: "يفحص إصدارات GitHub نحو مرة في اليوم. يمكن تثبيت التحديث من النافذة مباشرة.",
                ),
                isOn: $settings.checkUpdatesOnLaunch,
            )
        }
    }

    private var mediaDownloadSection: some View {
        FukahaSection(title: text(en: "Media download", ar: "تحميل الوسائط")) {
            VStack(spacing: 0) {
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) {
                        cobaltExpanded.toggle()
                    }
                } label: {
                    FukahaListRow(
                        icon: "externaldrive",
                        title: text(en: "Media download", ar: "تحميل الوسائط"),
                        subtitle: cobaltExpanded ? nil : text(
                            en: "Tap to set your Cobalt instance URL (HTTPS)",
                            ar: "اضغط لتعيين عنوان خادم Cobalt عبر HTTPS",
                        ),
                        trailingIcon: cobaltExpanded ? "chevron.up" : "chevron.down",
                    )
                }
                .buttonStyle(.plain)
                if cobaltExpanded {
                    FukahaDivider()
                    VStack(alignment: .leading, spacing: 12) {
                        Text(text(
                            en: "Media download needs your own self-hosted Cobalt instance URL (HTTPS) (and API key if your instance requires one). The public cobalt.tools API will not work with this app.",
                            ar: "يحتاج تحميل الوسائط إلى عنوان خادم Cobalt عبر HTTPS تستضيفه بنفسك (ومفتاح API إن طلبه خادمك). واجهة cobalt.tools العامة لا تعمل مع هذا التطبيق.",
                        ))
                        .font(.system(size: 15))
                        .foregroundStyle(Color.fukahaSecondary)
                        TextField(
                            text(en: "Cobalt instance URL (HTTPS)", ar: "عنوان خادم Cobalt عبر HTTPS"),
                            text: Binding(
                                get: { settings.cobaltBaseUrl },
                                set: { updateCobaltUrl($0) },
                            ),
                        )
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 12)
                        .frame(minHeight: 48)
                        .background(Color.fukahaBackground)
                        .overlay {
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(Color.fukahaOutline, lineWidth: 1.5)
                        }
                        TextField(
                            text(en: "Cobalt API key (optional)", ar: "مفتاح Cobalt (اختياري)"),
                            text: Binding(
                                get: { settings.cobaltApiKey },
                                set: {
                                    settings.cobaltApiKey = $0
                                    settings.save()
                                },
                            ),
                        )
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textFieldStyle(.plain)
                        .padding(.horizontal, 12)
                        .frame(minHeight: 48)
                        .background(Color.fukahaBackground)
                        .overlay {
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .stroke(Color.fukahaOutline, lineWidth: 1.5)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
            }
        }
    }

    private var storageSection: some View {
        FukahaSection(title: text(en: "Storage", ar: "التخزين")) {
            Button(action: onClearCache) {
                FukahaListRow(
                    icon: "trash",
                    title: text(en: "Clear media cache", ar: "مسح ملفات الوسائط المؤقتة"),
                    subtitle: text(en: "Delete leftover files from past downloads", ar: "يحذف الملفات المتبقية من تحميلات سابقة"),
                    trailingIcon: nil,
                )
            }
            .buttonStyle(.plain)
        }
    }

    private func toggleRow(
        icon: String,
        title: String,
        subtitle: String,
        isOn: Binding<Bool>,
    ) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 25, weight: .medium))
                .foregroundStyle(Color.fukahaSecondary)
                .frame(width: 30)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 18))
                    .foregroundStyle(Color.fukahaOnSurface)
                Text(subtitle)
                    .font(.system(size: 16))
                    .foregroundStyle(Color.fukahaSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 6)
            Toggle("", isOn: isOn)
                .labelsHidden()
                .tint(Color.fukahaPrimary)
                .accessibilityLabel(title)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private func externalLinkRow(
        icon: String,
        title: String,
        subtitle: String?,
        url: String,
    ) -> some View {
        if let destination = URL(string: url) {
            Link(destination: destination) {
                FukahaListRow(
                    icon: icon,
                    title: title,
                    subtitle: subtitle,
                    trailingIcon: "arrow.up.right",
                )
            }
            .buttonStyle(.plain)
        }
    }

    private var versionLabel: String {
        "v\(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "26.10.0")"
    }

    private func currentService(for key: String) -> IosServiceOption? {
        let services = serviceOptions(for: key)
        let preferred = facade.selectedFixer(platformKey: key, preferredHost: settings.preferredFixers[key])
        if let preferred {
            let normalized = normalizeHost(preferred)
            if let selected = services.first(where: { normalizeHost($0.host) == normalized }) {
                return selected
            }
        }
        return services.first
    }

    private func serviceOptions(for key: String) -> [IosServiceOption] {
        facade.serviceNames(platformKey: key).map { row in
            let parts = row.split(separator: "\t", maxSplits: 1).map(String.init)
            return IosServiceOption(
                name: parts.first ?? row,
                host: parts.count > 1 ? parts[1] : row,
            )
        }
    }

    private func platformTitle(_ key: String) -> String {
        switch key {
        case "x": return "X (Twitter)"
        case "youtube": return "YouTube"
        case "tiktok": return "TikTok"
        case "instagram": return "Instagram"
        case "facebook": return "Facebook"
        case "reddit": return "Reddit"
        case "deviantart": return "DeviantArt"
        case "bilibili": return "Bilibili"
        default: return key.capitalized
        }
    }

    private func displayHost(_ raw: String) -> String {
        if let host = URL(string: raw)?.host { return host }
        return raw
            .replacingOccurrences(of: "https://", with: "")
            .replacingOccurrences(of: "http://", with: "")
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private func urlForHost(_ raw: String) -> URL? {
        if let url = URL(string: raw), url.scheme != nil { return url }
        return URL(string: "https://\(raw)")
    }

    private func normalizeHost(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            .lowercased()
    }

    private func updateCobaltUrl(_ value: String) {
        settings.cobaltBaseUrl = value
        if settings.defaultAction == "Download" && !settings.hasValidCobaltBaseUrl {
            settings.defaultAction = "Ask"
        }
        settings.save()
    }

    private func persistCobaltUrl() {
        if settings.defaultAction == "Download" && !settings.hasValidCobaltBaseUrl {
            settings.defaultAction = "Ask"
        }
        settings.save()
    }

    private func text(en: String, ar: String) -> String {
        isArabic ? ar : en
    }
}

private struct IosFixerPickerSheet: View {
    let platformKey: String
    @Binding var sourceRanking: FukahaSourceRanking
    let platformName: String
    let services: [IosServiceOption]
    let selectedHost: String?
    @ObservedObject var sourceHealth: FukahaSourceHealth
    let isArabic: Bool
    let onSelect: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var editMode = EditMode.inactive

    private var orderedServices: [IosServiceOption] {
        let byHost = Dictionary(services.map { (FukahaSourceHealthSnapshot.normalize($0.host), $0) },
                                uniquingKeysWith: { first, _ in first })
        return sourceRanking.orderedHosts(platform: platformKey, catalog: services.map(\.host))
            .compactMap { byHost[$0] }
    }

    private func group(favorites: Bool) -> [IosServiceOption] {
        orderedServices.filter { sourceRanking.isFavorite(platform: platformKey, host: $0.host) == favorites }
    }

    var body: some View {
        NavigationStack {
            List {
                IosSourceCheckControl(health: sourceHealth, isArabic: isArabic)
                    .listRowBackground(Color.fukahaSurfaceLow)
                Section {
                    if group(favorites: true).isEmpty {
                        Text(isArabic ? "اضغط النجمة لإضافة مصدر إلى المفضلة" : "Tap a star to add a favorite source")
                            .font(.subheadline)
                            .foregroundStyle(Color.fukahaSecondary)
                            .listRowBackground(Color.fukahaSurfaceLow)
                    }
                    ForEach(group(favorites: true)) { service in
                        sourceRow(service, rank: (group(favorites: true).firstIndex { $0.id == service.id } ?? 0) + 1)
                    }
                    .onMove { offsets, destination in move(offsets, to: destination, favorites: true) }
                } header: {
                    Text(isArabic ? "المفضلة حسب الترتيب" : "Ranked favorites")
                } footer: {
                    Text(isArabic ? "رتّب المصادر بالسحب. النجمة والترتيب لا يغيّران المصدر المحدد." : "Use Reorder to drag sources. Stars and rank do not change your selected source.")
                }
                if !group(favorites: false).isEmpty {
                    Section(isArabic ? "مصادر أخرى" : "Other sources") {
                        ForEach(group(favorites: false)) { service in sourceRow(service, rank: nil) }
                            .onMove { offsets, destination in move(offsets, to: destination, favorites: false) }
                    }
                }
            }
            .environment(\.editMode, $editMode)
            .scrollContentBackground(.hidden)
            .background(Color.fukahaBackground)
            .navigationTitle(isArabic ? "خدمات \(platformName)" : "\(platformName) fixers")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isArabic ? "تم" : "Done") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button(editMode.isEditing
                        ? (isArabic ? "حفظ الترتيب" : "Finish ordering")
                        : (isArabic ? "ترتيب" : "Reorder")) {
                        withAnimation { editMode = editMode.isEditing ? .inactive : .active }
                    }
                    .disabled(!editMode.isEditing && group(favorites: true).count < 2 && group(favorites: false).count < 2)
                }
            }
        }
        .tint(Color.fukahaPrimary)
        .presentationDetents([.medium, .large])
    }

    private func sourceRow(_ service: IosServiceOption, rank: Int?) -> some View {
        HStack(spacing: 10) {
            Button { onSelect(service.host) } label: {
                HStack(spacing: 10) {
                    if let rank {
                        Text("\(rank)")
                            .font(.system(size: 14, weight: .semibold).monospacedDigit())
                            .foregroundStyle(Color.fukahaSecondary)
                            .accessibilityLabel(isArabic ? "الترتيب \(rank)" : "Rank \(rank)")
                    }
                    Circle()
                        .fill(sourceHealth.cache.snapshot.status(for: service.host).indicatorColor)
                        .frame(width: 10, height: 10)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(service.name)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(Color.fukahaOnSurface)
                        Text("\(service.host) · \(sourceHealth.cache.snapshot.label(for: service.host, isArabic: isArabic))")
                            .font(.system(size: 15))
                            .foregroundStyle(Color.fukahaSecondary)
                    }
                    Spacer(minLength: 0)
                    if selectedHost.map({ FukahaSourceHealthSnapshot.normalize($0) == FukahaSourceHealthSnapshot.normalize(service.host) }) == true {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(Color.fukahaPrimary)
                            .accessibilityLabel(isArabic ? "محدد" : "Selected")
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(editMode.isEditing)
            Button {
                sourceRanking.toggleFavorite(platform: platformKey, host: service.host, catalog: services.map(\.host))
            } label: {
                Image(systemName: sourceRanking.isFavorite(platform: platformKey, host: service.host) ? "star.fill" : "star")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.borderless)
            .disabled(editMode.isEditing)
            .accessibilityLabel(sourceRanking.isFavorite(platform: platformKey, host: service.host)
                ? (isArabic ? "إزالة \(service.name) من المفضلة" : "Remove \(service.name) from favorites")
                : (isArabic ? "إضافة \(service.name) إلى المفضلة" : "Add \(service.name) to favorites"))
        }
        .listRowBackground(Color.fukahaSurfaceLow)
    }

    private func move(_ offsets: IndexSet, to destination: Int, favorites: Bool) {
        sourceRanking.move(platform: platformKey, favoritesOnly: favorites,
                           from: offsets, to: destination, catalog: services.map(\.host))
    }
}

private extension FukahaSourceStatus {
    var indicatorColor: Color {
        switch self {
        case .responding: return .green
        case .failed: return .red
        case .unverified: return .fukahaSecondary
        }
    }
}

private struct IosSourceCheckControl: View {
    @ObservedObject var health: FukahaSourceHealth
    let isArabic: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(isArabic ? "فحص المصادر" : "Check sources")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.fukahaOnSurface)
                Spacer()
                Button { health.refresh() } label: {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 21, weight: .medium))
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .foregroundStyle(Color.fukahaPrimary)
                .disabled(health.isChecking)
                .accessibilityLabel(isArabic ? "إعادة فحص المصادر" : "Refresh source checks")
                .accessibilityIdentifier("refresh-source-checks")
            }
            if health.isChecking {
                ProgressView(value: Double(health.completed), total: Double(max(health.total, 1)))
                Text(isArabic
                    ? "جارٍ الفحص: \(health.completed) / \(health.total)"
                    : "Checking \(health.completed) / \(health.total)")
                if !health.currentHost.isEmpty {
                    Text(health.currentHost).lineLimit(1)
                }
            } else {
                if health.cache.lastAttemptFailed {
                    Text(health.cache.snapshot.checkedAt == nil
                        ? (isArabic ? "لم نتمكن من التحقق من أي مصدر. تحقق من اتصالك وحاول مجددًا." : "No sources could be verified. Check your connection and try again.")
                        : (isArabic ? "لم نتمكن من التحقق من أي مصدر. احتُفظ بالنتائج السابقة؛ يمكنك المحاولة مجددًا." : "No sources could be verified. Previous results kept; you can try again."))
                }
                if let date = health.cache.snapshot.checkedAt {
                    Text(isArabic
                        ? "\(health.cache.snapshot.respondingCount) يستجيب · \(health.cache.snapshot.failedCount) فشل · \(health.cache.snapshot.unverifiedCount) غير مؤكد"
                        : "\(health.cache.snapshot.respondingCount) responding · \(health.cache.snapshot.failedCount) failed · \(health.cache.snapshot.unverifiedCount) unverified")
                    Text(isArabic ? "آخر فحص: \(date.formatted(date: .abbreviated, time: .shortened))"
                        : "Last checked: \(date.formatted(date: .abbreviated, time: .shortened))")
                } else if !health.cache.lastAttemptFailed {
                    Text(isArabic ? "لم تُفحص المصادر بعد" : "Sources have not been checked yet")
                }
            }
        }
        .font(.system(size: 14))
        .foregroundStyle(Color.fukahaSecondary)
    }
}

private struct FukahaHelpView: View {
    let isArabic: Bool
    @Environment(\.dismiss) private var dismiss

    private var steps: [(icon: String, title: String, body: String)] {
        if isArabic {
            return [
                ("square.and.arrow.up", "شارك من أي تطبيق", "افتح منشوراً، اضغط مشاركة، ثم اختر فكها وأرسل النتيجة أينما تريد."),
                ("doc.on.clipboard", "أو الصق رابطاً", "الصق عنوان URL في أعلى الإعدادات لفتح شاشة المشاركة مباشرة."),
                ("wand.and.stars", "اختر النتيجة", "نظّف الرابط أو أصلح المعاينة أو نزّل الوسائط من شاشة المشاركة."),
            ]
        }
        return [
            ("square.and.arrow.up", "Share from any app", "Open a post, tap Share, choose Fukaha, and send the result wherever you want."),
            ("doc.on.clipboard", "Or paste a link", "Paste a URL at the top of Settings to open the share screen directly."),
            ("wand.and.stars", "Choose the result", "Clean the link, fix the preview, or download media from the share screen."),
        ]
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(steps.enumerated()), id: \.offset) { _, step in
                        HStack(alignment: .top, spacing: 14) {
                            Image(systemName: step.icon)
                                .font(.system(size: 25, weight: .medium))
                                .foregroundStyle(Color.fukahaPrimary)
                                .frame(width: 30)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(step.title)
                                    .font(.system(size: 19, weight: .semibold))
                                    .foregroundStyle(Color.fukahaOnSurface)
                                Text(step.body)
                                    .font(.system(size: 16))
                                    .foregroundStyle(Color.fukahaSecondary)
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.fukahaSurfaceLow)
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    }
                }
                .padding(16)
            }
            .background(Color.fukahaBackground)
            .navigationTitle(isArabic ? "كيف تستخدم فكها" : "How to use Fukaha")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isArabic ? "تم" : "Done") { dismiss() }
                }
            }
        }
        .tint(Color.fukahaPrimary)
        .presentationDetents([.medium, .large])
    }
}
