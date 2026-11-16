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

// iOS 16+ UIKit helpers kept here so Kotlin/Native does not depend on strict vs non-strict enum
// mappings across Xcode SDK updates, while avoiding any iOS 26+ class references outside @available.
static inline UIButtonConfiguration * _Nonnull plaza_button_configuration(BOOL prominent) {
    return prominent ? [UIButtonConfiguration filledButtonConfiguration]
                     : [UIButtonConfiguration plainButtonConfiguration];
}

static inline UIButtonConfiguration * _Nonnull plaza_tinted_button_configuration(void) {
    return [UIButtonConfiguration tintedButtonConfiguration];
}

static inline UIActivityIndicatorView * _Nonnull plaza_make_bar_spinner(UIColor * _Nullable color) {
    UIActivityIndicatorView *spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleMedium];
    spinner.color = color;
    [spinner startAnimating];
    return spinner;
}

static inline void plaza_configure_bar_item_style(UIBarButtonItem * _Nonnull item, BOOL prominent) {
    item.style = prominent ? UIBarButtonItemStyleDone : UIBarButtonItemStylePlain;
}

static inline void plaza_set_bar_item_selected(UIBarButtonItem * _Nonnull item, BOOL selected) {
    if (@available(iOS 15.0, *)) {
        item.selected = selected;
    }
}

static inline UIStackView * _Nonnull plaza_make_subtitle_view(void) {
    UILabel *titleLabel = [[UILabel alloc] init];
    titleLabel.tag = 1001;
    titleLabel.textAlignment = NSTextAlignmentCenter;
    titleLabel.lineBreakMode = NSLineBreakByTruncatingTail;
    titleLabel.numberOfLines = 1;
    titleLabel.adjustsFontForContentSizeCategory = YES;

    UILabel *subtitleLabel = [[UILabel alloc] init];
    subtitleLabel.tag = 1002;
    subtitleLabel.textAlignment = NSTextAlignmentCenter;
    subtitleLabel.lineBreakMode = NSLineBreakByTruncatingTail;
    subtitleLabel.numberOfLines = 1;
    subtitleLabel.adjustsFontForContentSizeCategory = YES;

    UIStackView *stack = [[UIStackView alloc] initWithArrangedSubviews:@[titleLabel, subtitleLabel]];
    stack.axis = UILayoutConstraintAxisVertical;
    stack.alignment = UIStackViewAlignmentCenter;
    stack.spacing = 1.0;
    return stack;
}

static inline void plaza_update_subtitle_view(
    UIStackView * _Nonnull stack,
    NSString * _Nonnull title,
    NSString * _Nonnull subtitle,
    UIColor * _Nonnull titleColor,
    UIColor * _Nonnull subtitleColor,
    UIFont * _Nonnull titleFont,
    UIFont * _Nonnull subtitleFont
) {
    UILabel *titleLabel = (UILabel *)[stack viewWithTag:1001];
    UILabel *subtitleLabel = (UILabel *)[stack viewWithTag:1002];
    titleLabel.text = title;
    titleLabel.textColor = titleColor;
    titleLabel.font = titleFont;
    subtitleLabel.text = subtitle;
    subtitleLabel.textColor = subtitleColor;
    subtitleLabel.font = subtitleFont;
}

static inline UIAlertController * _Nonnull plaza_present_confirm_alert(
    UIViewController * _Nonnull host,
    NSString * _Nonnull title,
    NSString * _Nonnull message,
    NSString * _Nonnull confirmTitle,
    BOOL confirmDestructive,
    NSString * _Nonnull dismissTitle,
    BOOL dismissDestructive,
    BOOL dismissCancel,
    BOOL dark,
    void (^ _Nonnull onConfirm)(void),
    void (^ _Nonnull onDismiss)(void)
) {
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:title
                                                                   message:message
                                                            preferredStyle:UIAlertControllerStyleAlert];
    alert.overrideUserInterfaceStyle = dark ? UIUserInterfaceStyleDark : UIUserInterfaceStyleLight;
    UIAlertActionStyle dismissStyle = dismissDestructive
        ? UIAlertActionStyleDestructive
        : (dismissCancel ? UIAlertActionStyleCancel : UIAlertActionStyleDefault);
    UIAlertAction *dismissAction = [UIAlertAction actionWithTitle:dismissTitle
                                                            style:dismissStyle
                                                          handler:^(__unused UIAlertAction * _Nonnull action) {
        onDismiss();
    }];
    UIAlertAction *confirmAction = [UIAlertAction actionWithTitle:confirmTitle
                                                            style:(confirmDestructive ? UIAlertActionStyleDestructive : UIAlertActionStyleDefault)
                                                          handler:^(__unused UIAlertAction * _Nonnull action) {
        onConfirm();
    }];
    [alert addAction:dismissAction];
    [alert addAction:confirmAction];
    if (!confirmDestructive && !dismissDestructive) {
        alert.preferredAction = confirmAction;
    }
    [host presentViewController:alert animated:YES completion:nil];
    return alert;
}
