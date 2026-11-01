//
// Fukaha iOS container app and settings screen
//

import Foundation
import SwiftUI
import Shared

@main
struct FukahaApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .background(FukahaKeyboardDismissal().allowsHitTesting(false))
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
    @StateObject private var client = FukahaClient()
    @State private var quickShareClient: FukahaClient?

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
            onCheckUpdates: { runUpdateCheck(manual: true) },
            onOpenQuickLink: {
                quickShareClient?.close()
                quickShareClient = FukahaClient()
                quickShareDetent = .medium
                quickShareText = $0
            },
            updateChecking: updateChecking,
        )
        .environment(\.layoutDirection, isArabic ? .rightToLeft : .leftToRight)
        .preferredColorScheme(preferredColorScheme)
        .tint(Color.fukahaPrimary)
        .task { runUpdateCheck(manual: false) }
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
            )
        }
        .sheet(isPresented: Binding(
            get: { quickShareText != nil },
            set: { if !$0 { dismissQuickShare() } },
        )) {
            Group {
                if let text = quickShareText, let quickShareClient {
                    FukahaQuickShareView(
                        text: text,
                        settings: settings,
                        client: quickShareClient,
                        onDismiss: dismissQuickShare,
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

    private func dismissQuickShare() {
        quickShareClient?.close()
        quickShareClient = nil
        quickShareText = nil
    }

    private func clearCache() {
        do {
            try FukahaMediaCache.clear()
            updateMessage = isArabic ? "تم مسح ملفات الوسائط المؤقتة" : "Media cache cleared"
        } catch {
            updateMessage = error.localizedDescription
        }
    }

    private func currentVersion() -> String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
    }

    private func runUpdateCheck(manual: Bool) {
        if updateChecking { return }
        if !manual {
            guard settings.checkUpdatesOnLaunch else { return }
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let interval: Int64 = 24 * 60 * 60 * 1000
            if settings.lastUpdateCheckEpochMs > 0 && now - settings.lastUpdateCheckEpochMs < interval {
                return
            }
        }
        updateChecking = true
        client.facade.checkForUpdate(currentVersion: currentVersion()) { status, version, changelog, htmlUrl, _ in
            DispatchQueue.main.async {
                settings.lastUpdateCheckEpochMs = Int64(Date().timeIntervalSince1970 * 1000)
                settings.save()
                updateChecking = false
                switch status {
                case "available":
                    if manual || version != settings.skippedUpdateVersion {
                        pendingUpdate = PendingAppUpdate(
                            version: version,
                            changelog: changelog,
                            htmlUrl: htmlUrl,
                        )
                    }
                case "up_to_date":
                    if manual {
                        updateMessage = isArabic ? "لديك أحدث إصدار" : "You are on the latest version"
                    }
                default:
                    if manual {
                        updateMessage = isArabic ? "تعذّر البحث عن تحديثات" : "Could not check for updates"
                    }
                }
            }
        }
    }
}

struct PendingAppUpdate: Identifiable {
    let version: String
    let changelog: String
    let htmlUrl: String
    var id: String { version }
}

struct UpdateAvailableView: View {
    let update: PendingAppUpdate
    let isArabic: Bool
    let onSkip: () -> Void
    let onDismiss: () -> Void

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
                    if let url = URL(string: update.htmlUrl) {
                        Link(isArabic ? "عرض الإصدار" : "View release", destination: url)
                    }
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
