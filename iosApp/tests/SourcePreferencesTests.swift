import Foundation

let duplicated = "x\thttps://fixvx.com\nx\thttps://fixupx.com"
assert(FukahaSourcePreferences.decode(duplicated)["x"] == "https://fixupx.com")
assert(FukahaSourcePreferences.decode("").isEmpty)
assert(FukahaSourcePreferences.decode("bad\n\tx\nx\t\n\t").isEmpty)
assert(FukahaSourcePreferences.decode(" x\t fixvx.com \r\n")["x"] == "fixvx.com")

let saved = ["x": "https://fixupx.com", "youtube": "https://koutube.com", "bluesky": "bskx.app"]
assert(FukahaSourcePreferences.decode(FukahaSourcePreferences.encode(saved)) == saved)
assert(FukahaSourcePreferences.encode(saved) == "bluesky\tbskx.app\nx\thttps://fixupx.com\nyoutube\thttps://koutube.com")
assert(FukahaSourcePreferences.encode(["x\nyoutube": "bad", "x": "bad\tvalue"]).isEmpty)
print("PASS: iOS source preferences (duplicates, malformed input, whitespace, round trip, stable order, invalid separators)")

let catalogPath = ProcessInfo.processInfo.environment["FUKAHA_CATALOG_PATH"]!
let catalogData = try Data(contentsOf: URL(fileURLWithPath: catalogPath))
let catalog = try JSONSerialization.jsonObject(with: catalogData) as! [String: [String: Any]]
var checkedSources = 0
for (platform, entry) in catalog {
    for service in entry["services"] as! [[String: Any]] {
        let host = service["host"] as! String
        let preference = [platform: host.trimmingCharacters(in: CharacterSet(charactersIn: "/"))]
        assert(FukahaSourcePreferences.decode(FukahaSourcePreferences.encode(preference)) == preference)
        checkedSources += 1
    }
}
print("PASS: iOS shared preference format preserves all \(checkedSources) catalog sources")
