import SwiftUI
import UIKit

extension Color {
    private static func fukahaAdaptive(
        lightRed: Double,
        lightGreen: Double,
        lightBlue: Double,
        darkRed: Double,
        darkGreen: Double,
        darkBlue: Double,
    ) -> Color {
        Color(uiColor: UIColor { traits in
            let dark = traits.userInterfaceStyle == .dark
            return UIColor(
                red: CGFloat(dark ? darkRed : lightRed),
                green: CGFloat(dark ? darkGreen : lightGreen),
                blue: CGFloat(dark ? darkBlue : lightBlue),
                alpha: 1,
            )
        })
    }

    // Keep the iOS palette on the same Material 3 roles used by Android.
    static let fukahaBackground = fukahaAdaptive(
        lightRed: 0.965, lightGreen: 0.984, lightBlue: 0.961,
        darkRed: 0.059, darkGreen: 0.082, darkBlue: 0.067,
    )
    static let fukahaSurfaceLow = fukahaAdaptive(
        lightRed: 0.941, lightGreen: 0.961, lightBlue: 0.937,
        darkRed: 0.090, darkGreen: 0.114, darkBlue: 0.098,
    )
    static let fukahaSurfaceHigh = fukahaAdaptive(
        lightRed: 0.894, lightGreen: 0.918, lightBlue: 0.890,
        darkRed: 0.145, darkGreen: 0.169, darkBlue: 0.153,
    )
    static let fukahaOnSurface = fukahaAdaptive(
        lightRed: 0.090, lightGreen: 0.114, lightBlue: 0.098,
        darkRed: 0.871, darkGreen: 0.894, darkBlue: 0.863,
    )
    static let fukahaSecondary = fukahaAdaptive(
        lightRed: 0.251, lightGreen: 0.286, lightBlue: 0.259,
        darkRed: 0.749, darkGreen: 0.796, darkBlue: 0.741,
    )
    static let fukahaPrimary = fukahaAdaptive(
        lightRed: 0.090, lightGreen: 0.420, lightBlue: 0.286,
        darkRed: 0.533, darkGreen: 0.847, darkBlue: 0.678,
    )
    static let fukahaOnPrimary = fukahaAdaptive(
        lightRed: 1.000, lightGreen: 1.000, lightBlue: 1.000,
        darkRed: 0.000, darkGreen: 0.220, darkBlue: 0.133,
    )
    static let fukahaPrimaryContainer = fukahaAdaptive(
        lightRed: 0.643, lightGreen: 0.949, lightBlue: 0.784,
        darkRed: 0.000, darkGreen: 0.322, darkBlue: 0.200,
    )
    static let fukahaOnPrimaryContainer = fukahaAdaptive(
        lightRed: 0.000, lightGreen: 0.129, lightBlue: 0.071,
        darkRed: 0.643, darkGreen: 0.949, darkBlue: 0.784,
    )
    static let fukahaOutline = fukahaAdaptive(
        lightRed: 0.749, lightGreen: 0.788, lightBlue: 0.749,
        darkRed: 0.251, darkGreen: 0.286, darkBlue: 0.251,
    )
    static let fukahaGold = Color(red: 1.0, green: 0.757, blue: 0.027)
    static let fukahaOnGold = Color(red: 0.169, green: 0.129, blue: 0.0)
    static let fukahaError = fukahaAdaptive(
        lightRed: 0.729, lightGreen: 0.102, lightBlue: 0.102,
        darkRed: 1.000, darkGreen: 0.706, darkBlue: 0.671,
    )
}

struct FukahaSection<Content: View>: View {
    let title: String
    private let content: Content

    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(Color.fukahaPrimary)
                .padding(.horizontal, 4)
                .padding(.vertical, 2)
            VStack(spacing: 0) {
                content
            }
            .padding(.vertical, 4)
            .background(Color.fukahaSurfaceLow)
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        }
    }
}

struct FukahaBrandAppBar: View {
    @Binding var language: String
    @Binding var theme: String
    let isArabic: Bool
    let onHelp: () -> Void
    @Environment(\.colorScheme) private var colorScheme

