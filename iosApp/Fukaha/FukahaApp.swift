//
// Fukaha iOS container app — Android-parity settings screen
//

import Foundation
import SwiftUI
import Shared

@main
struct FukahaApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @State private var settings = SettingsSnapshot.load()
    @State private var pendingUpdate: PendingAppUpdate?
    @State private var updateChecking = false
    @State private var updateMessage: String?
    @State private var quickShareText: String?
    @State private var quickShareDetent: PresentationDetent = .medium
    @State private var updateChannel: IosUpdateChannel?
    @Environment(\.openURL) private var openURL
    private let facade = FukahaIosFacade()

    private var isArabic: Bool {
        switch settings.language {
        case "Arabic": return true
        case "English", "Japanese", "SimplifiedChinese", "Spanish": return false
        default:
            return Locale.current.language.languageCode?.identifier == "ar"
        }
    }

    var body: some View {
        FukahaSettingsScreen(
            settings: $settings,
            onClearCache: clearCache,
            onCheckUpdates: { Task { await runUpdateCheck(manual: true) } },
            onOpenQuickLink: {
                quickShareDetent = .medium
                quickShareText = $0
            },
            updateChecking: updateChecking,
            updateChannel: updateChannel,
        )
        .environment(\.layoutDirection, isArabic ? .rightToLeft : .leftToRight)
        .preferredColorScheme(preferredColorScheme)
        .tint(Color.fukahaPrimary)
        .task {
            updateChannel = await IosUpdateChannel.current()
            await runUpdateCheck(manual: false)
        }
        .sheet(item: $pendingUpdate) { update in
            UpdateAvailableView(
                update: update,
                isArabic: isArabic,
                onSkip: {
                    settings.skippedUpdateVersion = update.version
                    settings.save()
                    pendingUpdate = nil
                },
                onDismiss: { pendingUpdate = nil },
                onInstall: {
                    openURL(update.storeURL) { accepted in
                        if !accepted {
                            pendingUpdate = nil
                            updateMessage = isArabic ? "تعذّر فتح App Store" : "Could not open the App Store"
                        }
                    }
                },
            )
        }
        .sheet(isPresented: Binding(
            get: { quickShareText != nil },
            set: { if !$0 { quickShareText = nil } },
        )) {
            Group {
                if let text = quickShareText {
                    FukahaQuickShareView(
                        text: text,
                        settings: settings,
                        facade: facade,
                        onDismiss: { quickShareText = nil },
                    )
                }
            }
            .presentationDetents([.medium, .large], selection: $quickShareDetent)
            .presentationDragIndicator(.visible)
        }
        .alert(updateMessage ?? "", isPresented: Binding(
            get: { updateMessage != nil },
            set: { if !$0 { updateMessage = nil } },
        )) {
            Button(isArabic ? "حسناً" : "OK", role: .cancel) { updateMessage = nil }
        }
    }

    private var preferredColorScheme: ColorScheme? {
        switch settings.theme {
        case "Light": return .light
        case "Dark": return .dark
        default: return nil
        }
    }

    private func clearCache() {
        let cache = FileManager.default.temporaryDirectory.appendingPathComponent("fukaha")
        try? FileManager.default.removeItem(at: cache)
        updateMessage = isArabic ? "تم مسح ملفات الوسائط المؤقتة" : "Media cache cleared"
    }

    private func currentVersion() -> String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
    }

    @MainActor
    private func runUpdateCheck(manual: Bool) async {
        guard !updateChecking else { return }
        updateChecking = true
        defer { updateChecking = false }
        if manual && (updateChannel == nil || updateChannel == .unknown) {
            updateChannel = await IosUpdateChannel.current(allowRefresh: true)
        }
        guard !Task.isCancelled, let channel = updateChannel else { return }
        switch channel {
        case .unknown:
            if manual {
                updateMessage = isArabic
                    ? "تعذّر تحديد مصدر التثبيت. تحقق من اتصالك وتسجيل الدخول إلى App Store، ثم حاول مجدداً."
                    : "Could not determine the installation source. Check your connection and App Store sign-in, then try again."
            }
            return
        case .testFlight:
            if manual {
                openURL(URL(string: "itms-beta://")!) { accepted in
                    if !accepted {
                        updateMessage = isArabic
                            ? "افتح تطبيق TestFlight لتحديث النسخة التجريبية."
                            : "Open the TestFlight app to update this beta build."
                    }
                }
            }
            return
        case .localBuild:
            if manual {
                updateMessage = isArabic
                    ? "هذه نسخة مثبتة يدوياً. ثبّت نسخة أحدث من المصدر الذي حصلت عليها منه."
                    : "This build was installed manually. Install a newer build from the same source."
            }
            return
        case .appStore:
            break
        }
        if !manual {
            guard settings.checkUpdatesOnLaunch,
                  IosAppUpdateChecker.isCheckDue(lastCheckEpochMs: settings.lastUpdateCheckEpochMs)
            else { return }
        }
        do {
            let result = try await IosAppUpdateChecker().check(
                bundleID: Bundle.main.bundleIdentifier ?? "",
                currentVersion: currentVersion()
            )
            try Task.checkCancellation()
            // Only successful Apple responses count toward the daily check interval.
            settings.lastUpdateCheckEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
            settings.save()
            switch result {
            case .available(let update):
                if manual || update.version != settings.skippedUpdateVersion {
                    pendingUpdate = update
                }
            case .upToDate:
                if manual {
                    updateMessage = isArabic ? "لديك أحدث إصدار متاح في App Store" : "You have the latest App Store version"
                }
            case .requiresNewerOS(let version):
                if manual {
                    updateMessage = isArabic
                        ? "يتطلب الإصدار الأحدث iOS \(version) أو أحدث."
                        : "The latest release requires iOS \(version) or later."
                }
            case .notListed:
                if manual {
                    updateMessage = isArabic
                        ? "فكها غير متاح حالياً في App Store في منطقتك."
                        : "Fukaha is not currently listed in the App Store in your region."
                }
            }
        } catch is CancellationError {
            // A cancelled view task should not display a failure or postpone the next check.
        } catch {
            if manual && !Task.isCancelled {
                updateMessage = isArabic
                    ? "تعذّر البحث عن تحديثات. تحقق من اتصالك وحاول مجدداً."
                    : "Could not check for updates. Check your connection and try again."
            }
        }
    }
}

