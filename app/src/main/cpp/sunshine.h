#pragma once
#include "stream.h"
#include "moonlight-common-c/src/Input.h"

namespace sunshine_callbacks {
    void callJavaOnPinRequested();
    void captureVideoLoop(void *channel_data, safe::mail_t mail, const video::config_t& config, const audio::config_t& audioConfig);
    void captureAudioLoop(void *channel_data, safe::mail_t mail, const audio::config_t& config);
    void callJavaOnTouch(SS_TOUCH_PACKET* touchPacket);
    void callJavaOnAbsMouseMove(NV_ABS_MOUSE_MOVE_PACKET* packet);
    void callJavaOnRelMouseMove(NV_REL_MOUSE_MOVE_PACKET* packet);
    void callJavaOnMouseButton(std::uint8_t button, bool release);
    void callJavaOnMirrorClientDiscovered(std::string mirrorClient);
    void callJavaSetMirrorServerUuid(std::string uuid);
    void callJavaOnKeyboard(uint16_t modcode, bool release, uint8_t flags);
    void callJavaOnVideoFrame(const uint8_t* data, size_t size, bool isKeyframe);
}