package com.jrobertgardzinski.memes.application;

import java.util.function.Supplier;

/**
 * Lets an upload through when there is room for its bytes. A port: how much room there is, and
 * what a full house answers, is the infrastructure's business.
 */
public interface UploadGate {

    <T> T admit(Supplier<T> upload);
}
