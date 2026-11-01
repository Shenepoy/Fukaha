import Foundation

enum FukahaSourceStatus: String, Codable {
    case responding = "Alive"
    case failed = "Dead"
    case unverified = "Unknown"
}

struct FukahaSourceCheckDetail: Codable {
    let reason: String
    let httpStatus: Int?
    let finalHost: String?
}

struct FukahaSourceHealthSnapshot: Codable {
    var statuses: [String: FukahaSourceStatus] = [:]
    var checkedAtEpochMs: Int64?
    var details: [String: FukahaSourceCheckDetail] = [:]

    private enum CodingKeys: String, CodingKey { case statuses, checkedAtEpochMs, details }
    init() {}
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        statuses = try values.decodeIfPresent([String: FukahaSourceStatus].self, forKey: .statuses) ?? [:]
        checkedAtEpochMs = try values.decodeIfPresent(Int64.self, forKey: .checkedAtEpochMs)
        details = try values.decodeIfPresent([String: FukahaSourceCheckDetail].self, forKey: .details) ?? [:]
    }

    var respondingCount: Int { statuses.values.filter { $0 == .responding }.count }
    var failedCount: Int { statuses.values.filter { $0 == .failed }.count }
    var unverifiedCount: Int { statuses.values.filter { $0 == .unverified }.count }
    var checkedAt: Date? { checkedAtEpochMs.map { Date(timeIntervalSince1970: Double($0) / 1000) } }

    func status(for host: String) -> FukahaSourceStatus {
        statuses[Self.normalize(host)] ?? .unverified
    }

    func label(for host: String, isArabic: Bool) -> String {
        guard let status = statuses[Self.normalize(host)] else {
            return isArabic ? "لم يُفحص" : "Not checked"
        }
        switch status {
        case .responding: return isArabic ? "يستجيب" : "Responding"
        case .failed: return isArabic ? "فشل الفحص" : "Failed"
        case .unverified: return isArabic ? "غير مؤكد" : "Unverified"
        }
    }

    static func normalize(_ host: String) -> String {
        var key = host.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        while key.hasSuffix("/") { key.removeLast() }
        guard !key.isEmpty else { return key }
        return key.hasPrefix("https://") || key.hasPrefix("http://") ? key : "https://\(key)"
    }
}

/// A separate cache cannot overwrite the app/extension source preferences.
struct FukahaSourceHealthCache: Codable {
    static let storageKey = "fukaha.sourceHealth.v1"
    var snapshot = FukahaSourceHealthSnapshot()
    var lastAttemptFailed = false

    mutating func accept(_ json: String?) {
        guard let data = json?.data(using: .utf8),
              let result = try? JSONDecoder().decode(FukahaSourceHealthSnapshot.self, from: data),
              result.checkedAtEpochMs != nil, result.respondingCount > 0 else {
            lastAttemptFailed = true
            return
        }
        snapshot = result
        lastAttemptFailed = false
    }

    static func load(from defaults: UserDefaults) -> Self {
        guard let data = defaults.data(forKey: storageKey),
              let cached = try? JSONDecoder().decode(Self.self, from: data) else { return Self() }
        return cached
    }

    func save(to defaults: UserDefaults) {
        if let data = try? JSONEncoder().encode(self) { defaults.set(data, forKey: Self.storageKey) }
    }
}
