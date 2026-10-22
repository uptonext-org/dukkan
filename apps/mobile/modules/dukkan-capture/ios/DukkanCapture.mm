#import "DukkanCapture.h"

#import <AVFoundation/AVFoundation.h>
#import <Speech/Speech.h>
#import <UIKit/UIKit.h>

/**
 * Speech: one SFSpeechAudioBufferRecognitionRequest fed from the microphone until stopSpeech; its
 * transcript is cumulative, so pauses do not split the list. Only the text is passed on (the app
 * sends it to Dukkan); Apple's recognizer may process the audio on the device or on Apple's servers
 * as described in the system permission prompt. Frames: AVAssetImageGenerator, JPEG in tmp/.
 */
@implementation DukkanCapture {
  AVAudioEngine *_engine;
  SFSpeechRecognizer *_recognizer;
  SFSpeechAudioBufferRecognitionRequest *_request;
  SFSpeechRecognitionTask *_task;
  NSString *_transcript;
  NSString *_error;
  BOOL _listening;
  /** Callers waiting for the final transcript (a second stop while stopping waits as well). */
  NSMutableArray<RCTPromiseResolveBlock> *_stopResolves;
}

RCT_EXPORT_MODULE()

+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

- (instancetype)init
{
  if (self = [super init]) {
    _transcript = @"";
    _error = @"";
    _stopResolves = [NSMutableArray array];
  }
  return self;
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:(const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeDukkanCaptureSpecJSI>(params);
}

- (void)isSpeechAvailable:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  SFSpeechRecognizer *recognizer = [[SFSpeechRecognizer alloc] init];
  resolve(@(recognizer != nil && recognizer.isAvailable));
}

- (void)startSpeech:(NSString *)languageTag resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [SFSpeechRecognizer requestAuthorization:^(SFSpeechRecognizerAuthorizationStatus status) {
    if (status != SFSpeechRecognizerAuthorizationStatusAuthorized) {
      reject(@"permission", @"Speech recognition is not allowed", nil);
      return;
    }
    void (^afterMicrophone)(BOOL) = ^(BOOL granted) {
      if (!granted) {
        reject(@"permission", @"Microphone access is not allowed", nil);
        return;
      }
      dispatch_async(dispatch_get_main_queue(), ^{
        [self beginWithLanguage:languageTag resolve:resolve reject:reject];
      });
    };
    if (@available(iOS 17.0, *)) {
      [AVAudioApplication requestRecordPermissionWithCompletionHandler:afterMicrophone];
    } else {
      [[AVAudioSession sharedInstance] requestRecordPermission:afterMicrophone];
    }
  }];
}

- (void)beginWithLanguage:(NSString *)languageTag resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  @synchronized(self) {
    if (_listening) {
      reject(@"busy", @"Already listening", nil);
      return;
    }
  }
  SFSpeechRecognizer *recognizer = [[SFSpeechRecognizer alloc] initWithLocale:[NSLocale localeWithLocaleIdentifier:languageTag]];
  if (recognizer == nil || !recognizer.isAvailable) {
    reject(@"unavailable", @"Speech recognition is not available for this language", nil);
    return;
  }
  NSError *error = nil;
  AVAudioSession *session = [AVAudioSession sharedInstance];
  if (![session setCategory:AVAudioSessionCategoryRecord mode:AVAudioSessionModeMeasurement options:AVAudioSessionCategoryOptionDuckOthers error:&error] ||
      ![session setActive:YES withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:&error]) {
    reject(@"failed", error.localizedDescription ?: @"Audio session failed", error);
    return;
  }
  SFSpeechAudioBufferRecognitionRequest *request = [[SFSpeechAudioBufferRecognitionRequest alloc] init];
  request.shouldReportPartialResults = YES;
  if (@available(iOS 16.0, *)) {
    request.addsPunctuation = YES;
  }
  AVAudioEngine *engine = [[AVAudioEngine alloc] init];
  AVAudioInputNode *input = engine.inputNode;
  [input installTapOnBus:0
              bufferSize:1024
                  format:[input outputFormatForBus:0]
                   block:^(AVAudioPCMBuffer *buffer, AVAudioTime *when) {
                     [request appendAudioPCMBuffer:buffer];
                   }];
  [engine prepare];
  if (![engine startAndReturnError:&error]) {
    [input removeTapOnBus:0];
    [session setActive:NO withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];
    reject(@"failed", error.localizedDescription ?: @"Microphone failed", error);
    return;
  }
  @synchronized(self) {
    _transcript = @"";
    _error = @"";
    _listening = YES;
    _engine = engine;
    _request = request;
    _recognizer = recognizer;
  }
  __weak DukkanCapture *weakSelf = self;
  _task = [recognizer recognitionTaskWithRequest:request
                                   resultHandler:^(SFSpeechRecognitionResult *result, NSError *taskError) {
                                     NSString *text = result.bestTranscription.formattedString;
                                     BOOL done = taskError != nil || result.isFinal;
                                     dispatch_async(dispatch_get_main_queue(), ^{
                                       DukkanCapture *strongSelf = weakSelf;
                                       // A late answer of an earlier session must not touch this one.
                                       if (strongSelf == nil || strongSelf->_request != request) {
                                         return;
                                       }
                                       if (text.length > 0) {
                                         @synchronized(strongSelf) {
                                           strongSelf->_transcript = text;
                                         }
                                       }
                                       if (done) {
                                         [strongSelf finishWithError:taskError];
                                       }
                                     });
                                   }];
  resolve(nil);
}

