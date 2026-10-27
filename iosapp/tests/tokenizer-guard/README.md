# Native iOS tokenizer guard regression harness

Run on an Apple Silicon Mac with Xcode, an available iOS Simulator runtime, and
Python 3. From the repository root:

```bash
bash .github/scripts/test-ios-tokenizer-guard.sh /absolute/path/to/output
# Or omit the output directory for build/tokenizer-guard-tests.
```

The script uses the existing `DEVELOPER_DIR`/Xcode selection; it does not change
it. It compiles the production guard with `xcrun clang`, without an Xcode
project, boots a newly created simulator, runs two minimal UIApplication
processes with a scene manifest and a connected `UIWindowScene`, and deletes **only**
that simulator. Tests run once after the scene connects; no success marker is
emitted on a legacy or failed scene launch. It writes build/run output,
per-app console logs, candidate/runtime metadata and app bundles to the output
directory. A zero `simctl launch` status alone is not enough: each app must
emit the exact success marker, and launches time out after 120 seconds.

`bridge` compiles the unchanged pinned Compose Objective-C bridge together with
a test subclass that stands in for the Kotlin override. Its controlled public
`UITextInput`/`UITextPosition` implementation supplies explicit UTF-16 offsets,
including stale/invalid positions UIKit might not construct normally. It checks
that invalid offsets never reach the trap/indexing override; valid offsets
(including both endpoints and an empty document) preserve the original result,
position, granularity and direction; emoji code-unit accounting, shrinking
text, distinct owners, pre-installation instances without an association, weak-owner
lifetime, repeated installation, and unchanged
UIKit base method implementations. The subclass does not override the inherited
initializer. `missing-class` is a separate process compiled without the bridge,
checking that installation fails safely without changing UIKit's base methods.
When UIKit's unhooked base tokenizer releases its input, the harness also asserts
that a captured, deallocated owner returns YES without calling the original. It
compares the guarded and unhooked base tokenizers' ownership first; if UIKit
itself retains the input, the destroyed-owner check is explicitly logged as skipped.

This tests the guard against the exact pinned **Objective-C bridge API** and a
Kotlin-like test override. It does **not** verify actual Kotlin/Native subclass
super-initializer dispatch or real device dictation, and is not a substitute for
an on-device dictation regression. The mock implements only the public input
operations this guard exercises, not all `UITextInput` behavior.

Runner failure handling can also be tested without Xcode:

```bash
python3 -m unittest discover -s iosapp/tests/tokenizer-guard -p 'test_*.py'
```

## Fixture provenance and license

`fixture/CMPTextInputStringTokenizer.h` and `.m` are byte-for-byte copies of
the official JetBrains `compose-multiplatform-core` v1.13.0-alpha01 files at
commit `a9e47e8aa04c95cfe572ab4297c770f3cbfb234e`, from
`compose/ui/ui-uikit/src/iosMain/objc/CMPUIKitUtils/CMPUIKitObjcUtils/`.
Only their local `pinned-` filename prefix was removed to satisfy the original
`.m` import; contents are unchanged. Upstream source:
https://github.com/JetBrains/compose-multiplatform-core/tree/a9e47e8aa04c95cfe572ab4297c770f3cbfb234e/compose/ui/ui-uikit/src/iosMain/objc/CMPUIKitUtils/CMPUIKitObjcUtils

The files retain their original Android Open Source Project copyright and
Apache License 2.0 notices; the full license is in `fixture/LICENSE-2.0.txt`.
SHA-256: header `f16a93b61661bc53c2977d7defe83feb000f6ac459ede0a176ce365146d4fa12`;
implementation `808a6e11070a44fd4c48a43558efdae7b5e9feabdfb4ac224f89eff4ff977652`.
