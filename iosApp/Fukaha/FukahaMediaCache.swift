import Foundation
import Shared

/// The app and both extensions must use the same container for manual cleanup.
enum FukahaMediaCache {
    static func directory() throws -> URL {
        let manager = FileManager.default
        guard let group = manager.containerURL(
            forSecurityApplicationGroupIdentifier: IosSettingsKeys.shared.APP_GROUP
        ) else {
            throw NSError(domain: "Fukaha", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Shared media storage is unavailable"
            ])
        }
        let directory = group.appendingPathComponent("Library/Caches/fukaha", isDirectory: true)
        try manager.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory
    }

    static func clear() throws {
        let manager = FileManager.default
        let directory = try directory()
        for file in try manager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
            try manager.removeItem(at: file)
        }
        // Also remove files left by older versions of the containing app.
        let legacy = manager.temporaryDirectory.appendingPathComponent("fukaha")
        if manager.fileExists(atPath: legacy.path) { try manager.removeItem(at: legacy) }
    }
}
