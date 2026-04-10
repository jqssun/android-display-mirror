#pragma once
#include <jni.h>
#include <cstdint>

namespace airplay_bridge {
    void init(JNIEnv *env);
    void cleanup(JNIEnv *env);
    void sendVideoFrame(JavaVM *jvm, const uint8_t *data, size_t size, bool isKeyframe);
}
