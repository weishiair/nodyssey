import UIKit
import ObjectiveC
import NodysseyShell

/// The whole of the Swift in this app.
///
/// `MainActivity` is the Android counterpart and it is 76 lines; this is short, and the difference is
/// not that iOS does less — it is that everything `MainActivity` does beyond installing a composition
/// is about the platform's own hooks, and this app has one so far: the offline downloader's
/// background tasks. `registerBackgroundTasks` installs their `BGTaskScheduler` handlers, which
/// **must** happen before launch finishes, so it goes here rather than in the scene. When
/// notifications and Universal Links arrive, this is where they land too.
@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        ComposeTokenizerGuard.install()
        NodysseyApp.shared.registerBackgroundTasks()
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting session: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        UISceneConfiguration(name: "Default", sessionRole: session.role)
    }
}

/// Where the window is made, which since iOS 26 is the only place it may be made.
///
/// The pre-scene shape — an app delegate that builds its own `UIWindow` in
/// `didFinishLaunchingWithOptions` — no longer merely warns: `UIKit` traps at launch in
/// `_UIApplicationEvaluateRuntimeIssueForNoSceneLifecycleAdoption` unless `Info.plist` declares a
/// `UIApplicationSceneManifest` and something adopts it. That trap is what the first run of this
/// target did, and it looks exactly like an app that will not start.
///
/// The two-step root is `NodysseyApp.start`'s doing: the app's `User-Agent` has to be the one WebKit
/// sends, WebKit will only say what that is asynchronously, and building the graph around a guess
/// would mean throwing away a `cf_clearance` the moment the real answer arrived. So the window comes
/// up empty for the few milliseconds that takes — which is what a launch screen is for.
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        self.window = window
        window.rootViewController = UIViewController()
        window.makeKeyAndVisible()

        NodysseyApp.shared.start { controller in
            // iOS 26+ supplies the floating Liquid Glass bar when the app is linked with SDK 26+.
            // Leave its appearance and translucency to UIKit. An older SDK keeps even iOS 27 in
            // the legacy design; making that bar transparent does not turn it into Liquid Glass.
            // The Actions build checks its SDK before building and again on the packaged app.
            if #unavailable(iOS 26.0), let tabs = controller as? UITabBarController {
                // Older systems use a full-width bar. Keep both states opaque rather than relying
                // on UIKit to infer the scroll position from Compose.
                let appearance = UITabBarAppearance()
                appearance.configureWithOpaqueBackground()
                appearance.backgroundColor = .systemBackground
                tabs.tabBar.standardAppearance = appearance
                tabs.tabBar.scrollEdgeAppearance = appearance
                tabs.tabBar.isTranslucent = false
            }
            window.rootViewController = controller
        }
    }
}

/// Keeps dictation from killing the app, which it did on every utterance into any text field.
///
/// When dictation hands a result over, UIKit asks the field's tokenizer whether the caret sits on a
/// boundary (`+[UIDictationUtilities needsTrailingSpaceForText:secureInput:]`, deciding whether to add
/// a space). Compose's `TextInputStringTokenizer` answers by indexing into the field's text — `text[position]`
/// for a paragraph, `BreakIterator.isBoundary(position)` otherwise — with no range check, and the position
/// UIKit passes can lie outside the text it reads back. The Kotlin side throws
/// `ArrayIndexOutOfBoundsException`, nothing on the Objective-C side can catch it, and the app aborts.
/// The code is the same in Compose 1.12.2 and 1.13.0-alpha02, so no version bump fixes it.
///
/// UIKit's entry point is `isPosition:atBoundary:inDirection:` on Compose's Objective-C base class,
/// `CMPTextInputStringTokenizer`, which only forwards to the Kotlin override. Patching it there works
/// from launch, without waiting for Kotlin to create its class — and if a future Compose renames the
/// class or range-checks the call itself, [install] finds nothing and the app carries on unpatched.
/// The guard answers the edges itself — the start and the end of the text are a boundary at every
/// granularity, and anything past them counts as the end — and lets Compose answer everything else. The
/// text comes from the field the tokenizer was made for, which `UITextInputStringTokenizer` keeps in
/// its `_textInput` ivar. (Not the first responder: while dictating, that is Compose's
/// `OverlayInputView`, not the field.)
///
/// Delete this once Compose range-checks `isPositionAtBoundary`.
enum ComposeTokenizerGuard {
    private static let isAtBoundary = #selector(UITextInputStringTokenizer.isPosition(_:atBoundary:inDirection:))
    private static let textInputIvar = class_getInstanceVariable(UITextInputStringTokenizer.self, "_textInput")

    static func install() {
        guard let cls = NSClassFromString("CMPTextInputStringTokenizer"),
              let method = class_getInstanceMethod(cls, isAtBoundary) else {
            debugLog("CMPTextInputStringTokenizer not found, guard not installed")
            return
        }

        typealias IsAtBoundary = @convention(c) (AnyObject, Selector, UITextPosition, Int, Int) -> Bool
        let original = unsafeBitCast(method_getImplementation(method), to: IsAtBoundary.self)
        let replacement: @convention(block) (AnyObject, UITextPosition, Int, Int) -> Bool = { this, position, granularity, direction in
            if let input = textInputIvar.flatMap({ object_getIvar(this, $0) }) as? UITextInput {
                let start = input.beginningOfDocument
                let length = input.textRange(from: start, to: input.endOfDocument)
                    .flatMap { input.text(in: $0) }?.utf16.count ?? 0
                let offset = input.offset(from: start, to: position)
                if offset <= 0 || offset >= length {
                    debugLog("answered edge: offset \(offset), length \(length), granularity \(granularity)")
                    return true
                }
            } else {
                debugLog("no text input on \(NSStringFromClass(type(of: this))), passing through")
            }
            return original(this, isAtBoundary, position, granularity, direction)
        }
        method_setImplementation(method, imp_implementationWithBlock(replacement))
        debugLog("installed on \(NSStringFromClass(cls)), _textInput ivar \(textInputIvar == nil ? "missing" : "found")")
    }

    private static func debugLog(_ message: @autoclosure () -> String) {
        #if DEBUG
        print("[ComposeTokenizerGuard] \(message())")
        #endif
    }
}
