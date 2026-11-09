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

find_library(log-lib log REQUIRED)
find_library(egl-lib EGL REQUIRED)
find_library(gles-lib GLESv3 REQUIRED)
find_library(dl-lib dl REQUIRED)

target_link_libraries(mbmods PRIVATE
    ${egl-lib}
    ${gles-lib}
    ${dl-lib}
    ${log-lib}
)

target_link_options(mbmods PRIVATE
    -Wl,--gc-sections
    -Wl,-z,relro
    -Wl,-z,now
    -Wl,--no-undefined
)
