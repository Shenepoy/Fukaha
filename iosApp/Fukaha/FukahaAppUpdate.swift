import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
#if os(iOS)
import StoreKit
#endif

enum IosUpdateChannel {
    case appStore, testFlight, localBuild, unknown

    static func resolve(
        transaction: () async throws -> Self,
        receiptName: String?,
        refresh: (() async throws -> Self)? = nil
    ) async -> Self {
        if let channel = try? await transaction() { return channel }
        switch receiptName {
        case "sandboxReceipt": return .testFlight
        case "receipt": return .appStore
        default:
            guard !Task.isCancelled, let refresh else { return .unknown }
            return (try? await refresh()) ?? .unknown
        }
    }

    #if os(iOS)
    static func current(allowRefresh: Bool = false) async -> Self {
        #if DEBUG
        return .localBuild
        #else
        let receipt = Bundle.main.appStoreReceiptURL
        let receiptName = receipt.flatMap {
            FileManager.default.fileExists(atPath: $0.path) ? $0.lastPathComponent : nil
        }
        return await resolve(
            transaction: {
                try verifiedChannel(from: await AppTransaction.shared)
            },
            receiptName: receiptName,
            // Refresh may show Apple's sign-in prompt; only a manual retry enables it.
            refresh: allowRefresh ? {
                try verifiedChannel(from: await AppTransaction.refresh())
            } : nil
        )
        #endif
    }

    private static func verifiedChannel(from result: VerificationResult<AppTransaction>) throws -> Self {
        guard case .verified(let transaction) = result else {
            throw IosAppUpdateChecker.CheckError.invalidResponse
        }
        if transaction.environment == .production { return .appStore }
        if transaction.environment == .sandbox { return .testFlight }
        return .localBuild
    }
    #endif
}

struct PendingAppUpdate: Identifiable, Equatable {
    let version: String
    let changelog: String
    let storeURL: URL
    var id: String { version }
}

enum IosUpdateResult: Equatable {
    case available(PendingAppUpdate)
    case upToDate
    case notListed
    case requiresNewerOS(String)
}

struct IosAppUpdateChecker {
    enum CheckError: Error { case invalidResponse }

    private struct Lookup: Decodable {
        let results: [Listing]
    }

    private struct Listing: Decodable {
        let bundleId: String
        let version: String
        let releaseNotes: String?
        let trackViewUrl: String
        let minimumOsVersion: String?
    }

    var session: URLSession = .shared

    func check(
        bundleID: String,
        currentVersion: String,
        country: String? = nil
    ) async throws -> IosUpdateResult {
        guard !bundleID.isEmpty else { throw CheckError.invalidResponse }
        var region = country
        if region == nil {
            #if os(iOS)
            let storefrontCode = await Storefront.current?.countryCode
            #else
            let storefrontCode: String? = nil
            #endif
            region = Self.lookupCountry(storefrontCode: storefrontCode)
        }
        var components = URLComponents(string: "https://itunes.apple.com/lookup")!
        components.queryItems = [
            URLQueryItem(name: "bundleId", value: bundleID),
            URLQueryItem(name: "country", value: region),
            URLQueryItem(name: "entity", value: "software"),
        ]
        var request = URLRequest(url: components.url!, cachePolicy: .reloadIgnoringLocalCacheData)
        request.timeoutInterval = 15
        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()
        guard (response as? HTTPURLResponse)?.statusCode == 200 else {
            throw CheckError.invalidResponse
        }
        return try Self.evaluate(data, bundleID: bundleID, currentVersion: currentVersion)
    }

    static func evaluate(
        _ data: Data, bundleID: String, currentVersion: String,
        operatingSystemVersion: OperatingSystemVersion = ProcessInfo.processInfo.operatingSystemVersion
    ) throws -> IosUpdateResult {
        let lookup = try JSONDecoder().decode(Lookup.self, from: data)
        guard !lookup.results.isEmpty else { return .notListed }
        guard let listing = lookup.results.first(where: { $0.bundleId == bundleID }),
              let latest = versionParts(listing.version),
              let current = versionParts(currentVersion),
              let url = URL(string: listing.trackViewUrl),
              url.scheme == "https", url.host == "apps.apple.com" else {
            throw CheckError.invalidResponse
        }
        let count = max(latest.count, current.count)
        for index in 0..<count {
            let a = index < latest.count ? latest[index] : 0
            let b = index < current.count ? current[index] : 0
            if a > b {
                if let minimumOS = listing.minimumOsVersion {
                    guard let required = versionParts(minimumOS) else { throw CheckError.invalidResponse }
                    let installed = [operatingSystemVersion.majorVersion, operatingSystemVersion.minorVersion,
                                     operatingSystemVersion.patchVersion]
                    let size = max(required.count, installed.count)
                    let paddedRequired = required + Array(repeating: 0, count: size - required.count)
                    let paddedInstalled = installed + Array(repeating: 0, count: size - installed.count)
                    if paddedInstalled.lexicographicallyPrecedes(paddedRequired) {
                        return .requiresNewerOS(minimumOS)
                    }
                }
                return .available(PendingAppUpdate(
                    version: listing.version,
                    changelog: listing.releaseNotes ?? "",
                    storeURL: url
                ))
            }
            if a < b { return .upToDate }
        }
        return .upToDate
    }

    static func lookupCountry(
        storefrontCode: String?, deviceRegion: String? = Locale.current.region?.identifier
    ) -> String {
        // Foundation canonicalizes ISO alpha-3 aliases (SAU, USA) to BCP 47 region codes.
        for code in [storefrontCode, deviceRegion].compactMap({ $0 }) {
            let identifier = Locale.canonicalIdentifier(from: "en_\(code.uppercased())")
            if let region = Locale(identifier: identifier).region?.identifier,
               region.count == 2, region.allSatisfy({ $0.isASCII && $0.isLetter }) {
                return region
            }
        }
        return "US"
    }

    static func isCheckDue(lastCheckEpochMs: Int64, now: Date = Date()) -> Bool {
        let nowMs = Int64(now.timeIntervalSince1970 * 1000)
        // Recover if a device clock correction left a timestamp in the future.
        return lastCheckEpochMs <= 0 || lastCheckEpochMs > nowMs ||
            nowMs - lastCheckEpochMs >= 24 * 60 * 60 * 1000
    }

    private static func versionParts(_ version: String) -> [Int]? {
        let components = version.split(separator: ".", omittingEmptySubsequences: false)
        guard !components.isEmpty else { return nil }
        let numbers = components.compactMap { part -> Int? in
            guard !part.isEmpty, part.allSatisfy({ $0.isASCII && $0.isNumber }) else { return nil }
            return Int(part)
        }
        return numbers.count == components.count ? numbers : nil
    }
}
