#include <jni.h>
#include <string>
#include "logging.h"
#include "config.h"
#include "nvhttp.h"
#include "globals.h"
#include "sunshine.h"
#include "stream.h"
#include "rtsp.h"
#include "audio.h"
#include "moonlight-common-c/src/Input.h"
#include "video_colorspace.h"

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <boost/endian/buffers.hpp>

using namespace std::literals;

extern "C" {

static std::unique_ptr<logging::deinit_t> deinit;
static JavaVM* jvm = nullptr;
static jclass sunshineServerClass = nullptr;
static jclass sunshineMouseClass = nullptr;
static audio::sample_queue_t samples = nullptr;

// Global variables for audio recording state
static std::thread audioRecordingThread;

// Cache frequently used method IDs
static jmethodID handleTouchPacketMethod = nullptr;
static jmethodID handleAbsMouseMoveMethod = nullptr;
static jmethodID handleRelMouseMoveMethod = nullptr;
static jmethodID handleLeftMouseButtonMethod = nullptr;

// Additional cached variables
static jclass sunshineKeyboardClass = nullptr;
static jmethodID handleKeyboardMethod = nullptr;

JNIEXPORT void JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_start(JNIEnv *env, jclass clazz) {
    env->GetJavaVM(&jvm);


    jclass serverClass = env->FindClass("io/github/jqssun/displaymirror/job/SunshineServer");
    if (serverClass != nullptr) {
        sunshineServerClass = (jclass)env->NewGlobalRef(serverClass);
        env->DeleteLocalRef(serverClass);
    } else {
        BOOST_LOG(error) << "Failed to find SunshineServer class at startup"sv;
    }
    
    jclass mouseClass = env->FindClass("io/github/jqssun/displaymirror/job/SunshineMouse");
    if (mouseClass != nullptr) {
        sunshineMouseClass = (jclass)env->NewGlobalRef(mouseClass);
        env->DeleteLocalRef(mouseClass);
        
        // Cache method IDs right after creating class refs
        handleTouchPacketMethod = env->GetStaticMethodID(sunshineMouseClass, "handleTouchPacket", "(IIIFFFFF)V");
        handleAbsMouseMoveMethod = env->GetStaticMethodID(sunshineMouseClass, "handleAbsMouseMovePacket", "(FFFF)V");
        handleRelMouseMoveMethod = env->GetStaticMethodID(sunshineMouseClass, "handleRelMouseMovePacket", "(SS)V");
        handleLeftMouseButtonMethod = env->GetStaticMethodID(sunshineMouseClass, "handleLeftMouseButton", "(Z)V");

        if (!handleTouchPacketMethod || !handleAbsMouseMoveMethod || !handleRelMouseMoveMethod || !handleLeftMouseButtonMethod) {
            BOOST_LOG(warning) << "Failed to cache one or more input handler method IDs"sv;
        }
    } else {
        BOOST_LOG(error) << "Failed to find SunshineMouse class at startup"sv;
    }
    
    // Cache SunshineKeyboard class ref
    jclass keyboardClass = env->FindClass("io/github/jqssun/displaymirror/job/SunshineKeyboard");
    if (keyboardClass != nullptr) {
        sunshineKeyboardClass = (jclass)env->NewGlobalRef(keyboardClass);
        env->DeleteLocalRef(keyboardClass);
        
        // Cache keyboard handler method IDs
        handleKeyboardMethod = env->GetStaticMethodID(sunshineKeyboardClass, "handleKeyboardEvent", "(IZI)V");
        
        if (!handleKeyboardMethod) {
            BOOST_LOG(warning) << "Failed to cache keyboard handler method IDs"sv;
        }
    } else {
        BOOST_LOG(error) << "Failed to find SunshineKeyboard class at startup"sv;
    }
    
    deinit = logging::init(1, "/dev/null");
    BOOST_LOG(info) << "start sunshine server"sv;
    mail::man = std::make_shared<safe::mail_raw_t>();
    task_pool.start(1);
    
    std::thread httpThread {nvhttp::start};
    rtsp_stream::rtpThread();
    httpThread.join();
}

JNIEXPORT void JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_setSunshineName(JNIEnv *env, jclass clazz, jstring sunshine_name) {
    const char *str = env->GetStringUTFChars(sunshine_name, nullptr);
    config::nvhttp.sunshine_name = str;
    env->ReleaseStringUTFChars(sunshine_name, str);
}

JNIEXPORT void JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_setPkeyPath(JNIEnv *env, jclass clazz, jstring path) {
    const char *str = env->GetStringUTFChars(path, nullptr);
    config::nvhttp.pkey = str;
    env->ReleaseStringUTFChars(path, str);
}

JNIEXPORT void JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_setCertPath(JNIEnv *env, jclass clazz, jstring path) {
    const char *str = env->GetStringUTFChars(path, nullptr);
    config::nvhttp.cert = str;
    env->ReleaseStringUTFChars(path, str);
}

JNIEXPORT void JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_setFileStatePath(JNIEnv *env, jclass clazz, jstring path) {
    const char *str = env->GetStringUTFChars(path, nullptr);
    config::nvhttp.file_state = str;
    env->ReleaseStringUTFChars(path, str);
}

JNIEXPORT void JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_submitPin(JNIEnv *env, jclass clazz, jstring pin) {
    const char *pinStr = env->GetStringUTFChars(pin, nullptr);
    nvhttp::pin(pinStr, "some-moonlight");
    env->ReleaseStringUTFChars(pin, pinStr);
}

JNIEXPORT void JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_cleanup(JNIEnv *env, jclass clazz) {
    if (sunshineServerClass != nullptr) {
        env->DeleteGlobalRef(sunshineServerClass);
        sunshineServerClass = nullptr;
        
        // Clear cached method IDs
        handleTouchPacketMethod = nullptr;
        handleAbsMouseMoveMethod = nullptr;
        handleLeftMouseButtonMethod = nullptr;
    }
    
    // Clean up SunshineKeyboard class ref
    if (sunshineKeyboardClass != nullptr) {
        env->DeleteGlobalRef(sunshineKeyboardClass);
        sunshineKeyboardClass = nullptr;
        handleKeyboardMethod = nullptr;
    }
    
    // Other cleanup...
}

JNIEXPORT void JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_startAudioRecording(JNIEnv *env, jclass clazz, jobject audioRecord, jint framesPerPacket) {
    // Create global ref for AudioRecord
    jobject globalAudioRecord = env->NewGlobalRef(audioRecord);
    if (globalAudioRecord == nullptr) {
        BOOST_LOG(error) << "Failed to create global ref for AudioRecord"sv;
        return;
    }
    
    // Get AudioRecord class and method IDs
    jclass audioRecordClass = env->GetObjectClass(globalAudioRecord);
    if (audioRecordClass == nullptr) {
        BOOST_LOG(error) << "Failed to get AudioRecord class"sv;
        env->DeleteGlobalRef(globalAudioRecord);
        return;
    }
    jmethodID readMethod = env->GetMethodID(audioRecordClass, "read", "([FIII)I");

    if (!readMethod) {
        BOOST_LOG(error) << "Failed to get AudioRecord methods"sv;
        env->DeleteGlobalRef(globalAudioRecord);
        return;
    }
    
    // Set active flag and start recording thread
    audioRecordingThread = std::thread([globalAudioRecord, readMethod, framesPerPacket, env]() {
        JNIEnv *threadEnv;
        jint result = jvm->AttachCurrentThread(&threadEnv, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach audio thread to JVM"sv;
            return;
        }
        
        // Create buffer
        jfloatArray buffer = threadEnv->NewFloatArray(framesPerPacket * 2); // stereo, 2 channels per frame
        
        try {
            while (true) {
                jint samplesRead = threadEnv->CallIntMethod(globalAudioRecord, readMethod, buffer, 0, framesPerPacket * 2, 0);

                if (samplesRead > 0) {
                    jfloat *audioData = threadEnv->GetFloatArrayElements(buffer, nullptr);
                    if (audioData) {
                        std::vector<float> audioSamples(audioData, audioData + samplesRead);

                        if (samples) {
                            samples->raise(std::move(audioSamples));
                        }

                        threadEnv->ReleaseFloatArrayElements(buffer, audioData, JNI_ABORT);
                    }
                }
            }
        } catch (...) {
            BOOST_LOG(error) << "Exception during audio recording"sv;
        }

        threadEnv->DeleteLocalRef(buffer);
        jvm->DetachCurrentThread();
    });
}

JNIEXPORT void JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_enableH265(JNIEnv *env, jclass clazz) {
    video::active_hevc_mode = 2;
}

JNIEXPORT jboolean JNICALL
Java_io_github_jqssun_displaymirror_job_SunshineServer_exitServer(JNIEnv *env, jclass clazz) {
    auto broadcast_shutdown_event = mail::man->event<bool>(mail::broadcast_shutdown);
    broadcast_shutdown_event->raise(true);
    if (stream::session::getRunningSessions() == 0) {
        return JNI_FALSE;
    }
    BOOST_LOG(info) << "Exiting Sunshine server"sv;
    for (int i = 0; i < 5; i++) {
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        if (stream::session::getRunningSessions() == 0) {
            return JNI_TRUE;
        }
    }
    return JNI_TRUE;
}

}

