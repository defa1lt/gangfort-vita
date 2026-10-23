package com.badlogic.gdx.utils.async;

import com.badlogic.gdx.utils.GdxRuntimeException;

/** Completed result used by the single-threaded Vita AssetManager. */
public final class AsyncResult<T> {
    private final T value;
    private final Throwable failure;

    AsyncResult(T value, Throwable failure) {
        this.value = value;
        this.failure = failure;
    }

    public T get() {
        if (failure != null) throw new GdxRuntimeException("Vita asset task failed", failure);
        return value;
    }

    public boolean isDone() {
        return true;
    }
}
