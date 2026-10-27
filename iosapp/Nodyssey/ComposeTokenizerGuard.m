#import "ComposeTokenizerGuard.h"

#import <UIKit/UIKit.h>
#import <objc/runtime.h>
#import <stdlib.h>
#import <string.h>

// Compose's internal Kotlin tokenizer is not replaceable through a public app API. In the
// pinned Compose 1.13.0-alpha01 bridge (a9e47e8), CMP owns the UIKit boundary selector and
// forwards it to Kotlin, which indexes paragraphs without a bounds check. Remove this temporary
// compatibility layer when the adopted Compose version checks these bounds and real tokenizer /
// dictation regression tests pass. It protects only this one boundary query.

@interface NDYWeakTextInput : NSObject
@property(nonatomic, weak, nullable) id<UITextInput> input;
@property(nonatomic) BOOL capturedInput;
@end
@implementation NDYWeakTextInput
@end

// This implementation is installed only on CMP, never on NSObject or UIKit. Both declarations
// are explicitly init-family so ARC treats the receiver and returned object as an initializer.
@interface NDYTokenizerInitMethods : NSObject
- (instancetype)ndy_guard_initWithTextInput:(UIResponder *)input __attribute__((objc_method_family(init)));
@end
// Declaration only: CMP receives UIKit's original IMP under this alias.
@interface NDYTokenizerInitMethods (NDYOriginalInitializer)
- (instancetype)ndy_guard_originalInitWithTextInput:(UIResponder *)input __attribute__((objc_method_family(init)));
@end

static char NDYTextInputKey;

