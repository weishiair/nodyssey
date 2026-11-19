#pragma once

#include <math.h>
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

static inline BOOL plaza_set_bar_item_indicator_badge(
    UIBarButtonItem * _Nonnull item,
    BOOL visible,
    UIColor * _Nullable color
) {
    if (@available(iOS 26.0, *)) {
        if (visible) {
            UIBarButtonItemBadge *badge = [UIBarButtonItemBadge indicatorBadge];
            if (color != nil) {
                badge.backgroundColor = color;
            }
            item.badge = badge;
        } else {
            item.badge = nil;
        }
        return YES;
    }
    return NO;
}

static inline BOOL plaza_bar_item_has_badge(UIBarButtonItem * _Nonnull item) {
    if (@available(iOS 26.0, *)) {
        return item.badge != nil;
    }
    return NO;
}

static inline UIImage * _Nonnull plaza_badge_image(
    UIImage * _Nonnull baseImage,
    UIColor * _Nonnull tintColor,
    UIColor * _Nonnull badgeColor
) {
    CGSize size = baseImage.size;
    if (size.width <= 0.0 || size.height <= 0.0) {
        size = CGSizeMake(22.0, 22.0);
    }
    CGFloat pad = 4.0;
    CGSize canvas = CGSizeMake(size.width + pad, size.height + pad);
    UIGraphicsImageRendererFormat *format = [UIGraphicsImageRendererFormat preferredFormat];
    format.opaque = NO;
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:canvas format:format];
    UIImage *rendered = [renderer imageWithActions:^(__unused UIGraphicsImageRendererContext * _Nonnull context) {
        UIImage *tinted = [baseImage imageWithTintColor:tintColor renderingMode:UIImageRenderingModeAlwaysOriginal];
        [tinted drawInRect:CGRectMake(0.0, pad * 0.5, size.width, size.height)];
        CGFloat dot = 7.0;
        CGRect dotRect = CGRectMake(canvas.width - dot, 0.0, dot, dot);
        [badgeColor setFill];
        [[UIBezierPath bezierPathWithOvalInRect:dotRect] fill];
    }];
    return [rendered imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
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

static inline BOOL plaza_switch_is_on(UISwitch * _Nonnull control) {
    return control.on;
}

static inline void plaza_switch_set_on(UISwitch * _Nonnull control, BOOL on, BOOL animated) {
    [control setOn:on animated:animated];
}

static inline void plaza_switch_set_on_tint(UISwitch * _Nonnull control, UIColor * _Nullable tint) {
    control.onTintColor = tint;
}

static inline float plaza_slider_value(UISlider * _Nonnull slider) {
    return slider.value;
}

static inline void plaza_slider_set_value(UISlider * _Nonnull slider, float value) {
    slider.value = value;
}

static inline BOOL plaza_slider_is_tracking(UISlider * _Nonnull slider) {
    return slider.tracking;
}

static inline void plaza_slider_configure(
    UISlider * _Nonnull slider,
    float minValue,
    float maxValue,
    UIColor * _Nullable minTint,
    UIColor * _Nullable maxTint
) {
    slider.minimumValue = minValue;
    slider.maximumValue = maxValue;
    slider.continuous = YES;
    slider.minimumTrackTintColor = minTint;
    slider.maximumTrackTintColor = maxTint;
}

static inline void plaza_segmented_control_set_segments(
    UISegmentedControl * _Nonnull control,
    NSArray * _Nonnull titles
) {
    [control removeAllSegments];
    NSUInteger count = titles.count;
    for (NSUInteger index = 0; index < count; index++) {
        id entry = [titles objectAtIndex:index];
        NSString *title = [entry isKindOfClass:[NSString class]] ? (NSString *)entry : [entry description];
        [control insertSegmentWithTitle:title atIndex:index animated:NO];
    }
}

static inline int plaza_segmented_control_selected_index(UISegmentedControl * _Nonnull control) {
    NSInteger index = control.selectedSegmentIndex;
    return index == UISegmentedControlNoSegment ? -1 : (int)index;
}

static inline void plaza_segmented_control_set_selected_index(UISegmentedControl * _Nonnull control, int index) {
    control.selectedSegmentIndex = index < 0 ? UISegmentedControlNoSegment : (NSInteger)index;
}

static inline void plaza_segmented_control_set_segment_enabled(
    UISegmentedControl * _Nonnull control,
    BOOL enabled,
    int index
) {
    if (index >= 0 && (NSUInteger)index < control.numberOfSegments) {
        [control setEnabled:enabled forSegmentAtIndex:(NSUInteger)index];
    }
}

static inline void plaza_segmented_control_set_appearance(
    UISegmentedControl * _Nonnull control,
    UIColor * _Nullable selectedTint,
    UIColor * _Nullable selectedTextColor,
    UIColor * _Nullable normalTextColor,
    UIFont * _Nullable font
) {
    control.selectedSegmentTintColor = selectedTint;
    NSMutableDictionary *normalAttributes = [NSMutableDictionary dictionary];
    NSMutableDictionary *selectedAttributes = [NSMutableDictionary dictionary];
    if (normalTextColor != nil) {
        normalAttributes[NSForegroundColorAttributeName] = normalTextColor;
    }
    if (selectedTextColor != nil) {
        selectedAttributes[NSForegroundColorAttributeName] = selectedTextColor;
    }
    if (font != nil) {
        normalAttributes[NSFontAttributeName] = font;
        selectedAttributes[NSFontAttributeName] = font;
    }
    [control setTitleTextAttributes:(normalAttributes.count > 0 ? normalAttributes : nil) forState:UIControlStateNormal];
    [control setTitleTextAttributes:(selectedAttributes.count > 0 ? selectedAttributes : nil) forState:UIControlStateSelected];
}

static inline double plaza_segmented_control_intrinsic_height(UISegmentedControl * _Nonnull control) {
    return (double)control.intrinsicContentSize.height;
}

static inline double plaza_text_width_for_font(NSString * _Nonnull text, UIFont * _Nonnull font) {
    return ceil((double)[text sizeWithAttributes:@{NSFontAttributeName: font}].width);
}

static inline double plaza_text_height_for_font(NSString * _Nonnull text, UIFont * _Nonnull font) {
    CGSize size = [text sizeWithAttributes:@{NSFontAttributeName: font}];
    return ceil((double)MAX(size.height, font.lineHeight));
}

static inline void plaza_control_add_value_changed_action(
    UIControl * _Nonnull control,
    void (^ _Nonnull handler)(UIControl * _Nonnull sender)
) {
    __weak UIControl *weakControl = control;
    [control addAction:[UIAction actionWithHandler:^(__unused UIAction * _Nonnull action) {
        UIControl *strongControl = weakControl;
        if (strongControl != nil) {
            handler(strongControl);
        }
    }] forControlEvents:UIControlEventValueChanged];
}

static inline void plaza_slider_wire_events(
    UISlider * _Nonnull slider,
    void (^ _Nonnull onTouchDown)(UISlider * _Nonnull sender),
    void (^ _Nonnull onValueChanged)(UISlider * _Nonnull sender),
    void (^ _Nonnull onTouchEnd)(UISlider * _Nonnull sender),
    void (^ _Nonnull onTouchCancel)(UISlider * _Nonnull sender)
) {
    __weak UISlider *weakSlider = slider;
    [slider addAction:[UIAction actionWithHandler:^(__unused UIAction * _Nonnull action) {
        UISlider *strongSlider = weakSlider;
        if (strongSlider != nil) {
            onTouchDown(strongSlider);
        }
    }] forControlEvents:UIControlEventTouchDown];
    [slider addAction:[UIAction actionWithHandler:^(__unused UIAction * _Nonnull action) {
        UISlider *strongSlider = weakSlider;
        if (strongSlider != nil) {
            onValueChanged(strongSlider);
        }
    }] forControlEvents:UIControlEventValueChanged];
    [slider addAction:[UIAction actionWithHandler:^(__unused UIAction * _Nonnull action) {
        UISlider *strongSlider = weakSlider;
        if (strongSlider != nil) {
            onTouchEnd(strongSlider);
        }
    }] forControlEvents:(UIControlEventTouchUpInside | UIControlEventTouchUpOutside)];
    [slider addAction:[UIAction actionWithHandler:^(__unused UIAction * _Nonnull action) {
        UISlider *strongSlider = weakSlider;
        if (strongSlider != nil) {
            onTouchCancel(strongSlider);
        }
    }] forControlEvents:UIControlEventTouchCancel];
}
