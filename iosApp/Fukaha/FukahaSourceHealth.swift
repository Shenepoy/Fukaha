import Combine
import Foundation
import Shared

@MainActor
final class FukahaSourceHealth: ObservableObject {
    @Published private(set) var cache: FukahaSourceHealthCache
    @Published private(set) var isChecking = false
    @Published private(set) var completed = 0
    @Published private(set) var total = 0
    @Published private(set) var currentHost = ""

    private let defaults: UserDefaults
    private let checker = IosSourceHealthChecker()
    private var generation = 0

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        cache = FukahaSourceHealthCache.load(from: defaults)
    }

    func refresh() {
        guard !isChecking else { return }
        generation += 1
        let run = generation
        isChecking = true
        completed = 0
        total = 0
        currentHost = ""
        checker.refresh(onProgress: { [weak self] progress in
            DispatchQueue.main.async {
                guard let self, self.generation == run else { return }
                self.completed = Int(progress.completedCount)
                self.total = Int(progress.total)
                self.currentHost = progress.displayHost
            }
        }, onResult: { [weak self] json, _ in
            DispatchQueue.main.async {
                guard let self, self.generation == run else { return }
                self.cache.accept(json)
                self.cache.save(to: self.defaults)
                self.isChecking = false
                self.currentHost = ""
            }
        })
    }

    func cancel() {
        generation += 1
        checker.cancel()
        isChecking = false
        currentHost = ""
    }

    deinit { checker.close() }
}
