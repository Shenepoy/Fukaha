import Foundation

let catalog = ["a.example", "b.example", "c.example", "d.example"]
let normalized = catalog.map(FukahaSourceHealthSnapshot.normalize)
var ranking = FukahaSourceRanking()
assert(ranking.orderedHosts(platform: "x", catalog: catalog) == normalized)
ranking.toggleFavorite(platform: "x", host: " C.EXAMPLE/ ", catalog: catalog)
ranking.toggleFavorite(platform: "x", host: "a.example", catalog: catalog)
assert(ranking.orderedHosts(platform: "x", catalog: catalog) == [normalized[2], normalized[0], normalized[1], normalized[3]])
assert(ranking.isFavorite(platform: "x", host: "https://c.example"))
assert(!ranking.isFavorite(platform: "youtube", host: "c.example"))

ranking.move(platform: "x", favoritesOnly: true, from: IndexSet(integer: 1), to: 0, catalog: catalog)
assert(ranking.orderedHosts(platform: "x", catalog: catalog) == [normalized[0], normalized[2], normalized[1], normalized[3]])
ranking.move(platform: "x", favoritesOnly: false, from: IndexSet(integer: 0), to: 2, catalog: catalog)
assert(ranking.orderedHosts(platform: "x", catalog: catalog) == [normalized[0], normalized[2], normalized[3], normalized[1]])

let beforeInvalidMove = ranking
ranking.move(platform: "x", favoritesOnly: true, from: IndexSet(integer: 20), to: 0, catalog: catalog)
ranking.move(platform: "x", favoritesOnly: true, from: IndexSet(integer: 0), to: -1, catalog: catalog)
ranking.toggleFavorite(platform: "x", host: "missing.example", catalog: catalog)
assert(ranking == beforeInvalidMove)

ranking.toggleFavorite(platform: "x", host: "a.example", catalog: catalog)
assert(!ranking.isFavorite(platform: "x", host: "a.example"))
assert(ranking.orderedHosts(platform: "x", catalog: catalog) == [normalized[2], normalized[0], normalized[3], normalized[1]])
assert(ranking.orderedHosts(platform: "x", catalog: ["c.example", "b.example", "new.example", "B.EXAMPLE/"]) == [normalized[2], normalized[1], "https://new.example"])

var multi = FukahaSourceRanking()
multi.move(platform: "x", favoritesOnly: false, from: IndexSet([0, 1]), to: 4, catalog: catalog)
assert(multi.orderedHosts(platform: "x", catalog: catalog) == [normalized[2], normalized[3], normalized[0], normalized[1]])

let suite = "fukaha-ranking-test-\(UUID().uuidString)"
let defaults = UserDefaults(suiteName: suite)!
defer { defaults.removePersistentDomain(forName: suite) }
defaults.set("x\\thttps://b.example", forKey: "preferred_fixers")
ranking.save(to: defaults)
let restored = FukahaSourceRanking.load(from: defaults)
assert(restored == ranking)
assert(restored.orderedHosts(platform: "x", catalog: catalog) == ranking.orderedHosts(platform: "x", catalog: catalog))
assert(restored.orderedHosts(platform: "youtube", catalog: catalog) == normalized)
assert(defaults.string(forKey: "preferred_fixers") == "x\\thttps://b.example")
defaults.set(Data("invalid".utf8), forKey: FukahaSourceRanking.storageKey)
assert(FukahaSourceRanking.load(from: defaults) == FukahaSourceRanking())
print("PASS: iOS favorites and ranking (star order, drag moves, section boundaries, normalization, new/removed sources, persistence, platform and selection isolation)")
