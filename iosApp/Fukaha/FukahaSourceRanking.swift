import Foundation

/// Display preferences only. Active sources remain in SettingsSnapshot.preferredFixers.
struct FukahaSourceRanking: Codable, Equatable {
    static let storageKey = "fukaha.sourceRanking.v1"
    private(set) var favorites: [String: [String]] = [:]
    private(set) var order: [String: [String]] = [:]

    func isFavorite(platform: String, host: String) -> Bool {
        let key = FukahaSourceHealthSnapshot.normalize(host)
        return favorites[platform, default: []].contains { FukahaSourceHealthSnapshot.normalize($0) == key }
    }

    func orderedHosts(platform: String, catalog: [String]) -> [String] {
        let available = unique(catalog)
        let availableSet = Set(available)
        let saved = unique(order[platform, default: []]).filter { availableSet.contains($0) }
        let savedSet = Set(saved)
        let arranged = saved + available.filter { !savedSet.contains($0) }
        return arranged.filter { isFavorite(platform: platform, host: $0) } +
            arranged.filter { !isFavorite(platform: platform, host: $0) }
    }

    mutating func toggleFavorite(platform: String, host: String, catalog: [String]) {
        let key = FukahaSourceHealthSnapshot.normalize(host)
        let arranged = orderedHosts(platform: platform, catalog: catalog)
        guard arranged.contains(key) else { return }
        let wasFavorite = isFavorite(platform: platform, host: key)
        let existingFavorites = arranged.filter { isFavorite(platform: platform, host: $0) && $0 != key }
        let others = arranged.filter { !isFavorite(platform: platform, host: $0) && $0 != key }
        favorites[platform] = wasFavorite ? existingFavorites : existingFavorites + [key]
        // New stars append to the ranked favorites; unstarred sources lead the other group.
        order[platform] = existingFavorites + [key] + others
    }

    /// SwiftUI move destinations refer to the section before the moved items are removed.
    mutating func move(
        platform: String, favoritesOnly: Bool, from offsets: IndexSet, to destination: Int, catalog: [String]
    ) {
        let arranged = orderedHosts(platform: platform, catalog: catalog)
        let section = arranged.filter { isFavorite(platform: platform, host: $0) == favoritesOnly }
        let other = arranged.filter { isFavorite(platform: platform, host: $0) != favoritesOnly }
        guard !offsets.isEmpty, offsets.allSatisfy({ section.indices.contains($0) }),
              (0...section.count).contains(destination) else { return }
        let moving = offsets.sorted().map { section[$0] }
        var remaining = section.enumerated().filter { !offsets.contains($0.offset) }.map(\.element)
        let insertion = destination - offsets.filter { $0 < destination }.count
        remaining.insert(contentsOf: moving, at: insertion)
        order[platform] = favoritesOnly ? remaining + other : other + remaining
    }

    static func load(from defaults: UserDefaults) -> Self {
        guard let data = defaults.data(forKey: storageKey),
              let value = try? JSONDecoder().decode(Self.self, from: data) else { return Self() }
        return value
    }

    func save(to defaults: UserDefaults) {
        if let data = try? JSONEncoder().encode(self) { defaults.set(data, forKey: Self.storageKey) }
    }

    private func unique(_ hosts: [String]) -> [String] {
        var seen = Set<String>()
        return hosts.map(FukahaSourceHealthSnapshot.normalize).filter { !$0.isEmpty && seen.insert($0).inserted }
    }
}
