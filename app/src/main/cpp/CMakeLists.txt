cmake_minimum_required(VERSION 3.24.0)

project("sunshine")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffile-prefix-map=${CMAKE_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=. -ffile-prefix-map=${ANDROID_NDK}=.")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffile-prefix-map=${CMAKE_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=. -ffile-prefix-map=${ANDROID_NDK}=.")

set(_page16 "-Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${_page16}")
set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} ${_page16}")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${_page16}")

include(dependencies/Boost_Sunshine.cmake)
add_subdirectory(dependencies/moonlight-common-c/enet)
set(BUILD_OPENSSL ON CACHE BOOL "Build OpenSSL from source" FORCE)
set(OPENSSL_BUILD_VERSION "3.5.5" CACHE STRING "OpenSSL version" FORCE)
set(CROSS_ANDROID ON CACHE BOOL "Cross-compiling for Android" FORCE)
set(FORWARD_ANDROID_NDK_ROOT "${ANDROID_NDK}" CACHE STRING "" FORCE) # ci
list(PREPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)
add_subdirectory(dependencies/openssl-cmake)
add_subdirectory(dependencies/Simple-Web-Server)

# build opus from source
set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)

# pin opus version for fdroid
set(__opus_version INCLUDED)
function(get_package_version PACKAGE_VERSION PROJECT_VERSION)
  set(${PACKAGE_VERSION} "unknown" PARENT_SCOPE)
  set(${PROJECT_VERSION} "0" PARENT_SCOPE)
endfunction()
add_subdirectory(dependencies/opus)

add_library(
        sunshine
        SHARED
        sunshine.cpp
        airplay_bridge.cpp
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
