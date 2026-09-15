package com.xbu.esportscenter.core.playground;

/**
 * Stable semantic events for model-rendered Hardware Playground scenes.
 *
 * Core owns the event vocabulary only. Rendering, DCC provenance, WebView resources, audio and
 * haptics remain adapter concerns.
 */
public enum HardwarePhysicalEvent {
    SHOULDER_LEFT_FOCUS,
    SHOULDER_RIGHT_FOCUS,
    MOTION_TILT,
    GLASS_REVEAL,
    DETENT_STEP,
    RATCHET_TOOTH,
    LATCH_RELEASE,
    THERMAL_SPINUP,
    IGNITION
}
