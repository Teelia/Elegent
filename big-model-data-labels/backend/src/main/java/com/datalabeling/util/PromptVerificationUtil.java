package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

/**
 * 提示词验证工具（使用JDBC直接查询）
 */
public class PromptVerificationUtil {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        try {
            System.out.println("=== 验证数据库中的提示词 ===\n");

            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);

            // 1. 检查表是否存在
            boolean tableExists = checkTableExists(conn);
            System.out.println("system_prompts 表存在: " + tableExists);
            if (!tableExists) {
                System.out.println("错误: system_prompts 表不存在！");
                System.exit(1);
            }

            // 2. 查询所有提示词
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT id, name, code, prompt_type, is_system_default, is_active, " +
                "LENGTH(template) as template_length FROM system_prompts ORDER BY id"
            );

            System.out.println("\n数据库中的提示词列表:\n");
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println("------------------------------------------------");
                System.out.println("ID: " + rs.getInt("id"));
                System.out.println("名称: " + rs.getString("name"));
                System.out.println("代码: " + rs.getString("code"));
                System.out.println("类型: " + rs.getString("prompt_type"));
                System.out.println("系统默认: " + rs.getBoolean("is_system_default"));
                System.out.println("启用: " + rs.getBoolean("is_active"));
                System.out.println("模板长度: " + rs.getInt("template_length") + " 字符");
                System.out.println();
            }
            System.out.println("总计: " + count + " 个提示词\n");
            rs.close();

            // 3. 检查关键提示词
            System.out.println("=== 关键提示词检查 ===\n");
            checkPromptExists(conn, "default_classification", "默认分类判断模板");
            checkPromptExists(conn, "default_extraction", "默认LLM结构化提取模板");
            checkPromptExists(conn, "default_free_form_extraction", "默认自由提取模板");
            checkPromptExists(conn, "classification_with_confidence", "默认分类判断模板（带信心度）");
            checkPromptExists(conn, "default_enhancement", "默认二次强化分析模板");

            // 4. 显示自由提取模板的详细内容
            System.out.println("=== 自由提取模板内容预览 ===\n");
            ResultSet templateRs = conn.createStatement().executeQuery(
                "SELECT template FROM system_prompts WHERE code = 'default_free_form_extraction'"
            );
            if (templateRs.next()) {
                String template = templateRs.getString("template");
                System.out.println(template);
            } else {
                System.out.println("未找到自由提取模板");
            }
            templateRs.close();

            conn.close();
            System.exit(0);

        } catch (Exception e) {
            System.err.println("验证失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean checkTableExists(Connection conn) throws Exception {
        ResultSet rs = conn.getMetaData().getTables(null, null, "system_prompts", null);
        boolean exists = rs.next();
        rs.close();
        return exists;
    }

    private static void checkPromptExists(Connection conn, String code, String expectedName) throws Exception {
        ResultSet rs = conn.createStatement().executeQuery(
            "SELECT name, prompt_type, is_active FROM system_prompts WHERE code = '" + code + "'"
        );
        if (rs.next()) {
            System.out.println("✓ " + code);
            System.out.println("  名称: " + rs.getString("name"));
            System.out.println("  类型: " + rs.getString("prompt_type") + ", 启用: " + rs.getBoolean("is_active"));
        } else {
            System.out.println("✗ " + code + " - 未找到 (预期: " + expectedName + ")");
        }
        System.out.println();
        rs.close();
    }
}
