// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "Fukaha",
    platforms: [
        .iOS(.v16),
    ],
    products: [
        .library(
            name: "Fukaha",
            targets: ["Fukaha"],
        ),
        .library(
            name: "FukahaShare",
            targets: ["FukahaShare"],
        ),
        .library(
            name: "FukahaAction",
            targets: ["FukahaAction"],
        ),
    ],
    targets: [
        .target(
            name: "Fukaha",
            dependencies: ["Shared"],
            path: ".xtool-sources/Fukaha",
        ),
        .target(
            name: "FukahaShare",
            dependencies: ["Shared"],
            path: ".xtool-sources/FukahaShare",
        ),
        .target(
            name: "FukahaAction",
            dependencies: ["Shared"],
            path: ".xtool-sources/FukahaAction",
        ),
        .binaryTarget(
            name: "Shared",
            path: ".xtool-input/Shared.xcframework",
        ),
    ],
    swiftLanguageModes: [.v5],
)
