import SwiftUI
import UIKit

/// A window-level observer also covers controls in sheets without swallowing their taps.
struct FukahaKeyboardDismissal: UIViewRepresentable {
    func makeUIView(context: Context) -> ObserverView { ObserverView() }
    func updateUIView(_ uiView: ObserverView, context: Context) {}

    static func dismantleUIView(_ uiView: ObserverView, coordinator: ()) {
        uiView.detach()
    }

    final class ObserverView: UIView, UIGestureRecognizerDelegate {
        private weak var observedWindow: UIWindow?
        private weak var editingView: UIView?
        private lazy var tap: UITapGestureRecognizer = {
            let tap = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
            tap.cancelsTouchesInView = false
            tap.delaysTouchesBegan = false
            tap.delaysTouchesEnded = false
            tap.delegate = self
            return tap
        }()

        override func didMoveToWindow() {
            super.didMoveToWindow()
            detach()
            observedWindow = window
            window?.addGestureRecognizer(tap)
        }

        func detach() {
            observedWindow?.removeGestureRecognizer(tap)
            observedWindow = nil
            editingView = nil
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            // Editing controls and their selection/inline controls keep native touch handling.
            var target = touch.view
            while let view = target {
                if view is UITextField || view is UITextView { return false }
                target = view.superview
            }
            editingView = firstResponder(in: observedWindow)
            return editingView is UITextField || editingView is UITextView
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool { true }

        @objc private func dismissKeyboard() {
            // A control may have moved focus to another editor during this same tap.
            if editingView?.isFirstResponder == true { editingView?.resignFirstResponder() }
            editingView = nil
        }

        private func firstResponder(in view: UIView?) -> UIView? {
            guard let view else { return nil }
            if view.isFirstResponder { return view }
            for child in view.subviews {
                if let responder = firstResponder(in: child) { return responder }
            }
            return nil
        }
    }
}