struct UpdateAvailableView: View {
    let update: PendingAppUpdate
    let isArabic: Bool
    let onSkip: () -> Void
    let onDismiss: () -> Void
    let onInstall: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("v\(update.version)")
                        .font(.headline)
                    Text(update.changelog.isEmpty
                         ? (isArabic ? "لا توجد ملاحظات لهذا الإصدار." : "No release notes for this version.")
                         : update.changelog)
                        .font(.body)
                        .foregroundStyle(.secondary)
                    Text(isArabic ? "يتم تنزيل التحديث وتثبيته عبر App Store." : "Download and install this update through the App Store.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                    Button(isArabic ? "التحديث عبر App Store" : "Update in App Store", action: onInstall)
                        .buttonStyle(.borderedProminent)
                    Button(isArabic ? "تخطَّ هذا الإصدار" : "Skip this version", action: onSkip)
                        .padding(.top, 8)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
            }
            .navigationTitle(isArabic ? "يتوفر تحديث" : "Update available")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(isArabic ? "لاحقاً" : "Later", action: onDismiss)
                }
            }
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
    var theme: String = "System"
    var preferredFixers: [String: String] = [:]
    var checkUpdatesOnLaunch: Bool = true
    var skippedUpdateVersion: String = ""
    var lastUpdateCheckEpochMs: Int64 = 0

    static let suite = UserDefaults(suiteName: IosSettingsKeys.shared.APP_GROUP) ?? .standard
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
        s.defaultAction = d.string(forKey: IosSettingsKeys.shared.DEFAULT_ACTION) ?? "Ask"
        s.cobaltBaseUrl = d.string(forKey: IosSettingsKeys.shared.COBALT) ?? ""
        s.cobaltApiKey = d.string(forKey: IosSettingsKeys.shared.COBALT_API_KEY) ?? ""
        if d.object(forKey: IosSettingsKeys.shared.RESOLVE_SHORT) != nil {
            s.resolveShortLinks = d.bool(forKey: IosSettingsKeys.shared.RESOLVE_SHORT)
        }
        if d.object(forKey: IosSettingsKeys.shared.DELETE_CACHE) != nil {
            s.deleteCacheAfterShare = d.bool(forKey: IosSettingsKeys.shared.DELETE_CACHE)
        }
        s.language = d.string(forKey: IosSettingsKeys.shared.LANGUAGE) ?? "System"
        s.theme = d.string(forKey: IosSettingsKeys.shared.THEME) ?? "System"
        if d.object(forKey: IosSettingsKeys.shared.CHECK_UPDATES) != nil {
            s.checkUpdatesOnLaunch = d.bool(forKey: IosSettingsKeys.shared.CHECK_UPDATES)
        }
        s.skippedUpdateVersion = d.string(forKey: "ios.appStore.skippedVersion") ?? ""
        if let stored = d.object(forKey: "ios.appStore.lastCheckEpochMs") as? NSNumber {
            s.lastUpdateCheckEpochMs = stored.int64Value
        }
        if let raw = d.string(forKey: IosSettingsKeys.shared.PREFERRED_FIXERS) {
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
        let stored = d.string(forKey: IosSettingsKeys.shared.COBALT)
        let normalized = stored?.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if stored == nil || normalized?.caseInsensitiveCompare(legacyPublicCobalt) == .orderedSame {
            d.set("", forKey: IosSettingsKeys.shared.COBALT)
        }
        let url = d.string(forKey: IosSettingsKeys.shared.COBALT) ?? ""
        let valid = {
            let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
            let lower = trimmed.lowercased()
            return !trimmed.isEmpty && (lower.hasPrefix("http://") || lower.hasPrefix("https://"))
        }()
        if d.string(forKey: IosSettingsKeys.shared.DEFAULT_ACTION) == "Download" && !valid {
            d.set("Ask", forKey: IosSettingsKeys.shared.DEFAULT_ACTION)
        }
        d.set(true, forKey: cobaltPublicClearedKey)
    }

    func save() {
        let d = Self.suite
        var action = defaultAction
        if action == "Download" && !hasValidCobaltBaseUrl {
            action = "Ask"
        }
        d.set(action, forKey: IosSettingsKeys.shared.DEFAULT_ACTION)
        d.set(cobaltBaseUrl, forKey: IosSettingsKeys.shared.COBALT)
        d.set(cobaltApiKey, forKey: IosSettingsKeys.shared.COBALT_API_KEY)
        d.set(resolveShortLinks, forKey: IosSettingsKeys.shared.RESOLVE_SHORT)
        d.set(deleteCacheAfterShare, forKey: IosSettingsKeys.shared.DELETE_CACHE)
        d.set(language, forKey: IosSettingsKeys.shared.LANGUAGE)
        d.set(theme, forKey: IosSettingsKeys.shared.THEME)
        d.set(checkUpdatesOnLaunch, forKey: IosSettingsKeys.shared.CHECK_UPDATES)
        d.set(skippedUpdateVersion, forKey: "ios.appStore.skippedVersion")
        d.set(NSNumber(value: lastUpdateCheckEpochMs), forKey: "ios.appStore.lastCheckEpochMs")
        let raw = preferredFixers.map { "\($0.key)\t\($0.value)" }.joined(separator: "\n")
        d.set(raw, forKey: IosSettingsKeys.shared.PREFERRED_FIXERS)
    }
}
