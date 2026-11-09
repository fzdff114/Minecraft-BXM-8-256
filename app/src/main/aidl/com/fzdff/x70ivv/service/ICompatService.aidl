package com.fzdff.x70ivv.service;

interface ICompatService {
    void destroy() = 16777114;
    void exit() = 1;
    String inspect(String targetPackage);
    String applyAngle(String targetPackage);
    String restoreAngle(String targetPackage, String backupEncoded);
    String collectGraphicsLogs();
}
