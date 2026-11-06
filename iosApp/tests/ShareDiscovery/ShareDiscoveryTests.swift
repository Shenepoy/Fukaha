import XCTest

final class ShareDiscoveryTests: XCTestCase {
    private let host = XCUIApplication(bundleIdentifier: "com.shenepoy.fukaha.discovery-host")
    override func setUpWithError() throws {
        continueAfterFailure = false
        host.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        host.launch()
    }
    override func tearDownWithError() throws { host.terminate() }

    private var fukaha: XCUIElement {
        host.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Fukaha")).firstMatch
    }

    private func openMore() {
        let more = host.cells.matching(NSPredicate(format: "label == %@", "More")).firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: 3), "Cannot inspect the complete activity list.\n\(host.debugDescription)")
        more.tap()
        XCTAssertTrue(host.navigationBars["Apps"].waitForExistence(timeout: 3), "More did not open the Apps list.\n\(host.debugDescription)")
    }

    private func findFukaha(sharing button: String) -> XCUIElement {
        host.buttons[button].tap()
        if fukaha.waitForExistence(timeout: 4) { return fukaha }
        openMore()
        let found = fukaha.waitForExistence(timeout: 8)
        let screenshot = XCTAttachment(screenshot: host.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
        XCTAssertTrue(found, "Fukaha missing for \(button), including More list.\n\(host.debugDescription)")
        return fukaha
    }

    func testURLIsListedAndLaunchesRealShareExtension() {
        let extensionButton = findFukaha(sharing: "Share URL")
        capture("System share menu")
        extensionButton.tap()
        // This crosses the actual OS extension boundary, unlike the controller unit harness.
        let cleaned = host.staticTexts["Cleaned link"].firstMatch
        XCTAssertTrue(cleaned.waitForExistence(timeout: 20), "Extension did not open its link UI.\n\(host.debugDescription)")
        capture("Share extension icon-only buttons")
    }

    func testActionIsListedAndLaunches() {
        host.buttons["Share URL"].tap()
        let action = host.cells["Clean with Fukaha"].firstMatch
        if !action.waitForExistence(timeout: 3) {
            host.swipeUp()
        }
        XCTAssertTrue(action.waitForExistence(timeout: 5), "Action extension is missing.\n\(host.debugDescription)")
        capture("Action extension template icon")
        action.tap()
        XCTAssertTrue(host.staticTexts["Cleaned link"].waitForExistence(timeout: 20))
        capture("Action extension icon-only buttons")
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: host.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
    func testTextIsListed() { _ = findFukaha(sharing: "Share text") }
    func testURLWithPreviewIsListedAndLaunches() {
        findFukaha(sharing: "Share URL and preview").tap()
        XCTAssertTrue(host.staticTexts["Cleaned link"].waitForExistence(timeout: 20), "Mixed payload did not load its URL.\n\(host.debugDescription)")
    }

    func testImageOnlyDoesNotOfferFukaha() {
        host.buttons["Share image only"].tap()
        XCTAssertFalse(fukaha.waitForExistence(timeout: 3))
        openMore()
        XCTAssertFalse(fukaha.waitForExistence(timeout: 3), "Image-only sharing must not offer the link cleaner.")
    }
}
