#include <jni.h>
#include "airplay_bridge.h"
#include "logging.h"

using namespace std::literals;

static jclass airPlayServiceClass = nullptr;
static jmethodID airPlaySendFrameMethod = nullptr;

namespace airplay_bridge {

void init(JNIEnv *env) {
    jclass apClass = env->FindClass("io/github/jqssun/displaymirror/job/AirPlayService");
    if (apClass != nullptr) {
        airPlayServiceClass = (jclass)env->NewGlobalRef(apClass);
        env->DeleteLocalRef(apClass);
        airPlaySendFrameMethod = env->GetStaticMethodID(airPlayServiceClass, "onNativeVideoFrame", "([BZ)V");
    }
}

void cleanup(JNIEnv *env) {
    if (airPlayServiceClass != nullptr) {
        env->DeleteGlobalRef(airPlayServiceClass);
        airPlayServiceClass = nullptr;
    }
    airPlaySendFrameMethod = nullptr;
}

void sendVideoFrame(JavaVM *jvm, const uint8_t *data, size_t size, bool isKeyframe) {
    if (jvm == nullptr || airPlayServiceClass == nullptr || airPlaySendFrameMethod == nullptr) {
        return;
    }

    JNIEnv *env;
    if (jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        return;
    }

    jbyteArray jdata = env->NewByteArray(size);
    if (jdata != nullptr) {
        env->SetByteArrayRegion(jdata, 0, size, reinterpret_cast<const jbyte *>(data));
        env->CallStaticVoidMethod(airPlayServiceClass, airPlaySendFrameMethod, jdata, (jboolean)isKeyframe);
        env->DeleteLocalRef(jdata);
    }

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    jvm->DetachCurrentThread();
}

} // namespace airplay_bridge
