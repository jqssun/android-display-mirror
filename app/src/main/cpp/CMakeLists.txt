cmake_minimum_required(VERSION 3.24.0)

project("sunshine")

include(dependencies/Boost_Sunshine.cmake)
add_subdirectory(dependencies/moonlight-common-c/enet)
add_subdirectory(dependencies/openssl-cmake)
add_subdirectory(dependencies/Simple-Web-Server)

# Build opus from source
set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
add_subdirectory(dependencies/opus)

add_library(
        sunshine
        SHARED
        sunshine.cpp
        logging.cpp
        network.cpp
        config.cpp
        platform/common.cpp
        crypto.cpp
        dependencies/nanors/rs.c
        stat_trackers.cpp
        globals.cpp
        stream.cpp
        rtsp.cpp
        dependencies/moonlight-common-c/src/RtspParser.c
        audio.cpp
        nvhttp.cpp
        file_handler.cpp
        input.cpp
        video.cpp
        video_colorspace.cpp
)

target_include_directories(sunshine PRIVATE
        dependencies/moonlight-common-c/enet/include
        dependencies/nanors/deps/obl
        dependencies/nanors
        dependencies
        ${Boost_INCLUDE_DIRS})

target_link_options(sunshine PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(
        sunshine
        android
        mediandk
        log
        enet
        opus
        ssl
        crypto
        simple-web-server
        ${Boost_LIBRARIES}
)
