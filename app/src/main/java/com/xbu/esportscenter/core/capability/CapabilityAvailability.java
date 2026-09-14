package com.xbu.esportscenter.core.capability;

/**
 * Stable capability availability state shared by Core and platform adapters.
 */
public enum CapabilityAvailability {
    AVAILABLE,
    UNAVAILABLE,
    DENIED,
    DEAD,
    UNSUPPORTED,
    UNKNOWN
}
