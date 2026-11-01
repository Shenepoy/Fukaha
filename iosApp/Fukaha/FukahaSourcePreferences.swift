import Foundation

/// The app and extensions share the same tab/newline format with IosSettingsStore.
enum FukahaSourcePreferences {
    static func decode(_ raw: String) -> [String: String] {
        var result: [String: String] = [:]
        for line in raw.split(separator: "\n") {
            let parts = line.split(separator: "\t", maxSplits: 1, omittingEmptySubsequences: false)
            guard parts.count == 2 else { continue }
            let key = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
            let host = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
            guard !key.isEmpty, !host.isEmpty else { continue }
            // Match Kotlin's toMap(): the last entry wins, without a startup crash.
            result[key] = host
        }
        return result
    }

    static func encode(_ values: [String: String]) -> String {
        values.keys.sorted().compactMap { key -> String? in
            guard let value = values[key], !key.isEmpty, !value.isEmpty,
                  !key.contains(where: { $0 == "\t" || $0 == "\n" || $0 == "\r" }),
                  !value.contains(where: { $0 == "\t" || $0 == "\n" || $0 == "\r" }) else { return nil }
            return "\(key)\t\(value)"
        }.joined(separator: "\n")
    }
}
