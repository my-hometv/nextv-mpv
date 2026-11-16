# Native runtime readiness — compatibility correction

The initial NDK probe was placed as a mandatory gate before the actual LibVLC Java constructor. This could make every channel unavailable on a valid LibVLC build whose native loader or library name differed from `libvlc.so`.

The isolated service now checks readiness by constructing the actual packaged Java LibVLC runtime, catching Java/native linkage failures. The C++ JNI probe remains optional diagnostic capability and restricts `dlopen` to the expected library name rather than accepting arbitrary paths. Added tests for absent native runtimes and blank inputs.

This change removes a potential all-channels-blackout regression introduced by the previous batch. **It is not the three-version relocation solution**: separate native builds, symbol/Java namespace isolation, JNI registration and a unified Android package are still pending. No workflow or APK was run.
