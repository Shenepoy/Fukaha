import Combine
import Shared

/// Keep the Kotlin client stable across SwiftUI renders and close it with its owner.
final class FukahaClient: ObservableObject {
    let facade = FukahaIosFacade()
    private(set) var isClosed = false

    func close() {
        guard !isClosed else { return }
        isClosed = true
        facade.close()
    }

    deinit {
        if !isClosed { facade.close() }
    }
}
