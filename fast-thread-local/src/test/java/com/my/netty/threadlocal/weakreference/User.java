package com.my.netty.threadlocal.weakreference;

import java.lang.ref.WeakReference;

public class User {

    /**
     * 为了与WeakReference对称，本质上就是 private UserPackage strongRef;
     * */
    private StrongReference<UserPackage> strongRef;

    private WeakReference<UserPackage> weakRef;

    public StrongReference<UserPackage> getStrongRef() {
        return strongRef;
    }

    public void setStrongRef(StrongReference<UserPackage> strongRef) {
        this.strongRef = strongRef;
    }

    public WeakReference<UserPackage> getWeakRef() {
        return weakRef;
    }

    public void setWeakRef(WeakReference<UserPackage> weakRef) {
        this.weakRef = weakRef;
    }
}
