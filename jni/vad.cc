#include <jni.h>

#include <string>

#include "webrtc/common_audio/resampler/include/resampler.h"
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/common_audio/vad/include/webrtc_vad.h"
#include "webrtc/typedefs.h"

using namespace std;

#ifndef _Included_com_babeefone_VoiceActivityDetector
#define _Included_com_babeefone_VoiceActivityDetector
#ifdef __cplusplus
extern "C" {
#endif

VadInst* handle = NULL;

JNIEXPORT void JNICALL Java_com_babeefone_VoiceActivityDetector_init (JNIEnv *env, jclass javaClass) {
    WebRtcVad_Create(&handle);
    WebRtcVad_Init(handle);
}

JNIEXPORT jboolean JNICALL Java_com_babeefone_VoiceActivityDetector_process (JNIEnv *env, jclass javaClass, jshortArray samples) {
    jint inputLength = env->GetArrayLength(samples);
    int inFrequency = inputLength * 100;
    int outFrequency = 32000;
    webrtc::Resampler resampler(inFrequency, outFrequency, webrtc::kResamplerSynchronous);
    int outputLength = 0;
    int maxLen = outFrequency / 100;
    jshort outputFrame[maxLen];
    jboolean isCopy = JNI_FALSE;
    jshort *inputFrame = env->GetShortArrayElements(samples, &isCopy);
    resampler.Push(inputFrame, inputLength, outputFrame, maxLen, outputLength);

    bool isVoice;
    if (WebRtcVad_Process(handle, outputLength * 100, outputFrame, outputLength) == 1) {
        isVoice = JNI_TRUE;
    } else {
        isVoice = JNI_FALSE;
    }

    env->ReleaseShortArrayElements(samples, inputFrame, JNI_ABORT);
    return isVoice;
}

JNIEXPORT void JNICALL Java_com_babeefone_VoiceActivityDetector_free (JNIEnv *env, jclass javaClass) {
    WebRtcVad_Free(handle);
    handle = NULL;
}

#ifdef __cplusplus
}
#endif
#endif
