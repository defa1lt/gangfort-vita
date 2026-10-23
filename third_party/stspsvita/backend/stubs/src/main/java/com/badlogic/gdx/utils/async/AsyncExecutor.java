package com.badlogic.gdx.utils.async;

import com.badlogic.gdx.utils.Disposable;

/** Runs LibGDX asset jobs inline; the Vita game loop is single-threaded. */
public final class AsyncExecutor implements Disposable {
    public AsyncExecutor(int maxConcurrent) {}

    public <T> AsyncResult<T> submit(AsyncTask<T> task) {
        try {
            return new AsyncResult<>(task.call(), null);
        } catch (Throwable failure) {
            return new AsyncResult<>(null, failure);
        }
    }

    @Override
    public void dispose() {}
}
