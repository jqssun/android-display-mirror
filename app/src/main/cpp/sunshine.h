#pragma once
#include <cstdint>
#include "stream.h"
#include "moonlight-common-c/src/Input.h"

namespace sunshine_callbacks {
    void callJavaOnPinRequested();
    void captureVideoLoop(void *channel_data, safe::mail_t mail, const video::config_t& config, const audio::config_t& audioConfig);
    void captureAudioLoop(void *channel_data, safe::mail_t mail, const audio::config_t& config);
    void callJavaOnTouch(std::int64_t session, SS_TOUCH_PACKET* touchPacket);
    void callJavaOnAbsMouseMove(std::int64_t session, NV_ABS_MOUSE_MOVE_PACKET* packet);
    void callJavaOnRelMouseMove(std::int64_t session, NV_REL_MOUSE_MOVE_PACKET* packet);
    void callJavaOnMouseButton(std::int64_t session, std::uint8_t button, bool release);
    void callJavaOnMirrorClientDiscovered(std::string mirrorClient);
    void callJavaSetMirrorServerUuid(std::string uuid);
    void callJavaOnKeyboard(uint16_t modcode, bool release, uint8_t flags);
    void callJavaOnGamepad(uint32_t buttonFlags, uint8_t lt, uint8_t rt, int16_t lsX, int16_t lsY, int16_t rsX, int16_t rsY);
    void callJavaOnVideoFrame(const uint8_t* data, size_t size, bool isKeyframe);
}