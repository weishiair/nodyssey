"""Regression coverage for detecting the iOS 17 dyld failure in final app binaries."""

import importlib.util
from pathlib import Path
import unittest


_spec = importlib.util.spec_from_file_location(
    "check_ios_weak_imports", Path(__file__).with_name("check-ios-weak-imports.py"),
)
checker = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(checker)


class WeakImportsTest(unittest.TestCase):
    def test_accepts_weak_glass_classes_beside_strong_supported_uikit_classes(self):
        symbols = """
Nodyssey:                 (undefined) external _OBJC_CLASS_$_UIView (from UIKit)
Nodyssey.debug.dylib:     (undefined) weak external _OBJC_CLASS_$_UICornerConfiguration (from UIKit)
Nodyssey.debug.dylib:     (undefined) weak external _OBJC_CLASS_$_UIGlassEffect (from UIKit)
"""
        report = checker.check_imports(symbols)
        self.assertEqual(2, len(report))
        self.assertTrue(all("weak external" in line for line in report))

    def test_rejects_either_new_class_being_strong_even_when_the_other_is_weak(self):
        for strong_class in ("UICornerConfiguration", "UIGlassEffect"):
            with self.subTest(strong_class=strong_class):
                symbols = "\n".join(
                    f"(undefined) {'weak ' if name != strong_class else ''}external "
                    f"_OBJC_CLASS_$_{name} (from UIKit)"
                    for name in ("UICornerConfiguration", "UIGlassEffect")
                )
                with self.assertRaises(ValueError) as raised:
                    checker.check_imports(symbols)
                self.assertIn(f"_OBJC_CLASS_$_{strong_class}", str(raised.exception))
                self.assertIn("crash older iOS", str(raised.exception))

    def test_a_strong_metaclass_is_not_hidden_by_weak_class_imports(self):
        symbols = """
                 (undefined) weak external _OBJC_CLASS_$_UICornerConfiguration (from UIKit)
                 (undefined) weak external _OBJC_CLASS_$_UIGlassEffect (from UIKit)
                 (undefined) external _OBJC_METACLASS_$_UIGlassEffect (from UIKit)
"""
        with self.assertRaises(ValueError) as raised:
            checker.check_imports(symbols)
        self.assertIn("_OBJC_METACLASS_$_UIGlassEffect", str(raised.exception))

    def test_missing_symbols_or_an_empty_symbol_table_cannot_pass_verification(self):
        for symbols in ("", "(undefined) weak external _OBJC_CLASS_$_UICornerConfiguration (from UIKit)"):
            with self.subTest(symbols=symbols), self.assertRaises(ValueError) as raised:
                checker.check_imports(symbols)
            self.assertIn("Cannot verify", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
