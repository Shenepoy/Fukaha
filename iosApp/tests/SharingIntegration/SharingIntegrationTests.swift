import XCTest
import UIKit
import SwiftUI
import UniformTypeIdentifiers
@testable import FukahaShareHarness

private final class InputContext: NSExtensionContext {
    var items: [Any] = []
    var completions = 0
    override var inputItems: [Any] { items }
    override func completeRequest(returningItems items: [Any]?, completionHandler: ((Bool) -> Void)? = nil) {
        completions += 1
        completionHandler?(false)
    }
}

private final class InputController: ShareViewController {
    let input = InputContext()
    override var extensionContext: NSExtensionContext? { input }
}

@MainActor
final class SharingIntegrationTests: XCTestCase {
    private var window: UIWindow!
    private var controller: InputController!
    private var previousSettings: SettingsSnapshot!
    private let source = "https://x.com/example/status/123?utm_source=share"
    private let cleaned = "https://x.com/example/status/123"
    private let embedded = "https://fixupx.com/example/status/123"

    override func setUp() {
        super.setUp()
        previousSettings = SettingsSnapshot.load()
        var settings = SettingsSnapshot()
        settings.resolveShortLinks = false // Deterministic; no external networking in these tests.
        settings.preferredFixers = ["x": "fixupx.com"]
        settings.language = "English"
        settings.save()
        window = UIWindow(frame: UIScreen.main.bounds)
    }

    override func tearDown() {
        controller?.perform(NSSelectorFromString("cancel"))
        window?.rootViewController?.dismiss(animated: false)
        window?.isHidden = true
        window?.rootViewController = nil
        controller = nil
        window = nil
        previousSettings.save()
        super.tearDown()
    }

    private func waitUntil(_ description: String, _ condition: @escaping () -> Bool) async {
        let deadline = Date().addingTimeInterval(8)
        while !condition() && Date() < deadline {
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertTrue(condition(), description)
    }

    private func load(_ providers: [NSItemProvider], fallback: String? = nil, appear: Bool = true) async {
        let item = NSExtensionItem()
        item.attachments = providers
        item.attributedContentText = fallback.map(NSAttributedString.init(string:))
        controller = InputController()
        controller.input.items = [item]
        controller.loadViewIfNeeded()
        if appear { show(controller) }
        await waitUntil("Shared input finishes preparing") { !self.controller.model.loading }
    }

    private func show(_ viewController: UIViewController) {
        window.rootViewController = viewController
        window.makeKeyAndVisible()
    }

    private func urlProvider() -> NSItemProvider {
        NSItemProvider(item: URL(string: source)! as NSURL, typeIdentifier: UTType.url.identifier)
    }

    private func assertPrepared() {
        XCTAssertNil(controller.model.error)
        XCTAssertEqual(controller.model.cleanedUrl, cleaned)
        XCTAssertEqual(controller.model.embedUrl, embedded)
        XCTAssertEqual(SettingsSnapshot.load().preferredFixers["x"], "fixupx.com")
    }

    func testIncomingURLUsesSavedSourceAndRemovesTracking() async {
        await load([urlProvider()])
        assertPrepared()
        XCTAssertNil(controller.presentedViewController, "Ask must wait for a choice")
    }

    func testIncomingTextAndFallbackText() async {
        let text = "Read this: \(source)"
        await load([NSItemProvider(item: text as NSString, typeIdentifier: UTType.plainText.identifier)])
        assertPrepared()
        await load([], fallback: text)
        assertPrepared()
    }

    func testFailedAttachmentFallsThroughToValidURL() async {
        let broken = NSItemProvider()
        broken.registerDataRepresentation(forTypeIdentifier: UTType.url.identifier, visibility: .all) { completion in
            completion(nil, NSError(domain: "TestProvider", code: 1))
            return nil
        }
        await load([broken, urlProvider()])
        assertPrepared()
    }

    func testNoLinkShowsErrorWithoutOpeningShareSheet() async {
        await load([], fallback: "No URL here")
        XCTAssertEqual(controller.model.error, "No link found")
        XCTAssertNil(controller.presentedViewController)
    }

    func testAutomaticCleanWaitsForAppearanceAndCancellationAllowsRetry() async {
        var settings = SettingsSnapshot.load()
        settings.defaultAction = "Clean"
        settings.save()
        await load([urlProvider()], appear: false)
        assertPrepared()
        XCTAssertNil(controller.presentedViewController)
        show(controller)
        await waitUntil("Automatic Clean opens the system share sheet") {
            self.controller.presentedViewController is UIActivityViewController
        }
        guard let activity = controller.presentedViewController as? UIActivityViewController else { return }
        if UIDevice.current.userInterfaceIdiom == .pad {
            XCTAssertTrue(activity.popoverPresentationController?.sourceView === controller.view)
            XCTAssertFalse(activity.popoverPresentationController?.sourceRect.isEmpty ?? true)
        }
        activity.completionWithItemsHandler?(nil, false, nil, nil)
        activity.dismiss(animated: false)
        await waitUntil("Cancelled sheet is dismissed") { self.controller.presentedViewController == nil }
        XCTAssertEqual(controller.input.completions, 0, "Cancellation must preserve extension choices")
        controller.perform(NSSelectorFromString("shareClean"))
        await waitUntil("Retry opens the system share sheet") {
            self.controller.presentedViewController is UIActivityViewController
        }
        let retry = controller.presentedViewController as? UIActivityViewController
        retry?.completionWithItemsHandler?(nil, true, nil, nil)
        await waitUntil("Successful share completes extension once") { self.controller.input.completions == 1 }
        controller.perform(NSSelectorFromString("cancel"))
        XCTAssertEqual(controller.input.completions, 1)
    }

    func testAutomaticEmbedAndDownloadWithoutServer() async {
        var settings = SettingsSnapshot.load()
        settings.defaultAction = "Embed"
        settings.save()
        await load([urlProvider()])
        assertPrepared()
        await waitUntil("Automatic Embed opens sharing") { self.controller.presentedViewController is UIActivityViewController }
        controller.presentedViewController?.dismiss(animated: false)
        controller.perform(NSSelectorFromString("cancel"))
        settings.defaultAction = "Download"
        settings.cobaltBaseUrl = ""
        settings.save()
        await load([urlProvider()])
        assertPrepared()
        XCTAssertNil(controller.presentedViewController, "Missing media server falls back to choices")
    }

    func testContainerQuickSharePresentsSystemSheetAndCompletes() async {
        var settings = SettingsSnapshot.load()
        settings.defaultAction = "Clean"
        let client = FukahaClient()
        defer { client.close() }
        var dismissed = false
        let host = UIHostingController(rootView: FukahaQuickShareView(
            text: source, settings: settings, client: client, onDismiss: { dismissed = true }
        ))
        show(host)
        await waitUntil("Container quick share presents a sheet") { host.presentedViewController != nil }
        func activity(in root: UIViewController) -> UIActivityViewController? {
            if let sheet = root as? UIActivityViewController { return sheet }
            if let presented = root.presentedViewController, let found = activity(in: presented) { return found }
            return root.children.lazy.compactMap { activity(in: $0) }.first
        }
        await waitUntil("Container presents the system activity controller") { activity(in: host) != nil }
        activity(in: host)?.completionWithItemsHandler?(nil, true, nil, nil)
        await waitUntil("Completed quick share dismisses its containing sheet") { dismissed }
    }
}
