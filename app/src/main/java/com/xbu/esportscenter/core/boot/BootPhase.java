package com.xbu.esportscenter.core.boot;

/** Platform-neutral startup milestones. */
public enum BootPhase {
    CORE_INIT,
    CAPABILITY_REGISTRY_READY,
    REDMAGIC_PROBE,
    SESSION_RUNTIME_READY,
    WEBVIEW_READY,
    BOOT_COMPLETE
}
