import UIKit
import NodysseyShell

/// The app lifecycle and the native tokenizer compatibility guard are wired here.
///
/// `MainActivity` is the Android counterpart and it is 76 lines; this is short, and the difference is
/// not that iOS does less — it is that everything `MainActivity` does beyond installing a composition
/// is about the platform's own hooks, including the offline downloader's
/// background tasks. `registerBackgroundTasks` installs their `BGTaskScheduler` handlers, which
/// **must** happen before launch finishes, so it goes here rather than in the scene. When
/// notifications and Universal Links arrive, this is where they land too.
@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let tokenizerGuardInstalled = NodysseyInstallComposeTokenizerGuard()
        #if DEBUG
        print("[ComposeTokenizerGuard] installation \(tokenizerGuardInstalled ? "succeeded" : "failed")")
        #else
        _ = tokenizerGuardInstalled
        #endif
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
            // iOS 26 and later draw a floating Liquid Glass tab bar. An opaque appearance or
            // isTranslucent=false paints the full bottom edge behind that floating bar. Leave the
            // system appearance intact there, including its transparent surrounding area.
            if #unavailable(iOS 26.0), let tabs = controller as? UITabBarController {
                // Older systems use an edge-to-edge bar. Match its two states so returning to a
                // tab root cannot drop the background. The semantic colour follows the shell's
                // light/dark override.
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
