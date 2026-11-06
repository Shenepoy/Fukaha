import XCTest

final class ShareDiscoveryTests: XCTestCase {
    private let host = XCUIApplication(bundleIdentifier: "com.shenepoy.fukaha.discovery-host")
    override func setUpWithError() throws {
        continueAfterFailure = false
        host.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        host.launch()
    }
    override func tearDownWithError() throws { host.terminate() }

    private func findFukaha(sharing button: String) -> XCUIElement {
        host.buttons[button].tap()
        let fukaha = host.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Fukaha")).firstMatch
        if fukaha.waitForExistence(timeout: 4) { return fukaha }
        let more = host.cells.matching(NSPredicate(format: "label == %@", "More")).firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 3), "Cannot inspect the complete activity list.\n\(host.debugDescription)")
        more.tap()
        XCTAssertTrue(host.navigationBars["Apps"].waitForExistence(timeout: 3), "More did not open the Apps list.\n\(host.debugDescription)")
        let found = fukaha.waitForExistence(timeout: 8)
        let screenshot = XCTAttachment(screenshot: host.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
        XCTAssertTrue(found, "Fukaha missing for \(button), including More list.\n\(host.debugDescription)")
        return fukaha
    }

    func testURLIsListedAndLaunchesRealShareExtension() {
        let extensionButton = findFukaha(sharing: "Share URL")
        extensionButton.tap()
        // This crosses the actual OS extension boundary, unlike the controller unit harness.
        let cleaned = host.staticTexts["Cleaned link"].firstMatch
        XCTAssertTrue(cleaned.waitForExistence(timeout: 20), "Extension did not open its link UI.\n\(host.debugDescription)")
    }
    func testTextIsListed() { _ = findFukaha(sharing: "Share text") }
    func testURLWithPreviewIsListed() { _ = findFukaha(sharing: "Share URL and preview") }
}
