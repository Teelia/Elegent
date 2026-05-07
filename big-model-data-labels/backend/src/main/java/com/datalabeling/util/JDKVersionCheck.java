package com.datalabeling.util;

/**
 * JDK版本检查工具
 * 确认当前Java运行环境
 */
public class JDKVersionCheck {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("JDK版本检查");
        System.out.println("========================================\n");

        // Java版本
        System.out.println("Java版本信息:");
        System.out.println("  java.version: " + System.getProperty("java.version"));
        System.out.println("  java.vendor: " + System.getProperty("java.vendor"));
        System.out.println("  java.home: " + System.getProperty("java.home"));
        System.out.println("  java.class.version: " + System.getProperty("java.class.version"));
        System.out.println("  java.specification.version: " + System.getProperty("java.specification.version"));

        // 编译器版本
        System.out.println("\n编译器信息:");
        System.out.println("  java.compiler: " + System.getProperty("java.compiler"));

        // 检查是否为JDK 1.8
        String javaVersion = System.getProperty("java.version");
        boolean isJava8 = javaVersion.startsWith("1.8");

        System.out.println("\n检查结果:");
        if (isJava8) {
            System.out.println("  ✅ 当前JDK版本正确: 1.8");
        } else {
            System.out.println("  ⚠️  当前JDK版本: " + javaVersion);
            System.out.println("  ⚠️  期望JDK版本: 1.8");
            System.out.println("\n建议操作:");
            System.out.println("  1. 检查 JAVA_HOME 环境变量");
            System.out.println("  2. 设置 PATH 使用 JDK 1.8");
            System.out.println("  3. 或在IDE中配置项目JDK为1.8");
        }

        System.out.println("\n========================================");
    }
}
