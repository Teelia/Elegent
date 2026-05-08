package com.datalabeling.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 查询任务20的标签绑定及对应配置
 */
public class QueryTask20Labels {

    private static final String URL = "jdbc:mysql://122.152.221.87:13308/data_labeling?useSSL=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "ea6741b12e0bea64";

    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            Statement stmt = conn.createStatement();

            System.out.println("=== 任务20绑定的标签 ===");
            java.sql.ResultSet rs = stmt.executeQuery(
                "SELECT analysis_task_id, label_id, label_name, label_version " +
                "FROM analysis_task_labels " +
                "WHERE analysis_task_id = 20"
            );

            while (rs.next()) {
                int taskId = rs.getInt("analysis_task_id");
                int labelId = rs.getInt("label_id");
                String labelName = rs.getString("label_name");
                int labelVersion = rs.getInt("label_version");

                System.out.println("任务ID: " + taskId);
                System.out.println("绑定标签ID: " + labelId);
                System.out.println("标签名称: " + labelName);
                System.out.println("标签版本: " + labelVersion);
                System.out.println();
            }
            rs.close();

            System.out.println("=== 标签ID 19 和 20 的配置对比 ===");
            rs = stmt.executeQuery(
                "SELECT id, name, version, type, extractor_config, extract_fields " +
                "FROM labels " +
                "WHERE id IN (19, 20) " +
                "ORDER BY id"
            );

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String version = rs.getString("version");
                String type = rs.getString("type");
                String extractorConfig = rs.getString("extractor_config");
                String extractFields = rs.getString("extract_fields");

                System.out.println("标签ID: " + id);
                System.out.println("标签名称: " + name);
                System.out.println("标签版本: " + version);
                System.out.println("标签类型: " + type);
                System.out.println("extract_fields: " + extractFields);
                System.out.println("extractor_config: " + extractorConfig);

                // 解析extractorConfig
                if (extractorConfig != null && !extractorConfig.isEmpty()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode config = mapper.readTree(extractorConfig);
                        System.out.println("  extractorType: " + config.path("extractorType").asText());

                        if (config.has("options")) {
                            com.fasterxml.jackson.databind.JsonNode options = config.path("options");
                            System.out.println("  include18Digit: " + options.path("include18Digit").asBoolean());
                            System.out.println("  include15Digit: " + options.path("include15Digit").asBoolean());
                            System.out.println("  includeLoose: " + options.path("includeLoose").asBoolean());
                        }
                    } catch (Exception e) {
                        System.out.println("配置解析失败: " + e.getMessage());
                    }
                }
                System.out.println("---");
            }
            rs.close();

            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.err.println("查询失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
