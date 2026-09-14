package com.xbu.esportscenter.core.session;

/**
 * Platform-neutral lifecycle for a game session.
 *
 * This enum deliberately contains no Android/vendor API references. Platform adapters
 * may observe the OS and request transitions, but Core owns the semantic state names.
 */
public enum GameSessionState {
    IDLE,
    PREPARING,
    LAUNCHING,
    RUNNING,
    SUSPENDED,
    ENDED
}
