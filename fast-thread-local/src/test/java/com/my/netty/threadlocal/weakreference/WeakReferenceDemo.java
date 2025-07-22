package com.my.netty.threadlocal.weakreference;

import java.lang.ref.WeakReference;

public class WeakReferenceDemo {

    public static void main(String[] args) throws InterruptedException {
        UserPackage userPackage = new UserPackage("user-package");

        User user = new User();
        user.setStrongRef(new StrongReference<>(userPackage));
        user.setWeakRef(new WeakReference<>(userPackage));

        // 斩断root与userPackage的关联
        userPackage = null;
        System.gc();

        System.out.println("strongRef=" + user.getStrongRef().get());  // 存在
        System.out.println("weakRef=" + user.getWeakRef().get()); // 存在

        System.gc();

        System.out.println("strongRef=" + user.getStrongRef().get()); // 存在
        System.out.println("weakRef=" + user.getWeakRef().get()); // 存在

        // 斩断strongRef与userPackage的关联
        user.setStrongRef(null);

        System.out.println("strongRef=" + user.getStrongRef()); // null
        System.out.println("weakRef=" + user.getWeakRef().get()); // 存在

        System.gc();

        // gc后，因为userPackage对象的强引用全都不存在了，所以userPackage被回收掉(即使weakRef还存在)
        System.out.println("strongRef=" + user.getStrongRef());  // null
        System.out.println("weakRef=" + user.getWeakRef().get());  // null
    }
}