- (NSNumber *)isListening
{
  @synchronized(self) {
    return @(_listening);
  }
}

- (NSString *)speechTranscript
{
  @synchronized(self) {
    return _transcript;
  }
}

- (NSString *)speechError
{
  @synchronized(self) {
    return _error;
  }
}

- (void)stopSpeech:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  dispatch_async(dispatch_get_main_queue(), ^{
    BOOL listening;
    @synchronized(self) {
      listening = self->_listening;
    }
    if (!listening) {
      resolve([self speechTranscript]);
      return;
    }
    [self->_stopResolves addObject:resolve];
    if (self->_stopResolves.count > 1) {
      return;
    }
    [self stopAudio];
    [self->_request endAudio];
    // The final result normally follows within a moment; do not wait for it forever.
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
      if ([self->_stopResolves containsObject:resolve]) {
        [self finishWithError:nil];
      }
    });
  });
}

- (void)cancelSpeech
{
  dispatch_async(dispatch_get_main_queue(), ^{
    [self->_task cancel];
    [self finishWithError:nil];
  });
}

- (void)stopAudio
{
  if (_engine != nil) {
    [_engine stop];
    [_engine.inputNode removeTapOnBus:0];
    _engine = nil;
  }
}

- (void)finishWithError:(NSError *)error
{
  [self stopAudio];
  [_request endAudio];
  [_task cancel];
  _request = nil;
  _task = nil;
  _recognizer = nil;
  [[AVAudioSession sharedInstance] setActive:NO withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];
  NSString *transcript;
  @synchronized(self) {
    // "No speech detected" and cancellations are not errors for the customer.
    if (error != nil && _stopResolves.count == 0 && _transcript.length == 0 && _listening) {
      _error = [error.domain isEqualToString:NSURLErrorDomain] ? @"network" : @"failed";
    }
    _listening = NO;
    transcript = _transcript;
  }
  NSArray<RCTPromiseResolveBlock> *waiting = [_stopResolves copy];
  [_stopResolves removeAllObjects];
  for (RCTPromiseResolveBlock resolve in waiting) {
    resolve(transcript);
  }
}

- (void)extractVideoFrames:(NSString *)uri
                     count:(double)count
                   maxEdge:(double)maxEdge
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
  dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
    NSURL *url = [uri hasPrefix:@"/"] ? [NSURL fileURLWithPath:uri] : [NSURL URLWithString:uri];
    if (url == nil) {
      reject(@"failed", @"Invalid video URI", nil);
      return;
    }
    AVURLAsset *asset = [AVURLAsset URLAssetWithURL:url options:nil];
    AVAssetImageGenerator *generator = [AVAssetImageGenerator assetImageGeneratorWithAsset:asset];
    generator.appliesPreferredTrackTransform = YES;
    generator.maximumSize = CGSizeMake(maxEdge, maxEdge);
    generator.requestedTimeToleranceBefore = CMTimeMakeWithSeconds(0.5, 600);
    generator.requestedTimeToleranceAfter = CMTimeMakeWithSeconds(0.5, 600);
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    Float64 duration = CMTimeGetSeconds(asset.duration);
#pragma clang diagnostic pop
    NSInteger frames = MAX(1, MIN(8, (NSInteger)count));
    NSMutableArray<NSString *> *out = [NSMutableArray array];
    for (NSInteger i = 0; i < frames; i++) {
      Float64 seconds = duration > 0 ? duration * (2 * i + 1) / (2 * frames) : 0;
      NSError *error = nil;
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
      CGImageRef image = [generator copyCGImageAtTime:CMTimeMakeWithSeconds(seconds, 600) actualTime:NULL error:&error];
#pragma clang diagnostic pop
      if (image == NULL) {
        continue;
      }
      NSData *jpeg = UIImageJPEGRepresentation([UIImage imageWithCGImage:image], 0.8);
      CGImageRelease(image);
      NSString *name = [NSString stringWithFormat:@"dukkan-frame-%@.jpg", [NSUUID UUID].UUIDString];
      NSString *path = [NSTemporaryDirectory() stringByAppendingPathComponent:name];
      if ([jpeg writeToFile:path atomically:YES]) {
        [out addObject:[NSURL fileURLWithPath:path].absoluteString];
      }
    }
    if (out.count == 0) {
      reject(@"failed", @"No frames could be read from the video", nil);
      return;
    }
    resolve(out);
  });
}

@end