@implementation NDYTokenizerInitMethods
- (instancetype)ndy_guard_initWithTextInput:(UIResponder *)input {
    self = [self ndy_guard_originalInitWithTextInput:input];
    if (self != nil) {
        NDYWeakTextInput *box = [NDYWeakTextInput new];
        if ([input conformsToProtocol:@protocol(UITextInput)]) {
            box.input = (id<UITextInput>)input;
            box.capturedInput = YES;
        }
        objc_setAssociatedObject(self, &NDYTextInputKey, box, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
    return self;
}
@end

typedef BOOL (*NDYBoundaryIMP)(id, SEL, UITextPosition *, UITextGranularity, UITextDirection);
static NDYBoundaryIMP NDYOriginalBoundary;
static BOOL NDYInstalled;

static BOOL NDYGuardedBoundary(id tokenizer, SEL selector, UITextPosition *position,
                               UITextGranularity granularity, UITextDirection direction) {
    NDYWeakTextInput *box = objc_getAssociatedObject(tokenizer, &NDYTextInputKey);
    id<UITextInput> input = box.input; // Strong local snapshot of the weak reference.
    if (box != nil && box.capturedInput && input == nil) {
        // The known document owner has gone away; do not call its Kotlin tokenizer.
        return YES;
    }
    if (box == nil || !box.capturedInput || position == nil) {
#if DEBUG
        NSLog(@"[ComposeTokenizerGuard] input or position unavailable; cannot verify bounds");
#endif
    } else {
        UITextPosition *beginning = input.beginningOfDocument;
        UITextPosition *end = input.endOfDocument;
        if (beginning != nil && end != nil) {
            NSInteger length = [input offsetFromPosition:beginning toPosition:end];
            if (length >= 0) {
                NSInteger offset = [input offsetFromPosition:beginning toPosition:position];
                if (offset < 0 || offset > length) return YES;
            } else {
#if DEBUG
                NSLog(@"[ComposeTokenizerGuard] invalid document length; cannot verify bounds");
#endif
            }
        } else {
#if DEBUG
            NSLog(@"[ComposeTokenizerGuard] document endpoints unavailable; cannot verify bounds");
#endif
        }
    }
    // Fail open if capture was bypassed or bounds are unavailable: compatibility is preserved,
    // but these paths are not protected. Delegate all valid offsets, including 0 and length.
    return NDYOriginalBoundary(tokenizer, selector, position, granularity, direction);
}

// class_getInstanceMethod can return an inherited Method; only mutate a method found here.
static Method NDYOwnedInstanceMethod(Class cls, SEL selector) {
    unsigned int count = 0;
    Method *methods = class_copyMethodList(cls, &count);
    Method found = NULL;
    for (unsigned int i = 0; i < count; i++) {
        if (method_getName(methods[i]) == selector) {
            found = methods[i];
            break;
        }
    }
    free(methods);
    return found;
}

// Check each runtime ABI component rather than comparing encodings containing frame offsets.
static BOOL NDYTypeMatches(const char *actual, const char *expected) {
    while (*actual && strchr("rnNoORV", *actual)) actual++;
    // Object encodings may include a quoted class/protocol annotation.
    if (*expected == '@') return *actual == '@';
    return strcmp(actual, expected) == 0;
}

static BOOL NDYSignatureMatches(Method method, const char *result,
                                const char *const *arguments, unsigned int count) {
    if (method == NULL || method_getNumberOfArguments(method) != count) return NO;
    char *type = method_copyReturnType(method);
    BOOL matches = type != NULL && NDYTypeMatches(type, result);
    free(type);
    for (unsigned int i = 0; matches && i < count; i++) {
        type = method_copyArgumentType(method, i);
        matches = type != NULL && NDYTypeMatches(type, arguments[i]);
        free(type);
    }
    return matches;
}

static BOOL NDYInstallationFailure(NSString *reason) {
#if DEBUG
    NSLog(@"[ComposeTokenizerGuard] not installed: %@", reason);
#else
    (void)reason;
#endif
    return NO;
}

BOOL NodysseyInstallComposeTokenizerGuard(void) {
    if (![NSThread isMainThread]) return NDYInstallationFailure(@"requires main thread");
    if (NDYInstalled) return YES;

    Class cls = NSClassFromString(@"CMPTextInputStringTokenizer");
    if (cls == Nil) return NDYInstallationFailure(@"CMP class missing");
    Class parent = class_getSuperclass(cls);
    if (parent != [UITextInputStringTokenizer class]) {
        return NDYInstallationFailure(@"unexpected CMP superclass");
    }

    SEL initializer = @selector(initWithTextInput:);
    SEL alias = @selector(ndy_guard_originalInitWithTextInput:);
    SEL boundary = @selector(isPosition:atBoundary:inDirection:);
    Method inheritedInit = class_getInstanceMethod(parent, initializer);
    Method bridgeBoundary = NDYOwnedInstanceMethod(cls, boundary);
    Method kotlinForward = NDYOwnedInstanceMethod(cls, @selector(isPositionAtBoundary:atBoundary:inDirection:));
    Method replacementInit = class_getInstanceMethod([NDYTokenizerInitMethods class],
                                                     @selector(ndy_guard_initWithTextInput:));
    if (NDYOwnedInstanceMethod(cls, initializer) != NULL ||
        class_getInstanceMethod(cls, alias) != NULL) {
        return NDYInstallationFailure(@"initializer or alias collision");
    }

    const char *initArguments[] = { @encode(id), @encode(SEL), @encode(id) };
    const char *boundaryArguments[] = { @encode(id), @encode(SEL), @encode(id),
                                        @encode(UITextGranularity), @encode(UITextDirection) };
    if (!NDYSignatureMatches(inheritedInit, @encode(id), initArguments, 3) ||
        !NDYSignatureMatches(replacementInit, @encode(id), initArguments, 3) ||
        !NDYSignatureMatches(bridgeBoundary, @encode(BOOL), boundaryArguments, 5) ||
        !NDYSignatureMatches(kotlinForward, @encode(BOOL), boundaryArguments, 5)) {
        return NDYInstallationFailure(@"missing method or incompatible signature");
    }

    IMP originalInit = method_getImplementation(inheritedInit);
    IMP originalBoundary = method_getImplementation(bridgeBoundary);
    if (originalInit == NULL || originalBoundary == NULL) return NDYInstallationFailure(@"missing implementation");

    // The alias is harmless on its own. Do not mutate inherited UIKit Method objects.
    if (!class_addMethod(cls, alias, originalInit, method_getTypeEncoding(inheritedInit))) {
        return NDYInstallationFailure(@"could not add initializer alias");
    }
    if (!class_addMethod(cls, initializer, method_getImplementation(replacementInit),
                         method_getTypeEncoding(inheritedInit))) {
        return NDYInstallationFailure(@"could not add initializer hook");
    }

    NDYOriginalBoundary = (NDYBoundaryIMP)originalBoundary;
    method_setImplementation(bridgeBoundary, (IMP)NDYGuardedBoundary);
    NDYInstalled = YES;
    return YES;
}