    private var themeIcon: String {
        switch theme {
        case "Light": return "sun.max.fill"
        case "Dark": return "moon.fill"
        default: return colorScheme == .dark ? "moon.fill" : "sun.max.fill"
        }
    }

    var body: some View {
        HStack(spacing: 10) {
            Image("FukahaBrand")
                .resizable()
                .scaledToFit()
                .frame(width: 52, height: 52)
                .accessibilityHidden(true)
            Text(isArabic ? "فكها" : "Fukaha")
                .font(.system(size: 36, weight: .bold))
                .foregroundStyle(Color.fukahaOnSurface)
                .lineLimit(1)
            Spacer(minLength: 8)
            Button(action: onHelp) {
                Image(systemName: "questionmark.circle")
                    .font(.system(size: 28, weight: .medium))
            }
            .accessibilityLabel(isArabic ? "المساعدة" : "Help")
            languageMenu
            themeMenu
        }
        .foregroundStyle(Color.fukahaSecondary)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
        .background(Color.fukahaBackground)
    }

    private var languageMenu: some View {
        Menu {
            Button { language = "System" } label: { Text("⚙︎  System") }
            Button { language = "English" } label: { Text("🇬🇧  English") }
            Button { language = "Arabic" } label: { Text("🇸🇦  العربية") }
        } label: {
            Image(systemName: "globe")
                .font(.system(size: 28, weight: .medium))
        }
        .accessibilityLabel(isArabic ? "اللغة" : "Language")
    }

    private var themeMenu: some View {
        Menu {
            Button { theme = "System" } label: {
                Text(isArabic ? "حسب النظام" : "System")
            }
            Button { theme = "Light" } label: {
                Text(isArabic ? "فاتح" : "Light")
            }
            Button { theme = "Dark" } label: {
                Text(isArabic ? "داكن" : "Dark")
            }
        } label: {
            Image(systemName: themeIcon)
                .font(.system(size: 28, weight: .medium))
        }
        .accessibilityLabel(isArabic ? "السمة" : "Theme")
    }
}

struct FukahaActionRow: View {
    let icon: String
    let title: String
    let subtitle: String
    let selected: Bool
    let enabled: Bool
    let selectedLabel: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 27, weight: .semibold))
                    .frame(width: 32)
                    .foregroundStyle(selected ? Color.fukahaPrimary : Color.fukahaSecondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 21, weight: .semibold))
                        .foregroundStyle(selected ? Color.fukahaOnPrimaryContainer : Color.fukahaOnSurface)
                    Text(subtitle)
                        .font(.system(size: 17, weight: .regular))
                        .foregroundStyle(
                            selected
                                ? Color.fukahaOnPrimaryContainer.opacity(0.78)
                                : Color.fukahaSecondary,
                        )
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                if selected {
                    Text(selectedLabel)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Color.fukahaOnGold)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.fukahaGold)
                        .clipShape(Capsule())
                }
            }
            .frame(maxWidth: .infinity, minHeight: 68, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(selected ? Color.fukahaPrimaryContainer : Color.fukahaBackground)
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(
                        selected ? Color.fukahaPrimary : Color.fukahaOutline,
                        lineWidth: selected ? 3 : 1.5,
                    )
            }
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.38)
    }
}

struct FukahaDivider: View {
    var body: some View {
        Divider()
            .overlay(Color.fukahaOutline.opacity(0.75))
            .padding(.horizontal, 16)
    }
}

struct FukahaListRow: View {
    let icon: String
    let title: String
    let subtitle: String?
    let trailingIcon: String?

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 25, weight: .medium))
                .foregroundStyle(Color.fukahaSecondary)
                .frame(width: 30)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.system(size: 18, weight: .regular))
                    .foregroundStyle(Color.fukahaOnSurface)
                if let subtitle {
                    Text(subtitle)
                        .font(.system(size: 16, weight: .regular))
                        .foregroundStyle(Color.fukahaSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 8)
            if let trailingIcon {
                Image(systemName: trailingIcon)
                    .font(.system(size: 23, weight: .medium))
                    .foregroundStyle(Color.fukahaSecondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}
