#import <UIKit/UIKit.h>
#import <dispatch/dispatch.h>
#import <objc/runtime.h>
#import "ComposeTokenizerGuard.h"
#ifndef MISSING_CLASS_TEST
#import "CMPTextInputStringTokenizer.h"
#endif
#include <stdio.h>
#include <stdlib.h>

#define CHECK(condition) do { if (!(condition)) { \
    fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #condition); \
    fflush(stderr); exit(1); } } while (0)

#ifndef MISSING_CLASS_TEST
// Unlike positions obtained from UITextView, these can deliberately represent stale or
// out-of-range offsets. All arithmetic and NSString lengths are UTF-16 code units.
@interface TestPosition : UITextPosition
@property(nonatomic) NSInteger offset;
+ (instancetype)position:(NSInteger)offset;
@end
@implementation TestPosition
+ (instancetype)position:(NSInteger)offset {
    TestPosition *p = [TestPosition new]; p.offset = offset; return p;
}
@end

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wprotocol"
@interface TestInput : UIResponder <UITextInput>
@property(nonatomic, copy) NSString *text;
@end
@implementation TestInput
- (UITextPosition *)beginningOfDocument { return [TestPosition position:0]; }
- (UITextPosition *)endOfDocument { return [TestPosition position:(NSInteger)self.text.length]; }
- (NSInteger)offsetFromPosition:(UITextPosition *)from toPosition:(UITextPosition *)to {
    CHECK([from isKindOfClass:TestPosition.class] && [to isKindOfClass:TestPosition.class]);
    return ((TestPosition *)to).offset - ((TestPosition *)from).offset;
}
@end
#pragma clang diagnostic pop

// The pinned Objective-C bridge forwards to this override, like the Kotlin subclass.
// Trap invalid positions *before* NSString can index them. Returning a configurable
// answer and recording arguments makes accidental unconditional YES observable.
@interface KotlinAnalog : CMPTextInputStringTokenizer
@property(nonatomic, weak) TestInput *owner;
@property(nonatomic) NSUInteger calls;
@property(nonatomic) BOOL answer;
@property(nonatomic) UITextGranularity lastGranularity;
@property(nonatomic) UITextDirection lastDirection;
@property(nonatomic, strong) UITextPosition *lastPosition;
@end
@implementation KotlinAnalog
- (BOOL)isPositionAtBoundary:(UITextPosition *)position atBoundary:(UITextGranularity)granularity inDirection:(UITextDirection)direction {
    TestInput *owner = self.owner;
    if (owner) {
        NSInteger offset = ((TestPosition *)position).offset;
        CHECK(offset >= 0 && offset <= (NSInteger)owner.text.length);
        if (offset < (NSInteger)owner.text.length) (void)[owner.text characterAtIndex:(NSUInteger)offset];
    }
    self.calls++;
    self.lastPosition = position;
    self.lastGranularity = granularity;
    self.lastDirection = direction;
    return self.answer;
}
@end

static KotlinAnalog *makeTokenizer(TestInput *input) {
    // No subclass initializer: this exercises the inherited public initializer and
    // ARC's ownership of the object returned through the installed hook.
    KotlinAnalog *tokenizer = [[KotlinAnalog alloc] initWithTextInput:input];
    CHECK(tokenizer != nil);
    tokenizer.owner = input;
    return tokenizer;
}
static void expectValid(KotlinAnalog *tokenizer, NSInteger offset, UITextGranularity granularity,
                        UITextDirection direction, BOOL expected) {
    TestPosition *position = [TestPosition position:offset];
    NSUInteger before = tokenizer.calls;
    tokenizer.answer = expected;
    BOOL result = [tokenizer isPosition:position atBoundary:granularity inDirection:direction];
    CHECK(result == expected && tokenizer.calls == before + 1);
    CHECK(tokenizer.lastPosition == position);
    CHECK(tokenizer.lastGranularity == granularity && tokenizer.lastDirection == direction);
}
static void expectBlocked(KotlinAnalog *tokenizer, UITextPosition *position) {
    NSUInteger before = tokenizer.calls;
    tokenizer.answer = NO;
    CHECK([tokenizer isPosition:position atBoundary:UITextGranularityWord inDirection:UITextStorageDirectionForward] == YES);
    CHECK(tokenizer.calls == before);
}
#endif

