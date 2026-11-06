import Combine
import SwiftUI

final class FukahaShareModel: ObservableObject {
    @Published var loading = true
    @Published var downloading = false
    @Published var error: String?
    @Published var platform: String?
    @Published var originalUrl: String?
    @Published var cleanedUrl: String?
    @Published var embedUrl: String?
    @Published var copiedMessage: String?
}

struct FukahaShareScreen: View {
    @ObservedObject var model: FukahaShareModel
    let isArabic: Bool
    let mediaDownloadEnabled: Bool
    let onDismiss: () -> Void
    let onShareCleaned: () -> Void
    let onShareEmbed: () -> Void
    let onShareMedia: () -> Void
    let onCopyOriginal: () -> Void
    let onCopyCleaned: () -> Void
    let onCopyEmbed: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    header

                    if model.loading || model.downloading {
                        progressBlock
                    } else if let error = model.error {
                        errorBlock(error)
                    } else if let originalUrl = model.originalUrl,
                              let cleanedUrl = model.cleanedUrl {
                        linkBlock(
                            title: text(en: "Original link", ar: "الرابط الأصلي"),
                            titleTrailing: model.platform ?? text(en: "Unknown", ar: "منصة غير معروفة"),
                            icon: "globe",
                            url: originalUrl,
                            onShare: nil,
                            onCopy: onCopyOriginal,
                        )
                        linkBlock(
                            title: text(en: "Cleaned link", ar: "الرابط النظيف"),
                            icon: "sparkles",
                            url: cleanedUrl,
                            onShare: onShareCleaned,
                            onCopy: onCopyCleaned,
                        )
                        if let embedUrl = model.embedUrl {
                            linkBlock(
                                title: text(en: "Embed link", ar: "رابط المعاينة"),
                                icon: "eye",
                                url: embedUrl,
                                onShare: onShareEmbed,
                                onCopy: onCopyEmbed,
                            )
                        }
                        if mediaDownloadEnabled {
                            mediaButton
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 18)
            }
            .background(Color.fukahaSurfaceLow.ignoresSafeArea())

            if let copiedMessage = model.copiedMessage {
                Text(copiedMessage)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.fukahaOnPrimaryContainer)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.fukahaPrimaryContainer)
                    .clipShape(Capsule())
                    .padding(.bottom, 12)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .environment(\.layoutDirection, isArabic ? .rightToLeft : .leftToRight)
        .tint(Color.fukahaPrimary)
        .animation(.easeInOut(duration: 0.16), value: model.copiedMessage)
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 6) {
            Button(action: onDismiss) {
                Image(systemName: "arrow.backward")
                    .font(.system(size: 20, weight: .medium))
                    .frame(width: 34, height: 34)
            }
            .accessibilityLabel(text(en: "Back", ar: "رجوع"))

            VStack(alignment: .leading, spacing: 4) {
                Text(isArabic ? "فكها" : "Fukaha")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(Color.fukahaOnSurface)
                Text(
                    mediaDownloadEnabled
                        ? text(
                            en: "Clean, embed, or download — then share again",
                            ar: "نظّف الرابط، أو جهّز المعاينة، أو حمّل الملف — ثم شارك من جديد",
                        )
                        : text(
                            en: "Clean it or fix the preview — then share again",
                            ar: "نظّف الرابط أو جهّز المعاينة — ثم شارك من جديد",
                        ),
                )
                .font(.system(size: 14))
                .foregroundStyle(Color.fukahaSecondary)
                .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var progressBlock: some View {
        HStack(spacing: 10) {
            ProgressView()
                .tint(Color.fukahaPrimary)
                .frame(width: 26, height: 26)
            Text(
                model.downloading
                    ? text(en: "Downloading…", ar: "جاري تحميل الوسائط…")
                    : text(en: "Preparing…", ar: "جاري التجهيز…"),
            )
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(Color.fukahaOnSurface)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 24)
    }

    private func errorBlock(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(message)
                .font(.system(size: 15))
                .foregroundStyle(Color.fukahaError)
                .fixedSize(horizontal: false, vertical: true)
            Button(action: onDismiss) {
                Text(isArabic ? "حسناً" : "OK")
                    .font(.system(size: 15, weight: .semibold))
                    .frame(maxWidth: .infinity, minHeight: 42)
            }
            .foregroundStyle(Color.fukahaPrimary)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.fukahaError.opacity(0.12))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func linkBlock(
        title: String,
        titleTrailing: String? = nil,
        icon: String,
        url: String,
        onShare: (() -> Void)?,
        onCopy: @escaping () -> Void,
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .medium))
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                if let titleTrailing {
                    Spacer(minLength: 8)
                    Text(titleTrailing)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.fukahaPrimary)
                }
            }
            .foregroundStyle(Color.fukahaSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)

            linkCard(icon: icon, url: url, onShare: onShare, onCopy: onCopy)
        }
    }

    private func linkCard(
        icon: String,
        url: String,
        onShare: (() -> Void)?,
        onCopy: @escaping () -> Void,
    ) -> some View {
        ZStack(alignment: .trailing) {
            Image(systemName: icon)
                .font(.system(size: 60, weight: .regular))
                .foregroundStyle(Color.fukahaPrimary.opacity(0.07))
                .padding(.trailing, onShare == nil ? 8 : 60)
                .allowsHitTesting(false)

            HStack(spacing: 6) {
                Button(action: onCopy) {
                    HStack(alignment: .center, spacing: 10) {
                        Image(systemName: "doc.on.doc")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundStyle(Color.fukahaPrimary)
                        Text(url)
                            .font(.system(size: 15))
                            .foregroundStyle(Color.fukahaOnSurface)
                            .multilineTextAlignment(.leading)
                            .fixedSize(horizontal: false, vertical: true)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(6)
                    .frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
                }
                .buttonStyle(.plain)

                if let onShare {
                    Button(action: onShare) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 20, weight: .medium))
                            .frame(width: 52, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Color.fukahaPrimary)
                    .accessibilityLabel(text(en: "Share", ar: "مشاركة"))
                }
            }
            .padding(8)
        }
        .frame(maxWidth: .infinity)
        .background(Color.fukahaBackground)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .environment(\.layoutDirection, .leftToRight)
    }

    private var mediaButton: some View {
        Button(action: onShareMedia) {
            HStack(spacing: 8) {
                Image(systemName: "arrow.down.to.line")
                    .font(.system(size: 19, weight: .medium))
                Text(text(en: "Share media file", ar: "مشاركة ملف الوسائط"))
                    .font(.system(size: 15, weight: .semibold))
            }
            .frame(maxWidth: .infinity, minHeight: 46)
        }
        .foregroundStyle(Color.fukahaOnPrimaryContainer)
        .background(Color.fukahaPrimaryContainer)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func text(en: String, ar: String) -> String {
        isArabic ? ar : en
    }
}
