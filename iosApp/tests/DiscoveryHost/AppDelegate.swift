import UIKit

@main
final class DiscoveryAppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.rootViewController = DiscoverySourceController()
        window?.makeKeyAndVisible()
        return true
    }
}

final class DiscoverySourceController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 24
        stack.translatesAutoresizingMaskIntoConstraints = false
        for (name, action) in [("Share URL", #selector(shareURL)), ("Share text", #selector(shareText)), ("Share URL and preview", #selector(shareMixed)), ("Share image only", #selector(shareImage))] {
            let button = UIButton(type: .system)
            button.setTitle(name, for: .normal)
            button.accessibilityIdentifier = name
            button.addTarget(self, action: action, for: .touchUpInside)
            stack.addArrangedSubview(button)
        }
        view.addSubview(stack)
        NSLayoutConstraint.activate([stack.centerXAnchor.constraint(equalTo: view.centerXAnchor), stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)])
    }
    private let url = URL(string: "https://x.com/example/status/123?utm_source=share")!
    private var preview: UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 80, height: 80)).image { context in
            UIColor.blue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 80, height: 80))
        }
    }
    @objc private func shareURL() { share([url]) }
    @objc private func shareText() { share(["A post: \(url.absoluteString)"]) }
    @objc private func shareMixed() { share([preview, url]) }
    @objc private func shareImage() { share([preview]) }
    private func share(_ items: [Any]) {
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        controller.popoverPresentationController?.sourceView = view
        controller.popoverPresentationController?.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
        present(controller, animated: true)
    }
}
