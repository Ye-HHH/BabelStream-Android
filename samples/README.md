Samples overview

This repository now includes the official Gummy Android SDK demo project extracted under:

- app/libs/事例代码 (extracted from example ZIP)

How it maps to our implementation:

- Official demo classes
  - mit.alibaba.nuidemo.DashGummySpeechTranscriberActivity
  - mit.alibaba.nuidemo.DashGummySpeechRecognizerActivity
  - Use NativeNui.initialize → setParams → startDialog → stopDialog → release

- Our production wrapper
  - com.babelstream.SdkGummyClient
  - Encapsulates NativeNui and feeds PCM from our audio capture managers via a ring buffer
  - Recognizer is used by RecognitionService and updates UI/Overlay via broadcasts

Run the demo

- Open app/libs/事例代码 as an Android Studio project (it is a full Gradle project)
- Set API Key in the demo app and run on a device

Note on dependencies

- The app uses libs/nuisdk-release.aar for the SDK runtime; you don’t need to reference the demo itself.

