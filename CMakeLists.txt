cmake_minimum_required(VERSION 3.22.1)
project(mbloader_water_reflections LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(mbmods SHARED
    native/mbmods_wrapper.cpp
    native/water_reflections.cpp
)

# MB Loader loads the native library as libmods.so.
set_target_properties(mbmods PROPERTIES
    OUTPUT_NAME "mods"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN YES
)

target_compile_options(mbmods PRIVATE
    -Wall -Wextra -Wpedantic
    -fno-exceptions
    -fno-rtti
    -ffunction-sections
    -fdata-sections
)

target_compile_definitions(mbmods PRIVATE
    WATER_REFLECTIONS_VERSION="0.1.0"
)

find_library(LOG_LIB log REQUIRED)
find_library(EGL_LIB EGL REQUIRED)
find_library(GLES_LIB GLESv3 REQUIRED)
find_library(DL_LIB dl REQUIRED)

target_link_libraries(mbmods PRIVATE
    ${EGL_LIB}
    ${GLES_LIB}
    ${DL_LIB}
    ${LOG_LIB}
)

target_link_options(mbmods PRIVATE
    -Wl,--gc-sections
    -Wl,-z,relro
    -Wl,-z,now
    -Wl,--no-undefined
)