namespace sunshine_callbacks {
    void callJavaOnPinRequested() {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null"sv;
            return;
        }
        
        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer class ref is null"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }

        jmethodID onPinRequestedMethod = env->GetStaticMethodID(sunshineServerClass, "onPinRequested", "()V");
        if (onPinRequestedMethod == nullptr) {
            BOOST_LOG(error) << "Cannot find onPinRequested method"sv;
            jvm->DetachCurrentThread();
            return;
        }

        env->CallStaticVoidMethod(sunshineServerClass, onPinRequestedMethod);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void createVirtualDisplay(JNIEnv *env, jint width, jint height, jint frameRate, jint packetDuration, jobject surface, jboolean shouldMute) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null"sv;
            return;
        }

        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer class ref is null"sv;
            return;
        }

        jmethodID createVirtualDisplayMethod = env->GetStaticMethodID(sunshineServerClass, "createVirtualDisplay", "(IIIILandroid/view/Surface;Z)V");
        if (createVirtualDisplayMethod == nullptr) {
            BOOST_LOG(error) << "Cannot find createVirtualDisplay method"sv;
            jvm->DetachCurrentThread();
            return;
        }

        env->CallStaticVoidMethod(sunshineServerClass, createVirtualDisplayMethod, width, height, frameRate, packetDuration, surface, shouldMute);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void stopVirtualDisplay() {
        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null"sv;
            return;
        }

        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer class ref is null"sv;
            return;
        }

        jmethodID stopVirtualDisplayMethod = env->GetStaticMethodID(sunshineServerClass, "stopVirtualDisplay", "()V");
        if (stopVirtualDisplayMethod == nullptr) {
            BOOST_LOG(error) << "Cannot find stopVirtualDisplay method"sv;
            jvm->DetachCurrentThread();
            return;
        }

        env->CallStaticVoidMethod(sunshineServerClass, stopVirtualDisplayMethod);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void showEncoderError(const char* errorMessage) {
        if (jvm == nullptr || sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "JVM pointer or SunshineServer class ref is null"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }

        jmethodID showErrorMethod = env->GetStaticMethodID(sunshineServerClass, "showEncoderError", "(Ljava/lang/String;)V");
        if (showErrorMethod == nullptr) {
            BOOST_LOG(error) << "Cannot find showEncoderError method"sv;
            jvm->DetachCurrentThread();
            return;
        }

        jstring jErrorMessage = env->NewStringUTF(errorMessage);
        env->CallStaticVoidMethod(sunshineServerClass, showErrorMethod, jErrorMessage);
        env->DeleteLocalRef(jErrorMessage);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void captureVideoLoop(void *channel_data, safe::mail_t mail, const video::config_t& config, const audio::config_t& audioConfig) {
        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }
        auto shutdown_event = mail->event<bool>(mail::shutdown);
        auto idr_events = mail->event<bool>(mail::idr);
        // Log detailed client config
        BOOST_LOG(info) << "Client requested video config:"sv;
        BOOST_LOG(info) << "  - Resolution: "sv << config.width << "x"sv << config.height;
        BOOST_LOG(info) << "  - Frame rate: "sv << config.framerate;
        BOOST_LOG(info) << "  - Video format: "sv << (config.videoFormat == 1 ? "HEVC" : "H.264");
        BOOST_LOG(info) << "  - Chroma sampling: "sv << (config.chromaSamplingType == 1 ? "YUV 4:4:4" : "YUV 4:2:0");
        BOOST_LOG(info) << "  - Dynamic range: "sv << (config.dynamicRange ? "HDR" : "SDR");
        BOOST_LOG(info) << "  - Encoder color space mode: 0x"sv << std::hex << config.encoderCscMode << std::dec;
        
        // Get and log detailed color space info
        bool isHdr = false;
        video::sunshine_colorspace_t colorspace = colorspace_from_client_config(config, isHdr);
        BOOST_LOG(info) << "Color space config:"sv;
        BOOST_LOG(info) << "  - Color space: "sv << static_cast<int>(colorspace.colorspace);
        BOOST_LOG(info) << "  - Bit depth: "sv << colorspace.bit_depth;
        BOOST_LOG(info) << "  - Color range: "sv << (colorspace.full_range ? "Full" : "Limited");
        
        // Create MediaFormat
        AMediaFormat *format = AMediaFormat_new();
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, config.videoFormat == 1 ? "video/hevc" : "video/avc");

        auto encodeFrameRate = config.framerate < 60 ? 60 : config.framerate;
        // Base config unchanged
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, config.width);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, config.height);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, config.bitrate * 1000);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_OPERATING_RATE, encodeFrameRate);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CAPTURE_RATE, encodeFrameRate);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, encodeFrameRate);
        AMediaFormat_setInt32(format, "max-fps-to-encoder", encodeFrameRate);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 3); // keyframe interval (seconds)
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, 2130708361); // COLOR_FormatSurface
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_LATENCY, 0); // minimum latency
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COMPLEXITY, 10);
        AMediaFormat_setInt32(format, "max-bframes", 0);

        // Set encoding config
        if (config.videoFormat == 1) {
            if (colorspace.bit_depth == 10) {
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_PROFILE, 2); // HEVCProfileMain10
            } else {
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_PROFILE, 1); // HEVCProfileMain
            }
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_LEVEL, 65536); // HEVCMainTierLevel51
        } else {
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_PROFILE, 0x08); // HIGH profile
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_LEVEL, 0x200); // Level 4.2
            AMediaFormat_setInt32(format, "vendor.qti-ext-enc-low-latency.enable", 1);
        }

        // Set color space
        switch (colorspace.colorspace) {
            case video::colorspace_e::rec601:
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_STANDARD, 4); // COLOR_STANDARD_BT601_NTSC
                break;
            case video::colorspace_e::rec709:
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_STANDARD, 1); // COLOR_STANDARD_BT709
                break;
            case video::colorspace_e::bt2020:
            case video::colorspace_e::bt2020sdr:
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_STANDARD, 6); // COLOR_STANDARD_BT2020
                break;
        }

        // Set color range
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_RANGE, 
            colorspace.full_range ? 1 : 2); // 1=FULL, 2=LIMITED

        // Set bit depth
        if (isHdr) {
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_TRANSFER, 6); // COLOR_TRANSFER_ST2084
        } else {
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_TRANSFER, 3); // COLOR_TRANSFER_SDR_VIDEO
        }

        // Print final media format color config
        int32_t colorStandard = 0, colorRange = 0, colorTransfer = 0;
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_COLOR_STANDARD, &colorStandard);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_COLOR_RANGE, &colorRange);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_COLOR_TRANSFER, &colorTransfer);
        
        BOOST_LOG(info) << "Final media format color config:"sv;
        BOOST_LOG(info) << "  - COLOR_STANDARD: "sv << colorStandard;
        BOOST_LOG(info) << "  - COLOR_RANGE: "sv << colorRange << (colorRange == 1 ? " (FULL)" : " (LIMITED)");
        BOOST_LOG(info) << "  - COLOR_TRANSFER: "sv << colorTransfer;

        // Create encoder
        AMediaCodec *codec = AMediaCodec_createEncoderByType(config.videoFormat == 1 ? "video/hevc" : "video/avc");
        if (!codec) {
           // Create encoder
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, 1920);
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, 1080);
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_OPERATING_RATE, 60);
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CAPTURE_RATE, 60);
            AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, 60);
            AMediaFormat_setInt32(format, "max-fps-to-encoder", 60);
            codec = AMediaCodec_createEncoderByType("video/avc");
        }
        if (!codec) {
            BOOST_LOG(error) << "Failed to create encoder"sv;
            AMediaFormat_delete(format);
            return;
        }
        
        // Configure encoder
        media_status_t status = AMediaCodec_configure(codec, format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
        if (status != AMEDIA_OK) {
            std::string errorMsg = "Failed to configure encoder, error: " + std::to_string(status);
            BOOST_LOG(error) << errorMsg;
            showEncoderError(errorMsg.c_str());
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }
        
        // Get input Surface
        ANativeWindow* inputSurface;
        media_status_t surfaceStatus = AMediaCodec_createInputSurface(codec, &inputSurface);
        if (surfaceStatus != AMEDIA_OK) {
            BOOST_LOG(error) << "Failed to create input Surface, error: "sv << surfaceStatus;
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }
        
        // Convert ANativeWindow to Java Surface and create virtual display
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null, cannot create Surface"sv;
            ANativeWindow_release(inputSurface);
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }
        
        // Convert ANativeWindow to Java Surface
        jobject javaSurface = ANativeWindow_toSurface(env, inputSurface);
        if (javaSurface == nullptr) {
            BOOST_LOG(error) << "Failed to convert ANativeWindow to Surface"sv;
            jvm->DetachCurrentThread();
            ANativeWindow_release(inputSurface);
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }

        bool shouldMute = true;
        if (audioConfig.flags[audio::config_t::HOST_AUDIO]) {
            BOOST_LOG(info) << "Audio config: sound will play on host (Sunshine server)"sv;
            shouldMute = false;
        } else {
            BOOST_LOG(info) << "Audio config: sound will play on client (Moonlight)"sv;
        }
        
        // Call createVirtualDisplay, passing shouldMute
        createVirtualDisplay(env, config.width, config.height, config.framerate, audioConfig.packetDuration, javaSurface, shouldMute);
        
        // Start encoder
        status = AMediaCodec_start(codec);
        if (status != AMEDIA_OK) {
            BOOST_LOG(error) << "Failed to start encoder, error: "sv << status;
            env->DeleteLocalRef(javaSurface);
            jvm->DetachCurrentThread();
            ANativeWindow_release(inputSurface);
            AMediaCodec_delete(codec);
            AMediaFormat_delete(format);
            return;
        }
        
        // Encoding loop
        std::vector<uint8_t> codecConfigData;  // stores complete codec config data
        int64_t frameIndex = 0;
        
        while (!shutdown_event->peek()) {
            bool requested_idr_frame = false;
            if (idr_events->peek()) {
                requested_idr_frame = true;
                idr_events->pop();
            }

            if (requested_idr_frame) {
                // Request sync frame (IDR) via Bundle params
                AMediaFormat* params = AMediaFormat_new();
                AMediaFormat_setInt32(params, "request-sync", 0);
                media_status_t status = AMediaCodec_setParameters(codec, params);
                if (status != AMEDIA_OK) {
                    BOOST_LOG(warning) << "Failed to request IDR frame, error: "sv << status;
                } else {
                    BOOST_LOG(info) << "IDR frame requested"sv;
                }
                AMediaFormat_delete(params);
            }
            
            // Dequeue output buffer with 1s timeout
            AMediaCodecBufferInfo bufferInfo;
            ssize_t outputBufferIndex = AMediaCodec_dequeueOutputBuffer(codec, &bufferInfo, 1000000); // 1s = 1000000us
            
            if (outputBufferIndex >= 0) {
                // Got valid output buffer
                size_t bufferSize = bufferInfo.size;
                uint8_t* buffer = nullptr;
                size_t out_size = 0;
                
                // Get buffer data
                buffer = AMediaCodec_getOutputBuffer(codec, outputBufferIndex, &out_size);
                if (buffer != nullptr) {
                    // Process encoded data
                    if (bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) {
                        // Codec config data (SPS/PPS)
                        BOOST_LOG(info) << "Received codec config data, size: "sv << bufferSize;
                        
                        // Save entire config data
                        codecConfigData.assign(buffer, buffer + bufferSize);
                        BOOST_LOG(info) << "Saved complete codec config data, size: "sv << codecConfigData.size();
                    } else {
                        // Normal encoded frame
                        bool isKeyFrame = (bufferInfo.flags & AMEDIACODEC_BUFFER_FLAG_KEY_FRAME) != 0;
                        BOOST_LOG(verbose) << "Received " << (isKeyFrame ? "keyframe" : "frame") << ", size: "sv << bufferSize;
                        frameIndex++;
                        
                        if(isKeyFrame) {
                            // Prepend codec config data for keyframes
                            if (!codecConfigData.empty()) {
                                // Build complete frame with config + keyframe
                                std::vector<uint8_t> frameData;
                                
                                // Append config data
                                frameData.insert(frameData.end(), codecConfigData.begin(), codecConfigData.end());
                                
                                // Append keyframe data
                                frameData.insert(frameData.end(), buffer, buffer + bufferSize);

                                BOOST_LOG(verbose) << "Sending keyframe (with config data), total size: "sv << frameData.size();
                                // Send complete keyframe data
                                stream::postFrame(std::move(frameData), frameIndex, true, channel_data);
                            } else {
                                BOOST_LOG(error) << "No codec config data, cannot send complete keyframe"sv;
                            }
                        } else {
                            std::vector<uint8_t> frameData;
                            frameData.insert(frameData.end(), buffer, buffer + bufferSize);
                            stream::postFrame(std::move(frameData), frameIndex, false, channel_data);
                        }
                    }
                }
                
                // Release output buffer
                AMediaCodec_releaseOutputBuffer(codec, outputBufferIndex, false);
            } else if (outputBufferIndex == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                BOOST_LOG(verbose) << "Encoder timeout, waiting for output buffer"sv;
                continue;
            } else if (outputBufferIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                // Output format changed
                AMediaFormat* format = AMediaCodec_getOutputFormat(codec);
                BOOST_LOG(info) << "Encoder output format changed"sv;
                // Can get more info from format
                AMediaFormat_delete(format);
            } else if (outputBufferIndex == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                // Output buffers changed
                BOOST_LOG(info) << "Encoder output buffers changed"sv;
                // Usually safe to ignore on newer NDK versions, AMediaCodec_getOutputBuffer handles buffer changes automatically
            } else {
                // Error
                BOOST_LOG(error) << "Encoder error, code: "sv << outputBufferIndex;
                break;
            }
        }

        stopVirtualDisplay();
        // Stop encoder
        AMediaCodec_stop(codec);
        
        // Clean up resources
        ANativeWindow_release(inputSurface);
        AMediaCodec_delete(codec);
        AMediaFormat_delete(format);
        
        // Clean up Java Surface ref
        jvm->DetachCurrentThread();
    }

    void captureAudioLoop(void *channel_data, safe::mail_t mail, const audio::config_t& config) {
        samples = std::make_shared<audio::sample_queue_t::element_type>(30);
        encodeThread(samples, config, channel_data);
    }

    float from_netfloat(netfloat f) {
        return boost::endian::endian_load<float, sizeof(float), boost::endian::order::little>(f);
    }

    void callJavaOnTouch(SS_TOUCH_PACKET* touchPacket) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null"sv;
            return;
        }
        
        if (sunshineMouseClass == nullptr || handleTouchPacketMethod == nullptr) {
            BOOST_LOG(error) << "SunshineServer class ref or method ID is null"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }

        // Use cached method ID
        env->CallStaticVoidMethod(sunshineMouseClass, handleTouchPacketMethod,
                                 static_cast<int>(touchPacket->eventType),
                                 static_cast<int>(touchPacket->rotation),
                                 static_cast<int>(touchPacket->pointerId),
                                 from_netfloat(touchPacket->x),
                                 from_netfloat(touchPacket->y),
                                 from_netfloat(touchPacket->pressureOrDistance),
                                 from_netfloat(touchPacket->contactAreaMajor),
                                 from_netfloat(touchPacket->contactAreaMinor));

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaOnAbsMouseMove(NV_ABS_MOUSE_MOVE_PACKET* packet) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null"sv;
            return;
        }
        
        if (sunshineMouseClass == nullptr || handleAbsMouseMoveMethod == nullptr) {
            BOOST_LOG(error) << "SunshineServer class ref or method ID is null"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }

        // Use cached method ID
        float x = util::endian::big(packet->x);
        float y = util::endian::big(packet->y);
        float width = (float) util::endian::big(packet->width);
        float height = (float) util::endian::big(packet->height);
        
        BOOST_LOG(info) << "Calling Java mouse move handler: "sv << x << ","sv << y << " in "sv << width << "*"sv << height;

        env->CallStaticVoidMethod(sunshineMouseClass, handleAbsMouseMoveMethod, x, y, width, height);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaOnRelMouseMove(NV_REL_MOUSE_MOVE_PACKET* packet) {
        if (jvm == nullptr || sunshineMouseClass == nullptr || handleRelMouseMoveMethod == nullptr) {
            return;
        }

        JNIEnv *env;
        if (jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return;
        }

        jshort dx = util::endian::big(packet->deltaX);
        jshort dy = util::endian::big(packet->deltaY);
        env->CallStaticVoidMethod(sunshineMouseClass, handleRelMouseMoveMethod, dx, dy);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
        jvm->DetachCurrentThread();
    }

    void callJavaOnMouseButton(std::uint8_t button, bool release) {
        BOOST_LOG(info) << "on mouse button "sv << static_cast<int>(button) << " release "sv << release;
        
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null"sv;
            return;
        }
        
        if (sunshineMouseClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer class ref is null"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }

        // Call different Java methods based on button type
        if (button == BUTTON_LEFT && handleLeftMouseButtonMethod != nullptr) {
            env->CallStaticVoidMethod(sunshineMouseClass, handleLeftMouseButtonMethod, release);
        } else {
            // More button types can be handled here
            BOOST_LOG(info) << "Unhandled mouse button type: "sv << static_cast<int>(button);
        }

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaOnMirrorClientDiscovered(std::string mirrorClient) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null"sv;
            return;
        }

        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer class ref is null"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }

        jmethodID onClientDiscoveredMethod = env->GetStaticMethodID(sunshineServerClass, "onMirrorClientDiscovered", "(Ljava/lang/String;)V");
        if (onClientDiscoveredMethod == nullptr) {
            BOOST_LOG(error) << "Cannot find onMirrorClientDiscovered method"sv;
            jvm->DetachCurrentThread();
            return;
        }

        jstring jClientName = env->NewStringUTF(mirrorClient.c_str());
        env->CallStaticVoidMethod(sunshineServerClass, onClientDiscoveredMethod, jClientName);
        env->DeleteLocalRef(jClientName);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaSetMirrorServerUuid(std::string uuid) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null"sv;
            return;
        }

        if (sunshineServerClass == nullptr) {
            BOOST_LOG(error) << "SunshineServer class ref is null"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }

        jmethodID setServerUuidMethod = env->GetStaticMethodID(sunshineServerClass, "setMirrorServerUuid", "(Ljava/lang/String;)V");
        if (setServerUuidMethod == nullptr) {
            BOOST_LOG(error) << "Cannot find setMirrorServerUuid method"sv;
            jvm->DetachCurrentThread();
            return;
        }

        jstring jUuid = env->NewStringUTF(uuid.c_str());
        env->CallStaticVoidMethod(sunshineServerClass, setServerUuidMethod, jUuid);
        env->DeleteLocalRef(jUuid);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }

    void callJavaOnKeyboard(uint16_t modcode, bool release, uint8_t flags) {
        if (jvm == nullptr) {
            BOOST_LOG(error) << "JVM pointer is null"sv;
            return;
        }
        
        if (sunshineKeyboardClass == nullptr || handleKeyboardMethod == nullptr) {
            BOOST_LOG(error) << "SunshineKeyboard class ref or method ID is null"sv;
            return;
        }

        JNIEnv *env;
        jint result = jvm->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            BOOST_LOG(error) << "Failed to attach to Java thread"sv;
            return;
        }

        // Use cached class ref and method ID
        env->CallStaticVoidMethod(sunshineKeyboardClass, handleKeyboardMethod, 
            static_cast<jint>(modcode),
            static_cast<jboolean>(release),
            static_cast<jint>(flags));

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }

        jvm->DetachCurrentThread();
    }
}