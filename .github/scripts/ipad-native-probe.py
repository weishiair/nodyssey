import json, os, pathlib, plistlib, subprocess
root = pathlib.Path(os.environ['RUNNER_TEMP']) / 'ipad-probe'
root.mkdir(exist_ok=True)
app = root / 'Probe.app'
app.mkdir(exist_ok=True)
def run(*args, **kwargs):
    print('RUN', *map(str, args), flush=True)
    return subprocess.run(list(map(str, args)), check=True, text=True, **kwargs)
swift = r'''
import UIKit
@main class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: "Default", sessionRole: session.role)
        config.delegateClass = SceneDelegate.self; return config
    }
}
class Split: UIViewController {
    let details = UINavigationController(rootViewController: UIViewController())
    var list: UIViewController?
    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(details); view.addSubview(details.view); details.didMove(toParent: self)
    }
    func show(_ controller: UIViewController) {
        list = controller
        addChild(controller); view.addSubview(controller.view); controller.didMove(toParent: self)
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        list?.view.frame = CGRect(x: 0, y: 0, width: 360, height: view.bounds.height)
        details.view.frame = CGRect(x: 361, y: 0, width: max(0, view.bounds.width - 361), height: view.bounds.height)
    }
}
class Tabs: UITabBarController {
    var action: (() -> Void)?
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if let action = action { self.action = nil; action() }
    }
}
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    var results: [[String: String]] = []
    func record(_ name: String, _ value: String) {
        results.append(["name": name, "value": value])
        let path = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("result.json")
        try! JSONSerialization.data(withJSONObject: results, options: .prettyPrinted).write(to: path)
        print(name + ": " + value)
    }
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        let tabs = Tabs()
        let list = UIViewController(); list.view.backgroundColor = .systemBlue
        let nav = UINavigationController(rootViewController: list); nav.setNavigationBarHidden(true, animated: false)
        tabs.setViewControllers([nav], animated: false)
        let window = UIWindow(windowScene: scene as! UIWindowScene); self.window = window
        window.rootViewController = tabs; window.makeKeyAndVisible()
        let mode = ProcessInfo.processInfo.arguments.last ?? "plain"
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            let action = {
                let split = Split()
                self.record("before", String(describing: list.parent))
                let error = CaptureException { nav.setViewControllers([split], animated: mode == "animated") }
                self.record("replace", error ?? "ok")
                self.record("after", String(describing: list.parent))
                let adopt = CaptureException { split.show(list) }
                self.record("adopt", adopt ?? "ok")
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    self.record("settled", String(describing: list.parent)); exit(0)
                }
            }
            if mode == "layout" { tabs.action = action; tabs.view.setNeedsLayout() } else { action() }
        }
    }
}
'''
(root / 'App.swift').write_text(swift)
(root / 'Capture.h').write_text('#import <Foundation/Foundation.h>' + chr(10) + 'NSString *CaptureException(void (^block)(void));')
(root / 'Capture.m').write_text('#import "Capture.h"' + chr(10) + 'NSString *CaptureException(void (^block)(void)) { @try { block(); return nil; } @catch(NSException *e) { return [NSString stringWithFormat:@"%@: %@", e.name, e.reason]; } }')
sdk = subprocess.check_output(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'], text=True).strip()
target = 'arm64-apple-ios16.0-simulator'
run('xcrun', 'clang', '-target', target, '-isysroot', sdk, '-fobjc-arc', '-c', root / 'Capture.m', '-o', root / 'Capture.o')
run('xcrun', 'swiftc', '-target', target, '-sdk', sdk, '-parse-as-library', '-module-name', 'Probe', '-import-objc-header', root / 'Capture.h', root / 'App.swift', root / 'Capture.o', '-o', app / 'Probe')
plist = dict(CFBundleExecutable='Probe', CFBundleIdentifier='io.github.nodyssey.probe', CFBundleName='Probe', CFBundlePackageType='APPL', CFBundleVersion='1', CFBundleShortVersionString='1.0', MinimumOSVersion='16.0', UIDeviceFamily=[1,2], UILaunchScreen={}, UIApplicationSceneManifest={'UIApplicationSupportsMultipleScenes': False}, UISupportedInterfaceOrientations=['UIInterfaceOrientationPortrait', 'UIInterfaceOrientationLandscapeLeft', 'UIInterfaceOrientationLandscapeRight'])
with (app / 'Info.plist').open('wb') as f: plistlib.dump(plist, f)
run('codesign', '--force', '--sign', '-', app)
runtimes = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'runtimes', '-j']))['runtimes']
runtime = next(r['identifier'] for r in reversed(runtimes) if r['isAvailable'] and 'iOS' in r['name'])
types = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'devicetypes', '-j']))['devicetypes']
device_type = next(r['identifier'] for r in types if 'iPad Pro 13-inch' in r['name'])
device = subprocess.check_output(['xcrun', 'simctl', 'create', 'NodysseyProbe', device_type, runtime], text=True).strip()
try:
    run('xcrun', 'simctl', 'boot', device)
    run('xcrun', 'simctl', 'bootstatus', device, '-b')
    run('xcrun', 'simctl', 'install', device, app)
    for mode in ['plain', 'layout', 'animated']:
        run('xcrun', 'simctl', 'launch', '--console', device, 'io.github.nodyssey.probe', mode, timeout=90)
        container = subprocess.check_output(['xcrun', 'simctl', 'get_app_container', device, 'io.github.nodyssey.probe', 'data'], text=True).strip()
        result = (pathlib.Path(container) / 'Documents/result.json').read_text()
        (root / (mode + '.json')).write_text(result)
        print(mode, result, flush=True)
finally:
    run('xcrun', 'simctl', 'shutdown', device)
