import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

private final class UpdateURLProtocol: URLProtocol {
    static var status = 200
    static var payload = Data()
    static var capturedRequest: URLRequest?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.capturedRequest = request
        let response = HTTPURLResponse(url: request.url!, statusCode: Self.status,
                                       httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.payload)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

@main
struct AppUpdateTests {
    static let bundleID = "com.shenepoy.fukaha"

    static func listing(
        version: String = "26.11.0", bundle: String = bundleID,
        url: String = "https://apps.apple.com/sa/app/fukaha/id1234567890",
        minimumOS: String? = nil
    ) -> Data {
        try! JSONSerialization.data(withJSONObject: ["results": [[
            "bundleId": bundle, "version": version, "trackViewUrl": url,
            "releaseNotes": "iOS sharing improvements",
            "minimumOsVersion": minimumOS as Any? ?? NSNull(),
        ]]])
    }

    static func reject(_ data: Data, current: String = "26.10.0") {
        do {
            _ = try IosAppUpdateChecker.evaluate(data, bundleID: bundleID, currentVersion: current)
            fatalError("Expected an invalid Apple response to fail")
        } catch {}
    }

    static func main() async throws {
        let available = try IosAppUpdateChecker.evaluate(listing(), bundleID: bundleID, currentVersion: "26.9.0")
        guard case .available(let update) = available else { fatalError("Expected newer iOS release") }
        assert(update.version == "26.11.0")
        assert(update.changelog == "iOS sharing improvements")
        assert(update.storeURL.host == "apps.apple.com")
        for current in ["26.11.0", "26.11", "26.12.0", "27.0.0"] {
            let result = try IosAppUpdateChecker.evaluate(listing(), bundleID: bundleID, currentVersion: current)
            assert(result == .upToDate)
        }
        let empty = Data(#"{"results":[]}"#.utf8)
        let missing = try IosAppUpdateChecker.evaluate(empty, bundleID: bundleID, currentVersion: "26.10.0")
        assert(missing == .notListed)
        reject(listing(bundle: "another.app"))
        reject(listing(url: "https://github.com/Zyzto/Fukaha/releases/latest"))
        reject(listing(url: "https://apps.apple.com.evil.example/app"))
        reject(listing(url: "http://apps.apple.com/app"))
        for version in ["", "v26.11.0", "26..11", "26.beta", "26.11.0-beta"] {
            reject(listing(version: version))
        }
        reject(listing(), current: "invalid")
        reject(Data("not json".utf8))

        enum Offline: Error { case unavailable }
        let offline: () async throws -> IosUpdateChannel = { throw Offline.unavailable }
        let unknown = await IosUpdateChannel.resolve(transaction: offline, receiptName: nil)
        assert(unknown == .unknown)
        let retried = await IosUpdateChannel.resolve(transaction: { .testFlight }, receiptName: nil)
        assert(retried == .testFlight)
        let receiptFlight = await IosUpdateChannel.resolve(transaction: offline, receiptName: "sandboxReceipt")
        let receiptStore = await IosUpdateChannel.resolve(transaction: offline, receiptName: "receipt")
        let manualBuild = await IosUpdateChannel.resolve(transaction: { .localBuild }, receiptName: nil)
        assert(receiptFlight == .testFlight && receiptStore == .appStore && manualBuild == .localBuild)
        var didRefresh = false
        let refreshed = await IosUpdateChannel.resolve(
            transaction: offline, receiptName: nil,
            refresh: { didRefresh = true; return .appStore }
        )
        assert(didRefresh && refreshed == .appStore)
        let cachedReceipt = await IosUpdateChannel.resolve(
            transaction: offline, receiptName: "sandboxReceipt",
            refresh: { fatalError("An existing receipt must avoid an authentication prompt") }
        )
        assert(cachedReceipt == .testFlight)
        let refreshFailed = await IosUpdateChannel.resolve(
            transaction: offline, receiptName: nil, refresh: offline
        )
        assert(refreshFailed == .unknown)
        let cancelledTask = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return await IosUpdateChannel.resolve(
                transaction: offline, receiptName: nil,
                refresh: { fatalError("A cancelled task must not request authentication") }
            )
        }
        let cancelledChannel = await cancelledTask.value
        assert(cancelledChannel == .unknown)
        assert(IosAppUpdateChecker.lookupCountry(storefrontCode: "SAU", deviceRegion: "US") == "SA")
        assert(IosAppUpdateChecker.lookupCountry(storefrontCode: "USA", deviceRegion: "SA") == "US")
        assert(IosAppUpdateChecker.lookupCountry(storefrontCode: "GBR", deviceRegion: "SA") == "GB")
        assert(IosAppUpdateChecker.lookupCountry(storefrontCode: nil, deviceRegion: "SA") == "SA")
        assert(IosAppUpdateChecker.lookupCountry(storefrontCode: nil, deviceRegion: "001") == "US")
        let ios16 = OperatingSystemVersion(majorVersion: 16, minorVersion: 0, patchVersion: 0)
        let incompatible = try IosAppUpdateChecker.evaluate(
            listing(minimumOS: "17.0"), bundleID: bundleID, currentVersion: "26.10.0",
            operatingSystemVersion: ios16
        )
        assert(incompatible == .requiresNewerOS("17.0"))
        let compatible = try IosAppUpdateChecker.evaluate(
            listing(minimumOS: "16.0.0"), bundleID: bundleID, currentVersion: "26.10.0",
            operatingSystemVersion: ios16
        )
        assert(compatible == available)
        let patchRequired = try IosAppUpdateChecker.evaluate(
            listing(minimumOS: "16.0.1"), bundleID: bundleID, currentVersion: "26.10.0",
            operatingSystemVersion: ios16
        )
        assert(patchRequired == .requiresNewerOS("16.0.1"))

        let now = Date(timeIntervalSince1970: 100_000)
        assert(IosAppUpdateChecker.isCheckDue(lastCheckEpochMs: 0, now: now))
        assert(!IosAppUpdateChecker.isCheckDue(lastCheckEpochMs: 99_000_000, now: now))
        assert(IosAppUpdateChecker.isCheckDue(lastCheckEpochMs: 13_600_000, now: now))
        assert(!IosAppUpdateChecker.isCheckDue(lastCheckEpochMs: 13_600_001, now: now))
        assert(IosAppUpdateChecker.isCheckDue(lastCheckEpochMs: 101_000_000, now: now))

        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [UpdateURLProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let checker = IosAppUpdateChecker(session: session)
        UpdateURLProtocol.payload = listing()
        let result = try await checker.check(bundleID: bundleID, currentVersion: "26.10.0", country: "SA")
        assert(result == available)
        let request = UpdateURLProtocol.capturedRequest!
        let components = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)!
        assert(components.host == "itunes.apple.com")
        assert(components.queryItems?.contains(URLQueryItem(name: "bundleId", value: bundleID)) == true)
        assert(components.queryItems?.contains(URLQueryItem(name: "country", value: "SA")) == true)
        assert(request.timeoutInterval == 15)
        assert(request.cachePolicy == .reloadIgnoringLocalCacheData)
        for status in [404, 429, 500] {
            UpdateURLProtocol.status = status
            do {
                _ = try await checker.check(bundleID: bundleID, currentVersion: "26.10.0")
                fatalError("HTTP \(status) should fail, not report up to date")
            } catch {}
        }
        UpdateURLProtocol.status = 200
        UpdateURLProtocol.payload = empty
        let notListed = try await checker.check(bundleID: bundleID, currentVersion: "26.10.0")
        assert(notListed == .notListed)
        print("PASS: iOS updates (channel recovery, storefront regions, OS compatibility, Apple lookup, versions, missing listings, HTTP failures, scheduling)")
    }
}
