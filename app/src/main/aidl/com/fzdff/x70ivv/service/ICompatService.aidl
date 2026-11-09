package com.fzdff.x70ivv.service;

interface ICompatService {
    void destroy() = 16777114;
    void exit() = 1;
    String inspect(String targetPackage) = 2;
    String applyAngle(String targetPackage) = 3;
    String restoreAngle(String targetPackage, String backupEncoded) = 4;
    String collectGraphicsLogs() = 5;
}