static void runTests(void) {
    CHECK([NSThread isMainThread]);
    Class base = UITextInputStringTokenizer.class;
    Method baseInit = class_getInstanceMethod(base, @selector(initWithTextInput:));
    Method baseBoundary = class_getInstanceMethod(base, @selector(isPosition:atBoundary:inDirection:));
    CHECK(baseInit && baseBoundary);
    IMP initBefore = method_getImplementation(baseInit);
    IMP boundaryBefore = method_getImplementation(baseBoundary);
#ifdef MISSING_CLASS_TEST
    CHECK(NSClassFromString(@"CMPTextInputStringTokenizer") == Nil);
    CHECK(!NodysseyInstallComposeTokenizerGuard());
    CHECK(!NodysseyInstallComposeTokenizerGuard());
#else
    // An instance initialized before installation has no captured association.
    TestInput *uncapturedInput = [TestInput new]; uncapturedInput.text = @"abc";
    KotlinAnalog *uncaptured = makeTokenizer(uncapturedInput);
    CHECK(NodysseyInstallComposeTokenizerGuard());
    CHECK(NodysseyInstallComposeTokenizerGuard());
    expectValid(uncaptured, 1, UITextGranularityWord, UITextStorageDirectionForward, NO);
#endif
    CHECK(method_getImplementation(baseInit) == initBefore);
    CHECK(method_getImplementation(baseBoundary) == boundaryBefore);
#ifndef MISSING_CLASS_TEST
    TestInput *first = [TestInput new]; first.text = @"A😀B"; // 4 UTF-16 units
    TestInput *second = [TestInput new]; second.text = @"abcdefgh";
    CHECK(first.text.length == 4 && second.text.length == 8);
    KotlinAnalog *a = makeTokenizer(first), *b = makeTokenizer(second);
    expectValid(a, 0, UITextGranularityCharacter, UITextStorageDirectionForward, NO);
    expectValid(a, 4, UITextGranularityWord, UITextStorageDirectionBackward, NO);
    expectValid(a, 1, UITextGranularityLine, UITextLayoutDirectionRight, YES);
    expectValid(a, 2, UITextGranularityCharacter, UITextStorageDirectionBackward, NO);
    expectValid(a, 3, UITextGranularityParagraph, UITextLayoutDirectionLeft, YES);
    expectBlocked(a, [TestPosition position:-1]);
    expectBlocked(a, [TestPosition position:5]);
    // Same numeric offset is valid for one owner and invalid for the other.
    expectValid(b, 5, UITextGranularitySentence, UITextStorageDirectionForward, NO);
    expectBlocked(a, [TestPosition position:5]);
    expectBlocked(b, [TestPosition position:9]);
    expectValid(a, 4, UITextGranularityWord, UITextStorageDirectionForward, NO);
    TestPosition *stale = [TestPosition position:8];
    expectValid(b, 8, UITextGranularityWord, UITextStorageDirectionBackward, NO);
    second.text = @"x";
    expectBlocked(b, stale);
    expectValid(a, 4, UITextGranularityWord, UITextStorageDirectionBackward, NO);
    first.text = @"";
    expectValid(a, 0, UITextGranularityDocument, UITextStorageDirectionForward, NO);
    expectBlocked(a, [TestPosition position:1]);
    expectBlocked(a, [TestPosition position:-1]);

    // Compare UIKit's own initializer lifetime before blaming the hook. Keep both
    // tokenizers alive until after the weak-reference checks, including under ARC.
    __weak TestInput *weakBaseline;
    __weak TestInput *weakOwner;
    __attribute__((objc_precise_lifetime)) UITextInputStringTokenizer *baseline;
    __attribute__((objc_precise_lifetime)) KotlinAnalog *orphan;
    @autoreleasepool {
        TestInput *temporary = [TestInput new]; temporary.text = @"abc";
        weakBaseline = temporary;
        baseline = [[UITextInputStringTokenizer alloc] initWithTextInput:temporary];
        CHECK(baseline != nil);
    }
    @autoreleasepool {
        TestInput *temporary = [TestInput new]; temporary.text = @"abc";
        weakOwner = temporary;
        orphan = makeTokenizer(temporary);
    }
    CHECK((weakOwner == nil) == (weakBaseline == nil));
    if (weakOwner == nil) {
        NSUInteger before = orphan.calls;
        orphan.answer = NO;
        CHECK([orphan isPosition:[TestPosition position:0] atBoundary:UITextGranularityWord inDirection:UITextStorageDirectionForward]);
        CHECK(orphan.calls == before); // Known destroyed owner must not reach Kotlin.
    } else {
        puts("SKIP destroyed-owner check: UIKit baseline retains the input");
    }
#endif
    puts("TOKENIZER_GUARD_TEST_SUCCESS"); fflush(stdout);
}

@interface HarnessDelegate : UIResponder <UIApplicationDelegate>
@end
@implementation HarnessDelegate
@end

@interface HarnessSceneDelegate : UIResponder <UIWindowSceneDelegate>
@property(nonatomic, strong) UIWindow *window;
@end
@implementation HarnessSceneDelegate
- (void)scene:(UIScene *)scene willConnectToSession:(UISceneSession *)session
      options:(UISceneConnectionOptions *)connectionOptions {
    CHECK([scene isKindOfClass:UIWindowScene.class]);
    self.window = [[UIWindow alloc] initWithWindowScene:(UIWindowScene *)scene];
    self.window.rootViewController = [UIViewController new];
    [self.window makeKeyAndVisible];
    // Run only after a real window scene connected. Never print the success marker
    // from the app delegate or a fallback path if scene initialization fails.
    static BOOL scheduled = NO;
    CHECK(!scheduled);
    scheduled = YES;
    dispatch_async(dispatch_get_main_queue(), ^{
        runTests();
        exit(0);
    });
}
@end
int main(int argc, char **argv) {
    @autoreleasepool { return UIApplicationMain(argc, argv, nil, NSStringFromClass(HarnessDelegate.class)); }
}
