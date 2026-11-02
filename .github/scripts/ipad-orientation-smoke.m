// Loaded only into the simulator app by test-ios-ipad.py. No production code is changed.
#import <UIKit/UIKit.h>
#import <stdio.h>
#import <stdlib.h>

static void Report(NSString *message) {
    fputs(message.UTF8String, stderr);
    fputc(10, stderr);
    fflush(stderr);
}

static UIWindow *AppWindow(void) {
    for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
        if (![scene isKindOfClass:UIWindowScene.class]) continue;
        for (UIWindow *window in ((UIWindowScene *)scene).windows) {
            if (window.isKeyWindow) return window;
        }
    }
    return nil;
}

static void Rotate(UIWindow *window, NSUInteger step) {
    NSArray<NSNumber *> *orientations = @[
        @(UIInterfaceOrientationMaskPortrait),
        @(UIInterfaceOrientationMaskLandscapeRight),
        @(UIInterfaceOrientationMaskPortrait),
        @(UIInterfaceOrientationMaskLandscapeLeft),
        @(UIInterfaceOrientationMaskPortrait),
    ];
    if (step == orientations.count) {
        Report(@"IPAD_SMOKE_OK");
        exit(0);
    }
    UIInterfaceOrientationMask mask = orientations[step].unsignedIntegerValue;
    UIWindowSceneGeometryPreferencesIOS *preferences =
        [[UIWindowSceneGeometryPreferencesIOS alloc] initWithInterfaceOrientations:mask];
    [window.windowScene requestGeometryUpdateWithPreferences:preferences errorHandler:^(NSError *error) {
        Report([@"IPAD_SMOKE_ERROR " stringByAppendingString:error.description]);
        exit(2);
    }];
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 2 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
        UIWindowScene *scene = window.windowScene;
        if (!(mask & (1UL << scene.interfaceOrientation))) {
            Report(@"IPAD_SMOKE_ERROR orientation did not settle");
            exit(3);
        }
        UITabBarController *tabs = (UITabBarController *)window.rootViewController;
        if (![tabs isKindOfClass:UITabBarController.class]) {
            Report(@"IPAD_SMOKE_ERROR missing tab root");
            exit(4);
        }
        UINavigationController *navigation = (UINavigationController *)tabs.selectedViewController;
        if (![navigation isKindOfClass:UINavigationController.class] || !navigation.topViewController) {
            Report(@"IPAD_SMOKE_ERROR missing navigation stack");
            exit(5);
        }
        Report([NSString stringWithFormat:@"IPAD_SMOKE_STEP %lu orientation=%ld width=%.0f controller=%@",
            (unsigned long)step, (long)scene.interfaceOrientation, window.bounds.size.width,
            NSStringFromClass(navigation.topViewController.class)]);
        Rotate(window, step + 1);
    });
}

@interface NodysseyIpadSmoke : NSObject
@end
@implementation NodysseyIpadSmoke
+ (void)load {
    dispatch_async(dispatch_get_main_queue(), ^{
        __block NSUInteger attempts = 0;
        [NSTimer scheduledTimerWithTimeInterval:0.1 repeats:YES block:^(NSTimer *timer) {
            UIWindow *window = AppWindow();
            if ([window.rootViewController isKindOfClass:UITabBarController.class]) {
                [timer invalidate];
                Report(@"IPAD_SMOKE_READY");
                // Allow the initial layout callback to install a split on a wide iPad.
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, NSEC_PER_SEC), dispatch_get_main_queue(), ^{
                    if (getenv("NODYSSEY_SMOKE_COLD_LANDSCAPE")) {
                        if (!UIInterfaceOrientationIsLandscape(window.windowScene.interfaceOrientation) ||
                            window.bounds.size.width <= window.bounds.size.height) {
                            Report(@"IPAD_SMOKE_ERROR cold launch did not use landscape");
                            exit(7);
                        }
                        UITabBarController *tabs = (UITabBarController *)window.rootViewController;
                        UINavigationController *navigation = (UINavigationController *)tabs.selectedViewController;
                        NSString *name = NSStringFromClass(navigation.topViewController.class);
                        if (![name containsString:@"PaneSplitController"]) {
                            Report([@"IPAD_SMOKE_ERROR cold landscape missing split: " stringByAppendingString:name]);
                            exit(8);
                        }
                        Report([@"IPAD_SMOKE_COLD_LANDSCAPE " stringByAppendingString:name]);
                        Report(@"IPAD_SMOKE_OK");
                        exit(0);
                    }
                    Rotate(window, 0);
                });
            } else if (++attempts >= 600) {
                Report(@"IPAD_SMOKE_ERROR app did not install its native shell");
                exit(6);
            }
        }];
    });
}
@end
