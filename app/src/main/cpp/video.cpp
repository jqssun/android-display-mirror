#include "video.h"
#include "config.h"

namespace video {  
  int active_hevc_mode = config::video.hevc_mode;
  int active_av1_mode = config::video.av1_mode;
  bool last_encoder_probe_supported_ref_frames_invalidation = false;
  std::array<bool, 3> last_encoder_probe_supported_yuv444_for_codec = {};  // 0 - H.264, 1 - HEVC, 2 - AV1
}
