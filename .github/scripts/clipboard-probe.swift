import Foundation
import UIKit

let text = "A shared post\nhttps://www.nodeseek.com/post-42-1"
print("PROBE bundle=\(Bundle.main.bundleIdentifier ?? "none")")

func probe(_ pasteboard: UIPasteboard, _ label: String) {
    pasteboard.string = text
    RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.1))
    print("PROBE \(label) count=\(pasteboard.changeCount) types=\(pasteboard.types) hasStrings=\(pasteboard.hasStrings) hasURLs=\(pasteboard.hasURLs) string=\(String(describing: pasteboard.string))")
    var completed = false
    pasteboard.detectPatterns(for: [.link]) { result in
        print("PROBE \(label) detection=\(result)")
        completed = true
    }
    let deadline = Date(timeIntervalSinceNow: 5)
    while !completed && Date() < deadline {
        RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.01))
    }
    print("PROBE \(label) completed=\(completed)")
}

probe(UIPasteboard.general, "general")
let named = UIPasteboard.withUniqueName()
probe(named, "named")
UIPasteboard.remove(withName: named.name)
