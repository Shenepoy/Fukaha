import Foundation

let suite = "fukaha-source-health-test-\(UUID().uuidString)"
let defaults = UserDefaults(suiteName: suite)!
defer { defaults.removePersistentDomain(forName: suite) }
let preferences = "x\thttps://chosen.example"
defaults.set(preferences, forKey: "preferred_fixers")

var cache = FukahaSourceHealthCache.load(from: defaults)
assert(cache.snapshot.status(for: "new.example") == .unverified)
assert(cache.snapshot.label(for: "new.example", isArabic: false) == "Not checked")
let success = #"{"statuses":{"https://ok.example":"Alive","https://chosen.example":"Dead","https://blocked.example":"Unknown"},"checkedAtEpochMs":1793491200000,"details":{"https://blocked.example":{"reason":"Blocked","httpStatus":403}}}"#
cache.accept(success)
assert(!cache.lastAttemptFailed)
assert(cache.snapshot.respondingCount == 1 && cache.snapshot.failedCount == 1 && cache.snapshot.unverifiedCount == 1)
assert(cache.snapshot.status(for: " OK.EXAMPLE/ ") == .responding)
assert(cache.snapshot.status(for: "chosen.example") == .failed)
assert(cache.snapshot.label(for: "blocked.example", isArabic: false) == "Unverified")
assert(cache.snapshot.details["https://blocked.example"]?.httpStatus == 403)
cache.save(to: defaults)
cache = FukahaSourceHealthCache.load(from: defaults)
assert(cache.snapshot.checkedAtEpochMs == 1793491200000)
assert(cache.snapshot.status(for: "ok.example") == .responding)

for failed in [nil, "invalid", #"{"statuses":{"https://ok.example":"Dead"},"checkedAtEpochMs":1793491300000}"#, #"{"statuses":{"https://blocked.example":"Unknown"},"checkedAtEpochMs":1793491300000}"#] as [String?] {
    cache.accept(failed)
    assert(cache.lastAttemptFailed)
    assert(cache.snapshot.status(for: "ok.example") == .responding)
    assert(cache.snapshot.checkedAtEpochMs == 1793491200000)
}
cache.save(to: defaults)
assert(FukahaSourceHealthCache.load(from: defaults).lastAttemptFailed)
assert(defaults.string(forKey: "preferred_fixers") == preferences)
cache.accept(success)
assert(!cache.lastAttemptFailed)
defaults.set(Data("broken".utf8), forKey: FukahaSourceHealthCache.storageKey)
assert(FukahaSourceHealthCache.load(from: defaults).snapshot.statuses.isEmpty)
print("PASS: iOS source-health decoding, status labels, persistence, failed-run preservation, retry, isolated preferences and corrupt-cache recovery")
