#pragma once

#import <UIKit/UIKit.h>

// Kotlin/Native strongly links an Objective-C class even behind an OS-version check. dyld would
// then fail before launch on iOS 16–18, regardless of settings. Clang respects UIKit's availability
// annotations and emits weak imports here. Keep iOS 26 types inside this Objective-C boundary until
// Kotlin/Native supports weak class references or the minimum supported OS reaches iOS 26.
// https://kotlinlang.org/docs/native-objc-interop.html#strong-linking
static inline BOOL plaza_native_glass_available(void) {
    if (@available(iOS 26.0, *)) {
        return YES;
    }
    return NO;
}

// Return the old base type so Kotlin never needs to name UIGlassEffect.
static inline UIVisualEffect * _Nullable plaza_glass_effect(BOOL interactive, UIColor * _Nullable tint) {
    if (@available(iOS 26.0, *)) {
        UIGlassEffect *effect = [UIGlassEffect effectWithStyle:UIGlassEffectStyleRegular];
        effect.interactive = interactive;
        effect.tintColor = tint;
        return effect;
    }
    return nil;
}

static inline UIButtonConfiguration * _Nullable plaza_glass_button_configuration(BOOL prominent) {
    if (@available(iOS 26.0, *)) {
        return prominent ? [UIButtonConfiguration prominentGlassButtonConfiguration]
                         : [UIButtonConfiguration glassButtonConfiguration];
    }
    return nil;
}

static inline void plaza_set_glass_capsule(UIView * _Nonnull view, double maximumRadius) {
    if (@available(iOS 26.0, *)) {
        view.cornerConfiguration = maximumRadius > 0.0
            ? [UICornerConfiguration capsuleConfigurationWithMaximumRadius:maximumRadius]
            : [UICornerConfiguration capsuleConfiguration];
    }
}
