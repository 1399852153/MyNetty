package com.my.netty.threadlocal.weakreference;

public class UserPackage {

    private String packageName;

    public UserPackage(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public String toString() {
        return "UserPackage{" +
            "packageName='" + packageName + '\'' +
            '}';
    }
}
