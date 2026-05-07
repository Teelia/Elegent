package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

/**
 * 检查提示词编码
 */
public class CheckPromptEncoding {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8mb4";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);

            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT id, name, code, LEFT(template, 100) as template_preview, " +
                "HEX(template) as template_hex FROM system_prompts WHERE code = 'default_free_form_extraction'"
            );

            if (rs.next()) {
                System.out.println("ID: " + rs.getInt("id"));
                System.out.println("Name: " + rs.getString("name"));
                System.out.println("Code: " + rs.getString("code"));
                System.out.println("\nTemplate Preview: " + rs.getString("template_preview"));

                String hex = rs.getString("template_hex");
                System.out.println("\nFirst 200 chars in HEX: " + hex.substring(0, Math.min(200, hex.length())));

                // 解析前几个字符的 HEX 看是否是正确的 UTF-8
                System.out.println("\n解析前几个字符:");
                String template = rs.getString("template_preview");
                for (int i = 0; i < Math.min(20, template.length()); i++) {
                    char c = template.charAt(i);
                    System.out.println("  '" + c + "' (U+" + Integer.toHexString(c) + ")");
                }
            }
            rs.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
