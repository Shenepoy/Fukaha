import Foundation
import Shared

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
        FukahaSettings.companion.isValidCobaltBaseUrl(url: cobaltBaseUrl)
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
        s.skippedUpdateVersion = d.string(forKey: IosSettingsKeys.shared.SKIPPED_UPDATE) ?? ""
        if let stored = d.object(forKey: IosSettingsKeys.shared.LAST_UPDATE_CHECK) as? NSNumber {
            s.lastUpdateCheckEpochMs = stored.int64Value
        }
        if let raw = d.string(forKey: IosSettingsKeys.shared.PREFERRED_FIXERS) {
            s.preferredFixers = FukahaSourcePreferences.decode(raw)
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
        let valid = FukahaSettings.companion.isValidCobaltBaseUrl(url: url)
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
        d.set(skippedUpdateVersion, forKey: IosSettingsKeys.shared.SKIPPED_UPDATE)
        d.set(NSNumber(value: lastUpdateCheckEpochMs), forKey: IosSettingsKeys.shared.LAST_UPDATE_CHECK)
        let raw = FukahaSourcePreferences.encode(preferredFixers)
        d.set(raw, forKey: IosSettingsKeys.shared.PREFERRED_FIXERS)
    }
}
