package com.badlogic.gdx.utils;

import com.gangfort.vita.GangfortObjectFactory;

/** Uses the same explicit constructors as the rest of the Vita AOT runtime. */
public class ReflectionPool<T> extends Pool<T> {
    private final Class<T> type;

    public ReflectionPool(Class<T> type) {
        this(type, 16, Integer.MAX_VALUE);
    }

    public ReflectionPool(Class<T> type, int initialCapacity) {
        this(type, initialCapacity, Integer.MAX_VALUE);
    }

    public ReflectionPool(Class<T> type, int initialCapacity, int max) {
        super(initialCapacity, max);
        this.type = type;
    }

    @Override
    public T newObject() {
        return GangfortObjectFactory.newInstance(type);
    }
}
